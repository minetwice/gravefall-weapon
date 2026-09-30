package com.gravefall.structure;

import com.gravefall.GravefallPlugin;
import com.gravefall.Items;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Ritual of Five Fragments:
 *  1. Players bind 5 Death Fragments into the structure's item frames.
 *     (soul particle animation + progress display for each one)
 *  2. All five bound -> soul "wires" connect the frames to the altar
 *     and a 10 minute boss bar timer starts.
 *  3. Timer complete -> the fragments fly to the altar, frames vanish,
 *     and the hammer stands on display (unclaimable until awakened... it
 *     already is awakened at this point - it simply waits for a claim).
 *  4. Right-click the hammer -> announcement, then the tornado finale.
 */
public class StructureRitual implements Listener {

    private enum Phase {
        NONE, WAITING, CHARGING, AWAKENED, DONE
    }

    private final GravefallPlugin plugin;

    private StructureBuilder.StructureInfo info;
    private final List<ItemFrame> frames = new ArrayList<>();
    private final Set<UUID> filled = new HashSet<>();

    private Phase phase = Phase.NONE;
    private BossBar bossBar;
    private long chargeStart;
    private long chargeTotal;
    private ItemDisplay hammer;
    private Interaction hitbox;

    private BukkitTask pollTask;
    private BukkitTask linesTask;
    private BukkitTask chargeTask;
    private BukkitTask hammerTask;
    private BukkitTask ambientTask;

    public StructureRitual(GravefallPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // setup
    // ------------------------------------------------------------------

    public void attachStructure(StructureBuilder.StructureInfo info, List<ItemFrame> frames) {
        this.info = info;
        this.frames.clear();
        this.frames.addAll(frames);
        this.filled.clear();
        this.phase = Phase.WAITING;

        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollFrames, 40L, 40L);
    }

    public boolean hasStructure() {
        return phase != Phase.NONE && phase != Phase.DONE;
    }

    // ------------------------------------------------------------------
    // polling (backup detection of fragments placed in frames)
    // ------------------------------------------------------------------

    private void pollFrames() {
        if (phase != Phase.WAITING) {
            return;
        }
        for (ItemFrame frame : frames) {
            if (!frame.isValid() || filled.contains(frame.getUniqueId())) {
                continue;
            }
            ItemStack it = frame.getItem();
            if (Items.isFragment(plugin, it)) {
                onFragmentBound(frame, null);
            }
        }
    }

    // ------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = e.getRightClicked();

        // --- hammer claim ---
        if (phase == Phase.AWAKENED && hitbox != null && clicked.getUniqueId().equals(hitbox.getUniqueId())) {
            e.setCancelled(true);
            tryClaim(e.getPlayer());
            return;
        }
        if (phase == Phase.AWAKENED && hammer != null && clicked.getUniqueId().equals(hammer.getUniqueId())) {
            e.setCancelled(true);
            tryClaim(e.getPlayer());
            return;
        }

        // --- item frames ---
        if (!(clicked instanceof ItemFrame frame) || phase == Phase.NONE || phase == Phase.DONE) {
            return;
        }
        if (!isRitualFrame(frame)) {
            return;
        }
        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        boolean empty = frame.getItem() == null || frame.getItem().getType().isAir();

