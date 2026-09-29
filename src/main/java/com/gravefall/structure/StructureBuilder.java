package com.gravefall.structure;

import com.gravefall.GravefallPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemFrame;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Procedurally generates the Corrupted Kingdom (default 500x500):
 *  - central soul altar with 5 fragment pedestals (item frames)
 *  - a smoking volcano
 *  - a drowned prismarine monument
 *  - obsidian spikes, ruined deepslate towers, corrupted ground,
 *    soul fire and wither roses
 * Generation is spread over ticks to avoid freezing the server.
 */
public final class StructureBuilder {

    public record StructureInfo(World world, Location center, Location altarTop, List<Location> frameSpots) {
    }

    private record Job(BlockData data, int x, int y, int z) {
    }

    private StructureBuilder() {
    }

    public static void build(GravefallPlugin plugin, Player issuer, World w, Location center, int radius) {
        List<Job> jobs = new ArrayList<>();
        Random rnd = new Random();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int groundY = w.getHighestBlockYAt(cx, cz);
        int baseY = groundY + 1;

        BlockData dPolished = Material.POLISHED_BLACKSTONE.createBlockData();
        BlockData dBricks = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
        BlockData dChiseled = Material.CHISELED_POLISHED_BLACKSTONE.createBlockData();
        BlockData dCrying = Material.CRYING_OBSIDIAN.createBlockData();
        BlockData dObsidian = Material.OBSIDIAN.createBlockData();
        BlockData dGilded = Material.GILDED_BLACKSTONE.createBlockData();
        BlockData dLantern = Material.SOUL_LANTERN.createBlockData();
        BlockData dSoulSoil = Material.SOUL_SOIL.createBlockData();
        BlockData dSoulFire = Material.SOUL_FIRE.createBlockData();
        BlockData dWitherRose = Material.WITHER_ROSE.createBlockData();
        BlockData dBone = Material.BONE_BLOCK.createBlockData();

        // ------------------------------------------------------------------
        // 1) Central altar platform
        // ------------------------------------------------------------------
        int r = 11;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r) {
                    continue;
                }
                int y = w.getHighestBlockYAt(cx + dx, cz + dz);
                BlockData pick = dist > r - 2.2 ? dCrying : dPolished;
                if (dist <= 0.5) {
                    pick = dChiseled;
                } else if (rnd.nextInt(13) == 0) {
                    pick = dGilded;
                }
                jobs.add(new Job(pick, cx + dx, y, cz + dz));
            }
        }
        // altar pedestal (3 layers) - the hammer will awaken above this
        for (int h = 1; h <= 3; h++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (h == 3 && (dx != 0 || dz != 0)) {
                        continue;
                    }
                    BlockData pick = h == 3 ? dGilded : (h == 2 ? dCrying : dBricks);
                    jobs.add(new Job(pick, cx + dx, baseY + h - 1, cz + dz));
                }
            }
        }
        // ring of soul fire around the altar
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2.0 / 10.0;
            int x = cx + (int) Math.round(Math.cos(a) * 6.5);
            int z = cz + (int) Math.round(Math.sin(a) * 6.5);
            int y = w.getHighestBlockYAt(x, z);
            jobs.add(new Job(dSoulSoil, x, y, z));
            jobs.add(new Job(dSoulFire, x, y + 1, z));
        }

        // ------------------------------------------------------------------
        // 2) The five fragment pedestals (item frame holders)
        // ------------------------------------------------------------------
        List<Location> frameSpots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2.0 / 5.0 + Math.PI / 10.0;
            int px = cx + (int) Math.round(Math.cos(a) * 22);
            int pz = cz + (int) Math.round(Math.sin(a) * 22);
            int py = w.getHighestBlockYAt(px, pz);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    jobs.add(new Job(dBricks, px + dx, py, pz + dz));
                }
            }
            for (int h = 1; h <= 3; h++) {
                jobs.add(new Job(h == 3 ? dChiseled : dCrying, px, py + h, pz));
            }
            // four soul lanterns on the base corners
            jobs.add(new Job(dLantern, px + 1, py + 1, pz + 1));
            jobs.add(new Job(dLantern, px - 1, py + 1, pz + 1));
            jobs.add(new Job(dLantern, px + 1, py + 1, pz - 1));
            jobs.add(new Job(dLantern, px - 1, py + 1, pz - 1));

            frameSpots.add(new Location(w, px + 0.5, py + 4.0, pz + 0.5));
        }

        // ------------------------------------------------------------------
        // 3) Volcano (NE)
        // ------------------------------------------------------------------
        int volcanoDist = Math.max(70, (int) (radius * 0.5));
        int coneR = Math.max(14, Math.min(44, (int) (radius * 0.18)));
        int coneH = Math.max(16, Math.min(36, coneR - 6));
        int vx = cx + (int) (Math.cos(Math.PI / 4) * volcanoDist);
        int vz = cz + (int) (Math.sin(Math.PI / 4) * volcanoDist);
        int vBaseY = w.getHighestBlockYAt(vx, vz);
        BlockData dBasalt = Material.BASALT.createBlockData();
        BlockData dBlackstone = Material.BLACKSTONE.createBlockData();
        BlockData dDeepslate = Material.DEEPSLATE.createBlockData();
        BlockData dMagma = Material.MAGMA_BLOCK.createBlockData();
        BlockData dLava = Material.LAVA.createBlockData();
        int craterR = Math.max(6, coneR / 4);

        for (int h = 0; h <= coneH; h++) {
            double rh = coneR * (1.0 - h / (double) coneH);
            int ri = (int) Math.ceil(rh);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > rh) {
                        continue;
                    }
                    int y = vBaseY + h;
                    BlockData pick;
                    if (h >= coneH - 1 && dist <= craterR) {
                        pick = dLava;
                    } else if (h >= coneH - 3 && dist >= craterR - 1) {
                        pick = dMagma;
                    } else {
                        int roll = rnd.nextInt(100);
                        if (roll < 45) {
                            pick = dBasalt;
                        } else if (roll < 85) {
                            pick = dBlackstone;
                        } else {
                            pick = dDeepslate;
                        }
                    }
                    jobs.add(new Job(pick, vx + dx, y, vz + dz));
                }
            }
        }

        // ------------------------------------------------------------------
        // 4) Monument (SW)
        // ------------------------------------------------------------------
        BlockData dPrismarine = Material.PRISMARINE.createBlockData();
        BlockData dPrismBricks = Material.PRISMARINE_BRICKS.createBlockData();
        BlockData dDarkPrism = Material.DARK_PRISMARINE.createBlockData();
        BlockData dSeaLantern = Material.SEA_LANTERN.createBlockData();
        BlockData dWater = Material.WATER.createBlockData();
        int mx = cx - (int) (Math.cos(Math.PI / 4) * volcanoDist);
        int mz = cz - (int) (Math.sin(Math.PI / 4) * volcanoDist);
        int monHalfX = 17;
        int monHalfZ = 11;
        int monBaseY = w.getHighestBlockYAt(mx, mz);

        for (int dx = -monHalfX; dx <= monHalfX; dx++) {
            for (int dz = -monHalfZ; dz <= monHalfZ; dz++) {
                boolean edge = Math.abs(dx) == monHalfX || Math.abs(dz) == monHalfZ;
                boolean pillar = (Math.abs(dx) % 6 == 0 && Math.abs(dz) == monHalfZ)
                        || (Math.abs(dz) % 6 == 0 && Math.abs(dx) == monHalfX);
                if (!edge) {
                    int roll = rnd.nextInt(100);
                    jobs.add(new Job(roll < 70 ? dPrismarine : (roll < 90 ? dPrismBricks : dDarkPrism),
                            mx + dx, monBaseY, mz + dz));
                    continue;
                }
                // wall / pillars
                if (pillar) {
                    for (int h = 1; h <= 5; h++) {
                        jobs.add(new Job(h == 5 ? dSeaLantern : dPrismBricks, mx + dx, monBaseY + h, mz + dz));
                    }
                } else {
                    for (int h = 1; h <= 4; h++) {
                        if (rnd.nextInt(8) == 0) {
                            continue; // weathered holes
                        }
                        jobs.add(new Job(rnd.nextInt(100) < 60 ? dPrismarine : dDarkPrism,
                                mx + dx, monBaseY + h, mz + dz));
                    }
                }
                // roof
                jobs.add(new Job(dDarkPrism, mx + dx, monBaseY + 6, mz + dz));
            }
        }
        // inner pool
        for (int dx = -monHalfX + 3; dx <= monHalfX - 3; dx++) {
            for (int dz = -monHalfZ + 3; dz <= monHalfZ - 3; dz++) {
                jobs.add(new Job(dWater, mx + dx, monBaseY + 1, mz + dz));
            }
        }
        jobs.add(new Job(dSeaLantern, mx, monBaseY + 1, mz));
        jobs.add(new Job(dSeaLantern, mx + 1, monBaseY + 1, mz + 1));
        jobs.add(new Job(dSeaLantern, mx - 1, monBaseY + 1, mz - 1));

        // ------------------------------------------------------------------
        // 5) Obsidian spikes
        // ------------------------------------------------------------------
        int spikes = Math.min(45, Math.max(12, radius / 6));
        for (int i = 0; i < spikes; i++) {
            int sx = 0;
            int sz = 0;
            int tries = 0;
            do {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double d = 30 + rnd.nextDouble() * Math.max(10, radius - 35);
                sx = cx + (int) (Math.cos(a) * d);
                sz = cz + (int) (Math.sin(a) * d);
                tries++;
            } while (tries < 10 && (dist2(sx, sz, vx, vz) < (coneR + 15) * (coneR + 15)
                    || dist2(sx, sz, mx, mz) < 25 * 25
                    || dist2(sx, sz, cx, cz) < 30 * 30));
            int h = 8 + rnd.nextInt(18);
            int sy = w.getHighestBlockYAt(sx, sz);
            for (int layer = 0; layer <= h; layer++) {
                double rl = Math.max(0.0, 2.0 * (1.0 - layer / (double) h));
                int ri = (int) Math.ceil(rl);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (dx * dx + dz * dz > rl * rl + 0.1) {
                            continue;
                        }
                        BlockData pick = (layer == h || rnd.nextInt(10) == 0) ? dCrying : dObsidian;
                        jobs.add(new Job(pick, sx + dx, sy + layer, sz + dz));
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // 6) Ruined deepslate towers
        // ------------------------------------------------------------------
        int towers = Math.min(14, Math.max(6, radius / 18));
        for (int i = 0; i < towers; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = 40 + rnd.nextDouble() * Math.max(10, radius - 45);
            int tx = cx + (int) (Math.cos(a) * d);
            int tz = cz + (int) (Math.sin(a) * d);
            if (dist2(tx, tz, vx, vz) < (coneR + 15) * (coneR + 15) || dist2(tx, tz, mx, mz) < 25 * 25) {
                continue;
            }
            int h = 12 + rnd.nextInt(14);
            int ty = w.getHighestBlockYAt(tx, tz);
            for (int layer = 0; layer <= h; layer++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx * dx + dz * dz > 5.5) {
                            continue;
                        }
                        if (rnd.nextInt(6) == 0) {
                            continue; // ruined look
                        }
                        int roll = rnd.nextInt(100);
                        BlockData pick;
                        if (roll < 55) {
                            pick = Material.DEEPSLATE_BRICKS.createBlockData();
                        } else if (roll < 80) {
                            pick = Material.CRACKED_DEEPSLATE_BRICKS.createBlockData();
                        } else if (roll < 95) {
                            pick = dDeepslate;
                        } else {
                            pick = dCrying;
                        }
                        jobs.add(new Job(pick, tx + dx, ty + layer, tz + dz));
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // 7) Corrupted ground patches + soul fire + wither roses
        // ------------------------------------------------------------------
        Set<Material> corruptible = Set.of(
                Material.GRASS_BLOCK, Material.DIRT, Material.SAND, Material.RED_SAND,
                Material.STONE, Material.GRAVEL, Material.PODZOL, Material.COARSE_DIRT,
                Material.SNOW_BLOCK, Material.ICE, Material.SANDSTONE, Material.TERRACOTTA);

        int patches = Math.min(160, Math.max(40, radius / 2));
        for (int i = 0; i < patches; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = 25 + rnd.nextDouble() * Math.max(5, radius - 30);
            int px = cx + (int) (Math.cos(a) * d);
            int pz = cz + (int) (Math.sin(a) * d);
            int pr = 2 + rnd.nextInt(7);
            for (int dx = -pr; dx <= pr; dx++) {
                for (int dz = -pr; dz <= pr; dz++) {
                    if (dx * dx + dz * dz > pr * pr) {
                        continue;
                    }
                    int x = px + dx;
                    int z = pz + dz;
                    int y = w.getHighestBlockYAt(x, z);
                    Material top = w.getBlockAt(x, y, z).getType();
                    if (!corruptible.contains(top)) {
                        continue;
                    }
                    BlockData pick;
                    int roll = rnd.nextInt(100);
                    if (roll < 45) {
                        pick = Material.NETHERRACK.createBlockData();
                    } else if (roll < 70) {
                        pick = dSoulSoil;
                    } else if (roll < 90) {
                        pick = Material.COARSE_DIRT.createBlockData();
                    } else {
                        pick = dDeepslate;
                    }
                    jobs.add(new Job(pick, x, y, z));
                    if (roll >= 45 && roll < 70 && w.getBlockAt(x, y + 1, z).getType().isAir()) {
                        if (rnd.nextInt(6) == 0) {
                            jobs.add(new Job(dSoulFire, x, y + 1, z));
                        } else if (rnd.nextInt(8) == 0) {
                            jobs.add(new Job(dWitherRose, x, y + 1, z));
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // 8) Bone decorations near the altar
        // ------------------------------------------------------------------
        for (int i = 0; i < 18; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = 14 + rnd.nextDouble() * 18;
            int x = cx + (int) (Math.cos(a) * d);
            int z = cz + (int) (Math.sin(a) * d);
            int y = w.getHighestBlockYAt(x, z);
            jobs.add(new Job(dBone, x, y + 1, z));
            if (rnd.nextInt(3) == 0) {
                jobs.add(new Job(dBone, x, y + 2, z));
            }
        }

        // ------------------------------------------------------------------
        // Apply the jobs over multiple ticks
        // ------------------------------------------------------------------
        Location altarTop = new Location(w, cx + 0.5, baseY + 3.2, cz + 0.5);
        StructureInfo info = new StructureInfo(w, center.clone(), altarTop, frameSpots);
        int total = jobs.size();
        int perTick = plugin.getConfig().getInt("structure.blocks-per-tick", 1200);
        int reportEvery = Math.max(1, (total / perTick) / 5);

        BukkitRunnable apply = new BukkitRunnable() {
            int idx = 0;
            int tickCount = 0;

            @Override
            public void run() {
                int applied = 0;
                while (applied < perTick && idx < jobs.size()) {
                    Job job = jobs.get(idx++);
                    w.getBlockAt(job.x, job.y, job.z).setBlockData(job.data(), false);
                    applied++;
                }
                tickCount++;
                if (issuer != null && tickCount % reportEvery == 0) {
                    int pct = (idx * 100) / total;
                    issuer.sendActionBar(MiniMessage.miniMessage().deserialize(
                            "<dark_purple>Corrupted Kingdom rising... <light_purple>" + pct + "%</light_purple>"));
                }
                if (idx >= jobs.size()) {
                    cancel();
                    spawnFrames(plugin, info, issuer);
                }
            }
        };
        apply.runTaskTimer(plugin, 1L, 1L);
    }

    private static void spawnFrames(GravefallPlugin plugin, StructureInfo info, Player issuer) {
        World w = info.world();
        List<ItemFrame> frames = new ArrayList<>();
        for (Location spot : info.frameSpots()) {
            ItemFrame frame = w.spawn(spot, ItemFrame.class, f -> {
                f.setFacingDirection(BlockFace.UP, true);
                f.setPersistent(true);
                f.setVisible(true);
            });
            frames.add(frame);
        }
        plugin.getStructureRitual().attachStructure(info, frames);

        if (issuer != null && issuer.isOnline()) {
            issuer.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<dark_purple><bold>☠ The Corrupted Kingdom has risen.</bold></dark_purple> <gray>Bind "
                            + plugin.getConfig().getInt("ritual.required-fragments", 5)
                            + " Death Fragments to the item frames to begin the awakening.</gray>"));
        }
        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.broadcast(MiniMessage.miniMessage().deserialize(
                        "<dark_purple><bold>☠ A Corrupted Kingdom has risen somewhere in the world... ☠</bold></dark_purple>")));
    }

    private static int dist2(int ax, int az, int bx, int bz) {
        int dx = ax - bx;
        int dz = az - bz;
        return dx * dx + dz * dz;
    }
}
