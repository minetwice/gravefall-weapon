package com.gravefall;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Factory + identification for the GRAVEFALL hammer and Death Fragments.
 */
public final class Items {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Items() {
    }

    private static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Creates the GRAVEFALL hammer (netherite axe base + custom model data). */
    public static ItemStack createGravefall(GravefallPlugin plugin) {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = axe.getItemMeta();

        meta.displayName(mm("<bold><gradient:#b312ff:#ff3ef0>☠ GRAVEFALL ☠</gradient></bold>"));

        meta.lore(List.of(
                mm("<dark_gray>Forged in the ashes of the Fallen Kingdom,</dark_gray>"),
                mm("<dark_gray>where the souls of a thousand warriors sleep.</dark_gray>"),
                mm(""),
                mm("<gradient:#7b2ff7:#b07dff><bold>SOUL SPIRAL</bold></gradient> <dark_gray>•</dark_gray> <gray>Right Click</gray>"),
                mm("<gray>A spiral of souls erupts around you as</gray>"),
                mm("<gray>spectral wolf claws rake your enemies away.</gray>"),
                mm(""),
                mm("<gradient:#ff5e00:#ffb800><bold>GRAVE QUAKE</bold></gradient> <dark_gray>•</dark_gray> <gray>Sneak + Right Click</gray>"),
                mm("<gray>Leap skyward and shatter the earth - a 10x10</gray>"),
                mm("<gray>field of stone rises, hurling and crushing foes.</gray>"),
                mm(""),
                mm("<gradient:#ff003c:#ff7b00><bold>DEATH METEOR</bold></gradient> <dark_gray>•</dark_gray> <gray>Passive</gray>"),
                mm("<gray>Land 3 strikes in a row to call down a burning</gray>"),
                mm("<gray>meteor upon your victim. It fades after 10s.</gray>"),
                mm(""),
                mm("<dark_gray>»</dark_gray> <gray>Only the one who completes the</gray>"),
                mm("<gray>Ritual of Five Fragments may wield it.</gray>")
        ));

        meta.setCustomModelData(plugin.getConfig().getInt("custom-model-data", 7401));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.UNBREAKING, 5, true);
        meta.getPersistentDataContainer().set(plugin.gravefallKey(), PersistentDataType.BYTE, (byte) 1);

        axe.setItemMeta(meta);
        return axe;
    }

    public static boolean isGravefall(GravefallPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_AXE || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.gravefallKey(), PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    /** Creates a Death Fragment (dropped when a player kills another player). */
    public static ItemStack createFragment(GravefallPlugin plugin) {
        ItemStack shard = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = shard.getItemMeta();

        meta.displayName(mm("<bold><gradient:#5a00b0:#9b30ff>☠ Death Fragment</gradient></bold>"));
        meta.lore(List.of(
                mm("<dark_gray>A crystallized shard of a fallen soul.</dark_gray>"),
                mm(""),
                mm("<gray>Collect <light_purple>5</light_purple> of these and bind them</gray>"),
                mm("<gray>to the item frames of a corrupted altar</gray>"),
                mm("<gray>to awaken the <bold>GRAVEFALL</bold> hammer.</gray>"),
                mm(""),
                mm("<dark_gray>» Obtained by killing players</dark_gray>")
        ));

        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(plugin.fragmentKey(), PersistentDataType.BYTE, (byte) 1);

        shard.setItemMeta(meta);
        return shard;
    }

    public static boolean isFragment(GravefallPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.fragmentKey(), PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    /** Invisible icon item for the portal plane display entity (RP model). */
    public static ItemStack createPortalIcon(GravefallPlugin plugin) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<blue>Gravefall Portal</blue>"));
        meta.setCustomModelData(plugin.getConfig().getInt("custom-model-data-portal", 7402));
        paper.setItemMeta(meta);
        return paper;
    }

    /** Icon item for the floating soul crystals (RP model). */
    public static ItemStack createCrystalIcon(GravefallPlugin plugin) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<color:#7fd4ff>Soul Crystal</color>"));
        meta.setCustomModelData(plugin.getConfig().getInt("custom-model-data-crystal", 7403));
        paper.setItemMeta(meta);
        return paper;
    }

    /** Icon item for the spinning portal rune ring (RP model). */
    public static ItemStack createPortalRingIcon(GravefallPlugin plugin) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<color:#00e5ff>Portal Rune Ring</color>"));
        meta.setCustomModelData(plugin.getConfig().getInt("custom-model-data-portal-ring", 7404));
        paper.setItemMeta(meta);
        return paper;
    }
}
