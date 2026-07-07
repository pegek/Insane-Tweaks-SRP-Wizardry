package com.spege.insanetweaks.spells;

import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.SRPAttributes;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfigMobs;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeGlandProjectile;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellYelloweyeGland extends Spell {

    private static final float PROJECTILE_VELOCITY = 1.25F;

    public SpellYelloweyeGland() {
        super(InsaneTweaksMod.MODID, "yelloweye_gland", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        // Every shot is the heavy explosive variant; the cast is gated by the
        // chargeup time in yelloweye_gland.json instead of the old 4-shot cycle.
        float potency = modifiers.get(SpellModifiers.POTENCY);
        EntityYelloweyeGlandProjectile projectile = new EntityYelloweyeGlandProjectile(world);
        projectile.setCaster(caster);
        projectile.setBaseDamage(SRPAttributes.EMANA_RANGED_DAMAGE);
        projectile.setDurationAmplifier(SRPConfigMobs.emanaPoisonDuration, SRPConfigMobs.emanaPoisonAmplifier);
        projectile.setGearDamage(SRPConfigMobs.emanaGearD);
        projectile.setPotencyMultiplier(potency);
        projectile.setExplosiveShot(true);
        projectile.configureNade(3, 60, SRPAttributes.EMANA_RANGED_DAMAGE * potency);
        projectile.aim(caster, PROJECTILE_VELOCITY);
        world.spawnEntity(projectile);

        world.playSound(null, caster.posX, caster.posY, caster.posZ, SRPSounds.EMANA_SHOOTING,
                SoundCategory.PLAYERS, 2.0F, 2.0F);
        return true;
    }
}
