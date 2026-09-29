package com.gravefall.ability;

import com.gravefall.GravefallPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Passive - DEATH METEOR (after a 3-hit combo).
 * A 5x5 burning rock descends from the sky onto the victim, damaging
 * everything near the impact. The meteor remains for 10 seconds and
 * then vanishes, restoring the terrain below it.
 */
public class MeteorTask extends BukkitRunnable {

    private final GravefallPlugin plugin;
    private final World w;
    private final int bx;
    private final int bz;
    private final Random rnd = new Random();

    private int t = 0;
    private int currentY;
    private int groundY;
    private boolean impacted = false;
    private int impactTick = -1;

    private final Map<String, BlockState> saved = new HashMap<>();
    private final List<int[]> sphereOffsets = new ArrayList<>();
    private final List<int[]> placedNow = new ArrayList<>();

    public MeteorTask(GravefallPlugin plugin, Location target) {
        this.plugin = plugin;
        this.w = target.getWorld();
        this.bx = target.getBlockX();
        this.bz = target.getBlockZ();
        // 5x5x5 rounded rock.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= 6) {
                        sphereOffsets.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        this.groundY = w.getHighestBlockYAt(bx, bz);
        this.currentY = Math.min(groundY + 45, w.getMaxHeight() - 8);
    }

    public void start() {
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void run() {
        t++;

        if (t <= 16) {
            // Warning circle on the ground.
            for (int i = 0; i < 26; i++) {
                double a = i * Math.PI * 2.0 / 26.0;
                Location pt = new Location(w, bx + 0.5 + Math.cos(a) * 3.2, groundY + 1.1, bz + 0.5 + Math.sin(a) * 3.2);
                w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 40, 40), 1.6f));
            }
            if (t == 1) {
                w.playSound(new Location(w, bx, groundY, bz), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 0.7f);
                w.playSound(new Location(w, bx, groundY, bz), Sound.ENTITY_GHAST_SCREAM, 0.9f, 0.5f);
            }
            return;
        }

        if (!impacted) {
            clearPlaced();
            currentY--;
            placeSphere();

            Location above = new Location(w, bx + 0.5, currentY + 6, bz + 0.5);
            w.spawnParticle(Particle.FLAME, above, 30, 1.2, 1.0, 1.2, 0.05);
            w.spawnParticle(Particle.LAVA, above, 4, 0.8, 0.5, 0.8, 0);
            w.spawnParticle(Particle.SMOKE, above, 10, 1.0, 0.8, 1.0, 0.02);

            if (currentY <= groundY + 1) {
                impact();
            }
        } else {
            if (t % 5 == 0) {
                Location mid = new Location(w, bx + 0.5, groundY + 3, bz + 0.5);
                w.spawnParticle(Particle.SMOKE, mid, 12, 1.8, 1.2, 1.8, 0.02);
                w.spawnParticle(Particle.FLAME, mid, 10, 2.0, 1.0, 2.0, 0.01);
                w.spawnParticle(Particle.LAVA, mid, 3, 2.0, 0.5, 2.0, 0);
            }
            int lifetime = plugin.getConfig().getInt("abilities.meteor.lifetime-seconds", 10) * 20;
            if (t >= impactTick + lifetime) {
                clearPlaced();
                restoreAll();
                w.playSound(new Location(w, bx + 0.5, groundY + 2, bz + 0.5), Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.6f);
                w.spawnParticle(Particle.SMOKE, new Location(w, bx + 0.5, groundY + 2, bz + 0.5), 40, 2, 1, 2, 0.04);
                cancel();
            }
        }
    }

    private void placeSphere() {
        for (int[] o : sphereOffsets) {
            int x = bx + o[0];
            int y = currentY + o[1];
            int z = bz + o[2];
            String key = x + "," + y + "," + z;
            Block b = w.getBlockAt(x, y, z);
            saved.computeIfAbsent(key, k -> b.getState());
            b.setType(rnd.nextInt(10) < 6 ? Material.MAGMA_BLOCK : Material.BLACKSTONE, false);
            placedNow.add(new int[]{x, y, z});
        }
    }

    private void clearPlaced() {
        for (int[] pos : placedNow) {
            BlockState st = saved.get(pos[0] + "," + pos[1] + "," + pos[2]);
            if (st != null) {
                st.update(true, false);
            }
        }
        placedNow.clear();
    }

    private void restoreAll() {
        for (BlockState st : saved.values()) {
            st.update(true, false);
        }
        saved.clear();
    }

    private void impact() {
        impacted = true;
        impactTick = t;
        Location center = new Location(w, bx + 0.5, groundY + 1.5, bz + 0.5);

        w.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 0.8, 0.5, 0.8, 0);
        w.spawnParticle(Particle.FLAME, center, 130, 2.2, 1.2, 2.2, 0.15);
        w.spawnParticle(Particle.LAVA, center, 35, 2.6, 1.0, 2.6, 0);
        w.spawnParticle(Particle.DUST, center, 60, 2.5, 1.0, 2.5, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 120, 20), 2.0f));
        w.spawnParticle(Particle.SOUL, center, 25, 2.0, 1.0, 2.0, 0.05);

        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
        w.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.5f);
        w.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.4f);

        double damage = plugin.getConfig().getDouble("abilities.meteor.damage", 10.0);
        for (Entity ent : w.getNearbyEntities(center, 5.5, 6.0, 5.5)) {
            if (!(ent instanceof LivingEntity lv)) {
                continue;
            }
            lv.damage(damage);
            Vector away = ent.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(0, 1, 0);
            }
            away.normalize().multiply(0.9).setY(0.7);
            ent.setVelocity(away);
            ent.getWorld().spawnParticle(Particle.SOUL, ent.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.06);
        }
    }

    /** Utility used by other classes to remove leftover falling blocks. */
    public static void removeLater(GravefallPlugin plugin, List<? extends org.bukkit.entity.FallingBlock> blocks, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (org.bukkit.entity.FallingBlock fb : blocks) {
                if (fb != null && fb.isValid()) {
                    fb.remove();
                }
            }
        }, delay);
    }
}
