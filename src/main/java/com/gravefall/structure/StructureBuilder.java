package com.gravefall.structure;

import com.gravefall.GravefallPlugin;
import com.gravefall.Items;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Procedurally generates the Corrupted Kingdom (v2):
 *  - theme CORRUPTED (overworld, blackstone) or SOUL (dimension, blue/glowing)
 *  - 3-tier soul altar with obelisk + floating rotating crystals
 *  - 5 fragment pedestals (new design: flanked by lantern pillars)
 *  - big volcano with lava crater + lava channels + obsidian spike ring
 *  - drowned prismarine monument
 *  - kingdom wall ring with 8 towers + gate
 *  - ruined houses, obsidian spikes, corrupted/sculk ground
 * Generation is spread over ticks. onComplete receives the StructureInfo.
 */
public final class StructureBuilder {

    public enum Theme {CORRUPTED, SOUL}

    public record StructureInfo(World world, Location center, Location altarTop, Location volcanoTop,
                                 Location entrance, List<Location> frameSpots) {
    }

    private record Job(BlockData data, int x, int y, int z) {
    }

    /** Per-theme material palette. */
    private static final class Palette {
        BlockData platform, platformAccent, rareAccent;
        BlockData altarTier, altarBody, altarPillar, altarTopBlock;
        BlockData frameBase, framePillar, frameTop;
        BlockData wall, wallCracked, wallBattlement, towerLight;
        BlockData lantern, soulSoil, soulFire;

        Palette(Theme theme) {
            if (theme == Theme.SOUL) {
                platform = Material.POLISHED_DEEPSLATE.createBlockData();
                platformAccent = Material.SCULK.createBlockData();
                rareAccent = Material.SEA_LANTERN.createBlockData();
                altarTier = Material.DEEPSLATE_TILES.createBlockData();
                altarBody = Material.SCULK.createBlockData();
                altarPillar = Material.CRYING_OBSIDIAN.createBlockData();
                altarTopBlock = Material.CHISELED_DEEPSLATE.createBlockData();
                frameBase = Material.DEEPSLATE_BRICKS.createBlockData();
                framePillar = Material.DEEPSLATE_TILES.createBlockData();
                frameTop = Material.CHISELED_DEEPSLATE.createBlockData();
                wall = Material.DEEPSLATE_BRICKS.createBlockData();
                wallCracked = Material.CRACKED_DEEPSLATE_BRICKS.createBlockData();
                wallBattlement = Material.DEEPSLATE_TILE_WALL.createBlockData();
                towerLight = Material.SEA_LANTERN.createBlockData();
            } else {
                platform = Material.POLISHED_BLACKSTONE.createBlockData();
                platformAccent = Material.CRYING_OBSIDIAN.createBlockData();
                rareAccent = Material.GILDED_BLACKSTONE.createBlockData();
                altarTier = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
                altarBody = Material.CRYING_OBSIDIAN.createBlockData();
                altarPillar = Material.OBSIDIAN.createBlockData();
                altarTopBlock = Material.CHISELED_POLISHED_BLACKSTONE.createBlockData();
                frameBase = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
                framePillar = Material.CRYING_OBSIDIAN.createBlockData();
                frameTop = Material.CHISELED_POLISHED_BLACKSTONE.createBlockData();
                wall = Material.DEEPSLATE_BRICKS.createBlockData();
                wallCracked = Material.CRACKED_DEEPSLATE_BRICKS.createBlockData();
                wallBattlement = Material.POLISHED_BLACKSTONE_BRICK_WALL.createBlockData();
                towerLight = Material.SEA_LANTERN.createBlockData();
            }
            lantern = Material.SOUL_LANTERN.createBlockData();
            soulSoil = Material.SOUL_SOIL.createBlockData();
            soulFire = Material.SOUL_FIRE.createBlockData();
        }
    }

    private StructureBuilder() {
    }

