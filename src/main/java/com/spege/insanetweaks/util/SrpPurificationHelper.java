package com.spege.insanetweaks.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

public final class SrpPurificationHelper {

    private static final Map<String, String> EXACT_MAPPINGS = new HashMap<String, String>();
    private static final ResourceLocation BECKON_SI = new ResourceLocation("srparasites", "beckon_si");
    private static final ResourceLocation BECKON_SII = new ResourceLocation("srparasites", "beckon_sii");
    private static final ResourceLocation BECKON_SIII = new ResourceLocation("srparasites", "beckon_siii");
    private static final ResourceLocation BECKON_SIV = new ResourceLocation("srparasites", "beckon_siv");

    static {
        EXACT_MAPPINGS.put("srparasites:infestedstain", "minecraft:dirt");
        EXACT_MAPPINGS.put("srparasites:infested_leaves", "minecraft:leaves");
        EXACT_MAPPINGS.put("srparasites:infestedrubble", "minecraft:stone");
        EXACT_MAPPINGS.put("srparasites:infested_cobblestone", "minecraft:cobblestone");
        EXACT_MAPPINGS.put("srparasites:infested_planks", "minecraft:planks");
        EXACT_MAPPINGS.put("srparasites:infested_plank_stairs", "minecraft:oak_stairs");
        EXACT_MAPPINGS.put("srparasites:infested_stone_bricks", "minecraft:stonebrick");
        EXACT_MAPPINGS.put("srparasites:infested_stone_polished", "minecraft:stone");
        EXACT_MAPPINGS.put("srparasites:infested_terracotta", "minecraft:stained_hardened_clay");
        EXACT_MAPPINGS.put("srparasites:infested_column", "minecraft:log");
        EXACT_MAPPINGS.put("srparasites:infested_pot", "minecraft:flower_pot");
        EXACT_MAPPINGS.put("srparasites:infestedsand", "minecraft:sand");
        EXACT_MAPPINGS.put("srparasites:inf_ss", "minecraft:sandstone");
        EXACT_MAPPINGS.put("srparasites:inf_ss_chiseled", "minecraft:sandstone");
        EXACT_MAPPINGS.put("srparasites:infested_sandstone_stairs", "minecraft:sandstone_stairs");
        EXACT_MAPPINGS.put("srparasites:infested_stone_bricks_stairs", "minecraft:stone_brick_stairs");
        EXACT_MAPPINGS.put("srparasites:infested_fence", "minecraft:fence");
        EXACT_MAPPINGS.put("srparasites:infested_stone_brick_wall", "minecraft:cobblestone_wall");
        EXACT_MAPPINGS.put("srparasites:infested_glass", "minecraft:glass");
        EXACT_MAPPINGS.put("srparasites:infested_glass_pane", "minecraft:glass_pane");
    }

    private SrpPurificationHelper() {
    }

    public static boolean isSrpInfested(IBlockState state) {
        Block block = state.getBlock();
        ResourceLocation registryName = block.getRegistryName();
        if (registryName == null || !"srparasites".equals(registryName.getResourceDomain())) {
            return false;
        }

        String path = registryName.getResourcePath().toLowerCase(Locale.ROOT);
        if (path.contains("infestation_purifier")) {
            return false;
        }

        return path.contains("inf") || path.contains("infect") || path.contains("parasite");
    }

    public static boolean isBeckon(Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        return BECKON_SI.equals(id) || BECKON_SII.equals(id) || BECKON_SIII.equals(id) || BECKON_SIV.equals(id);
    }

    public static IBlockState getPurifiedState(IBlockState srpState) {
        Block targetBlock = getMappedBlock(srpState);
        if (targetBlock == null) {
            return null;
        }

        return copyCommonProperties(srpState, targetBlock.getDefaultState());
    }

