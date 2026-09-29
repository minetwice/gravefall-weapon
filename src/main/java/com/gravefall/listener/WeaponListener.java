package com.gravefall.listener;

import com.gravefall.GravefallPlugin;
import com.gravefall.Items;
import com.gravefall.ability.GroundSlamTask;
import com.gravefall.ability.MeteorTask;
import com.gravefall.ability.SoulSpiralTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all GRAVEFALL weapon input:
 *  - Right Click        -> Soul Spiral
 *  - Sneak + Right Click-> Grave Quake
 *  - 3-hit melee combo  -> Death Meteor (passive)
 */
public class WeaponListener implements Listener {

    private static final long COMBO_WINDOW_MS = 4000;

    private final GravefallPlugin plugin;
    private final Map<UUID, Combo> combos = new HashMap<>();

    private static final class Combo {
        int hits;
        long last;
    }

    public WeaponListener(GravefallPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        if (!Items.isGravefall(plugin, p.getInventory().getItemInMainHand())) {
            return;
        }
        e.setCancelled(true);

        if (p.isSneaking()) {
            if (!plugin.getConfig().getBoolean("abilities.ground-slam.enabled", true)) {
                disabled(p, "Grave Quake");
                return;
            }
            long rem = plugin.getCooldowns().remaining(p, "slam");
            if (rem > 0) {
                denied(p, "Grave Quake", rem);
                return;
            }
            plugin.getCooldowns().set(p, "slam",
                    plugin.getConfig().getDouble("abilities.ground-slam.cooldown", 22));
            new GroundSlamTask(plugin, p).start();
        } else {
            if (!plugin.getConfig().getBoolean("abilities.soul-spiral.enabled", true)) {
                disabled(p, "Soul Spiral");
                return;
            }
            long rem = plugin.getCooldowns().remaining(p, "spiral");
            if (rem > 0) {
                denied(p, "Soul Spiral", rem);
                return;
            }
            plugin.getCooldowns().set(p, "spiral",
                    plugin.getConfig().getDouble("abilities.soul-spiral.cooldown", 8));
            new SoulSpiralTask(plugin, p).start();
        }
    }

    private void denied(Player p, String ability, long ms) {
        p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.6f);
        p.sendActionBar(MiniMessage.miniMessage().deserialize(
                "<gray>" + ability + " <red>on cooldown — " + ((ms + 999L) / 1000L) + "s</red></gray>"));
    }

    private void disabled(Player p, String ability) {
        p.sendActionBar(MiniMessage.miniMessage().deserialize(
                "<gray>" + ability + " <dark_gray>is disabled on this server.</dark_gray></gray>"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }
        if (!Items.isGravefall(plugin, p.getInventory().getItemInMainHand())) {
            return;
        }
        if (!plugin.getConfig().getBoolean("abilities.meteor.enabled", true)) {
            return;
        }
        if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity)) {
            return;
        }

        // Every gravefall hit shows a small spectral swipe on the victim.
        e.getEntity().getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                e.getEntity().getLocation().add(0, 1, 0), 1, 0.2, 0.4, 0.2, 0);

        long now = System.currentTimeMillis();
        Combo c = combos.computeIfAbsent(p.getUniqueId(), k -> new Combo());
        if (now - c.last > COMBO_WINDOW_MS) {
            c.hits = 0;
        }
        c.last = now;
        c.hits++;

        int needed = plugin.getConfig().getInt("abilities.combo-hits", 3);

        if (c.hits >= needed) {
            c.hits = 0;
            long rem = plugin.getCooldowns().remaining(p, "meteor");
            if (rem > 0) {
                p.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<gray>Death Meteor recharging — " + ((rem + 999L) / 1000L) + "s</gray>"));
                return;
            }
            plugin.getCooldowns().set(p, "meteor",
                    plugin.getConfig().getDouble("abilities.meteor.cooldown", 25));
            new MeteorTask(plugin, e.getEntity().getLocation()).start();
            p.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<bold><gradient:#ff003c:#ff7b00>DEATH METEOR INCOMING!</gradient></bold>"));
        } else {
            p.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<gray>Combo <yellow>" + c.hits + "/" + needed + "</yellow> — Death Meteor</gray>"));
        }
    }
}
