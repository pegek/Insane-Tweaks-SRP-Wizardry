package com.spege.insanetweaks.items.wand;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.google.common.collect.Multimap;
import com.spege.insanetweaks.InsaneTweaksMod;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

public class BaseCustomWandItem extends ItemWand {

    private static final UUID MAGICAL_ADAPTED_UUID = UUID.fromString("6C2F3E8A-5182-421A-B01B-BCCE9786A100");
    private final float basePotencyBonus;
    private final int arcaneAdaptationLevel;

    public BaseCustomWandItem(Tier tier, Element element, float basePotencyBonus, int arcaneAdaptationLevel) {
        super(tier, element);
        this.basePotencyBonus = basePotencyBonus;
        this.arcaneAdaptationLevel = Math.max(0, Math.min(3, arcaneAdaptationLevel));
    }

    @Override
    public SpellModifiers calculateModifiers(ItemStack stack, EntityPlayer player, Spell spell) {
        SpellModifiers modifiers = super.calculateModifiers(stack, player, spell);

        float customPotency = 1.0f + this.basePotencyBonus;
        modifiers.set(SpellModifiers.POTENCY, customPotency, true);

        int points = 0;
        if (stack.hasTagCompound()) {
            net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
            if (tag != null) {
                points = tag.getInteger("WandEvolutionPoints");
            }
        }

        boolean isLiving = false;
        boolean isSentient = false;
        ResourceLocation reg = this.getRegistryName();
        if (reg != null) {
            isLiving = "living_wand".equals(reg.getResourcePath());
            isSentient = "sentient_wand".equals(reg.getResourcePath());
        }

        float durationBonus = 0.0f;
        float costReduction = 0.0f;
        float chargeupReduction = 0.0f;
        float cooldownReduction = 0.0f;
        float minionHealthBonus = 0.0f;

        if (isLiving) {
            float progress = Math.min(1.0f, Math.max(0.0f, points / 5000.0f));
            durationBonus = 0.05f + (0.20f * progress);
            minionHealthBonus = 0.05f + (0.20f * progress);
            costReduction = 0.05f + (0.15f * progress);
            chargeupReduction = 0.05f + (0.15f * progress);
            cooldownReduction = 0.05f + (0.15f * progress);
        } else if (isSentient) {
            durationBonus = 0.25f;
            minionHealthBonus = 0.25f;
            costReduction = 0.20f;
            chargeupReduction = 0.20f;
            cooldownReduction = 0.20f;

            if (points >= 10000) {
                modifiers.set("minion_count", modifiers.get("minion_count") + 1.0f, false);
            }
        }

        modifiers.set("minion_health", modifiers.get("minion_health") + minionHealthBonus, false);
        modifiers.set("duration", modifiers.get("duration") + durationBonus, false);

        float costMultiplier = Math.max(0.0f, 1.0f - costReduction);

        if (this.shouldApplyArcaneAdaptation(spell)) {
            costMultiplier *= this.getArcaneAdaptationManaMultiplier();
        }

        modifiers.set(SpellModifiers.COST, modifiers.get(SpellModifiers.COST) * costMultiplier, false);
        modifiers.set(SpellModifiers.CHARGEUP, modifiers.get(SpellModifiers.CHARGEUP) * Math.max(0.0f, 1.0f - chargeupReduction), false);
        modifiers.set("cooldown", modifiers.get("cooldown") * Math.max(0.0f, 1.0f - cooldownReduction), false);

        return modifiers;
    }

    private boolean shouldApplyArcaneAdaptation(Spell spell) {
        if (this.arcaneAdaptationLevel <= 0 || spell == null) {
            return false;
        }

        ResourceLocation registryName = spell.getRegistryName();
        return registryName == null || !InsaneTweaksMod.MODID.equals(registryName.getResourceDomain());
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return false;
    }

    public float getPotencyBonus() {
        return this.basePotencyBonus;
    }

    public int getArcaneAdaptationLevel() {
        return this.arcaneAdaptationLevel;
    }

    public int getArcaneAdaptationManaMultiplier() {
        switch (this.arcaneAdaptationLevel) {
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 4;
            default:
                return 1;
        }
    }

    public int getMagicDamageBonusPercent(ItemStack stack) {
        int points = 0;
        if (stack.hasTagCompound()) {
            net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
            if (tag != null) {
                points = tag.getInteger("WandEvolutionPoints");
            }
        }

        return Math.max(1, Math.min(10, (points * 10) / 5000));
    }

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);

        if (slot == EntityEquipmentSlot.MAINHAND && Loader.isModLoaded("potioncore")) {
            multimap.put(
                    "potioncore.magicDamage",
                    new AttributeModifier(
                            MAGICAL_ADAPTED_UUID,
                            "Magically Adapted",
                            this.getMagicDamageBonusPercent(stack) / 100.0D,
                            0));
        }

        return multimap;
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public ItemStack applyUpgrade(EntityPlayer player, @Nonnull ItemStack wand, @Nonnull ItemStack upgrade) {
        int forcedLimit = 10;

        if (electroblob.wizardry.util.WandHelper.getTotalUpgrades(wand) >= forcedLimit) {
            return wand;
        }

        return super.applyUpgrade(player, wand, upgrade);
    }
}
