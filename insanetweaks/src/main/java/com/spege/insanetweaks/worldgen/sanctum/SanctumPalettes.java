package com.spege.insanetweaks.worldgen.sanctum;

/**
 * Block ids of a sanctum's palette per dimension (spec 16.3), as "modid:name" or
 * "modid:name:meta". Pure Java; the builder resolves them and falls back to the Overworld block
 * when a mod is missing. Dimensions without a palette of their own use the Overworld one.
 */
public final class SanctumPalettes {

    private SanctumPalettes() {
    }

    public static String wall(int dimension, double noise) {
        switch (dimension) {
            case 111:
                return noise < 0.85D ? "minecraft:obsidian" : "nb:cry_obi";
            case -1:
                return noise < 0.7D ? "minecraft:nether_brick" : "minecraft:red_nether_brick";
            case 1:
                return noise < 0.75D ? "minecraft:end_bricks" : "minecraft:purpur_block";
            default:
                return noise < 0.6D ? "minecraft:stonebrick" : noise < 0.85D ? "minecraft:stonebrick:1"
                        : "minecraft:stonebrick:2";
        }
    }

    public static String chiseled(int dimension) {
        switch (dimension) {
            case 111:
                return "nb:black_stone_bricks";
            case -1:
                return "minecraft:red_nether_brick";
            case 1:
                return "minecraft:purpur_pillar";
            default:
                return "minecraft:stonebrick:3";
        }
    }

    public static String stairs(int dimension) {
        switch (dimension) {
            case 111:
                return "nb:black_stone_brick_stairs";
            case -1:
                return "minecraft:nether_brick_stairs";
            case 1:
                return "minecraft:purpur_stairs";
            default:
                return "minecraft:stone_brick_stairs";
        }
    }

    /** Silverfish blocks only imitate stone bricks, so only the Overworld palette hides them. */
    public static boolean silverfish(int dimension) {
        return dimension != 111 && dimension != -1 && dimension != 1;
    }
}
