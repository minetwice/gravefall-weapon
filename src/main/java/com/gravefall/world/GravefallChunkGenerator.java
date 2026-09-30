package com.gravefall.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.World;

import java.util.Random;

/**
 * Generates the floating blue "Soul Island" of the Gravefall dimension:
 * an End-style starry void with one big island of warped nylium, sculk and
 * glowing features. The area around 0,0 (the kingdom) is flattened.
 */
public class GravefallChunkGenerator extends ChunkGenerator {

    private final int islandRadius;
    private final int flattenRadius;

    public GravefallChunkGenerator(int islandRadius, int flattenRadius) {
        this.islandRadius = islandRadius;
        this.flattenRadius = flattenRadius;
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                double dist = Math.sqrt(x * x + z * z);
                if (dist > islandRadius) {
                    continue; // void beyond the island
                }
                int surfaceY = surfaceHeight(x, z, dist);
                if (surfaceY < 40) {
                    continue; // island edge fades into the void
                }
                // deepslate body
                for (int y = 40; y <= surfaceY - 1; y++) {
                    data.setBlock(lx, y, lz, Material.DEEPSLATE);
                }
                // glowing blue surface
                data.setBlock(lx, surfaceY, lz, surfaceBlock(x, z, random));
                decorateColumn(data, lx, surfaceY, lz, x, z, random);
            }
        }
        return data;
    }

    private int surfaceHeight(int x, int z, double dist) {
        if (dist < flattenRadius) {
            return 64; // flat kingdom ground
        }
        // gentle rolling hills
        double h = 64
                + 5.0 * Math.sin(x * 0.03) * Math.cos(z * 0.03)
                + 3.0 * Math.sin((x + z) * 0.017);
        // island fades at the outer rim
        double edge = (islandRadius - dist) / 60.0;
        if (edge < 1.0) {
            h *= Math.max(0.0, Math.min(1.0, edge));
        }
        return (int) h;
    }

    private Material surfaceBlock(int x, int z, Random random) {
        // sculk patches
        double n = Math.sin(x * 0.11) * Math.cos(z * 0.13) + Math.sin((x * z) * 0.001) * 0.5;
        if (n > 0.55) {
            return Material.SCULK;
        }
        if (n < -0.85) {
            return Material.PURPUR_BLOCK; // Soul Stone veins (RP)
        }
        if (n < -0.65) {
            return Material.SOUL_SOIL;
        }
        return Material.WARPED_NYLIUM;
    }

    private void decorateColumn(ChunkData data, int lx, int surfaceY, int lz, int x, int z, Random random) {
        if (surfaceY + 1 > data.getMaxHeight() - 1) {
            return;
        }
        int r = random.nextInt(1000);
        if (r < 3) {
            data.setBlock(lx, surfaceY + 1, lz, Material.SEA_LANTERN); // glowing blue lights
        } else if (r < 12) {
            data.setBlock(lx, surfaceY + 1, lz, Material.WARPED_ROOTS);
        } else if (r < 18) {
            data.setBlock(lx, surfaceY + 1, lz, Material.SOUL_SOIL);
            data.setBlock(lx, surfaceY + 2, lz, Material.SOUL_FIRE);
        } else if (r < 24) {
            int h = 1 + random.nextInt(3);
            for (int i = 1; i <= h && surfaceY + i < data.getMaxHeight() - 1; i++) {
                data.setBlock(lx, surfaceY + i, lz, Material.SCULK_VEIN);
            }
        } else if (r < 26) {
            data.setBlock(lx, surfaceY + 1, lz, Material.WARPED_WART_BLOCK);
            if (random.nextBoolean()) {
                data.setBlock(lx, surfaceY + 2, lz, Material.SHROOMLIGHT);
            }
        }
    }
}
