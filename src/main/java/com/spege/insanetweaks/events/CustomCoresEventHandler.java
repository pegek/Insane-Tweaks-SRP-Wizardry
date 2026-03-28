package com.spege.insanetweaks.events;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.util.SpellModifiers;

public class CustomCoresEventHandler {

    private static final String COST_NBT_KEY = "SpellCostBonus";
    private static final String POTENCY_NBT_KEY = "SpellPotencyBonus";
    private static final String CHARGEUP_NBT_KEY = "SpellChargeupBonus";

    private static final float REDUCTION_PER_LEVEL = 0.05f;
    private static final float POTENCY_PER_LEVEL = 0.05f;
    private static final float BONUS_PER_CORE = 0.05f;

    private static final int MAX_UPGRADES = 2;

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (left.isEmpty() || right.isEmpty() || !(left.getItem() instanceof ItemArmor))
            return;

        ResourceLocation rightReg = right.getItem().getRegistryName();
        if (rightReg == null)
            return;

        String rightName = rightReg.toString();

        if (rightName.equals("insanetweaks:cost_core")) {
            applyCoreUpgrade(event, left, COST_NBT_KEY, REDUCTION_PER_LEVEL);
        } else if (rightName.equals("insanetweaks:potency_core")) {
            applyCoreUpgrade(event, left, POTENCY_NBT_KEY, POTENCY_PER_LEVEL);
        } else if (rightName.equals("insanetweaks:speedcast_core")) {
            applyCoreUpgrade(event, left, CHARGEUP_NBT_KEY, BONUS_PER_CORE);
        }
    }

    @SuppressWarnings("null")
    private void applyCoreUpgrade(AnvilUpdateEvent event, ItemStack left, String nbtKey, float increment) {
        float currentBonus = 0.0f;
        if (left.hasTagCompound()) {
            NBTTagCompound leftNbt = left.getTagCompound();
            if (leftNbt != null && leftNbt.hasKey(nbtKey)) {
                currentBonus = leftNbt.getFloat(nbtKey);
            }
        }

        float maxBonus = increment * MAX_UPGRADES;
        if (currentBonus >= maxBonus - 0.001f)
            return;

        ItemStack output = left.copy();
        if (!output.hasTagCompound()) {
            output.setTagCompound(new NBTTagCompound());
        }

        NBTTagCompound outputNbt = output.getTagCompound();
        if (outputNbt != null && nbtKey != null) {
            outputNbt.setFloat(nbtKey, currentBonus + increment);
        }

        event.setOutput(output);
        event.setCost(3);
        event.setMaterialCost(1);
    }

    @SubscribeEvent
    public void onSpellCast(SpellCastEvent.Pre event) {
        if (event.getCaster() == null)
            return;

        float totalCostBonus = 0.0f;
        float totalPotencyBonus = 0.0f;
        float totalChargeupBonus = 0.0f;

        EntityEquipmentSlot[] slots = { EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET };
        for (EntityEquipmentSlot slot : slots) {
            if (slot == null) continue;
            net.minecraft.entity.EntityLivingBase caster = event.getCaster();
            if (caster == null) continue;
            
            ItemStack stack = caster.getItemStackFromSlot(slot);
            if (stack != null && !stack.isEmpty() && stack.hasTagCompound()) {
                NBTTagCompound nbt = stack.getTagCompound();
                if (nbt != null) {
                    if (nbt.hasKey(COST_NBT_KEY))
                        totalCostBonus += nbt.getFloat(COST_NBT_KEY);
                    if (nbt.hasKey(POTENCY_NBT_KEY))
                        totalPotencyBonus += nbt.getFloat(POTENCY_NBT_KEY);
                    if (nbt.hasKey(CHARGEUP_NBT_KEY))
                        totalChargeupBonus += nbt.getFloat(CHARGEUP_NBT_KEY);
                }
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
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    @SuppressWarnings("null")
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.item.ItemArmor))
            return;
        
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) return;

        if (nbt.hasKey(COST_NBT_KEY)) {
            float bonus = nbt.getFloat(COST_NBT_KEY);
            int level = (int) (bonus / REDUCTION_PER_LEVEL);
            if (level > 0) {
                int percent = (int) (level * REDUCTION_PER_LEVEL * 100);
                event.getToolTip()
                        .add(TextFormatting.BLUE + "Cost Reduction Upgrades: " + level + " / " + MAX_UPGRADES);
                event.getToolTip().add(TextFormatting.GRAY + "  -" + percent + "% Mana Cost");
            }
        }

        if (nbt.hasKey(POTENCY_NBT_KEY)) {
            float bonus = nbt.getFloat(POTENCY_NBT_KEY);
            int level = (int) (bonus / POTENCY_PER_LEVEL);
            if (level > 0) {
                int percent = (int) (level * POTENCY_PER_LEVEL * 100);
                event.getToolTip().add(TextFormatting.RED + "Potency Upgrades: " + level + " / " + MAX_UPGRADES);
                event.getToolTip().add(TextFormatting.GRAY + "  +" + percent + "% Spell Omnipotency");
            }
        }

        if (nbt.hasKey(CHARGEUP_NBT_KEY)) {
            float bonus = nbt.getFloat(CHARGEUP_NBT_KEY);
            int level = (int) (bonus / BONUS_PER_CORE);
            if (level > 0) {
                int percent = (int) (level * BONUS_PER_CORE * 100);
                event.getToolTip()
                        .add(TextFormatting.LIGHT_PURPLE + "Speedcast Upgrades: " + level + " / " + MAX_UPGRADES);
                event.getToolTip().add(TextFormatting.GRAY + "  -" + percent + "% Charge-up Time");
            }
        }
    }
}
