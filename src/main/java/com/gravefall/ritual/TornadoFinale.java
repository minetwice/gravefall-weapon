package com.gravefall.ritual;

import com.gravefall.GravefallPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The 20-second finale after the hammer is claimed:
 * lightning rains over the whole structure, blocks are ripped out
 * and spiral into a growing tornado around the new owner, and every
 * nearby player except the owner takes damage.
 */
public class TornadoFinale extends BukkitRunnable {

    private static final int TOTAL = 400; // 20 seconds

    private final GravefallPlugin plugin;
    private final Player owner;
    private final Location center;
    private final Random rnd = new Random();
    private final List<FallingBlock> debris = new ArrayList<>();
    private int t = 0;

    public TornadoFinale(GravefallPlugin plugin, Player owner, Location center) {
        this.plugin = plugin;
        this.owner = owner;
        this.center = center;
    }

    public void start() {
        World w = center.getWorld();
        w.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.5f);
        w.playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void run() {
        t++;
        World w = center.getWorld();
        double rr = 2.0 + 14.0 * t / TOTAL; // tornado grows over time

        // ---- tornado particle columns ----
        for (int arm = 0; arm < 3; arm++) {
            for (int h = 0; h <= 24; h += 2) {
                double ang = t * 0.35 + arm * (Math.PI * 2.0 / 3.0) + h * 0.28;
                double shrink = 0.35 + 0.65 * (h / 24.0);
                Location pt = center.clone().add(
                        Math.cos(ang) * rr * shrink, h, Math.sin(ang) * rr * shrink);
                w.spawnParticle(Particle.PORTAL, pt, 1, 0.05, 0.1, 0.05, 0.1);
                w.spawnParticle(Particle.SOUL, pt, 1, 0.05, 0.1, 0.05, 0.02);
            }
        }
        if (t % 2 == 0) {
            w.spawnParticle(Particle.ASH, center.clone().add(0, 18, 0), 25, 6, 8, 6, 0.05);
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, 10, 0), 12, 4, 6, 4, 0.02);
        }
        if (t % 40 == 0) {
            w.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.6f);
        }

        // ---- lightning + block flinging ----
        if (t % 15 == 0) {
            Location strike = center.clone().add(rnd.nextInt(45) - 22, 0, rnd.nextInt(45) - 22);
            w.strikeLightningEffect(strike);
            w.playSound(strike, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.7f);

            for (int i = 0; i < 5; i++) {
                int dx = rnd.nextInt(49) - 24;
                int dz = rnd.nextInt(49) - 24;
                Block b = w.getHighestBlockAt(strike.getBlockX() + dx, strike.getBlockZ() + dz);
                if (!b.getType().isSolid() || b.isLiquid()
                        || b.getType() == Material.BEDROCK || b.getType() == Material.OBSIDIAN
                        || b.getType() == Material.CRYING_OBSIDIAN) {
                    continue;
                }
                BlockData d = b.getBlockData();
                b.setType(Material.AIR, false);
                FallingBlock fb = w.spawnFallingBlock(b.getLocation().add(0.5, 0, 0.5), d);
                fb.setDropItem(false);
                fb.setHurtEntities(true);
                // tangential velocity = spiral into the tornado
                Vector rel = fb.getLocation().toVector().subtract(center.toVector());
                Vector tangent = new Vector(-rel.getZ(), 0, rel.getX());
                if (tangent.lengthSquared() > 0.01) {
                    tangent.normalize().multiply(0.8);
                }
                tangent.setY(0.9);
                fb.setVelocity(tangent);
                debris.add(fb);
            }
        }

        // ---- damage everyone near the storm, except the owner ----
        if (t % 20 == 0) {
            for (Entity ent : w.getNearbyEntities(center, 40, 20, 40)) {
                if (!(ent instanceof LivingEntity lv) || ent.equals(owner)) {
                    continue;
                }
                lv.damage(4.0, owner);
                Vector pull = center.toVector().subtract(ent.getLocation().toVector());
                pull.setY(0);
                if (pull.lengthSquared() > 0.01) {
                    pull.normalize().multiply(0.35);
                }
                pull.setY(0.7);
                ent.setVelocity(pull);
                ent.getWorld().spawnParticle(Particle.SOUL, ent.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.05);
            }
            if (owner.isOnline()) {
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, owner.getLocation().add(0, 1, 0), 10, 0.5, 0.8, 0.5, 0.02);
            }
        }

        if (t >= TOTAL) {
            end();
        }
    }

    private void end() {
        World w = center.getWorld();
        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        w.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.8f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 5, 0), 5, 3, 5, 3, 0);
        w.spawnParticle(Particle.SMOKE, center.clone().add(0, 5, 0), 150, 10, 12, 10, 0.1);
        w.spawnParticle(Particle.SOUL, center.clone().add(0, 3, 0), 200, 12, 8, 12, 0.1);

        // clean up flying debris later
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (FallingBlock fb : debris) {
                if (fb != null && fb.isValid()) {
                    fb.remove();
                }
            }
        }, 200L);
        cancel();
    }
}
