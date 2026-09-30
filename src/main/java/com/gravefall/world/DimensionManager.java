package com.gravefall.world;

import com.gravefall.GravefallPlugin;
import com.gravefall.structure.StructureBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Creates and manages the GRAVEFALL dimension:
 * a floating blue soul island in a starry End-like void containing the
 * one and only Corrupted Kingdom. Handles kingdom generation, ambient
 * effects (volcano smoke, altar beam, rotating crystals) and persistence.
 */
public class DimensionManager {

    private final GravefallPlugin plugin;
    private World dimension;
    private Location kingdomSpawn;
    private Location altarTop;
    private Location volcanoTop;
    private boolean building = false;

    public DimensionManager(GravefallPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads the dimension back if it was already created (server restart). */
    public void init() {
        if (markerFile().exists()) {
            ensureWorld();
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(markerFile());
            double sy = cfg.getDouble("spawn-y", 65);
            double ay = cfg.getDouble("altar-y", 71);
            double vy = cfg.getDouble("volcano-y", 100);
            kingdomSpawn = new Location(dimension, 0.5, sy, 25.5);
            altarTop = new Location(dimension, 0.5, ay, 0.5);
            volcanoTop = new Location(dimension, cfg.getDouble("volcano-x", 120), vy, cfg.getDouble("volcano-z", 120));
            plugin.getLogger().info("Gravefall dimension loaded (kingdom already built).");
        }
    }

    private File markerFile() {
        return new File(plugin.getDataFolder(), "dimension.yml");
    }

    private World ensureWorld() {
        if (dimension != null) {
            return dimension;
        }
        String name = plugin.getConfig().getString("dimension.world-name", "gravefall_dimension");
        int islandRadius = plugin.getConfig().getInt("dimension.island-radius", 380);
        dimension = WorldCreator.name(name)
                .environment(World.Environment.THE_END)
                .generator(new GravefallChunkGenerator(islandRadius, 120))
                .generateStructures(false)
                .createWorld();
        if (dimension != null) {
            dimension.getWorldBorder().setSize(islandRadius * 2 + 40);
            dimension.getWorldBorder().setCenter(0, 0);
            dimension.setSpawnLocation(0, 65, 25);
        }
        return dimension;
    }

    /**
     * Makes sure the dimension + kingdom exist, then runs the callback.
     * Creates the world and generates the Corrupted Kingdom (soul theme)
     * on first use. Feedback messages are sent to the given player.
     */
    public void ensureDimension(Player feedbackTo, Runnable onComplete) {
        World w = ensureWorld();
        if (w == null) {
            if (feedbackTo != null) {
                feedbackTo.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>Failed to create the Gravefall dimension.</red>"));
            }
            return;
        }
        if (kingdomSpawn != null || building) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        building = true;
        if (feedbackTo != null) {
            feedbackTo.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<dark_purple><bold>☠ The Soul Dimension is being torn open...</bold></dark_purple> "
                            + "<gray>generating the Corrupted Kingdom.</gray>"));
        }
        Location center = new Location(w, 0.5, 0, 0.5);
        int radius = plugin.getConfig().getInt("dimension.kingdom-radius", 200);
        StructureBuilder.build(plugin, feedbackTo, w, center, radius, StructureBuilder.Theme.SOUL, info -> {
            // store references
            kingdomSpawn = info.entrance().clone();
            altarTop = info.altarTop().clone();
            volcanoTop = info.volcanoTop().clone();
            building = false;
            saveMarker();
            if (feedbackTo != null) {
                feedbackTo.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<light_purple><bold>The Corrupted Kingdom has risen in the Soul Dimension!</bold></light_purple>"));
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void saveMarker() {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("spawn-y", kingdomSpawn.getY());
            cfg.set("altar-y", altarTop.getY());
            cfg.set("volcano-x", volcanoTop.getX());
            cfg.set("volcano-y", volcanoTop.getY());
            cfg.set("volcano-z", volcanoTop.getZ());
            cfg.save(markerFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save dimension marker: " + e.getMessage());
        }
    }

    /** Ambient effects: volcano smoke, altar beam, rotating soul crystals. */
    public void startAmbience() {
        NamespacedKey crystalKey = new NamespacedKey(plugin, "grv_crystal");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dimension == null || dimension.getPlayers().isEmpty()) {
                    return;
                }
                // volcano smoke column
                if (volcanoTop != null) {
                    Location top = volcanoTop.clone().add(0, 3, 0);
                    dimension.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, top, 3, 2.5, 0.5, 2.5, 0.01);
                    dimension.spawnParticle(Particle.LARGE_SMOKE, top, 1, 1.5, 0.3, 1.5, 0.01);
                    if (Math.random() < 0.1) {
                        dimension.spawnParticle(Particle.LAVA, top, 3, 2.0, 0.5, 2.0, 0);
                    }
                }
                // altar light column
                if (altarTop != null) {
                    for (int y = 0; y < 24; y += 3) {
                        dimension.spawnParticle(Particle.END_ROD, altarTop.clone().add(0, y, 0), 1, 0.08, 0.1, 0.08, 0);
                    }
                }
                // rotating crystals
                long t = System.currentTimeMillis();
                for (Entity e : dimension.getEntitiesByClass(ItemDisplay.class)) {
                    if (e.getPersistentDataContainer().has(crystalKey, PersistentDataType.BYTE)) {
                        ItemDisplay d = (ItemDisplay) e;
                        d.setRotation((t / 60f) % 360f, 0f);
                        d.setInterpolationDelay(-1);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    public World getWorld() {
        return dimension;
    }

    public Location getKingdomSpawn() {
        return kingdomSpawn;
    }

    public Location getAltarTop() {
        return altarTop;
    }

    public Location getVolcanoTop() {
        return volcanoTop;
    }

    public boolean isDimensionWorld(World w) {
        return dimension != null && w != null && w.getName().equals(dimension.getName());
    }
}