    private static Block getMappedBlock(IBlockState srpState) {
        ResourceLocation registryName = srpState.getBlock().getRegistryName();
        if (registryName == null) {
            return null;
        }

        String key = registryName.toString();
        String exactTarget = EXACT_MAPPINGS.get(key);
        if (exactTarget != null) {
            return getBlock(exactTarget);
        }

        String path = registryName.getResourcePath().toLowerCase(Locale.ROOT);
        if ("infestremain".equals(path)) {
            return Blocks.AIR;
        }
        if (containsAny(path, "glass_pane")) {
            return Blocks.GLASS_PANE;
        }
        if (containsAny(path, "glass")) {
            return Blocks.GLASS;
        }
        if (containsAny(path, "stone_brick_wall", "stonebrick_wall", "brick_wall", "rubble_wall")) {
            return Blocks.COBBLESTONE_WALL;
        }
        if (containsAny(path, "fence")) {
            return Blocks.OAK_FENCE;
        }
        if (containsAny(path, "stone_bricks_stairs", "stonebrick_stairs")) {
            return Blocks.STONE_BRICK_STAIRS;
        }
        if (containsAny(path, "sandstone_stairs")) {
            return Blocks.SANDSTONE_STAIRS;
        }
        if (containsAny(path, "plank_stairs", "planks_stairs", "wood_stairs")) {
            return Blocks.OAK_STAIRS;
        }
        if (containsAny(path, "ss_chiseled", "chiseled_sandstone", "inf_ss", "sandstone")) {
            return Blocks.SANDSTONE;
        }
        if (containsAny(path, "pot")) {
            return Blocks.FLOWER_POT;
        }
        if (containsAny(path, "column", "pillar", "log_axis", "axis", "trunk", "bark", "stem", "wood")) {
            return Blocks.LOG;
        }
        if (containsAny(path, "terracotta", "hardened_clay", "stained_clay")) {
            return Blocks.STAINED_HARDENED_CLAY;
        }
        if (containsAny(path, "stone_polished", "polished")) {
            return Blocks.STONE;
        }
        if (containsAny(path, "stone_bricks", "stonebrick")) {
            return Blocks.STONEBRICK;
        }
        if (containsAny(path, "planks", "wood_planks")) {
            return Blocks.PLANKS;
        }
        if (containsAny(path, "cobblestone")) {
            return Blocks.COBBLESTONE;
        }
        if (containsAny(path, "infestedsand", "red_sand", "sand_red", "sand")) {
            return Blocks.SAND;
        }
        if (containsAny(path, "rubble", "andesite", "diorite", "granite", "stone")) {
            return Blocks.STONE;
        }
        if (containsAny(path, "leaves")) {
            return Blocks.LEAVES;
        }
        if (containsAny(path, "stain", "dirt", "grass")) {
            return Blocks.DIRT;
        }

        Material material = srpState.getMaterial();
        if (material == Material.WOOD) {
            return Blocks.LOG;
        }
        if (material == Material.LEAVES) {
            return Blocks.LEAVES;
        }
        if (material == Material.GLASS) {
            return Blocks.GLASS;
        }
        if (material == Material.SAND) {
            return Blocks.SAND;
        }
        if (material == Material.GROUND || material == Material.GRASS || material == Material.CLAY) {
            return Blocks.DIRT;
        }
        if (material == Material.PLANTS || material == Material.VINE || material == Material.CACTUS
                || material == Material.WEB || material == Material.CIRCUITS) {
            return Blocks.AIR;
        }

        return Blocks.STONE;
    }

    private static Block getBlock(String id) {
        return Block.REGISTRY.getObject(new ResourceLocation(id));
    }

    private static boolean containsAny(String path, String... keys) {
        for (String key : keys) {
            if (path.contains(key)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static IBlockState copyCommonProperties(IBlockState from, IBlockState to) {
        IBlockState output = to;
        for (IProperty property : to.getPropertyKeys()) {
            if (!from.getPropertyKeys().contains(property)) {
                continue;
            }
            try {
                Comparable value = from.getValue(property);
                if (value != null && property.getAllowedValues().contains(value)) {
                    output = output.withProperty(property, value);
                }
            } catch (Exception ignored) {
            }
        }
        return output;
    }
}