    public static void build(GravefallPlugin plugin, Player issuer, World w, Location center,
                             int radius, Theme theme, Consumer<StructureInfo> onComplete) {
        Palette pal = new Palette(theme);
        List<Job> jobs = new ArrayList<>();
        Random rnd = new Random();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int groundY = w.getHighestBlockYAt(cx, cz);
        int baseY = groundY + 1;

        // ------------------------------------------------------------------
        // 1) 3-tier soul altar
        // ------------------------------------------------------------------
        int[] tierR = {14, 10, 6};
        for (int tier = 0; tier < 3; tier++) {
            int r = tierR[tier];
            int y = groundY + tier;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > r) {
                        continue;
                    }
                    int yy = Math.min(y, w.getHighestBlockYAt(cx + dx, cz + dz) + tier);
                    BlockData pick = tier == 0 ? pal.platform
                            : (tier == 1 ? pal.altarTier : pal.altarBody);
                    if (rnd.nextInt(9) == 0) {
                        pick = pal.platformAccent;
                    }
                    if (rnd.nextInt(30) == 0) {
                        pick = pal.rareAccent;
                    }
                    jobs.add(new Job(pick, cx + dx, yy, cz + dz));
                }
            }
        }
        // central obelisk
        for (int h = 1; h <= 4; h++) {
            jobs.add(new Job(h == 4 ? pal.altarTopBlock : pal.altarPillar, cx, baseY + 2 + h, cz));
        }
        // 4 braziers on the top tier
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0 + Math.PI / 4.0;
            int x = cx + (int) Math.round(Math.cos(a) * 4.5);
            int z = cz + (int) Math.round(Math.sin(a) * 4.5);
            jobs.add(new Job(pal.soulSoil, x, baseY + 2, z));
            jobs.add(new Job(pal.soulFire, x, baseY + 3, z));
        }
        Location altarTop = new Location(w, cx + 0.5, baseY + 7.0, cz + 0.5);

        // ------------------------------------------------------------------
        // 2) The five fragment pedestals (new design)
        // ------------------------------------------------------------------
        List<Location> frameSpots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2.0 / 5.0 + Math.PI / 10.0;
            int px = cx + (int) Math.round(Math.cos(a) * 22);
            int pz = cz + (int) Math.round(Math.sin(a) * 22);
            int py = w.getHighestBlockYAt(px, pz);

            // 5x5 base
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    jobs.add(new Job(pal.frameBase, px + dx, py, pz + dz));
                }
            }
            // 4 corner lantern pillars
            int[][] corners = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
            for (int[] c : corners) {
                jobs.add(new Job(pal.framePillar, px + c[0], py + 1, pz + c[1]));
                jobs.add(new Job(pal.lantern, px + c[0], py + 2, pz + c[1]));
            }
            // main pillar 4 high
            for (int h = 1; h <= 4; h++) {
                jobs.add(new Job(h == 4 ? pal.frameTop : pal.framePillar, px, py + h, pz));
            }
            frameSpots.add(new Location(w, px + 0.5, py + 5.0, pz + 0.5));
        }

        // ------------------------------------------------------------------
        // 3) Volcano (bigger, lava crater + channels + spike ring)
        // ------------------------------------------------------------------
        int volcanoDist = Math.max(70, (int) (radius * 0.45));
        int coneR = Math.max(14, Math.min(48, (int) (radius * 0.16)));
        int coneH = Math.max(16, Math.min(40, coneR - 6));
        int vx = cx + (int) (Math.cos(Math.PI / 4) * volcanoDist);
        int vz = cz + (int) (Math.sin(Math.PI / 4) * volcanoDist);
        int vBaseY = w.getHighestBlockYAt(vx, vz);
        BlockData dBasalt = Material.BASALT.createBlockData();
        BlockData dBlackstone = Material.BLACKSTONE.createBlockData();
        BlockData dDeepslate = Material.DEEPSLATE.createBlockData();
        BlockData dMagma = Material.MAGMA_BLOCK.createBlockData();
        BlockData dLava = Material.LAVA.createBlockData();
        BlockData dTuff = Material.TUFF.createBlockData();
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
                        pick = roll < 35 ? dBasalt : (roll < 70 ? (rnd.nextBoolean() ? dBlackstone : dTuff) : dDeepslate);
                    }
                    jobs.add(new Job(pick, vx + dx, y, vz + dz));
                }
            }
        }
        // lava channels running down 4 sides
        for (int ch = 0; ch < 4; ch++) {
            double ca = ch * Math.PI / 2.0;
            for (double step = craterR + 1; step < coneR; step += 1.0) {
                int x = vx + (int) (Math.cos(ca) * step);
                int z = vz + (int) (Math.sin(ca) * step);
                double frac = step / coneR;
                int y = vBaseY + (int) (coneH * (1.0 - frac)) - 1;
                jobs.add(new Job(dLava, x, y, z));
            }
        }
        // obsidian spike ring at the base
        for (int i = 0; i < 10; i++) {
            double sa = rnd.nextDouble() * Math.PI * 2.0;
            double sd = coneR + 4 + rnd.nextDouble() * 8;
            int sx = vx + (int) (Math.cos(sa) * sd);
            int sz = vz + (int) (Math.sin(sa) * sd);
            int sh = 6 + rnd.nextInt(10);
            int sy = w.getHighestBlockYAt(sx, sz);
            for (int layer = 0; layer <= sh; layer++) {
                jobs.add(new Job(layer == sh ? Material.CRYING_OBSIDIAN.createBlockData() : Material.OBSIDIAN.createBlockData(),
                        sx, sy + layer, sz));
            }
        }
        Location volcanoTop = new Location(w, vx, vBaseY + coneH, vz);

        // ------------------------------------------------------------------
        // 4) Monument (prismarine, SW)
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
                if (pillar) {
                    for (int h = 1; h <= 5; h++) {
                        jobs.add(new Job(h == 5 ? dSeaLantern : dPrismBricks, mx + dx, monBaseY + h, mz + dz));
                    }
                } else {
                    for (int h = 1; h <= 4; h++) {
                        if (rnd.nextInt(8) == 0) {
                            continue;
                        }
                        jobs.add(new Job(rnd.nextInt(100) < 60 ? dPrismarine : dDarkPrism, mx + dx, monBaseY + h, mz + dz));
                    }
                }
                jobs.add(new Job(dDarkPrism, mx + dx, monBaseY + 6, mz + dz));
            }
        }
        for (int dx = -monHalfX + 3; dx <= monHalfX - 3; dx++) {
            for (int dz = -monHalfZ + 3; dz <= monHalfZ - 3; dz++) {
                jobs.add(new Job(dWater, mx + dx, monBaseY + 1, mz + dz));
            }
        }
        jobs.add(new Job(dSeaLantern, mx, monBaseY + 1, mz));

        // ------------------------------------------------------------------
        // 5) Kingdom wall ring with 8 towers + gate
        // ------------------------------------------------------------------
        int wallR = Math.max(60, Math.min(90, radius / 3));
        for (int a = 0; a < 360; a += 2) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(rad) * wallR);
            int z = cz + (int) Math.round(Math.sin(rad) * wallR);
            boolean gate = Math.abs(a - 90) < 5; // south gate
            if (gate) {
                continue;
            }
            int y = w.getHighestBlockYAt(x, z);
            int height = 5 + (a % 6 == 0 ? 1 : 0);
            for (int h = 0; h <= height; h++) {
                BlockData pick;
                if (h == height && a % 3 == 0) {
                    pick = pal.wallBattlement;
                } else {
                    pick = rnd.nextInt(100) < 70 ? pal.wall : (rnd.nextInt(100) < 50 ? pal.wallCracked : pal.platformAccent);
                }
                jobs.add(new Job(pick, x, y + h, z));
            }
        }
        // 8 wall towers
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int tx = cx + (int) Math.round(Math.cos(a) * wallR);
            int tz = cz + (int) Math.round(Math.sin(a) * wallR);
            int ty = w.getHighestBlockYAt(tx, tz);
            for (int h = 0; h <= 12; h++) {
                double rr = h < 10 ? 2.5 : 1.5;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx * dx + dz * dz > rr * rr) {
                            continue;
                        }
                        BlockData pick = h == 12 ? pal.towerLight
                                : (rnd.nextInt(100) < 75 ? pal.wall : pal.wallCracked);
                        jobs.add(new Job(pick, tx + dx, ty + h, tz + dz));
                    }
                }
            }
            jobs.add(new Job(pal.lantern, tx, ty + 13, tz));
        }

        // ------------------------------------------------------------------
        // 6) Ruined houses inside the walls
        // ------------------------------------------------------------------
        int houses = Math.min(12, Math.max(4, radius / 20));
        for (int i = 0; i < houses; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = 25 + rnd.nextDouble() * (wallR - 35);
            int hx = cx + (int) (Math.cos(a) * d);
            int hz = cz + (int) (Math.sin(a) * d);
            if (dist2(hx, hz, vx, vz) < (coneR + 20) * (coneR + 20) || dist2(hx, hz, mx, mz) < 28 * 28) {
                continue;
            }
            int hw = 3 + rnd.nextInt(2);
            int hd = 3 + rnd.nextInt(2);
            int hh = 3 + rnd.nextInt(2);
            int hy = w.getHighestBlockYAt(hx, hz);
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -hd; dz <= hd; dz++) {
                    for (int h = 0; h <= hh; h++) {
                        boolean wall = Math.abs(dx) == hw || Math.abs(dz) == hd;
                        boolean roof = h == hh;
                        if (h == 0) {
                            jobs.add(new Job(pal.platform, hx + dx, hy, hz + dz));
                        } else if (roof) {
                            jobs.add(new Job(theme == Theme.SOUL ? Material.WARPED_WART_BLOCK.createBlockData() : pal.platformAccent,
                                    hx + dx, hy + h, hz + dz));
                        } else if (wall && rnd.nextInt(6) > 0) {
                            jobs.add(new Job(rnd.nextInt(100) < 70 ? pal.wall : pal.wallCracked,
                                    hx + dx, hy + h, hz + dz));
                        }
                    }
                }
            }
            if (rnd.nextInt(2) == 0) {
                jobs.add(new Job(pal.lantern, hx - hw + 1, hy + 1, hz - hd + 1));
            }
        }

        // ------------------------------------------------------------------
        // 7) Obsidian spikes + ground patches
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
                    || dist2(sx, sz, mx, mz) < 25 * 25 || dist2(sx, sz, cx, cz) < 30 * 30));
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
                        jobs.add(new Job((layer == h || rnd.nextInt(10) == 0)
                                ? Material.CRYING_OBSIDIAN.createBlockData() : Material.OBSIDIAN.createBlockData(),
                                sx + dx, sy + layer, sz + dz));
                    }
                }
            }
        }

        Set<Material> corruptible = theme == Theme.SOUL
                ? Set.of(Material.WARPED_NYLIUM, Material.SOUL_SOIL, Material.SCULK, Material.NETHERRACK)
                : Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.SAND, Material.RED_SAND,
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
                    if (theme == Theme.SOUL) {
                        pick = roll < 50 ? pal.platformAccent : (roll < 75 ? pal.soulSoil : Material.WARPED_NYLIUM.createBlockData());
                    } else {
                        pick = roll < 45 ? Material.NETHERRACK.createBlockData()
                                : (roll < 70 ? pal.soulSoil : Material.COARSE_DIRT.createBlockData());
                    }
                    jobs.add(new Job(pick, x, y, z));
                    if (roll >= 50 && roll < 75 && w.getBlockAt(x, y + 1, z).getType().isAir()) {
                        if (rnd.nextInt(6) == 0) {
                            jobs.add(new Job(pal.soulFire, x, y + 1, z));
                        } else if (rnd.nextInt(8) == 0) {
                            jobs.add(new Job(Material.WITHER_ROSE.createBlockData(), x, y + 1, z));
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // Apply jobs over multiple ticks
        // ------------------------------------------------------------------
        Location entrance = new Location(w, cx + 0.5, groundY + 2, cz + 25.5);
        StructureInfo info = new StructureInfo(w, center.clone(), altarTop, volcanoTop, entrance, frameSpots);
        int total = jobs.size();
        int perTick = plugin.getConfig().getInt("structure.blocks-per-tick", 1200);
        int reportEvery = Math.max(1, (total / perTick) / 5);

        new BukkitRunnable() {
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
                if (issuer != null && issuer.isOnline() && tickCount % reportEvery == 0) {
                    int pct = (idx * 100) / total;
                    issuer.sendActionBar(MiniMessage.miniMessage().deserialize(
                            "<dark_purple>Corrupted Kingdom rising... <light_purple>" + pct + "%</light_purple>"));
                }
                if (idx >= jobs.size()) {
                    cancel();
                    finishFrames(plugin, info, issuer, theme);
                    if (onComplete != null) {
                        onComplete.accept(info);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void finishFrames(GravefallPlugin plugin, StructureInfo info, Player issuer, Theme theme) {
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
        // floating rotating crystals on the altar obelisk + around it
        NamespacedKey crystalKey = new NamespacedKey(plugin, "grv_crystal");
        Location obelisk = info.altarTop().clone().add(0, 1.2, 0);
        for (int i = 0; i < 5; i++) {
            double ang = i * Math.PI * 2.0 / 5.0;
            Location cLoc = i == 0 ? obelisk : obelisk.clone().add(Math.cos(ang) * 2.2, 0.4 * (i % 2), Math.sin(ang) * 2.2);
            w.spawn(cLoc, ItemDisplay.class, d -> {
                d.setItemStack(Items.createCrystalIcon(plugin));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.CENTER);
                d.setGlowing(true);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(crystalKey, PersistentDataType.BYTE, (byte) 1);
            });
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
                        "<dark_purple><bold>☠ A Corrupted Kingdom has risen... ☠</bold></dark_purple>")));
        w.playSound(info.center(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, info.center(), 2, 1, 0.5, 1, 0);
    }

    private static int dist2(int ax, int az, int bx, int bz) {
        int dx = ax - bx;
        int dz = az - bz;
        return dx * dx + dz * dz;
    }
}
