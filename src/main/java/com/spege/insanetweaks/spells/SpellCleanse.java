package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModPotions;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.SpellRay;
import electroblob.wizardry.util.ParticleBuilder;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Cleanse — Master-tier Abomination (JSON element "magic") ray spell.
 *
 * Hit a living entity: applies the InsaneTweaks CLEANSE potion to it
 * (removes all harmful effects, incl. the built-in parasite list, one pulse
 * per 10 ticks for the whole duration).
 * Hit a block or nothing: falls back to self-cast on the caster.
 *
 * Pattern reference: EB Wizardry's Petrify (SpellRay + duration property).
 */
@SuppressWarnings("null")
public class SpellCleanse extends SpellRay {

    /** Duration (ticks) of the applied CLEANSE effect. Set in cleanse.json. */
    public static final String EFFECT_DURATION = "effect_duration";

    public SpellCleanse() {
        super(InsaneTweaksMod.MODID, "cleanse", SpellActions.POINT, false);
        this.soundValues(1.0f, 1.2f, 0.2f);
        this.addProperties(EFFECT_DURATION);
    }

    @Override
    protected boolean onEntityHit(World world, Entity target, Vec3d hit, EntityLivingBase caster,
            Vec3d origin, int ticksInUse, SpellModifiers modifiers) {
        if (!(target instanceof EntityLivingBase)) {
            return false;
        }
        applyCleanse(world, (EntityLivingBase) target, modifiers);
        return true;
    }

    @Override
    protected boolean onBlockHit(World world, BlockPos pos, EnumFacing side, Vec3d hit,
            EntityLivingBase caster, Vec3d origin, int ticksInUse, SpellModifiers modifiers) {
        // Aiming at the ground counts as "no target" — self-cast fallback.
        applyCleanse(world, caster, modifiers);
        return true;
    }

    @Override
    protected boolean onMiss(World world, EntityLivingBase caster, Vec3d origin, Vec3d direction,
            int ticksInUse, SpellModifiers modifiers) {
        applyCleanse(world, caster, modifiers);
        return true;
    }

    private void applyCleanse(World world, EntityLivingBase target, SpellModifiers modifiers) {
        if (world.isRemote) {
            return;
        }
        int duration = (int) (getProperty(EFFECT_DURATION).floatValue()
                * modifiers.get(WizardryItems.duration_upgrade));
        target.addPotionEffect(new PotionEffect(ModPotions.CLEANSE, duration, 0, false, true));
        world.playSound(null, target.posX, target.posY, target.posZ,
                SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.PLAYERS, 0.9f, 1.3f);
    }

    @Override
    protected void spawnParticle(World world, double x, double y, double z,
            double vx, double vy, double vz) {
        // Icy-white cleanse sparkle matching the CLEANSE potion colour (0xAADDFF).
        ParticleBuilder.create(ParticleBuilder.Type.SPARKLE).pos(x, y, z)
                .time(12 + world.rand.nextInt(8)).clr(0.67f, 0.87f, 1.0f).spawn(world);
    }
}