        if (empty) {
            if (!Items.isFragment(plugin, hand)) {
                e.setCancelled(true);
                e.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<red>The altar rejects this offering. Only Death Fragments may bind here.</red>"));
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.5f);
            } else {
                // allow the vanilla insert, verify on the next tick
                Player who = e.getPlayer();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (phase == Phase.WAITING && frame.isValid() && !filled.contains(frame.getUniqueId())
                            && Items.isFragment(plugin, frame.getItem())) {
                        onFragmentBound(frame, who);
                    }
                });
            }
        } else {
            // fragments cannot be taken back out once bound
            e.setCancelled(true);
            if (phase == Phase.WAITING) {
                e.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<gray>The fragment is bound to the ritual.</gray>"));
            }
        }
    }

    @EventHandler
    public void onFrameDamage(EntityDamageByEntityEvent e) {
        if (info == null) {
            return;
        }
        Entity ent = e.getEntity();
        if (ent instanceof ItemFrame && isRitualFrame((ItemFrame) ent)) {
            e.setCancelled(true);
        }
        if (phase == Phase.AWAKENED && hitbox != null && ent.getUniqueId().equals(hitbox.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent e) {
        if (info == null) {
            return;
        }
        if (e.getEntity() instanceof ItemFrame frame && isRitualFrame(frame)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (bossBar != null && phase == Phase.CHARGING) {
            bossBar.addPlayer(e.getPlayer());
        }
    }

    // ------------------------------------------------------------------
    // ritual flow
    // ------------------------------------------------------------------

    private boolean isRitualFrame(ItemFrame frame) {
        for (ItemFrame f : frames) {
            if (f.getUniqueId().equals(frame.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private void onFragmentBound(ItemFrame frame, Player who) {
        filled.add(frame.getUniqueId());
        World w = info.world();
        Location fl = frame.getLocation().add(0, 0.5, 0);

        w.spawnParticle(Particle.SOUL, fl, 60, 0.3, 0.5, 0.3, 0.1);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, fl, 25, 0.3, 0.5, 0.3, 0.02);
        w.playSound(fl, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.4f);
        w.playSound(fl, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.8f);

        // one-shot bright soul line to the altar
        drawLine(fl, info.altarTop(), 2.0);

        int need = plugin.getConfig().getInt("ritual.required-fragments", 5);
        int have = filled.size();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(info.center()) < 70 * 70) {
                p.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<gray>Soul Fragment bound: <light_purple><bold>" + have + "/" + need
                                + "</bold></light_purple></gray>"));
            }
        }
        if (who != null) {
            who.playSound(who.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        }

        if (have >= need) {
            beginCharging();
        }
    }

    private void beginCharging() {
        phase = Phase.CHARGING;
        World w = info.world();
        int seconds = plugin.getConfig().getInt("ritual.charge-seconds", 600);
        chargeStart = System.currentTimeMillis();
        chargeTotal = seconds * 1000L;

        w.playSound(info.center(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.0f, 0.5f);
        w.playSound(info.center(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
        plugin.broadcast(MiniMessage.miniMessage().deserialize(
                "<dark_purple><bold>☠ The five fragments resonate... GRAVEFALL is awakening! ☠</bold></dark_purple> "
                        + "<gray>(" + formatSeconds(seconds) + ")</gray>"));

        bossBar = Bukkit.createBossBar(
                "☠ Gravefall Awakening", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        // soul "wires" from every filled frame to the altar
        linesTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (ItemFrame frame : frames) {
                if (frame.isValid() && filled.contains(frame.getUniqueId())) {
                    drawLine(frame.getLocation().add(0, 0.5, 0), info.altarTop(), 1.0);
                }
            }
        }, 0L, 3L);

        chargeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long rem = chargeTotal - (System.currentTimeMillis() - chargeStart);
            if (rem <= 0) {
                awaken();
                return;
            }
            bossBar.setProgress(rem / (double) chargeTotal);
            long secs = (rem + 999) / 1000;
            String msg = "<dark_purple>Awakening in </dark_purple><light_purple><bold>"
                    + formatSeconds((int) secs) + "</bold></light_purple>";
            for (Player p : info.world().getPlayers()) {
                if (p.getLocation().distanceSquared(info.center()) < 100 * 100) {
                    p.sendActionBar(MiniMessage.miniMessage().deserialize(msg));
                }
            }
        }, 10L, 20L);
    }

    private void awaken() {
        phase = Phase.AWAKENED;
        if (chargeTask != null) {
            chargeTask.cancel();
        }
        if (linesTask != null) {
            linesTask.cancel();
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        World w = info.world();
        w.playSound(info.center(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.8f);
        w.playSound(info.center(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.6f);

        // 2 second convergence animation, then the hammer appears
        List<Location> frameLocs = new ArrayList<>();
        for (ItemFrame frame : frames) {
            frameLocs.add(frame.getLocation().add(0, 0.5, 0));
        }
        final Location altar = info.altarTop().clone();
        new org.bukkit.scheduler.BukkitRunnable() {
            int step = 0;
            final int maxStep = 40;

            @Override
            public void run() {
                step++;
                double p = step / (double) maxStep;
                for (Location from : frameLocs) {
                    Vector dir = altar.toVector().subtract(from.toVector()).multiply(p);
                    Location pt = from.clone().add(dir);
                    w.spawnParticle(Particle.SOUL, pt, 6, 0.15, 0.15, 0.15, 0.02);
                    w.spawnParticle(Particle.REVERSE_PORTAL, pt, 3, 0.1, 0.1, 0.1, 0.05);
                }
                // beam of light
                for (int y = 0; y < 30; y += 2) {
                    w.spawnParticle(Particle.END_ROD, altar.clone().add(0, y, 0), 1, 0.1, 0.1, 0.1, 0);
                }
                if (step >= maxStep) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // remove fragments + frames with a poof
            for (ItemFrame frame : frames) {
                if (frame.isValid()) {
                    frame.getWorld().spawnParticle(Particle.POOF, frame.getLocation().add(0, 0.5, 0), 12, 0.2, 0.3, 0.2, 0.02);
                    frame.setItem(null);
                    frame.remove();
                }
            }
            frames.clear();
            spawnHammer();
        }, 45L);

        plugin.broadcast(MiniMessage.miniMessage().deserialize(
                "<bold><gradient:#b312ff:#ff3ef0>☠ GRAVEFALL HAS AWAKENED ☠</gradient></bold> "
                        + "<gray>The hammer waits upon the corrupted altar... claim it, if you dare.</gray>"));
    }

    private void spawnHammer() {
        World w = info.world();
        Location at = info.altarTop().clone().add(0, 1.0, 0);

        hammer = w.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(Items.createGravefall(plugin));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.CENTER);
            d.setGlowing(true);
            d.setPersistent(false);
        });
        hitbox = w.spawn(at, Interaction.class, i -> {
            i.setInteractionWidth(1.6f);
            i.setInteractionHeight(1.6f);
            i.setPersistent(false);
        });

        w.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, 1.2f);
        w.playSound(at, Sound.ITEM_TOTEM_USE, 1.5f, 1.6f);

        hammerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (hammer == null || !hammer.isValid()) {
                return;
            }
            hammer.setRotation((hammer.getLocation().getYaw() + 7f) % 360f, 0f);
            Location hl = hammer.getLocation();
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, hl, 2, 0.3, 0.4, 0.3, 0.01);
            w.spawnParticle(Particle.END_ROD, hl, 1, 0.25, 0.4, 0.25, 0.02);
            for (int i = 0; i < 4; i++) {
                double a = (System.currentTimeMillis() / 700.0) + i * Math.PI / 2.0;
                w.spawnParticle(Particle.SOUL, hl.clone().add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9), 1, 0, 0, 0, 0);
            }
        }, 2L, 2L);

        ambientTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(info.center()) < 40 * 40) {
                    p.sendActionBar(MiniMessage.miniMessage().deserialize(
                            "<yellow><bold>Right-click the hammer to claim it!</bold></yellow>"));
                }
            }
            if (Math.random() < 0.3) {
                w.playSound(info.center(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.6f);
            }
        }, 20L, 20L);
    }

    private void tryClaim(Player p) {
        if (phase != Phase.AWAKENED) {
            return;
        }
        phase = Phase.DONE;
        World w = p.getWorld();

        if (hammerTask != null) {
            hammerTask.cancel();
        }
        if (ambientTask != null) {
            ambientTask.cancel();
        }
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.2f);
        w.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.2f, 1.4f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, info.altarTop(), 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.SOUL, info.altarTop(), 150, 2, 2, 2, 0.1);

        var leftover = p.getInventory().addItem(Items.createGravefall(plugin));
        leftover.values().forEach(it -> w.dropItemNaturally(p.getLocation(), it));

        plugin.broadcast(MiniMessage.miniMessage().deserialize(
                "<bold><gradient:#b312ff:#ff3ef0>☠ " + p.getName()
                        + " has obtained GRAVEFALL, the Hammer of the Fallen Kingdom! ☠</gradient></bold>"));

        if (hammer != null && hammer.isValid()) {
            hammer.remove();
        }
        if (hitbox != null && hitbox.isValid()) {
            hitbox.remove();
        }
        new com.gravefall.ritual.TornadoFinale(plugin, p, info.center().clone()).start();

        // open return portals back to the overworld inside the dimension
        if (plugin.getDimensionManager().isDimensionWorld(w)) {
            int returns = plugin.getConfig().getInt("portal.return-portals", 6);
            plugin.getPortalManager().spawnReturnPortals(returns);
            plugin.broadcast(MiniMessage.miniMessage().deserialize(
                    "<color:#00c8ff><bold>Rift portals have torn open across the kingdom —</bold></color> "
                            + "<gray>they lead back to the living world!</gray>"));
        }

        // this structure is spent
        info = null;
        frames.clear();
        filled.clear();
        if (pollTask != null) {
            pollTask.cancel();
        }
    }

    // ------------------------------------------------------------------
    // utils
    // ------------------------------------------------------------------

    private void drawLine(Location from, Location to, double density) {
        World w = info.world();
        long t = System.currentTimeMillis() / 50L; // flow animation
        int steps = (int) (28 * density);
        Vector dir = to.toVector().subtract(from.toVector());
        Vector step = dir.clone().multiply(1.0 / steps);
        // perpendicular basis for orbiting strands
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        if (perp.lengthSquared() < 0.01) {
            perp = new Vector(1, 0, 0);
        } else {
            perp.normalize();
        }
        Location cur = from.clone();
        for (int i = 0; i < steps; i++) {
            cur.add(step);
            double f = i / (double) steps;
            // core strand
            w.spawnParticle(Particle.PORTAL, cur, 2, 0.02, 0.02, 0.02, 0.02);
            // two orbiting soul strands (flowing toward the altar)
            double ang = f * Math.PI * 7.0 + t * 0.35;
            double wob = 0.22;
            Location s1 = cur.clone().add(perp.clone().multiply(Math.cos(ang) * wob)).add(0, Math.sin(ang) * wob, 0);
            w.spawnParticle(Particle.SOUL, s1, 1, 0, 0, 0, 0);
            Location s2 = cur.clone().add(perp.clone().multiply(Math.cos(ang + Math.PI) * wob)).add(0, Math.sin(ang + Math.PI) * wob, 0);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, s2, 1, 0, 0, 0, 0);
            // witch sparks every few steps
            if (i % 3 == 0) {
                w.spawnParticle(Particle.WITCH, cur, 1, 0.03, 0.03, 0.03, 0);
            }
            if (i % 4 == 0) {
                w.spawnParticle(Particle.DUST, cur, 1, 0.02, 0.02, 0.02, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 60, 255), 1.2f));
            }
        }
        // end sparks
        w.spawnParticle(Particle.END_ROD, to, 4, 0.1, 0.1, 0.1, 0.03);
        w.spawnParticle(Particle.END_ROD, from, 2, 0.08, 0.08, 0.08, 0.02);
    }

    private static String formatSeconds(int total) {
        int m = total / 60;
        int s = total % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    public void cleanup() {
        if (pollTask != null) {
            pollTask.cancel();
        }
        if (linesTask != null) {
            linesTask.cancel();
        }
        if (chargeTask != null) {
            chargeTask.cancel();
        }
        if (hammerTask != null) {
            hammerTask.cancel();
        }
        if (ambientTask != null) {
            ambientTask.cancel();
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        for (ItemFrame frame : frames) {
            if (frame.isValid()) {
                frame.remove();
            }
        }
        frames.clear();
        filled.clear();
        if (hammer != null && hammer.isValid()) {
            hammer.remove();
        }
        if (hitbox != null && hitbox.isValid()) {
            hitbox.remove();
        }
        info = null;
        phase = Phase.NONE;
    }
}
