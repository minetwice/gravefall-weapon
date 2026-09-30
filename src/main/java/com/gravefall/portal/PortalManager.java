package com.gravefall.portal;

import com.gravefall.GravefallPlugin;
import com.gravefall.Items;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Gravefall Portal:
 *  - summoned by /gravefall portal (dormant/sealed state)
 *  - only a player holding 5 Death Fragments can awaken it (right-click)
 *  - once open, it stays open forever and EVERYONE can walk through
 *  - every portal leads to the SAME Corrupted Kingdom in the Soul Dimension
 *  - after the hammer is claimed, return portals open inside the dimension
 *  - /gravefall removeportal removes the portal in front of you
 */
public class PortalManager implements Listener {

    /** One portal instance (persistent across restarts via portals.yml). */
    private static final class Portal {
        World world;
        Location center;       // interior center (teleport detection zone)
        float yaw;             // portal facing
        boolean open;
        boolean returnPortal;  // true = sends player back to the overworld
        Location target;       // teleport destination (may be filled lazily)
        List<int[]> frameBlocks = new ArrayList<>(); // [x,y,z] world coords
        List<Entity> entities = new ArrayList<>();   // displays + interaction
        List<Block> savedFrame = new ArrayList<>();  // original blocks (session only)
        BukkitTask ambientTask;
    }

    private final GravefallPlugin plugin;
    private final List<Portal> portals = new ArrayList<>();
    private final Map<UUID, Long> travelCooldown = new HashMap<>();
    private final NamespacedKey portalTag;

    public PortalManager(GravefallPlugin plugin) {
        this.plugin = plugin;
        this.portalTag = new NamespacedKey(plugin, "gravefall_portal");
    }

    private File portalFile() {
        return new File(plugin.getDataFolder(), "portals.yml");
    }

    // ------------------------------------------------------------------
    // building
    // ------------------------------------------------------------------

