package com.spege.insanetweaks.util;

import com.spege.insanetweaks.entities.EntityItemIndestructible;
import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.enchant.EnchantmentSentientCodex;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
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

    public static boolean isLegendaryDropItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // Sentient Codex confers the Ashen Legacy property on ANY item (the enchant works on vanilla
        // gear that can't implement ITweaksPropertyHolder), so detect it by enchant presence.
        if (ModConfig.enchantments.sentientCodex.conferAshenLegacy && EnchantmentSentientCodex.hasSentientCodex(stack)) {
            return true;
        }
        Item item = stack.getItem();
        return item instanceof ITweaksPropertyHolder
                && ((ITweaksPropertyHolder) item).hasAdvProperty(stack, AdvPropertyRegistry.ASHEN_LEGACY);
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
