package com.spege.insanetweaks.anchor;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * The tag an anchor writes into its mob before petrifying it. EBW saves the mob's whole NBT
 * (ForgeData included) into the statue, so the tag survives into the released mob however the
 * statue was broken.
 */
public final class AnchorLeash {

    public static final String HOME = "InsaneTweaksAnchorHome";
    public static final String DIM = "InsaneTweaksAnchorDim";

    private AnchorLeash() {
    }

    public static void tag(Entity mob, BlockPos anchor, int dimension) {
        NBTTagCompound data = mob.getEntityData();
        data.setLong(HOME, anchor.toLong());
        data.setInteger(DIM, dimension);
    }
}