    /** Builds a portal facing the player and registers it. openNow = force open. */
    public void summon(Player issuer, boolean openNow, boolean returnPortal) {
        World w = issuer.getWorld();
        if (plugin.getDimensionManager().isDimensionWorld(w) && !returnPortal) {
            issuer.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>You are already inside the Soul Dimension.</red>"));
            return;
        }
        Portal p = new Portal();
        p.world = w;
        p.returnPortal = returnPortal;

        Location base = issuer.getLocation().getBlock().getLocation();
        float yaw = issuer.getLocation().getYaw();
        p.yaw = yaw;
        // local axes
        Vector fwd = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
        BlockFace fwdFace = nearestFace(fwd);

        int by = base.getBlockY();
        // frame footprint: interior 2 wide (side -1..0), 3 tall (y+1..y+3)
        // clear interior + surroundings
        for (int i = -2; i <= 1; i++) {
            for (int h = 1; h <= 4; h++) {
                Block b = at(w, base, side, fwd, i, 0, h);
                remember(p, b);
                b.setType(Material.AIR, false);
            }
            Block b = at(w, base, side, fwd, i, 1, 0); // ground row
            remember(p, b);
            b.setType(i <= -2 || i >= 0 ? Material.CRYING_OBSIDIAN : Material.AIR, false);
        }
        // side pillars
        for (int h = 1; h <= 4; h++) {
            for (int i : new int[]{-2, 1}) {
                Block b = at(w, base, side, fwd, i, 0, h);
                remember(p, b);
                b.setType(h == 4 ? Material.SEA_LANTERN : Material.CRYING_OBSIDIAN, false);
            }
        }
        // top row
        for (int i = -2; i <= 1; i++) {
            Block b = at(w, base, side, fwd, i, 0, 4);
            remember(p, b);
            b.setType(Material.CRYING_OBSIDIAN, false);
        }
        // platform
        for (int i = -3; i <= 2; i++) {
            for (int f = -1; f <= 1; f++) {
                Block b = at(w, base, side, fwd, i, 1, f);
                if (b.getY() > w.getMinHeight()) {
                    remember(p, b);
                    b.setType(ThreadLocalRandom.current().nextInt(10) < 3
                            ? Material.SCULK : Material.POLISHED_DEEPSLATE, false);
                }
            }
        }
        // obelisks flanking the gate
        for (int i : new int[]{-4, 3}) {
            for (int h = 1; h <= 3; h++) {
                Block b = at(w, base, side, fwd, i, 1, 0);
                remember(p, b);
                b.setType(h == 3 ? Material.SOUL_LANTERN : Material.DEEPSLATE_BRICKS, false);
            }
        }

        p.center = base.clone().add(side.getX() * -0.5 + fwd.getX() * 0.5 + 0.5, 2, side.getZ() * -0.5 + fwd.getZ() * 0.5 + 0.5);
        portals.add(p);
        spawnPortalEntities(p);

        if (openNow) {
            openPortal(p, issuer, true);
        } else {
            issuer.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<dark_purple>A dormant Gravefall Portal rises. It can only be awakened by one who carries "
                            + plugin.getConfig().getInt("ritual.required-fragments", 5) + " Death Fragments.</dark_purple>"));
        }
        savePortals();
    }

    private BlockFace nearestFace(Vector v) {
        double x = v.getX();
        double z = v.getZ();
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return z > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private Block at(World w, Location base, Vector side, Vector fwd, int sideOff, int upOff, int hOff) {
        int x = base.getBlockX() + (int) Math.round(side.getX() * sideOff + fwd.getX() * hOff);
        int y = base.getBlockY() + upOff + hOff - 0;
        int z = base.getBlockZ() + (int) Math.round(side.getZ() * sideOff + fwd.getZ() * hOff);
        return w.getBlockAt(x, y, z);
    }

    private void remember(Portal p, Block b) {
        p.frameBlocks.add(new int[]{b.getX(), b.getY(), b.getZ()});
        if (p.savedFrame.size() < 2000) {
            p.savedFrame.add(b);
        }
    }

    private void spawnPortalEntities(Portal p) {
        // interaction hitbox in the middle of the interior
        Interaction interaction = p.world.spawn(p.center, Interaction.class, i -> {
            i.setInteractionWidth(1.6f);
            i.setInteractionHeight(3.0f);
            i.setPersistent(true);
            i.getPersistentDataContainer().set(portalTag, PersistentDataType.BYTE, (byte) 1);
        });
        p.entities.add(interaction);
        if (p.open) {
            spawnOpenVisuals(p);
        }
    }

    private void spawnOpenVisuals(Portal p) {
        // swirling portal planes (custom resource pack model)
        double[] rotations = {-28, 0, 28};
        for (double rot : rotations) {
            ItemDisplay display = p.world.spawn(p.center.clone().add(0, -1, 0), ItemDisplay.class, d -> {
                d.setItemStack(Items.createPortalIcon(plugin));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.FIXED);
                d.setPersistent(true);
                d.setRotation(p.yaw + (float) rot, 0f);
                d.setInterpolationDelay(-1);
            });
            p.entities.add(display);
        }
        // crystal floating above the gate
        ItemDisplay crystal = p.world.spawn(p.center.clone().add(0, 2.6, 0), ItemDisplay.class, d -> {
            d.setItemStack(Items.createCrystalIcon(plugin));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.CENTER);
            d.setGlowing(true);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(new NamespacedKey(plugin, "grv_crystal"), PersistentDataType.BYTE, (byte) 1);
        });
        p.entities.add(crystal);

        startAmbient(p);
    }

    private void startAmbient(Portal p) {
        if (p.ambientTask != null) {
            p.ambientTask.cancel();
        }
        p.ambientTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.open) {
                return;
            }
            Location c = p.center;
            World w = p.world;
            // soul vortex inside the gate
            long t = System.currentTimeMillis() / 50;
            for (int h = 0; h <= 6; h++) {
                double ang = t * 0.25 + h * 0.9;
                double r = 0.35 + 0.65 * (h / 6.0);
                w.spawnParticle(Particle.PORTAL, c.clone().add(Math.cos(ang) * r, h * 0.45 - 1.2, Math.sin(ang) * r),
                        2, 0.05, 0.05, 0.05, 0.05);
                if (h % 2 == 0) {
                    w.spawnParticle(Particle.SOUL, c.clone().add(Math.cos(ang) * r, h * 0.45 - 1.2, Math.sin(ang) * r),
                            1, 0.02, 0.02, 0.02, 0.0);
                }
            }
            w.spawnParticle(Particle.DUST, c, 4, 0.4, 1.0, 0.4, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 140, 255), 1.4f));
            if (Math.random() < 0.08) {
                w.playSound(c, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.7f + (float) Math.random() * 0.4f);
            }
        }, 10L, 3L);
    }

    // ------------------------------------------------------------------
    // opening
    // ------------------------------------------------------------------

    /** Opens (awakens) the given portal for a player. */
    public void openPortal(Portal p, Player opener, boolean silentForce) {
        if (p.open) {
            return;
        }
        p.open = true;
        World w = p.world;

        // awakening animation
        w.strikeLightningEffect(p.center);
        w.playSound(p.center, Sound.BLOCK_PORTAL_TRIGGER, 1.2f, 0.6f);
        w.playSound(p.center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                Location c = p.center;
                if (step <= 16) {
                    // souls converge into the gate
                    double ang = step * 0.8;
                    double r = 4.0 * (1.0 - step / 16.0);
                    w.spawnParticle(Particle.SOUL, c.clone().add(Math.cos(ang) * r, 0.5, Math.sin(ang) * r),
                            8, 0.2, 0.6, 0.2, 0.05);
                    w.spawnParticle(Particle.REVERSE_PORTAL, c, 10, 1.0, 1.2, 1.0, 0.3);
                }
                if (step == 16) {
                    spawnOpenVisuals(p);
                    w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1, 0, 0, 0, 0);
                    for (Player pl : w.getPlayers()) {
                        if (pl.getLocation().distanceSquared(c) < 50 * 50) {
                            pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                                    "<bold><gradient:#00c8ff:#7b2ff7>☠ THE GRAVEFALL PORTAL IS OPEN ☠</gradient></bold>"));
                        }
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        savePortals();
    }

    // ------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Portal p = portalByInteraction(e.getRightClicked());
        if (p == null) {
            return;
        }
        e.setCancelled(true);
        if (p.open) {
            e.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<gray>Step into the portal...</gray>"));
            return;
        }
        Player pl = e.getPlayer();
        int needed = plugin.getConfig().getInt("ritual.required-fragments", 5);
        if (countFragments(pl) >= needed) {
            openPortal(p, pl, false);
            pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        } else {
            pl.playSound(pl.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.6f);
            pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<red>The portal rejects you — you carry " + countFragments(pl) + "/" + needed
                            + " Death Fragments.</red>"));
        }
    }

    @EventHandler
    public void onPortalDamage(EntityDamageByEntityEvent e) {
        if (portalByInteraction(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    private int countFragments(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (Items.isFragment(plugin, it)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // travel
    // ------------------------------------------------------------------

    public void startTravelTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Portal p : new ArrayList<>(portals)) {
                if (!p.open) {
                    // dormant portals give off a faint cold shimmer
                    if (Math.random() < 0.3) {
                        p.world.spawnParticle(Particle.SOUL, p.center, 1, 0.5, 1.0, 0.5, 0.01);
                    }
                    continue;
                }
                for (Player pl : p.world.getPlayers()) {
                    Long last = travelCooldown.get(pl.getUniqueId());
                    if (last != null && now - last < 3000) {
                        continue;
                    }
                    Location l = pl.getLocation();
                    if (Math.abs(l.getX() - p.center.getX()) < 1.2
                            && Math.abs(l.getZ() - p.center.getZ()) < 1.2
                            && Math.abs(l.getY() - p.center.getY()) < 2.5) {
                        teleportThrough(pl, p);
                    }
                }
            }
        }, 20L, 10L);
    }

    private void teleportThrough(Player pl, Portal p) {
        travelCooldown.put(pl.getUniqueId(), System.currentTimeMillis());
        World w = pl.getWorld();
        w.playSound(pl.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 0.8f);
        w.spawnParticle(Particle.PORTAL, pl.getLocation().add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.5);
        w.spawnParticle(Particle.SOUL, pl.getLocation().add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0.1);

        Location dest = resolveTarget(p);
        if (dest == null) {
            pl.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>The portal fizzles... the dimension is not ready yet.</red>"));
            return;
        }
        Location safe = dest.clone();
        safe.setYaw(p.yaw);
        pl.teleport(safe);
        World dw = dest.getWorld();
        dw.playSound(safe, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 1.4f);
        dw.spawnParticle(Particle.PORTAL, safe.add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.5);
        dw.spawnParticle(Particle.DUST, safe, 40, 0.8, 1.2, 0.8, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 140, 255), 1.5f));
        pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                p.returnPortal
                        ? "<gray>You step back into the living world...</gray>"
                        : "<dark_purple>You enter the Soul Dimension...</dark_purple>"));
    }

    private Location resolveTarget(Portal p) {
        if (p.returnPortal) {
            // back to the first non-dimension world's spawn (or stored overworld portal)
            if (p.target != null) {
                return p.target;
            }
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == World.Environment.NORMAL
                        && !plugin.getDimensionManager().isDimensionWorld(w)) {
                    return w.getSpawnLocation().add(0.5, 1, 0.5);
                }
            }
            return null;
        }
        // into the dimension kingdom
        Location spawn = plugin.getDimensionManager().getKingdomSpawn();
        if (spawn == null) {
            return null;
        }
        return spawn.clone();
    }

    // ------------------------------------------------------------------
    // return portals (spawned after the hammer is claimed)
    // ------------------------------------------------------------------

    public void spawnReturnPortals(int count) {
        World dim = plugin.getDimensionManager().getWorld();
        if (dim == null) {
            return;
        }
        Location center = new Location(dim, 0, 65, 0);
        for (int i = 0; i < count; i++) {
            double ang = i * Math.PI * 2.0 / count;
            double d = 55 + ThreadLocalRandom.current().nextInt(20);
            int x = (int) (Math.cos(ang) * d);
            int z = (int) (Math.sin(ang) * d);
            int y = dim.getHighestBlockYAt(x, z) + 1;
            if (y < 50) {
                y = 65;
            }
            Location loc = new Location(dim, x + 0.5, y, z + 0.5);
            dim.strikeLightningEffect(loc);
        }
        // delay the actual builds so the lightning drama plays first
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < count; i++) {
                double ang = i * Math.PI * 2.0 / count;
                double d = 55 + ThreadLocalRandom.current().nextInt(20);
                int x = (int) (Math.cos(ang) * d);
                int z = (int) (Math.sin(ang) * d);
                int y = dim.getHighestBlockYAt(x, z) + 1;
                if (y < 50) {
                    y = 65;
                }
                buildReturnPortalAt(new Location(dim, x + 0.5, y, z + 0.5), ang);
            }
            savePortals();
        }, 60L);
    }

    private void buildReturnPortalAt(Location base, double facing) {
        Portal p = new Portal();
        p.world = base.getWorld();
        p.returnPortal = true;
        p.yaw = (float) Math.toDegrees(-facing);
        World w = p.world;

        // smaller return gate: obsidian ring + blue fire interior visuals
        Vector fwd = new Vector(-Math.sin(facing), 0, Math.cos(facing));
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
        for (int i = -2; i <= 1; i++) {
            for (int h = 0; h <= 4; h++) {
                boolean frame = (i == -2 || i == 1) || (h == 0 || h == 4);
                if (frame) {
                    Block b = at(w, base, side, fwd, i, 0, h);
                    remember(p, b);
                    b.setType(h == 4 ? Material.SEA_LANTERN : Material.CRYING_OBSIDIAN, false);
                }
            }
        }
        p.center = base.clone().add(side.getX() * -0.5 + fwd.getX() * 0.5, 2, side.getZ() * -0.5 + fwd.getZ() * 0.5);
        p.open = true;
        portals.add(p);
        spawnPortalEntities(p);
        w.playSound(p.center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.6f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, p.center, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------
    // removal
    // ------------------------------------------------------------------

    /** Removes the portal nearest to (or looked at by) the player. */
    public void removeNear(Player issuer) {
        Portal best = null;
        double bestDist = 14 * 14;
        for (Portal p : portals) {
            if (!p.world.equals(issuer.getWorld())) {
                continue;
            }
            double d = p.center.distanceSquared(issuer.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best == null) {
            issuer.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>No Gravefall Portal found within 14 blocks.</red>"));
            return;
        }
        remove(best);
        issuer.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>Portal unmade — the frame crumbles to dust.</gray>"));
        issuer.playSound(issuer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
        savePortals();
    }

    private void remove(Portal p) {
        if (p.ambientTask != null) {
            p.ambientTask.cancel();
        }
        for (Entity e : p.entities) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        for (Block b : p.savedFrame) {
            if (b.getY() > p.world.getMinHeight() && b.getY() < p.world.getMaxHeight()) {
                Block now = p.world.getBlockAt(b.getX(), b.getY(), b.getZ());
                now.setType(Material.AIR, false);
                now.getWorld().spawnParticle(Particle.POOF, now.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.01);
            }
        }
        portals.remove(p);
    }

    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------

    public void savePortals() {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            int i = 0;
            for (Portal p : portals) {
                String path = "portals." + (i++);
                cfg.set(path + ".world", p.world.getName());
                cfg.set(path + ".x", p.center.getX());
                cfg.set(path + ".y", p.center.getY());
                cfg.set(path + ".z", p.center.getZ());
                cfg.set(path + ".yaw", p.yaw);
                cfg.set(path + ".open", p.open);
                cfg.set(path + ".return", p.returnPortal);
            }
            cfg.save(portalFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save portals: " + e.getMessage());
        }
    }

    public void loadPortals() {
        if (!portalFile().exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(portalFile());
        ConfigurationSection root = cfg.getConfigurationSection("portals");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            String worldName = root.getString(key + ".world");
            World w = Bukkit.getWorld(worldName == null ? "world" : worldName);
            if (w == null) {
                continue;
            }
            Portal p = new Portal();
            p.world = w;
            p.center = new Location(w, root.getDouble(key + ".x"), root.getDouble(key + ".y"), root.getDouble(key + ".z"));
            p.yaw = (float) root.getDouble(key + ".yaw");
            p.open = root.getBoolean(key + ".open");
            p.returnPortal = root.getBoolean(key + ".return");
            portals.add(p);
            spawnPortalEntities(p);
        }
        if (!portals.isEmpty()) {
            plugin.getLogger().info("Loaded " + portals.size() + " Gravefall Portal(s).");
        }
    }

    // ------------------------------------------------------------------

    private Portal portalByInteraction(Entity entity) {
        if (entity == null || entity.getPersistentDataContainer() == null) {
            return null;
        }
        if (!entity.getPersistentDataContainer().has(portalTag, PersistentDataType.BYTE)) {
            return null;
        }
        for (Portal p : portals) {
            if (p.world.equals(entity.getWorld())
                    && p.center.distanceSquared(entity.getLocation()) < 9) {
                return p;
            }
        }
        return null;
    }
}
