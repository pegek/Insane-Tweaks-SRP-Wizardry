package com.spege.insanetweaks.items.wand;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.google.common.collect.Multimap;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.AdaptationUpgradeHelper;

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
import java.util.Arrays;
import java.util.List;

import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.api.AdvPropertyRegistry;

@SuppressWarnings("null")
public class BaseCustomWandItem extends ItemWand implements ITweaksPropertyHolder {

    @Nonnull
    private static final UUID MAGICAL_ADAPTED_UUID = UUID.fromString("6C2F3E8A-5182-421A-B01B-BCCE9786A100");
    private final float basePotencyBonus;
    private final int defaultAdaptationLevel;

    public BaseCustomWandItem(Tier tier, Element element, float basePotencyBonus, int defaultAdaptationLevel) {
        super(tier, element);
        this.basePotencyBonus = basePotencyBonus;
        this.defaultAdaptationLevel = Math.max(0, Math.min(3, defaultAdaptationLevel));
    }

    @Override
    public SpellModifiers calculateModifiers(ItemStack stack, EntityPlayer player, Spell spell) {
        SpellModifiers modifiers = super.calculateModifiers(stack, player, spell);

        float customPotency = 1.0f + this.getPotencyBonus();
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

            if (points >= 10000 && com.spege.insanetweaks.config.ModConfig.gear.wands.sentientExtraMinion) {
                modifiers.set("minion_count", modifiers.get("minion_count") + 1.0f, false);
            }
        }

        // Every bonus above is derived from evolution progress, so config scales the computed
        // value instead of replacing it - a part-evolved wand stays where it is on its curve.
        com.spege.insanetweaks.config.categories.GearCategory.Wands wandCfg =
                com.spege.insanetweaks.config.ModConfig.gear.wands;
        durationBonus *= (float) wandCfg.durationMultiplier;
        minionHealthBonus *= (float) wandCfg.minionHealthMultiplier;
        costReduction *= (float) wandCfg.spellDiscountMultiplier;
        chargeupReduction *= (float) wandCfg.spellDiscountMultiplier;
        cooldownReduction *= (float) wandCfg.spellDiscountMultiplier;

        modifiers.set("minion_health", modifiers.get("minion_health") + minionHealthBonus, false);
        modifiers.set("duration", modifiers.get("duration") + durationBonus, false);

        float costMultiplier = Math.max(0.0f, 1.0f - costReduction);

        modifiers.set(SpellModifiers.COST, modifiers.get(SpellModifiers.COST) * costMultiplier, false);
        modifiers.set(SpellModifiers.CHARGEUP, modifiers.get(SpellModifiers.CHARGEUP) * Math.max(0.0f, 1.0f - chargeupReduction), false);
        modifiers.set("cooldown", modifiers.get("cooldown") * Math.max(0.0f, 1.0f - cooldownReduction), false);

        return modifiers;
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return false;
    }

    /**
     * The wand's flat spell-power bonus. Config overrides it for the two wands this mod ships;
     * anything else built on this class keeps the value it was constructed with.
     */
    public float getPotencyBonus() {
        ResourceLocation reg = this.getRegistryName();
        if (reg != null) {
            if ("living_wand".equals(reg.getResourcePath())) {
                return (float) com.spege.insanetweaks.config.ModConfig.gear.wands.livingPotencyBonus;
            }
            if ("sentient_wand".equals(reg.getResourcePath())) {
                return (float) com.spege.insanetweaks.config.ModConfig.gear.wands.sentientPotencyBonus;
            }
        }
        return this.basePotencyBonus;
    }

    public int getArcaneAdaptationLevel(ItemStack stack) {
        return Math.min(3, this.defaultAdaptationLevel + AdaptationUpgradeHelper.getAppliedAdaptationUpgradeLevel(stack));
    }

    public int getArcaneAdaptationPenaltyPercent(ItemStack stack) {
        return AdaptationUpgradeHelper.getForeignSpellCostPenaltyPercent(this.getArcaneAdaptationLevel(stack));
    }

    /** Scaled here rather than at the call site so the tooltip and the effect cannot disagree. */
    public int getMagicDamageBonusPercent(ItemStack stack) {
        ResourceLocation reg = this.getRegistryName();
        double multiplier = com.spege.insanetweaks.config.ModConfig.gear.wands.magicDamageMultiplier;
        if (reg != null) {
            if ("living_wand".equals(reg.getResourcePath())) {
                return (int) Math.round(5 * multiplier);
            }
            if ("sentient_wand".equals(reg.getResourcePath())) {
                return (int) Math.round(10 * multiplier);
            }
        }

        return 0;
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

        if (upgrade.getItem() == ModItems.ADAPTATION_UPGRADE
                && AdaptationUpgradeHelper.getAppliedAdaptationUpgradeLevel(wand) >= AdaptationUpgradeHelper.getMaxAppliedAdaptationUpgrades(wand)) {
            return wand;
        }

        return super.applyUpgrade(player, wand, upgrade);
    }

    @Override
    public List<String> getActiveAdvProperties(ItemStack stack) {
        return Arrays.asList(AdvPropertyRegistry.ASHEN_LEGACY);
    }
}
