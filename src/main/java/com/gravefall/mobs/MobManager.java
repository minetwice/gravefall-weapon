package com.gravefall.mobs;

import com.gravefall.GravefallPlugin;
import com.gravefall.Items;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Frog;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The five custom Soul Dimension mobs (v2):
 *
 *  - FROST FROG    (base: real Frog, invisible)   - custom 3D model rider.
 *                   Lunges its long spectral tongue, damaging + freezing players ~2s.
 *  - SOUL WISP     (pure display entity)           - glowing cyan orb with orbit shards.
 *  - GRAVE SENTINEL(base: WitherSkeleton, invisible)- armored knight model, guards the kingdom.
 *  - VOID MOTH     (base: Bat, invisible)          - dark moth with cyan wings, drifts around.
 *  - CRYO SPIDER   (base: Spider, invisible)       - icy spider, fast hunter.
 *
 * Custom geometry + textures come from the Gravefall resource pack (paper icons
 * with custom model data 7405-7409 shown on display entities riding the bases).
 */
public class MobManager implements Listener {

    private final GravefallPlugin plugin;
    private final NamespacedKey frostKey;
    private final NamespacedKey wispKey;
    private final NamespacedKey sentinelKey;
    private final NamespacedKey mothKey;
    private final NamespacedKey spiderKey;
    private final Map<UUID, Long> frogAttackCd = new HashMap<>();

    public MobManager(GravefallPlugin plugin) {
        this.plugin = plugin;
        this.frostKey = new NamespacedKey(plugin, "frost_frog");
        this.wispKey = new NamespacedKey(plugin, "soul_wisp");
        this.sentinelKey = new NamespacedKey(plugin, "grave_sentinel");
        this.mothKey = new NamespacedKey(plugin, "void_moth");
        this.spiderKey = new NamespacedKey(plugin, "cryo_spider");
    }

    // ------------------------------------------------------------------
    // spawning
    // ------------------------------------------------------------------

