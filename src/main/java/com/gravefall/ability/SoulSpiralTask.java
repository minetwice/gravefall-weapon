package com.gravefall.ability;

import com.gravefall.GravefallPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Ability 1 - SOUL SPIRAL (Right Click).
 * A rising spiral of soul particles wraps the player while spectral
 * wolf claws swipe left, then right, damaging + knocking back enemies
 * in front and draining soul particles out of them.
 */
public class SoulSpiralTask extends BukkitRunnable {

    private static final int DURATION = 32; // ticks (1.6s)

    private final GravefallPlugin plugin;
    private final Player p;
    private int t = 0;

    public SoulSpiralTask(GravefallPlugin plugin, Player p) {
        this.plugin = plugin;
        this.p = p;
    }

    public void start() {
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void run() {
        if (!p.isOnline() || p.isDead()) {
            cancel();
            return;
        }
        t++;
        World w = p.getWorld();
        Location base = p.getLocation().add(0, 1, 0);
        double progress = t / (double) DURATION;
        double radius = 0.7 + 2.0 * progress;

        // Rising triple-helix spiral around the player.
        for (int arm = 0; arm < 3; arm++) {
            double angle = progress * Math.PI * 4.0 + arm * (Math.PI * 2.0 / 3.0);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = -0.5 + progress * 2.6;
            Location pt = base.clone().add(x, y, z);
            w.spawnParticle(Particle.SOUL, pt, 3, 0.05, 0.05, 0.05, 0.02);
            w.spawnParticle(Particle.PORTAL, pt, 2, 0.08, 0.08, 0.08, 0.05);
        }
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0, -0.8, 0), 4, 0.4, 0.05, 0.4, 0.01);

        if (t == 1) {
            w.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.6f);
            w.playSound(base, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.6f);
        }
        if (t == 6) {
            strike(true);  // left claw
        }
        if (t == 16) {
            strike(false); // right claw
        }
        if (t >= DURATION) {
            cancel();
        }
    }

    /** One spectral claw swipe in front of the player. */
    private void strike(boolean left) {
        World w = p.getWorld();
        Vector dir = p.getLocation().getDirection().clone();
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());

        Location front = p.getEyeLocation().add(dir.clone().multiply(1.1)).subtract(0, 0.2, 0);
        for (int i = 0; i < 9; i++) {
            double off = (left ? -1 : 1) * (i - 4) * 0.32;
            Location pt = front.clone().add(side.clone().multiply(off));
            w.spawnParticle(Particle.SWEEP_ATTACK, pt, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.CRIT, pt, 4, 0.12, 0.3, 0.12, 0.15);
            w.spawnParticle(Particle.SOUL, pt, 2, 0.1, 0.25, 0.1, 0.03);
        }
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, left ? 0.75f : 1.1f);
        w.playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.7f, 1.6f);

        double damage = plugin.getConfig().getDouble("abilities.soul-spiral.damage", 5.0);
        double knock = plugin.getConfig().getDouble("abilities.soul-spiral.knockback", 1.5);

        for (Entity ent : p.getNearbyEntities(5.5, 3.5, 5.5)) {
            if (!(ent instanceof LivingEntity lv) || ent instanceof ArmorStand || ent == p) {
                continue;
            }
            Vector to = ent.getLocation().toVector().subtract(p.getLocation().toVector());
            to.setY(0);
            if (to.length() > 4.75) {
                continue;
            }
            to.normalize();
            if (to.dot(dir) < 0.3) {
                continue; // outside the frontal cone
            }
            lv.damage(damage, p);
            ent.setVelocity(to.multiply(knock).setY(0.55));

            // Souls ripped out of the victim.
            ent.getWorld().spawnParticle(Particle.SOUL, ent.getLocation().add(0, 1, 0), 35, 0.3, 0.5, 0.3, 0.06);
            ent.getWorld().spawnParticle(Particle.WITCH, ent.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.0);
            ent.getWorld().playSound(ent.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.7f);
        }
    }
}
