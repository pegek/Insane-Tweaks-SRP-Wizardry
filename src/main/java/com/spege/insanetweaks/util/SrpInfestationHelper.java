package com.spege.insanetweaks.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.dhanantry.scapeandrunparasites.block.BlockInfestedStain;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SrpInfestationHelper {

    private static final Map<String, String> EXACT_MAPPINGS = new HashMap<String, String>();
    private static final ResourceLocation INFESTED_STAIN_ID = new ResourceLocation("srparasites", "infestedstain");

    static {
        EXACT_MAPPINGS.put("minecraft:dirt", "srparasites:infestedstain");
        EXACT_MAPPINGS.put("minecraft:grass", "srparasites:infestedstain");
        EXACT_MAPPINGS.put("minecraft:mycelium", "srparasites:infestedstain");
        EXACT_MAPPINGS.put("minecraft:stone", "srparasites:infestedrubble");
        EXACT_MAPPINGS.put("minecraft:cobblestone", "srparasites:infested_cobblestone");
        EXACT_MAPPINGS.put("minecraft:planks", "srparasites:infested_planks");
        EXACT_MAPPINGS.put("minecraft:oak_stairs", "srparasites:infested_plank_stairs");
        EXACT_MAPPINGS.put("minecraft:spruce_stairs", "srparasites:infested_plank_stairs");
        EXACT_MAPPINGS.put("minecraft:birch_stairs", "srparasites:infested_plank_stairs");
        EXACT_MAPPINGS.put("minecraft:jungle_stairs", "srparasites:infested_plank_stairs");
        EXACT_MAPPINGS.put("minecraft:acacia_stairs", "srparasites:infested_plank_stairs");
        EXACT_MAPPINGS.put("minecraft:dark_oak_stairs", "srparasites:infested_plank_stairs");
        EXACT_MAPPINGS.put("minecraft:stonebrick", "srparasites:infested_stone_bricks");
        EXACT_MAPPINGS.put("minecraft:stone_brick_stairs", "srparasites:infested_stone_bricks_stairs");
        EXACT_MAPPINGS.put("minecraft:stained_hardened_clay", "srparasites:infested_terracotta");
        EXACT_MAPPINGS.put("minecraft:hardened_clay", "srparasites:infested_terracotta");
        EXACT_MAPPINGS.put("minecraft:log", "srparasites:infested_column");
        EXACT_MAPPINGS.put("minecraft:log2", "srparasites:infested_column");
        EXACT_MAPPINGS.put("minecraft:flower_pot", "srparasites:infested_pot");
        EXACT_MAPPINGS.put("minecraft:sand", "srparasites:infestedsand");
        EXACT_MAPPINGS.put("minecraft:sandstone", "srparasites:inf_ss");
        EXACT_MAPPINGS.put("minecraft:sandstone_stairs", "srparasites:infested_sandstone_stairs");
        EXACT_MAPPINGS.put("minecraft:fence", "srparasites:infested_fence");
        EXACT_MAPPINGS.put("minecraft:cobblestone_wall", "srparasites:infested_stone_brick_wall");
        EXACT_MAPPINGS.put("minecraft:glass", "srparasites:infested_glass");
        EXACT_MAPPINGS.put("minecraft:glass_pane", "srparasites:infested_glass_pane");
        EXACT_MAPPINGS.put("minecraft:leaves", "srparasites:infested_leaves");
        EXACT_MAPPINGS.put("minecraft:leaves2", "srparasites:infested_leaves");
    }

    private SrpInfestationHelper() {
    }

    public static int infestNearbyBlocks(World world, BlockPos center, int horizontalRadius, int verticalRadius,
            int maxConversions) {
        if (world == null || center == null || maxConversions <= 0) {
            return 0;
        }

        int converted = 0;
        int horizontalRadiusSq = horizontalRadius * horizontalRadius;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int yIndex = 0; yIndex <= verticalRadius * 2; yIndex++) {
            int y = getPreferredVerticalOffset(yIndex);
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    if (x * x + z * z > horizontalRadiusSq) {
                        continue;
                    }

                    mutablePos.setPos(center.getX() + x, center.getY() + y, center.getZ() + z);
                    IBlockState currentState = world.getBlockState(mutablePos);
                    IBlockState infestedState = getInfestedState(currentState);
                    if (infestedState == null) {
                        continue;
                    }

                    if (!world.setBlockState(mutablePos, infestedState, 3)) {
                        continue;
                    }

                    converted++;
                    if (converted >= maxConversions) {
                        return converted;
                    }
                }
            }
        }

        return converted;
    }

    private static int getPreferredVerticalOffset(int index) {
        if (index == 0) {
            return 0;
        }
        int magnitude = (index + 1) / 2;
        return index % 2 == 1 ? magnitude : -magnitude;
    }

    public static IBlockState getInfestedState(IBlockState originalState) {
        if (originalState == null) {
            return null;
        }

        ResourceLocation originalId = originalState.getBlock().getRegistryName();
        if (originalId == null) {
            return null;
        }

        if ("srparasites".equals(originalId.getResourceDomain())) {
            return null;
        }

        Block infestedBlock = getMappedBlock(originalId);
        if (infestedBlock == null) {
            return null;
        }

        IBlockState infestedState = SrpPurificationHelper.copyCommonProperties(originalState, infestedBlock.getDefaultState());
        if (INFESTED_STAIN_ID.equals(infestedBlock.getRegistryName())
                && infestedState.getPropertyKeys().contains(BlockInfestedStain.STAGE)) {
            infestedState = infestedState.withProperty(BlockInfestedStain.STAGE, Integer.valueOf(5));
        }

        return infestedState;
    }

    private static Block getMappedBlock(ResourceLocation originalId) {
        String exactTarget = EXACT_MAPPINGS.get(originalId.toString());
        if (exactTarget != null) {
            return getBlock(exactTarget);
        }

        String path = originalId.getResourcePath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "stone_brick_stairs", "stonebrick_stairs")) {
            return getBlock("srparasites:infested_stone_bricks_stairs");
        }
        if (containsAny(path, "stairs") && containsAny(path, "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "wood")) {
            return getBlock("srparasites:infested_plank_stairs");
        }
        if (containsAny(path, "stonebrick", "stone_bricks")) {
            return getBlock("srparasites:infested_stone_bricks");
        }
        if (containsAny(path, "sandstone")) {
            return getBlock("srparasites:inf_ss");
        }
        if (containsAny(path, "glass_pane")) {
            return getBlock("srparasites:infested_glass_pane");
        }
        if (containsAny(path, "glass")) {
            return getBlock("srparasites:infested_glass");
        }
        if (containsAny(path, "leaves")) {
            return getBlock("srparasites:infested_leaves");
        }
        if (containsAny(path, "log", "wood", "bark", "stem")) {
            return getBlock("srparasites:infested_column");
        }
        if (containsAny(path, "planks")) {
            return getBlock("srparasites:infested_planks");
        }
        if (containsAny(path, "cobblestone")) {
            return getBlock("srparasites:infested_cobblestone");
        }
        if (containsAny(path, "dirt", "grass", "mycelium")) {
            return getBlock("srparasites:infestedstain");
        }
        if (containsAny(path, "stone")) {
            return getBlock("srparasites:infestedrubble");
        }
        return null;
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
}
