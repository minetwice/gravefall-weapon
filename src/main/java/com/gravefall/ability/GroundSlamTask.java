package com.gravefall.ability;

import com.gravefall.GravefallPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
 * Ability 2 - GRAVE QUAKE (Sneak + Right Click).
 * The user leaps into the air; when they come down, a 10x10 area of
 * ground blocks erupts upward as falling blocks. Players caught in the
 * blast are launched and can be crushed by the falling stone.
 */
public class GroundSlamTask extends BukkitRunnable {

    private final GravefallPlugin plugin;
    private final Player p;
    private final Random rnd = new Random();
    private int t = 0;
    private boolean slammed = false;
    private int slamTick = -1;

    public GroundSlamTask(GravefallPlugin plugin, Player p) {
        this.plugin = plugin;
        this.p = p;
    }

    public void start() {
        double power = plugin.getConfig().getDouble("abilities.ground-slam.launch-power", 1.15);
        p.setVelocity(new Vector(0, power, 0));
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.55f);
        w.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 0.6f);
        w.spawnParticle(Particle.SOUL, p.getLocation().add(0, 0.2, 0), 30, 0.6, 0.2, 0.6, 0.05);
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

        if (!slammed) {
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation(), 3, 0.3, 0.2, 0.3, 0.02);
            w.spawnParticle(Particle.PORTAL, p.getLocation(), 5, 0.3, 0.3, 0.3, 0.3);
            if ((t > 5 && p.isOnGround()) || t > 90) {
                slammed = true;
                slamTick = t;
                slam();
            }
        } else {
            if (t % 4 == 0) {
                w.spawnParticle(Particle.SOUL, p.getLocation().clone().add(0, 0.5, 0), 6, 2.5, 0.4, 2.5, 0.05);
            }
            if (t > slamTick + 30) {
                cancel();
            }
        }
    }

    private void slam() {
        World w = p.getWorld();
        Location loc = p.getLocation().getBlock().getLocation().add(0.5, 0.1, 0.5);

        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.55f);
        w.playSound(loc, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.7f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0.5, 0.2, 0.5, 0);
        w.spawnParticle(Particle.LAVA, loc, 35, 3, 0.4, 3, 0);
        w.spawnParticle(Particle.SOUL, loc, 90, 3.5, 1.0, 3.5, 0.08);
        w.spawnParticle(Particle.SMOKE, loc, 40, 3, 0.5, 3, 0.02);

        // 10x10 field of erupting stone (corners trimmed).
        List<FallingBlock> blocks = new ArrayList<>();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.abs(dx) == 5 && Math.abs(dz) == 5) {
                    continue;
                }
                Block b = loc.clone().add(dx, 2, dz).getBlock();
                for (int i = 0; i < 8 && !b.getType().isSolid(); i++) {
                    b = b.getRelative(BlockFace.DOWN);
                }
                if (!b.getType().isSolid() || b.isLiquid()) {
                    continue;
                }
                if (b.getType() == Material.BEDROCK || b.getType() == Material.BARRIER
                        || b.getType() == Material.OBSIDIAN || b.getType() == Material.CRYING_OBSIDIAN) {
                    continue;
                }
                BlockData data = b.getBlockData();
                b.setType(Material.AIR, false);
                FallingBlock fb = w.spawnFallingBlock(b.getLocation().add(0.5, 0, 0.5), data);
                fb.setDropItem(false);
                fb.setHurtEntities(true);
                fb.setVelocity(new Vector(
                        (rnd.nextDouble() - 0.5) * 0.5,
                        0.7 + rnd.nextDouble() * 0.6,
                        (rnd.nextDouble() - 0.5) * 0.5));
                blocks.add(fb);
            }
        }

        // Launch + damage entities caught in the blast.
        double damage = plugin.getConfig().getDouble("abilities.ground-slam.damage", 4.0);
        for (Entity ent : w.getNearbyEntities(loc, 6.5, 3.0, 6.5)) {
            if (!(ent instanceof LivingEntity lv) || ent == p) {
                continue;
            }
            lv.damage(damage, p);
            Vector away = ent.getLocation().toVector().subtract(loc.toVector());
            away.setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(0, 0, 1);
            }
            away.normalize().multiply(0.6).setY(1.0);
            ent.setVelocity(away);
            ent.getWorld().spawnParticle(Particle.SOUL, ent.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
        }

        // Clean up any blocks that never land.
        BukkitCleanup.schedule(plugin, blocks, 200L);

        p.sendActionBar(MiniMessage.miniMessage().deserialize(
                "<bold><gradient:#ff5e00:#ffea00>GRAVE QUAKE!</gradient></bold>"));
    }

    /** Removes leftover falling-block entities after a delay. */
    public static final class BukkitCleanup {
        private BukkitCleanup() {
        }

        public static void schedule(GravefallPlugin plugin, List<FallingBlock> blocks, long delayTicks) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (FallingBlock fb : blocks) {
                    if (fb != null && fb.isValid()) {
                        fb.remove();
                    }
                }
            }, delayTicks);
        }
    }
}
