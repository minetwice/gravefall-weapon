package com.gravefall.ability;

import com.gravefall.GravefallPlugin;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Ability 1 - SOUL SPIRAL (Right Click) - v2
 *  1. A rising triple-helix spiral of souls wraps the player.
 *  2. A ghost-line of soul particles lashes out from the hammer to the
 *     enemy the player is aiming at (up to 18 blocks).
 *  3. The line crashes into the target: a tight double-helix of soul
 *     fire spirals around the enemy's body, slowing them, then detonates
 *     with damage + knockback + a burst of stolen souls.
 *  4. Spectral wolf claws swipe left and right for anyone in melee range.
 */
public class SoulSpiralTask extends BukkitRunnable {

    private static final int DURATION = 40; // 2s
    private static final double BEAM_RANGE = 18.0;

    private final GravefallPlugin plugin;
    private final Player p;
    private LivingEntity target;
    private boolean targetHit = false;
    private int t = 0;

    public SoulSpiralTask(GravefallPlugin plugin, Player p) {
        this.plugin = plugin;
        this.p = p;
        // find the aimed-at enemy
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection();
        RayTraceResult ray = w.rayTraceEntities(p.getEyeLocation(), dir, BEAM_RANGE, 1.2,
                e -> e instanceof LivingEntity && !e.equals(p) && !(e instanceof ArmorStand));
        if (ray != null && ray.getHitEntity() instanceof LivingEntity le) {
            this.target = le;
        }
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

        // ---- rising spiral around the player ----
        for (int arm = 0; arm < 3; arm++) {
            double angle = progress * Math.PI * 4.0 + arm * (Math.PI * 2.0 / 3.0);
            Location pt = base.clone().add(Math.cos(angle) * radius, -0.5 + progress * 2.6, Math.sin(angle) * radius);
            w.spawnParticle(Particle.SOUL, pt, 3, 0.05, 0.05, 0.05, 0.02);
            w.spawnParticle(Particle.PORTAL, pt, 2, 0.08, 0.08, 0.08, 0.05);
        }
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0, -0.8, 0), 4, 0.4, 0.05, 0.4, 0.01);

        if (t == 1) {
            w.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.6f);
            w.playSound(base, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.6f);
        }

        // ---- ghost line beam to the aimed target (t 2..14) ----
        if (t >= 2 && t <= 14 && target != null && target.isValid()) {
            Location from = p.getEyeLocation();
            Location to = target.getLocation().add(0, 1, 0);
            Vector seg = to.toVector().subtract(from.toVector());
            double frac = (t - 2) / 12.0;
            Location head = from.clone().add(seg.clone().multiply(frac));
            Vector step = seg.clone().multiply(1.0 / 24.0);
            Location cur = from.clone();
            for (int i = 0; i <= 24; i++) {
                w.spawnParticle(Particle.SOUL, cur, 1, 0.02, 0.02, 0.02, 0);
                if (i % 2 == 0) {
                    w.spawnParticle(Particle.DUST, cur, 1, 0.02, 0.02, 0.02, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(160, 80, 255), 1.1f));
                }
                if (i % 3 == 0) {
                    w.spawnParticle(Particle.WITCH, cur, 1, 0.02, 0.02, 0.02, 0);
                }
                cur.add(step);
            }
            w.spawnParticle(Particle.END_ROD, head, 3, 0.05, 0.05, 0.05, 0.02);
            if (t == 2) {
                w.playSound(from, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 1.6f);
            }
            if (t == 14) {
                w.playSound(to, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.4f);
            }
        }

        // ---- spiral around the target's body (t 14..30) ----
        if (t > 14 && t <= 30 && target != null && target.isValid()) {
            double tp = (t - 14) / 16.0;
            Location tc = target.getLocation().add(0, 0.2, 0);
            double height = target.getHeight();
            for (int arm = 0; arm < 2; arm++) {
                double ang = tp * Math.PI * 8.0 + arm * Math.PI;
                double y = tp * height;
                Location pt = tc.clone().add(Math.cos(ang) * 0.55, y, Math.sin(ang) * 0.55);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 1, 0.01, 0.01, 0.01, 0.005);
                w.spawnParticle(Particle.SOUL, pt, 2, 0.02, 0.02, 0.02, 0.01);
                w.spawnParticle(Particle.WITCH, pt, 1, 0.03, 0.03, 0.03, 0);
            }
            if (!targetHit) {
                targetHit = true;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false, true));
            }
            if (t % 5 == 0 && target instanceof Player victim) {
                victim.playSound(victim.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.6f);
            }
        }

        // ---- beam detonation (t 30) ----
        if (t == 30 && target != null && target.isValid()) {
            double damage = plugin.getConfig().getDouble("abilities.soul-spiral.damage", 5.0);
            double knock = plugin.getConfig().getDouble("abilities.soul-spiral.knockback", 1.5);
            target.damage(damage, p);
            Vector away = target.getLocation().toVector().subtract(p.getLocation().toVector());
            away.setY(0);
            if (away.lengthSquared() < 0.01) {
                away = p.getLocation().getDirection().setY(0);
            }
            away.normalize();
            target.setVelocity(away.clone().multiply(knock * 0.8).setY(0.45));
            w.spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 50, 0.35, 0.6, 0.35, 0.08);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            w.spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
            w.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
            w.playSound(target.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.2f, 0.5f);
        }

        if (t == 8) {
            strike(true);  // left claw
        }
        if (t == 20) {
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

        double damage = plugin.getConfig().getDouble("abilities.soul-spiral.damage", 5.0) * 0.6;
        double knock = plugin.getConfig().getDouble("abilities.soul-spiral.knockback", 1.5);

        for (Entity ent : p.getNearbyEntities(5.5, 3.5, 5.5)) {
            if (!(ent instanceof LivingEntity lv) || ent instanceof ArmorStand || ent == p) {
                continue;
            }
            if (ent == target) {
                continue; // target already gets the beam
            }
            Vector to = ent.getLocation().toVector().subtract(p.getLocation().toVector());
            to.setY(0);
            if (to.length() > 4.75) {
                continue;
            }
            to.normalize();
            if (to.dot(dir) < 0.3) {
                continue;
            }
            lv.damage(damage, p);
            ent.setVelocity(to.multiply(knock).setY(0.55));
            ent.getWorld().spawnParticle(Particle.SOUL, ent.getLocation().add(0, 1, 0), 35, 0.3, 0.5, 0.3, 0.06);
            ent.getWorld().spawnParticle(Particle.WITCH, ent.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.0);
            ent.getWorld().playSound(ent.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.7f);
        }
    }
}
