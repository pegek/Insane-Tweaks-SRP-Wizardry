package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.ImmuneBondHandler;
import com.spege.insanetweaks.util.SpellCastFeedback;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.RayTracer;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellImmuneBond extends Spell {

    private static final int BASE_DURATION = 1800; // 90 seconds
    private static final double TARGET_RANGE = 24.0D;

    public SpellImmuneBond() {
        super(InsaneTweaksMod.MODID, "immune_bond", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        EntityLivingBase target = null;
        RayTraceResult hit = RayTracer.standardEntityRayTrace(world, caster, TARGET_RANGE, false);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY
                && hit.entityHit instanceof EntityLivingBase) {
            target = (EntityLivingBase) hit.entityHit;
        }

        if (target == null) {
            return false;
        }

        // We only bond with non-player entities for now
        // (Player bonding can be implemented later with a specific artifact check)
        if (target instanceof EntityPlayer) {
            return false;
        }

        // Duration calculation: base * duration_upgrade + potency bonus (40 ticks per 10% potency step)
        float durationMult = modifiers.get(WizardryItems.duration_upgrade);
        float potency = modifiers.get(SpellModifiers.POTENCY);
        int potencyBonus = Math.max(0, Math.round((potency - 1.0f) / 0.1f)) * 40;
        int duration = Math.round(BASE_DURATION * durationMult) + potencyBonus;

        ImmuneBondHandler.applyBond(caster, target, duration);

        // Instant visual feedback on cast
        SpellCastFeedback.impactAt(world, target, 0.5D,
                EnumParticleTypes.SPELL_WITCH, 15, 0.4D, 0.5D, 0.4D, 0.05D,
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS,
                0.7F, 1.2F + world.rand.nextFloat() * 0.15F);

        return true;
    }
}
