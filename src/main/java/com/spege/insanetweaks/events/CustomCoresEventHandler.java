package com.spege.insanetweaks.events;

import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.items.core.WizardryCoreItem;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CustomCoresEventHandler {

    private static final int MAX_UPGRADES = 2;

    private static final Item[] ALL_CORE_ITEMS = {
            ModItems.COST_CORE,
            ModItems.POTENCY_CORE,
            ModItems.SPEEDCAST_CORE,
            ModItems.MINION_HEALTH_CORE,
            ModItems.MINION_COUNT_CORE,
            ModItems.SUMMON_RADIUS_CORE,
            ModItems.SUMMON_DURATION_CORE
    };

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (left.isEmpty() || right.isEmpty() || !(left.getItem() instanceof ItemArmor)) {
            return;
        }

        if (!(right.getItem() instanceof WizardryCoreItem)) {
            return;
        }

        applyCoreUpgrade(event, left, (WizardryCoreItem) right.getItem());
    }

    @SuppressWarnings("null")
    private void applyCoreUpgrade(AnvilUpdateEvent event, ItemStack left, WizardryCoreItem core) {
        float currentBonus = 0.0f;

        if (left.hasTagCompound()) {
            NBTTagCompound leftNbt = left.getTagCompound();
            if (leftNbt != null && leftNbt.hasKey(core.getUpgradeNbtKey())) {
                currentBonus = leftNbt.getFloat(core.getUpgradeNbtKey());
            }
        }

        float maxBonus = core.getIncrement() * MAX_UPGRADES;
        if (currentBonus >= maxBonus - 0.001f) {
            return;
        }

        ItemStack output = left.copy();
        if (!output.hasTagCompound()) {
            output.setTagCompound(new NBTTagCompound());
        }

        NBTTagCompound outputNbt = output.getTagCompound();
        if (outputNbt != null) {
            outputNbt.setFloat(core.getUpgradeNbtKey(), currentBonus + core.getIncrement());
        }

        event.setOutput(output);
        event.setCost(3);
        event.setMaterialCost(1);
    }

    @SubscribeEvent
    public void onSpellCast(SpellCastEvent.Pre event) {
        if (event.getCaster() == null) {
            return;
        }

        float totalCostBonus = 0.0f;
        float totalPotencyBonus = 0.0f;
        float totalChargeupBonus = 0.0f;
        float totalMinionHealthBonus = 0.0f;
        float totalMinionCountBonus = 0.0f;
        float totalSummonRadiusBonus = 0.0f;
        float totalSummonDurationBonus = 0.0f;

        EntityEquipmentSlot[] slots = { EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET };

        for (EntityEquipmentSlot slot : slots) {
            net.minecraft.entity.EntityLivingBase caster = event.getCaster();
            if (caster == null) {
                continue;
            }

            ItemStack stack = caster.getItemStackFromSlot(slot);
            if (stack.isEmpty() || !stack.hasTagCompound()) {
                continue;
            }

            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt == null) {
                continue;
            }

            if (nbt.hasKey(ModItems.COST_CORE.getUpgradeNbtKey())) {
                totalCostBonus += nbt.getFloat(ModItems.COST_CORE.getUpgradeNbtKey());
            }
            if (nbt.hasKey(ModItems.POTENCY_CORE.getUpgradeNbtKey())) {
                totalPotencyBonus += nbt.getFloat(ModItems.POTENCY_CORE.getUpgradeNbtKey());
            }
            if (nbt.hasKey(ModItems.SPEEDCAST_CORE.getUpgradeNbtKey())) {
                totalChargeupBonus += nbt.getFloat(ModItems.SPEEDCAST_CORE.getUpgradeNbtKey());
            }
            if (nbt.hasKey(ModItems.MINION_HEALTH_CORE.getUpgradeNbtKey())) {
                totalMinionHealthBonus += nbt.getFloat(ModItems.MINION_HEALTH_CORE.getUpgradeNbtKey());
            }
            if (nbt.hasKey(ModItems.MINION_COUNT_CORE.getUpgradeNbtKey())) {
                totalMinionCountBonus += nbt.getFloat(ModItems.MINION_COUNT_CORE.getUpgradeNbtKey());
            }
            if (nbt.hasKey(ModItems.SUMMON_RADIUS_CORE.getUpgradeNbtKey())) {
                totalSummonRadiusBonus += nbt.getFloat(ModItems.SUMMON_RADIUS_CORE.getUpgradeNbtKey());
            }
            if (nbt.hasKey(ModItems.SUMMON_DURATION_CORE.getUpgradeNbtKey())) {
                totalSummonDurationBonus += nbt.getFloat(ModItems.SUMMON_DURATION_CORE.getUpgradeNbtKey());
            }
        }

        SpellModifiers modifiers = event.getModifiers();

        if (totalCostBonus > 0.0f) {
            float current = modifiers.get(SpellModifiers.COST);
            float multiplier = Math.max(0.05f, 1.0f - totalCostBonus);
            modifiers.set(SpellModifiers.COST, Math.max(0.05f, current * multiplier), false);
        }

        if (totalPotencyBonus > 0.0f) {
            float current = modifiers.get(SpellModifiers.POTENCY);
            modifiers.set(SpellModifiers.POTENCY, current + totalPotencyBonus, false);
        }

        if (totalChargeupBonus > 0.0f) {
            float current = modifiers.get(SpellModifiers.CHARGEUP);
            float multiplier = Math.max(0.05f, 1.0f - totalChargeupBonus);
            modifiers.set(SpellModifiers.CHARGEUP, Math.max(0.05f, current * multiplier), false);
        }

        if (totalMinionHealthBonus > 0.0f) {
            float current = modifiers.get("minion_health");
            modifiers.set("minion_health", current + totalMinionHealthBonus, false);
        }

        if (totalMinionCountBonus > 0.0f) {
            // Experimental: base SpellMinion does not read minion_count from
            // SpellModifiers, but summon_fer_cow has a local override that consumes
            // this value as flat extra summon count.
            float current = modifiers.get("minion_count");
            modifiers.set("minion_count", current + totalMinionCountBonus, false);
        }

        if (totalSummonRadiusBonus > 0.0f) {
            // Experimental: base SpellMinion does not read summon_radius from
            // SpellModifiers, but we keep the value here for future custom logic.
            float current = modifiers.get("summon_radius");
            modifiers.set("summon_radius", current + totalSummonRadiusBonus, false);
        }

        if (totalSummonDurationBonus > 0.0f) {
            float current = modifiers.get("duration");
            modifiers.set("duration", current + totalSummonDurationBonus, false);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    @SuppressWarnings("null")
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemArmor)) {
            return;
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            return;
        }

        for (Item core : ALL_CORE_ITEMS) {
            if (core instanceof WizardryCoreItem) {
                ((WizardryCoreItem) core).addAppliedUpgradeTooltip(event.getToolTip(), nbt, MAX_UPGRADES);
            }
        }
    }
}
