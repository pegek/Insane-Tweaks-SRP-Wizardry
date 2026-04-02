package com.spege.insanetweaks.util;

import com.spege.insanetweaks.entities.EntityItemIndestructible;
import com.spege.insanetweaks.items.armor.BattleMageArmorItem;
import com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem;
import com.spege.insanetweaks.items.fruit.BaseBaubleFruitItem;
import com.spege.insanetweaks.items.shield.LivingAegisItem;
import com.spege.insanetweaks.items.shield.SentientAegisItem;
import com.spege.insanetweaks.items.spellblade.BridgeSpellblade;
import com.spege.insanetweaks.items.wand.BaseCustomWandItem;

import net.minecraft.entity.item.EntityItem;
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

    public static boolean isLegendaryDropItem(Item item) {
        return item instanceof LivingAegisItem
                || item instanceof SentientAegisItem
                || item instanceof BridgeSpellblade
                || item instanceof BaseBaubleFruitItem
                || item instanceof BattleMageArmorItem
                || item instanceof ParasiteWizardArmorItem
                || item instanceof BaseCustomWandItem;
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
