package com.gravefall.ritual;

import com.gravefall.GravefallPlugin;
import com.gravefall.Items;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Admin obtain ritual (/gravefall give <player>):
 *  1. The earth quakes - nearby blocks hop, ground shakes.
 *  2. A portal of particles opens in the sky and the hammer
 *     descends slowly towards the player.
 *  3. A soul circle ignites on the ground, lightning strikes,
 *     and the hammer is finally granted.
 */
public class RitualGiveTask extends BukkitRunnable {

    private static final int TOTAL = 280; // 14 seconds

    private final GravefallPlugin plugin;
    private final Player p;
    private final Random rnd = new Random();
    private final List<FallingBlock> debris = new ArrayList<>();
    private int t = 0;
    private ItemDisplay display;

    public RitualGiveTask(GravefallPlugin plugin, Player p) {
        this.plugin = plugin;
        this.p = p;
    }

    public void start() {
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.55f);
        p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<dark_purple><bold>☠ THE EARTH TREMBLES ☠</bold></dark_purple>"),
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<gray>GRAVEFALL is being forged above you...</gray>"),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(250),
                        java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofSeconds(1))));
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void run() {
        if (!p.isOnline()) {
            cleanupDisplay();
            cancel();
            return;
        }
        t++;
        World w = p.getWorld();
        Location base = p.getLocation().add(0, 1, 0);

        // ---------- Phase 1: earthquake (0 - 100) ----------
        if (t <= 100) {
            if (t % 4 == 0) {
                for (int i = 0; i < 3; i++) {
                    int dx = rnd.nextInt(9) - 4;
                    int dz = rnd.nextInt(9) - 4;
                    Block b = w.getHighestBlockAt(base.getBlockX() + dx, base.getBlockZ() + dz);
                    if (!b.getType().isSolid() || b.isLiquid()
                            || b.getType() == Material.BEDROCK || b.getType() == Material.OBSIDIAN) {
                        continue;
                    }
                    BlockData d = b.getBlockData();
                    b.setType(Material.AIR, false);
                    FallingBlock fb = w.spawnFallingBlock(b.getLocation().add(0.5, 0, 0.5), d);
                    fb.setDropItem(false);
                    fb.setVelocity(new org.bukkit.util.Vector(0, 0.3 + rnd.nextDouble() * 0.35, 0));
                    debris.add(fb);
                }
                // small "camera shake" imitation
                p.setVelocity(p.getVelocity().add(new org.bukkit.util.Vector(0, 0.06, 0)));
            }
            if (t % 20 == 0) {
                w.playSound(base, Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);
                w.playSound(base, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.7f, 0.5f);
            }
            // spiral around the player
            double progress = t / 100.0;
            for (int arm = 0; arm < 3; arm++) {
                double angle = progress * Math.PI * 6.0 + arm * (Math.PI * 2.0 / 3.0);
                double r = 0.8 + 2.2 * progress;
                Location pt = base.clone().add(Math.cos(angle) * r, -0.5 + progress * 2.5, Math.sin(angle) * r);
                w.spawnParticle(Particle.SOUL, pt, 3, 0.05, 0.05, 0.05, 0.02);
                w.spawnParticle(Particle.PORTAL, pt, 3, 0.1, 0.1, 0.1, 0.08);
            }
        }

        // ---------- Phase 2: sky portal + descent (100 - 240) ----------
        if (t > 100 && t <= 240) {
            double ph = (t - 100) / 140.0;
            Location sky = base.clone().add(0, 20, 0);

            if (t == 105) {
                w.playSound(base, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 0.6f);
                w.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
            }
            // rotating portal ring in the sky
            for (int i = 0; i < 20; i++) {
                double a = t * 0.12 + i * Math.PI * 2.0 / 20.0;
                double r = 2.6 + 0.8 * Math.sin(ph * Math.PI);
                Location pt = sky.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                w.spawnParticle(Particle.REVERSE_PORTAL, pt, 2, 0.05, 0.05, 0.05, 0.02);
                w.spawnParticle(Particle.PORTAL, pt, 2, 0.1, 0.1, 0.1, 0.1);
            }
            // beam of light towards the player
            for (int y = 0; y < 20 * ph + 1; y++) {
                w.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0.05, 0.05, 0.05, 0);
            }
            if (t == 112) {
                display = w.spawn(sky, ItemDisplay.class, d -> {
                    d.setItemStack(Items.createGravefall(plugin));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setGlowing(true);
                    d.setPersistent(false);
                });
            }
            if (display != null && display.isValid()) {
                display.teleport(base.clone().add(0, 20.0 * (1.0 - ph) + 0.5, 0));
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, display.getLocation(), 3, 0.2, 0.2, 0.2, 0.01);
            }
            if (t % 40 == 0) {
                w.playSound(base, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.6f);
            }
        }

        // ---------- Phase 3: arrival (240 - 280) ----------
        if (t == 240) {
            w.strikeLightningEffect(base);
            w.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
            w.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.7f);
        }
        if (t > 240) {
            double ph = (t - 240) / 40.0;
            double r = 1.0 + ph * 3.2;
            for (int i = 0; i < 30; i++) {
                double a = i * Math.PI * 2.0 / 30.0 + t * 0.1;
                Location pt = base.clone().add(0, -0.6, 0).add(Math.cos(a) * r, 0, Math.sin(a) * r);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.END_ROD, pt, 1, 0.02, 0.05, 0.02, 0);
            }
        }

        if (t >= TOTAL) {
            finish();
        }
    }

    private void finish() {
        World w = p.getWorld();
        Location base = p.getLocation().add(0, 1, 0);
        w.playSound(base, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.2f);
        w.playSound(base, Sound.ITEM_TOTEM_USE, 1.0f, 1.3f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, base, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, base, 120, 1.2, 1.2, 1.2, 0.15);
        w.spawnParticle(Particle.SOUL, base, 90, 1.5, 1.5, 1.5, 0.1);

        var leftover = p.getInventory().addItem(Items.createGravefall(plugin));
        leftover.values().forEach(it -> w.dropItemNaturally(p.getLocation(), it));

        p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<bold><gradient:#b312ff:#ff3ef0>☠ GRAVEFALL ☠</gradient></bold>"),
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<gray>The hammer has chosen you.</gray>"),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(250),
                        java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofSeconds(1))));

        cleanupDisplay();
        for (FallingBlock fb : debris) {
            if (fb != null && fb.isValid()) {
                fb.remove();
            }
        }
        debris.clear();
        cancel();
    }

    private void cleanupDisplay() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        display = null;
    }
}
