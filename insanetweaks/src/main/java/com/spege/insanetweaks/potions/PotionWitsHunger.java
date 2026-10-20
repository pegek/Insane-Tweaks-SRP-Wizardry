package com.spege.insanetweaks.potions;

import java.util.UUID;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.enchant.WitsHungerMath;

import electroblob.wizardry.potion.Curse;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

/**
 * Wit's Hunger - the curse a starving Sentient Codex lays on its holder.
 *
 * <p>One effect where there used to be two: level N weakens like vanilla Weakness N plus Mining
 * Fatigue N (weaker blows, a slower swing, much slower mining), but shows as a single tile, so the
 * player can see what is doing it. It is an Electroblob's Wizardry {@link Curse}: purple tile, no
 * timer, not cured by milk.
 *
 * <p>🚨 It never stacks with the real effects - whichever is stronger counts. That is why it does not
 * register fixed attribute modifiers like vanilla potions do: {@link #applyAttributesModifiersToEntity}
 * computes only the difference to any real Weakness / Mining Fatigue the entity has at that moment
 * ({@link WitsHungerMath}). The Codex handler refreshes the effect every tick interval, and every
 * refresh makes vanilla remove and re-apply these modifiers ({@code onChangedPotionEffect}), so a real
 * effect arriving or running out is taken into account within one interval. Dig speed is not an
 * attribute in 1.12 - {@code SentientCodexHandler.onBreakSpeed} applies that part.
 *
 * <p>The "permanent" look is only the curse's missing timer. The handler gives it a short duration
 * and keeps renewing it, so if the Codex module is ever switched off the curse simply runs out
 * instead of staying on a player forever - and its modifiers go with it, via
 * {@link #removeAttributesModifiersFromEntity}.
 */
public class PotionWitsHunger extends Curse {

    private static final UUID ATTACK_DAMAGE_ID = UUID.fromString("6f0e3b52-4c1a-4f0b-9d55-8a6b2a5e7c01");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("6f0e3b52-4c1a-4f0b-9d55-8a6b2a5e7c02");

    public PotionWitsHunger() {
        super(true, 0x5A2A6E, new ResourceLocation(InsaneTweaksMod.MODID, "textures/gui/potion/wits_hunger.png"));
        setPotionName("potion.insanetweaks.wits_hunger");
    }

    @Override
    public void applyAttributesModifiersToEntity(EntityLivingBase entity, AbstractAttributeMap attributes, int amplifier) {
        int weakness = amplifierOf(entity, MobEffects.WEAKNESS);
        int fatigue = amplifierOf(entity, MobEffects.MINING_FATIGUE);
        set(attributes, SharedMonsterAttributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ID, "Wit's Hunger damage",
                WitsHungerMath.attackDamageModifier(amplifier, weakness), 0);
        set(attributes, SharedMonsterAttributes.ATTACK_SPEED, ATTACK_SPEED_ID, "Wit's Hunger speed",
                WitsHungerMath.attackSpeedModifier(amplifier, fatigue), 2);
    }

    @Override
    public void removeAttributesModifiersFromEntity(EntityLivingBase entity, AbstractAttributeMap attributes, int amplifier) {
        remove(attributes, SharedMonsterAttributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ID);
        remove(attributes, SharedMonsterAttributes.ATTACK_SPEED, ATTACK_SPEED_ID);
    }

    /** The amplifier of a vanilla effect on the entity, or {@link WitsHungerMath#NONE}. */
    public static int amplifierOf(EntityLivingBase entity, Potion potion) {
        PotionEffect effect = entity.getActivePotionEffect(potion);
        return effect == null ? WitsHungerMath.NONE : effect.getAmplifier();
    }

    private static void set(AbstractAttributeMap attributes, IAttribute attribute, UUID id, String name,
            double amount, int operation) {
        IAttributeInstance instance = attributes.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0.0) {
            instance.applyModifier(new AttributeModifier(id, name, amount, operation));
        }
    }

    private static void remove(AbstractAttributeMap attributes, IAttribute attribute, UUID id) {
        IAttributeInstance instance = attributes.getAttributeInstance(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
