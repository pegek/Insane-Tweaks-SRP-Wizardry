package com.spege.insanetweaks.spells;

import java.util.List;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.ImmuneBondHandler;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

@SuppressWarnings("null")
public class SpellImmuneBond extends Spell {

    private static final int BASE_DURATION = 1800; // 90 seconds

    public SpellImmuneBond() {
        super(InsaneTweaksMod.MODID, "immune_bond", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        // Ray-trace to find targeting entity
        EntityLivingBase target = rayTraceEntity(caster, world, 24.0D);
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
        int potencyBonus = Math.max(0, (int) Math.round((potency - 1.0f) / 0.1f)) * 40;
        int duration = Math.round(BASE_DURATION * durationMult) + potencyBonus;

        ImmuneBondHandler.applyBond(caster, target, duration);

        // Instant visual feedback on cast
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                    target.posX, target.posY + target.height * 0.5D, target.posZ,
                    15, 0.4, 0.5, 0.4, 0.05);
        }

        world.playSound(null, target.posX, target.posY, target.posZ,
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS,
                0.7F, 1.2F + world.rand.nextFloat() * 0.15F);

        return true;
    }

    private static EntityLivingBase rayTraceEntity(EntityPlayer caster, World world, double range) {
        Vec3d eyePos = caster.getPositionEyes(1.0F);
        Vec3d lookVec = caster.getLook(1.0F);
        Vec3d endPos = eyePos.addVector(lookVec.x * range, lookVec.y * range, lookVec.z * range);

        AxisAlignedBB searchBox = caster.getEntityBoundingBox().grow(range + 1.0D, range + 1.0D, range + 1.0D);
        List<EntityLivingBase> candidates = world.getEntitiesWithinAABB(EntityLivingBase.class, searchBox);

        EntityLivingBase bestTarget = null;
        double bestDist = Double.MAX_VALUE;

        for (EntityLivingBase e : candidates) {
            if (e == caster) continue;

            AxisAlignedBB hitbox = e.getEntityBoundingBox().grow(0.3D);
            RayTraceResult hit = hitbox.calculateIntercept(eyePos, endPos);
            
            if (hit != null) {
                double dist = eyePos.distanceTo(hit.hitVec);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestTarget = e;
                }
            }
        }

        return bestTarget;
    }
}
