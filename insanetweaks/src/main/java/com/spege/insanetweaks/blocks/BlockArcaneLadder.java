package com.spege.insanetweaks.blocks;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.BlockLadder;
import net.minecraft.block.SoundType;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;

/**
 * Arcane Ladder: a vanilla ladder in sealed-stone colours that makes no climbing sound (volume 0,
 * which vanilla scales step sounds by) and is faster and gentler to use (ArcaneLadderHandler).
 */
public class BlockArcaneLadder extends BlockLadder {

    /** Silent: every ladder sound at volume 0. */
    public static final SoundType SILENT = new SoundType(0.0F, 1.0F, SoundEvents.BLOCK_LADDER_BREAK,
            SoundEvents.BLOCK_LADDER_STEP, SoundEvents.BLOCK_LADDER_PLACE, SoundEvents.BLOCK_LADDER_HIT,
            SoundEvents.BLOCK_LADDER_FALL);

    public BlockArcaneLadder() {
        super();
        this.setHardness(0.4F);
        this.setSoundType(SILENT);
        this.setUnlocalizedName(InsaneTweaksMod.MODID + ".arcane_ladder");
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "arcane_ladder"));
    }
}
