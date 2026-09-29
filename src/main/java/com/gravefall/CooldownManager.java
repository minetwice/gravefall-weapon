package com.gravefall;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-ability cooldowns and shows them on the action bar
 * (above the hotbar) for anyone holding GRAVEFALL.
 */
public class CooldownManager {

    private final GravefallPlugin plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public CooldownManager(GravefallPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isReady(Player p, String key) {
        return remaining(p, key) <= 0L;
    }

    public long remaining(Player p, String key) {
        Map<String, Long> m = cooldowns.get(p.getUniqueId());
        if (m == null) {
            return 0L;
        }
        Long until = m.get(key);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void set(Player p, String key, double seconds) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(key, System.currentTimeMillis() + (long) (seconds * 1000L));
    }

    public void startActionBarTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!Items.isGravefall(plugin, p.getInventory().getItemInMainHand())) {
                    continue;
                }
                long spiral = remaining(p, "spiral");
                long slam = remaining(p, "slam");
                long meteor = remaining(p, "meteor");
                String s = entry("Soul Spiral", spiral, "#7b2ff7")
                        + "<dark_gray> ┃ </dark_gray>"
                        + entry("Grave Quake", slam, "#ff9500")
                        + "<dark_gray> ┃ </dark_gray>"
                        + entry("Death Meteor", meteor, "#ff003c");
                p.sendActionBar(MiniMessage.miniMessage().deserialize(s));
            }
        }, 10L, 10L);
    }

    private String entry(String name, long ms, String readyColor) {
        if (ms <= 0L) {
            return "<gray>" + name + ":</gray> <color:" + readyColor + "><bold>READY</bold></color>";
        }
        return "<gray>" + name + ":</gray> <red>" + ((ms + 999L) / 1000L) + "s</red>";
    }
}
