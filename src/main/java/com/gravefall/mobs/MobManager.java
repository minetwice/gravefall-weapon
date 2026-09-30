package com.gravefall.mobs;

import com.gravefall.GravefallPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Frog;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
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
 * Custom dimension mobs:
 *  - FROST FROG: pale cold frog; lunges a long spectral tongue at nearby
 *    players, damaging and freezing them for ~2 seconds.
 *  - SOUL WISP: harmless glowing allay that drifts around the kingdom.
 *  - GRAVE SENTINEL: wither skeleton guardians of the kingdom walls.
 */
public class MobManager {

    private final GravefallPlugin plugin;
    private final NamespacedKey frostKey;
    private final NamespacedKey wispKey;
    private final NamespacedKey sentinelKey;
    private final Map<UUID, Long> frogAttackCd = new HashMap<>();

    public MobManager(GravefallPlugin plugin) {
        this.plugin = plugin;
        this.frostKey = new NamespacedKey(plugin, "frost_frog");
        this.wispKey = new NamespacedKey(plugin, "soul_wisp");
        this.sentinelKey = new NamespacedKey(plugin, "grave_sentinel");
    }

    public void start() {
        // population task
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
                for (Entity e : dim.getEntities()) {
                    if (e.getPersistentDataContainer().has(frostKey, PersistentDataType.BYTE)) {
                        frogs++;
                    } else if (e.getPersistentDataContainer().has(wispKey, PersistentDataType.BYTE)) {
                        wisps++;
                    } else if (e.getPersistentDataContainer().has(sentinelKey, PersistentDataType.BYTE)) {
                        sentinels++;
                    }
                }
                int wantFrogs = plugin.getConfig().getInt("mobs.frost-frogs", 10);
                int wantWisps = plugin.getConfig().getInt("mobs.soul-wisps", 8);
                int wantSentinels = plugin.getConfig().getInt("mobs.grave-sentinels", 5);

                if (frogs < wantFrogs) {
                    spawnFrostFrog(dim, randomSurfaceNear(dim, 90, 200));
                }
                if (wisps < wantWisps) {
                    spawnSoulWisp(dim, randomSurfaceNear(dim, 30, 120));
                }
                if (sentinels < wantSentinels) {
                    spawnSentinel(dim, randomSurfaceNear(dim, 40, 85));
                }
            }
        }.runTaskTimer(plugin, 100L, 200L);

        // frost frog behaviour (tongue attacks)
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
                        if (!(e instanceof Frog frog)
                                || !frog.getPersistentDataContainer().has(frostKey, PersistentDataType.BYTE)
                                || !frog.isValid()) {
                            continue;
                        }
                        Long cd = frogAttackCd.get(frog.getUniqueId());
                        if (cd != null && now < cd) {
                            continue;
                        }
                        if (frog.getLocation().distanceSquared(pl.getLocation()) > 8 * 8) {
                            continue;
                        }
                        frogAttackCd.put(frog.getUniqueId(), now + 3000);
                        tongueAttack(frog, pl);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 4L);

        // ambient wisp sparkle
        new BukkitRunnable() {
            @Override
            public void run() {
                World dim = plugin.getDimensionManager().getWorld();
                if (dim == null || dim.getPlayers().isEmpty()) {
                    return;
                }
                for (Entity e : dim.getEntitiesByClass(Allay.class)) {
                    if (e.getPersistentDataContainer().has(wispKey, PersistentDataType.BYTE)) {
                        dim.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.3, 0), 1, 0.15, 0.15, 0.15, 0.01);
                        if (ThreadLocalRandom.current().nextInt(40) == 0) {
                            dim.playSound(e.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.6f, 1.6f);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 10L);
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

    private void spawnFrostFrog(World dim, Location loc) {
        dim.spawn(loc, Frog.class, frog -> {
            try {
                frog.setVariant(Frog.Variant.COLD);
            } catch (Throwable ignored) {
            }
            frog.setAdult();
            frog.setRemoveWhenFarAway(false);
            frog.setPersistent(true);
            frog.customName(MiniMessage.miniMessage().deserialize("<color:#9ff3ff>Frost Frog</color>"));
            frog.setCustomNameVisible(false);
            frog.getPersistentDataContainer().set(frostKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void spawnSoulWisp(World dim, Location loc) {
        dim.spawn(loc, Allay.class, allay -> {
            allay.setRemoveWhenFarAway(false);
            allay.setPersistent(true);
            allay.setGlowing(true);
            allay.customName(MiniMessage.miniMessage().deserialize("<color:#b7e5ff>Soul Wisp</color>"));
            allay.setCustomNameVisible(false);
            allay.getPersistentDataContainer().set(wispKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void spawnSentinel(World dim, Location loc) {
        dim.spawn(loc, WitherSkeleton.class, ws -> {
            ws.setRemoveWhenFarAway(false);
            ws.setPersistent(true);
            ws.customName(MiniMessage.miniMessage().deserialize("<color:#7a8bff>Grave Sentinel</color>"));
            ws.setCustomNameVisible(false);
            var maxAttr = ws.getAttribute(Attribute.MAX_HEALTH);
            if (maxAttr != null) {
                maxAttr.setBaseValue(40.0);
                ws.setHealth(40.0);
            }
            ws.getPersistentDataContainer().set(sentinelKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** The frost frog's long tongue: spectral line + damage + 2s freeze. */
    private void tongueAttack(Frog frog, Player target) {
        World w = frog.getWorld();
        Location from = frog.getLocation().add(0, 0.6, 0);
        Location to = target.getLocation().add(0, 1.2, 0);
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / 16.0);

        w.playSound(from, Sound.ENTITY_FROG_LONG_JUMP, 1.0f, 1.6f);

        // draw the tongue as a fast icicle beam
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
