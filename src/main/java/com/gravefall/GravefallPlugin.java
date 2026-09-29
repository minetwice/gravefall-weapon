package com.gravefall;

import com.gravefall.command.GravefallCommand;
import com.gravefall.listener.DeathListener;
import com.gravefall.listener.WeaponListener;
import com.gravefall.structure.StructureRitual;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * GRAVEFALL - the corrupted hammer of the Fallen Kingdom.
 */
public final class GravefallPlugin extends JavaPlugin {

    private NamespacedKey gravefallKey;
    private NamespacedKey fragmentKey;
    private CooldownManager cooldownManager;
    private DeathTracker deathTracker;
    private StructureRitual structureRitual;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gravefallKey = new NamespacedKey(this, "gravefall_weapon");
        fragmentKey = new NamespacedKey(this, "death_fragment");

        cooldownManager = new CooldownManager(this);
        deathTracker = new DeathTracker();
        structureRitual = new StructureRitual(this);

        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(structureRitual, this);

        PluginCommand cmd = Objects.requireNonNull(getCommand("gravefall"), "gravefall command missing from plugin.yml");
        GravefallCommand executor = new GravefallCommand(this);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);

        cooldownManager.startActionBarTask();

        getLogger().info("Gravefall enabled - the corrupted hammer sleeps in the Fallen Kingdom...");
    }

    @Override
    public void onDisable() {
        if (structureRitual != null) {
            structureRitual.cleanup();
        }
        getLogger().info("Gravefall disabled.");
    }

    public NamespacedKey gravefallKey() {
        return gravefallKey;
    }

    public NamespacedKey fragmentKey() {
        return fragmentKey;
    }

    public CooldownManager getCooldowns() {
        return cooldownManager;
    }

    public DeathTracker getDeathTracker() {
        return deathTracker;
    }

    public StructureRitual getStructureRitual() {
        return structureRitual;
    }

    /** Broadcasts a component to every online player and the console. */
    public void broadcast(Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
