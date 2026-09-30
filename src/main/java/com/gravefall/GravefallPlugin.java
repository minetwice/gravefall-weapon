package com.gravefall;

import com.gravefall.command.GravefallCommand;
import com.gravefall.listener.DeathListener;
import com.gravefall.listener.WeaponListener;
import com.gravefall.mobs.MobManager;
import com.gravefall.portal.PortalManager;
import com.gravefall.structure.StructureRitual;
import com.gravefall.world.DimensionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * GRAVEFALL - the corrupted hammer of the Fallen Kingdom.
 * v1.2.0: Soul Dimension + portals + dimension mobs + epic ability/ritual FX.
 */
public final class GravefallPlugin extends JavaPlugin {

    private NamespacedKey gravefallKey;
    private NamespacedKey fragmentKey;
    private CooldownManager cooldownManager;
    private DeathTracker deathTracker;
    private StructureRitual structureRitual;
    private DimensionManager dimensionManager;
    private PortalManager portalManager;
    private MobManager mobManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gravefallKey = new NamespacedKey(this, "gravefall_weapon");
        fragmentKey = new NamespacedKey(this, "death_fragment");

        cooldownManager = new CooldownManager(this);
        deathTracker = new DeathTracker();
        structureRitual = new StructureRitual(this);
        dimensionManager = new DimensionManager(this);
        portalManager = new PortalManager(this);
        mobManager = new MobManager(this);

        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(structureRitual, this);
        getServer().getPluginManager().registerEvents(portalManager, this);

        PluginCommand cmd = Objects.requireNonNull(getCommand("gravefall"), "gravefall command missing from plugin.yml");
        GravefallCommand executor = new GravefallCommand(this);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);

        cooldownManager.startActionBarTask();
        portalManager.startTravelTask();
        mobManager.start();
        dimensionManager.startAmbience();
        dimensionManager.init();          // re-load an existing dimension after restart
        portalManager.loadPortals();      // re-load existing portals after restart

        getLogger().info("Gravefall enabled - the Soul Dimension sleeps beyond the veil...");
    }

    @Override
    public void onDisable() {
        if (structureRitual != null) {
            structureRitual.cleanup();
        }
        if (portalManager != null) {
            portalManager.savePortals();
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

    public DimensionManager getDimensionManager() {
        return dimensionManager;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }

    public MobManager getMobManager() {
        return mobManager;
    }

    /** Broadcasts a component to every online player and the console. */
    public void broadcast(Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