    public void start() {
        // population
        new BukkitRunnable() {
            @Override
            public void run() {
                World dim = plugin.getDimensionManager().getWorld();
                if (dim == null || dim.getPlayers().isEmpty()) {
                    return;
                }
                int frogs = 0;
                int wisps = 0;
                int sentinels = 0;
                int moths = 0;
                int spiders = 0;
                for (Entity e : dim.getEntities()) {
                    var pdc = e.getPersistentDataContainer();
                    if (pdc.has(frostKey, PersistentDataType.BYTE)) {
                        frogs++;
                    } else if (pdc.has(wispKey, PersistentDataType.BYTE)) {
                        wisps++;
                    } else if (pdc.has(sentinelKey, PersistentDataType.BYTE)) {
                        sentinels++;
                    } else if (pdc.has(mothKey, PersistentDataType.BYTE)) {
                        moths++;
                    } else if (pdc.has(spiderKey, PersistentDataType.BYTE)) {
                        spiders++;
                    }
                }
                int wantFrogs = plugin.getConfig().getInt("mobs.frost-frogs", 8);
                int wantWisps = plugin.getConfig().getInt("mobs.soul-wisps", 8);
                int wantSentinels = plugin.getConfig().getInt("mobs.grave-sentinels", 4);
                int wantMoths = plugin.getConfig().getInt("mobs.void-moths", 6);
                int wantSpiders = plugin.getConfig().getInt("mobs.cryo-spiders", 4);

                // 2 per cycle so the world fills up quickly
                if (frogs < wantFrogs) {
                    spawnFrostFrog(dim, randomSurfaceNear(dim, 90, 200));
                    if (frogs + 1 < wantFrogs) {
                        spawnFrostFrog(dim, randomSurfaceNear(dim, 90, 200));
                    }
                }
                if (wisps < wantWisps) {
                    spawnSoulWisp(dim, randomSurfaceNear(dim, 30, 120));
                    if (wisps + 1 < wantWisps) {
                        spawnSoulWisp(dim, randomSurfaceNear(dim, 30, 120));
                    }
                }
                if (sentinels < wantSentinels) {
                    spawnSentinel(dim, randomSurfaceNear(dim, 40, 85));
                }
                if (moths < wantMoths) {
                    spawnVoidMoth(dim, randomSurfaceNear(dim, 20, 150));
                }
                if (spiders < wantSpiders) {
                    spawnCryoSpider(dim, randomSurfaceNear(dim, 60, 180));
                }
            }
        }.runTaskTimer(plugin, 100L, 200L);

        // behaviour + ambience
        new BukkitRunnable() {
            @Override
            public void run() {
                World dim = plugin.getDimensionManager().getWorld();
                if (dim == null || dim.getPlayers().isEmpty()) {
                    return;
                }
                long now = System.currentTimeMillis();
                for (Player pl : dim.getPlayers()) {
                    for (Entity e : pl.getNearbyEntities(10, 6, 10)) {
                        if (!(e instanceof LivingEntity lv) || !lv.isValid()) {
                            continue;
                        }
                        var pdc = e.getPersistentDataContainer();
                        if (e instanceof Frog && pdc.has(frostKey, PersistentDataType.BYTE)) {
                            Long cd = frogAttackCd.get(e.getUniqueId());
                            if (cd == null || now >= cd) {
                                if (e.getLocation().distanceSquared(pl.getLocation()) <= 8 * 8) {
                                    frogAttackCd.put(e.getUniqueId(), now + 3000);
                                    tongueAttack((Frog) e, pl);
                                }
                            }
                        } else if (e instanceof WitherSkeleton && pdc.has(sentinelKey, PersistentDataType.BYTE)) {
                            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                                dim.spawnParticle(Particle.SOUL_FIRE_FLAME,
                                        e.getLocation().add(0, 1.8, 0), 1, 0.2, 0.1, 0.2, 0.005);
                            }
                        } else if (e instanceof Spider && pdc.has(spiderKey, PersistentDataType.BYTE)) {
                            if (ThreadLocalRandom.current().nextInt(15) == 0) {
                                dim.spawnParticle(Particle.SNOWFLAKE,
                                        e.getLocation().add(0, 0.6, 0), 2, 0.3, 0.15, 0.3, 0.01);
                            }
                        } else if (e instanceof Bat && pdc.has(mothKey, PersistentDataType.BYTE)) {
                            if (ThreadLocalRandom.current().nextInt(10) == 0) {
                                dim.spawnParticle(Particle.DUST, e.getLocation().add(0, 0.3, 0), 1, 0.15, 0.15, 0.15, 0,
                                        new Particle.DustOptions(Color.fromRGB(90, 200, 255), 0.9f));
                            }
                        }
                    }
                }
                // wisps: bob + sparkle (display entities)
                long t = System.currentTimeMillis();
                for (Entity e : dim.getEntitiesByClass(ItemDisplay.class)) {
                    if (!e.getPersistentDataContainer().has(wispKey, PersistentDataType.BYTE)) {
                        continue;
                    }
                    ItemDisplay d = (ItemDisplay) e;
                    d.setRotation((t / 40f) % 360f, 0f);
                    double bob = Math.sin(t / 700.0 + e.getEntityId()) * 0.25;
                    Location loc = e.getLocation().add(0, 0, 0);
                    loc.setY(loc.getY() + bob * 0.08);
                    e.teleport(loc);
                    dim.spawnParticle(Particle.END_ROD, loc.add(0, 0.3 + bob, 0), 1, 0.15, 0.15, 0.15, 0.01);
                    if (ThreadLocalRandom.current().nextInt(60) == 0) {
                        dim.playSound(loc, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.5f, 1.8f);
                    }
                }
                // orphan cleanup: remove mob-model displays whose base died
                for (Entity e : dim.getEntitiesByClass(ItemDisplay.class)) {
                    var pdc = e.getPersistentDataContainer();
                    if (pdc.has(riderKey(), PersistentDataType.BYTE) && e.getVehicle() == null) {
                        e.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 3L);
    }

    private NamespacedKey riderKey() {
        return new NamespacedKey(plugin, "grv_mob_rider");
    }

    private Location randomSurfaceNear(World dim, int minR, int maxR) {
        double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double d = minR + ThreadLocalRandom.current().nextDouble(maxR - minR);
        int x = (int) (Math.cos(ang) * d);
        int z = (int) (Math.sin(ang) * d);
        int y = dim.getHighestBlockYAt(x, z) + 1;
        if (y < 50) {
            y = 65;
        }
        return new Location(dim, x + 0.5, y, z + 0.5);
    }

    // ------------------------------------------------------------------
    // spawn helpers: invisible vanilla base + custom-model display rider
    // ------------------------------------------------------------------

    private <T extends Entity> void spawnWithModel(World dim, Location loc, Class<T> type,
                                                   NamespacedKey tag, String modelKey,
                                                   double scale, double yOffset, java.util.function.Consumer<T> setup) {
        T base = dim.spawn(loc, type, b -> {
            b.setPersistent(true);
            if (b instanceof org.bukkit.entity.Mob m) {
                m.setRemoveWhenFarAway(false);
            }
            b.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        if (base instanceof LivingEntity lv) {
            lv.setInvisible(true);
            lv.setSilent(true);
        }
        if (setup != null) {
            setup.accept(base);
        }
        ItemDisplay rider = dim.spawn(loc.clone().add(0, yOffset, 0), ItemDisplay.class, d -> {
            d.setItemStack(Items.createMobIcon(plugin, modelKey));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(true);
            d.setInterpolationDelay(-1);
            d.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(), new org.joml.Quaternionf(),
                    new org.joml.Vector3f((float) scale, (float) scale, (float) scale),
                    new org.joml.Quaternionf()));
            d.getPersistentDataContainer().set(riderKey(), PersistentDataType.BYTE, (byte) 1);
        });
        base.addPassenger(rider);
    }

    private void spawnFrostFrog(World dim, Location loc) {
        spawnWithModel(dim, loc, Frog.class, frostKey, "frost-frog", 0.9, 0.35, frog -> {
            try {
                frog.setVariant(Frog.Variant.COLD);
            } catch (Throwable ignored) {
            }
            frog.setAdult();
            frog.customName(MiniMessage.miniMessage().deserialize("<color:#9ff3ff>Frost Frog</color>"));
            frog.setCustomNameVisible(false);
        });
    }

    private void spawnSoulWisp(World dim, Location loc) {
        ItemDisplay d = dim.spawn(loc, ItemDisplay.class, dd -> {
            dd.setItemStack(Items.createMobIcon(plugin, "soul-wisp"));
            dd.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            dd.setBillboard(Display.Billboard.CENTER);
            dd.setGlowing(true);
            dd.setPersistent(true);
            dd.getPersistentDataContainer().set(wispKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void spawnSentinel(World dim, Location loc) {
        spawnWithModel(dim, loc, WitherSkeleton.class, sentinelKey, "grave-sentinel", 1.0, 0.0, ws -> {
            var maxAttr = ws.getAttribute(Attribute.MAX_HEALTH);
            if (maxAttr != null) {
                maxAttr.setBaseValue(40.0);
                ws.setHealth(40.0);
            }
            ws.customName(MiniMessage.miniMessage().deserialize("<color:#7a8bff>Grave Sentinel</color>"));
            ws.setCustomNameVisible(false);
        });
    }

    private void spawnVoidMoth(World dim, Location loc) {
        spawnWithModel(dim, loc, Bat.class, mothKey, "void-moth", 0.55, 0.2, bat -> {
            bat.setAwake(true);
        });
    }

    private void spawnCryoSpider(World dim, Location loc) {
        spawnWithModel(dim, loc, Spider.class, spiderKey, "cryo-spider", 0.8, 0.2, sp -> {
            sp.setInvisible(true);
        });
    }

    /** Remove the model rider when its base dies. */
    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        for (Entity passenger : e.getEntity().getPassengers()) {
            if (passenger.getPersistentDataContainer().has(riderKey(), PersistentDataType.BYTE)) {
                passenger.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // frost frog tongue
    // ------------------------------------------------------------------

    private void tongueAttack(Frog frog, Player target) {
        World w = frog.getWorld();
        Location from = frog.getLocation().add(0, 0.6, 0);
        Location to = target.getLocation().add(0, 1.2, 0);
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / 16.0);

        w.playSound(from, Sound.ENTITY_FROG_LONG_JUMP, 1.0f, 1.6f);

        for (int i = 0; i <= 16; i++) {
            Location pt = from.clone().add(step.clone().multiply(i));
            w.spawnParticle(Particle.SNOWFLAKE, pt, 1, 0.02, 0.02, 0.02, 0);
            w.spawnParticle(Particle.DUST, pt, 1, 0.03, 0.03, 0.03, 0,
                    new Particle.DustOptions(Color.fromRGB(140, 220, 255), 1.3f));
        }
        w.spawnParticle(Particle.SWEEP_ATTACK, to, 1, 0.1, 0.2, 0.1, 0);

        double damage = plugin.getConfig().getDouble("mobs.frog-damage", 3.0);
        int freezeSeconds = plugin.getConfig().getInt("mobs.frog-freeze-seconds", 2);
        target.damage(damage, frog);
        target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), 140) + 40);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeSeconds * 20, 6, false, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, freezeSeconds * 20, 128, false, false, false));
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.2f);
        target.sendActionBar(MiniMessage.miniMessage().deserialize(
                "<color:#9ff3ff><bold>FROST FROG</bold></color> <gray>froze you with its tongue!</gray>"));
        w.spawnParticle(Particle.SOUL, to, 10, 0.3, 0.5, 0.3, 0.03);
    }
}
