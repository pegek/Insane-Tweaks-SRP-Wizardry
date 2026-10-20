package com.spege.insanetweaks.anchor;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Tags an anchor writes into its mob before petrifying it: home, dimension and, inside a sanctum,
 * the sanctum id. They are part of the stored NBT copy, so the released mob carries them.
 */
public final class AnchorLeash {

    public static final String HOME = "InsaneTweaksAnchorHome";
    public static final String DIM = "InsaneTweaksAnchorDim";
    public static final String SANCTUM = "InsaneTweaksSanctum";
    public static final String ALONE = "InsaneTweaksAnchorAlone";
    /** Set when the mob is loaded back in away from home with nobody near; it goes home on its next check. */
    public static final String RECALL = "InsaneTweaksAnchorRecall";

    private AnchorLeash() {
    }

    public static void tag(Entity mob, BlockPos anchor, int dimension, boolean inSanctum, long sanctumId) {
        NBTTagCompound data = mob.getEntityData();
        data.setLong(HOME, anchor.toLong());
        data.setInteger(DIM, dimension);
        if (inSanctum) {
            data.setLong(SANCTUM, sanctumId);
        }
    }
}
