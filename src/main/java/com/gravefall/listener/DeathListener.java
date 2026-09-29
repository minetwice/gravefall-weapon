package com.gravefall.listener;

import com.gravefall.GravefallPlugin;
import com.gravefall.Items;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

/**
 * Death system:
 *  - Killing a player grants the killer a Death Fragment.
 *  - Killing a player WITH GRAVEFALL heals the wielder (Soul Harvest).
 *  - Dying more than 'max-deaths' times bans the player
 *    (undo with /gravefall revive).
 *  - GRAVEFALL itself never drops on death (keep-on-death).
 */
public class DeathListener implements Listener {

    private final GravefallPlugin plugin;

    public DeathListener(GravefallPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();

        // ---- keep the hammer on death ----
        if (plugin.getConfig().getBoolean("weapon.keep-on-death", true)) {
            java.util.List<ItemStack> toKeep = new ArrayList<>();
            for (ItemStack drop : e.getDrops()) {
                if (Items.isGravefall(plugin, drop)) {
                    toKeep.add(drop);
                }
            }
            e.getDrops().removeAll(toKeep);
            e.getItemsToKeep().addAll(toKeep);
        }

        // ---- ban check ----
        int count = plugin.getDeathTracker().record(victim.getUniqueId());
        int max = plugin.getConfig().getInt("deaths.max-deaths", 3);
        if (count > max) {
            String reason = plugin.getConfig().getString("deaths.ban-reason",
                    "The corruption has claimed your soul...");
            @SuppressWarnings({"unchecked", "rawtypes"})
            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
            banList.addBan(victim.getName(), reason, (Date) null, "Gravefall");
            plugin.broadcast(MiniMessage.miniMessage().deserialize(
                    "<dark_red><bold>☠ " + victim.getName()
                            + "</bold></dark_red><red> has fallen too many times and was consumed by the corruption...</red>"));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isOnline()) {
                    victim.kick(MiniMessage.miniMessage().deserialize(
                            "<dark_red><bold>☠ GRAVEFALL ☠</bold></dark_red><newline><red>"
                                    + reason + "</red>"));
                }
            });
        }

        // ---- fragment drop + soul harvest for the killer ----
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            ItemStack frag = Items.createFragment(plugin);
            Map<Integer, ItemStack> leftover = killer.getInventory().addItem(frag);
            for (ItemStack rest : leftover.values()) {
                killer.getWorld().dropItemNaturally(killer.getLocation(), rest);
            }
            killer.playSound(killer.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.6f);
            killer.getWorld().spawnParticle(Particle.SOUL,
                    victim.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.08);
            killer.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<light_purple>You claimed a <bold>Death Fragment</bold> from "
                            + victim.getName() + "<light_purple>!"));

            // Soul Harvest: killing with the hammer heals the wielder.
            if (Items.isGravefall(plugin, killer.getInventory().getItemInMainHand())) {
                double heal = plugin.getConfig().getDouble("abilities.soul-harvest.heal", 4.0);
                if (heal > 0) {
                    double maxHealth = killer.getAttribute(Attribute.MAX_HEALTH) != null
                            ? killer.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
                    killer.setHealth(Math.min(maxHealth, killer.getHealth() + heal));
                }
                killer.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        killer.getLocation().add(0, 1, 0), 35, 0.5, 0.8, 0.5, 0.05);
                killer.getWorld().spawnParticle(Particle.WITCH,
                        killer.getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.0);
                killer.getWorld().playSound(killer.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.2f, 0.6f);
                killer.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<gradient:#7b2ff7:#b07dff><bold>SOUL HARVEST!</bold></gradient> "
                                + "<gray>Your hammer devours the fallen soul...</gray>"));
            }
        }
    }
}
