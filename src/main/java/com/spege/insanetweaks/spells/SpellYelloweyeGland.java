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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellYelloweyeGland extends Spell {

    private static final String CAST_CYCLE_TAG = "insanetweaksYelloweyeGlandCycle";
    private static final int EXPLOSIVE_CAST_THRESHOLD = 4;
    private static final float PROJECTILE_VELOCITY = 1.25F;

    public SpellYelloweyeGland() {
        super(InsaneTweaksMod.MODID, "yelloweye_gland", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        boolean explosiveCast = this.advanceShotCycle(caster);
        float potency = modifiers.get(SpellModifiers.POTENCY);
        EntityYelloweyeGlandProjectile projectile = new EntityYelloweyeGlandProjectile(world);
        projectile.setCaster(caster);
        projectile.setBaseDamage(SRPAttributes.EMANA_RANGED_DAMAGE);
        projectile.setDurationAmplifier(SRPConfigMobs.emanaPoisonDuration, SRPConfigMobs.emanaPoisonAmplifier);
        projectile.setGearDamage(SRPConfigMobs.emanaGearD);
        projectile.setPotencyMultiplier(potency);
        projectile.setExplosiveShot(explosiveCast);
        projectile.configureNade(3, 60, SRPAttributes.EMANA_RANGED_DAMAGE * potency);
        projectile.aim(caster, PROJECTILE_VELOCITY);
        world.spawnEntity(projectile);

        float pitch = explosiveCast ? 2.0F : 1.0F;
        world.playSound(null, caster.posX, caster.posY, caster.posZ, SRPSounds.EMANA_SHOOTING,
                SoundCategory.PLAYERS, 2.0F, pitch);
        return true;
    }

    private boolean advanceShotCycle(EntityPlayer caster) {
        NBTTagCompound data = caster.getEntityData();
        int castCycle = data.getInteger(CAST_CYCLE_TAG) + 1;
        boolean explosiveCast = castCycle >= EXPLOSIVE_CAST_THRESHOLD;
        data.setInteger(CAST_CYCLE_TAG, explosiveCast ? 0 : castCycle);
        return explosiveCast;
    }
}
