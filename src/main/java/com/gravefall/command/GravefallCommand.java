package com.gravefall.command;

import com.gravefall.GravefallPlugin;
import com.gravefall.Items;
import com.gravefall.ritual.RitualGiveTask;
import com.gravefall.structure.StructureBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /gravefall give <player>        - full sky ritual, then grants the hammer
 * /gravefall fragment <player> [n]- give death fragments (testing)
 * /gravefall structure [radius]   - spawn the Corrupted Kingdom
 * /gravefall revive <player>      - unban a corruption-banned player
 * /gravefall deaths [player]      - show death count
 * /gravefall help
 */
public class GravefallCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "give", "fragment", "structure", "revive", "deaths", "reload", "help");

    private final GravefallPlugin plugin;

    public GravefallCommand(GravefallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("gravefall.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission.</red>"));
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /gravefall give <player></red>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not online.</red>"));
                    return true;
                }
                new RitualGiveTask(plugin, target).start();
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<dark_purple>Summoning GRAVEFALL for <light_purple>" + target.getName() + "</light_purple>..."));
            }
            case "fragment" -> {
                if (args.length < 2) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<red>Usage: /gravefall fragment <player> [amount]</red>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not online.</red>"));
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                for (int i = 0; i < amount; i++) {
                    ItemStack leftover = null;
                    for (ItemStack rest : target.getInventory().addItem(Items.createFragment(plugin)).values()) {
                        leftover = rest;
                    }
                    if (leftover != null) {
                        target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                    }
                }
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<light_purple>Gave " + amount + " Death Fragment(s) to " + target.getName() + ".</light_purple>"));
            }
            case "structure" -> {
                if (plugin.getStructureRitual().hasStructure()) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<red>A corrupted structure already exists in this world.</red>"));
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Only players can use this.</red>"));
                    return true;
                }
                int radius = plugin.getConfig().getInt("structure.radius", 250);
                if (args.length >= 2) {
                    try {
                        radius = Math.max(50, Math.min(250, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                World w = p.getWorld();
                Location center = p.getLocation().getBlock().getLocation();
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<dark_purple>Raising the Corrupted Kingdom (" + (radius * 2) + "x" + (radius * 2)
                                + " blocks) around you...</dark_purple>"));
                StructureBuilder.build(plugin, p, w, center, radius);
            }
            case "revive" -> {
                if (args.length < 2) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /gravefall revive <player></red>"));
                    return true;
                }
                String name = args[1];
                Player target = Bukkit.getPlayerExact(name);
                @SuppressWarnings({"unchecked", "rawtypes"})
                BanList banList = Bukkit.getBanList(BanList.Type.NAME);
                banList.pardon(name);
                if (target != null) {
                    plugin.getDeathTracker().reset(target.getUniqueId());
                } else {
                    // reset by name is not possible offline; it resets on next death anyway
                }
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<green>Revived " + name + " - they may walk the earth again.</green>"));
            }
            case "deaths" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    target = null;
                }
                if (target == null) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not online.</red>"));
                    return true;
                }
                int deaths = plugin.getDeathTracker().get(target.getUniqueId());
                int max = plugin.getConfig().getInt("deaths.max-deaths", 3);
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<gray>" + target.getName() + " has died <red>" + deaths + "</red>/"
                                + max + " allowed deaths.</gray>"));
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<light_purple><bold>☠ Gravefall</bold></light_purple> <gray>configuration reloaded.</gray>"));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<bold><gradient:#b312ff:#ff3ef0>☠ GRAVEFALL ☠</gradient></bold> <dark_gray>- commands</dark_gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_purple>»</dark_purple> <gray>/gravefall give <player> <dark_gray>- summon ritual + grant</dark_gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_purple>»</dark_purple> <gray>/gravefall fragment <player> [n] <dark_gray>- give fragments</dark_gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_purple>»</dark_purple> <gray>/gravefall structure [radius] <dark_gray>- raise the kingdom</dark_gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_purple>»</dark_purple> <gray>/gravefall revive <player> <dark_gray>- unban the fallen</dark_gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_purple>»</dark_purple> <gray>/gravefall deaths [player] <dark_gray>- death count</dark_gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_purple>»</dark_purple> <gray>/gravefall reload <dark_gray>- reload config</dark_gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("fragment") || args[0].equalsIgnoreCase("revive"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return Collections.emptyList();
    }
}
