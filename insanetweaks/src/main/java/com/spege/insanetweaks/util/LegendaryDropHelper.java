package com.spege.insanetweaks.util;

import com.spege.insanetweaks.entities.EntityItemIndestructible;
import com.spege.insanetweaks.api.AdvPropertyRegistry;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Centralized rules for legendary Living/Sentient drops.
 *
 * Protected drops are replaced with a hardened EntityItem subclass because
 * vanilla EntityItem did not prove reliable enough against explosions in
 * practice.
 *
 * Out-of-world / void removal is not covered here and requires a dedicated
 * custom entity or explicit out-of-world recovery logic.
 */
public final class LegendaryDropHelper {

    public static final int LEGENDARY_DROP_LIFESPAN = 72000;

    private LegendaryDropHelper() {
    }

    /**
     * Ashen Legacy from any source: the item class, a Property Book on this stack, or a Sentient
     * Codex enchant. This used to hand-roll the class and enchant cases and knew nothing about the
     * stack; deferring to {@link AdvPropertyResolver} is what makes a book-granted Ashen Legacy
     * actually survive lava, with no other change anywhere in the drop pipeline.
     */
    public static boolean isLegendaryDropItem(ItemStack stack) {
        return AdvPropertyResolver.has(stack, AdvPropertyRegistry.ASHEN_LEGACY);
    }

    public static void applyLegendaryDropRules(EntityItem entityItem) {
        if (entityItem.lifespan < LEGENDARY_DROP_LIFESPAN) {
            entityItem.lifespan = LEGENDARY_DROP_LIFESPAN;
        }
    }

    public static EntityItemIndestructible createLegendaryDropEntity(EntityItem original) {
        NBTTagCompound nbt = original.writeToNBT(new NBTTagCompound());
        EntityItemIndestructible protectedEntity = new EntityItemIndestructible(original.world);
        protectedEntity.readFromNBT(nbt);
        applyLegendaryDropRules(protectedEntity);
        return protectedEntity;
    }
}
