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
 * The 24-second FINALE after the hammer is claimed (v2 - more epic):
 * a giant particle tornado rips the kingdom apart while lightning rains,
 * blocks spiral into the storm around the new owner, everyone nearby
 * except the owner takes damage, and it ends with a lightning barrage
 * and shockwave rings.
 */
public class TornadoFinale extends BukkitRunnable {

    private static final int TOTAL = 480; // 24 seconds

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
        double rr = 2.0 + 20.0 * t / TOTAL;

        // ---- triple tornado columns ----
        for (int arm = 0; arm < 3; arm++) {
            for (int h = 0; h <= 30; h += 2) {
                double ang = t * 0.35 + arm * (Math.PI * 2.0 / 3.0) + h * 0.28;
                double shrink = 0.3 + 0.7 * (h / 30.0);
                Location pt = center.clone().add(Math.cos(ang) * rr * shrink, h, Math.sin(ang) * rr * shrink);
                w.spawnParticle(Particle.PORTAL, pt, 2, 0.05, 0.12, 0.05, 0.12);
                w.spawnParticle(Particle.SOUL, pt, 1, 0.05, 0.1, 0.05, 0.02);
            }
        }
        // soul-dust sheath around the whole storm
        if (t % 2 == 0) {
            w.spawnParticle(Particle.DUST, center.clone().add(0, 14, 0), 25, rr * 0.6, 14, rr * 0.6, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 60, 255), 2.2f));
            w.spawnParticle(Particle.ASH, center.clone().add(0, 20, 0), 30, 8, 10, 8, 0.05);
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, 10, 0), 14, 5, 7, 5, 0.02);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 2, 0), 20, 5, 1, 5, 0.02);
        }
        if (t % 40 == 0) {
            w.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 1.2f, 0.6f);
            w.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.4f);
        }

        // ---- lightning + block flinging ----
        if (t % 10 == 0) {
            Location strike = center.clone().add(rnd.nextInt(51) - 25, 0, rnd.nextInt(51) - 25);
            w.strikeLightningEffect(strike);
            w.playSound(strike, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.7f);

            for (int i = 0; i < 8; i++) {
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
                Vector rel = fb.getLocation().toVector().subtract(center.toVector());
                Vector tangent = new Vector(-rel.getZ(), 0, rel.getX());
                if (tangent.lengthSquared() > 0.01) {
                    tangent.normalize().multiply(1.0);
                }
                tangent.setY(1.0 + rnd.nextDouble() * 0.4);
                fb.setVelocity(tangent);
                debris.add(fb);
            }
        }

        // ---- damage everyone in the storm, except the owner ----
        if (t % 15 == 0) {
            for (Entity ent : w.getNearbyEntities(center, 45, 25, 45)) {
                if (!(ent instanceof LivingEntity lv) || ent.equals(owner)) {
                    continue;
                }
                lv.damage(5.0, owner);
                Vector pull = center.toVector().subtract(ent.getLocation().toVector());
                pull.setY(0);
                if (pull.lengthSquared() > 0.01) {
                    pull.normalize().multiply(0.45);
                }
                pull.setY(0.8);
                ent.setVelocity(pull);
                ent.getWorld().spawnParticle(Particle.SOUL, ent.getLocation().add(0, 1, 0), 16, 0.3, 0.5, 0.3, 0.05);
            }
            if (owner.isOnline()) {
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, owner.getLocation().add(0, 1, 0), 12, 0.5, 0.8, 0.5, 0.02);
                w.spawnParticle(Particle.END_ROD, owner.getLocation().add(0, 2, 0), 4, 0.3, 0.5, 0.3, 0.02);
            }
        }

        if (t >= TOTAL) {
            end();
        }
    }

    private void end() {
        World w = center.getWorld();
        // lightning barrage in a ring
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI * 2.0 / 12.0;
            Location strike = center.clone().add(Math.cos(ang) * 30, 0, Math.sin(ang) * 30);
            w.strikeLightningEffect(strike);
        }
        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.5f);
        w.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.8f);
        w.playSound(center, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.7f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 5, 0), 6, 4, 5, 4, 0);
        // expanding shockwave rings
        for (int ring = 0; ring < 3; ring++) {
            double radius = 8 + ring * 12;
            for (int i = 0; i < 40; i++) {
                double ang = i * Math.PI * 2.0 / 40.0;
                w.spawnParticle(Particle.SOUL, center.clone().add(Math.cos(ang) * radius, 2, Math.sin(ang) * radius),
                        2, 0.1, 0.5, 0.1, 0.05);
                w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(ang) * radius, 1, Math.sin(ang) * radius),
                        1, 0.1, 0.3, 0.1, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 60, 255), 2.5f));
            }
        }
        w.spawnParticle(Particle.SMOKE, center.clone().add(0, 5, 0), 250, 12, 12, 12, 0.1);
        w.spawnParticle(Particle.SOUL, center.clone().add(0, 3, 0), 300, 14, 8, 14, 0.1);

        // clean up flying debris later
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (FallingBlock fb : debris) {
                if (fb != null && fb.isValid()) {
                    fb.remove();
                }
            }
        }, 300L);
        cancel();
    }
}
