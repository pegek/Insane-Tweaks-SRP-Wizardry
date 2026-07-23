package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellTestProjectile extends Spell {

    public SpellTestProjectile() {
        super(InsaneTweaksMod.MODID, "test_projectile", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (!world.isRemote) {
            Vec3d lookVec = caster.getLookVec();
            EntitySmallFireball fireball = new EntitySmallFireball(
                    world, 
                    caster.posX + lookVec.x * 1.5, 
                    caster.posY + caster.getEyeHeight() - 0.2 + lookVec.y * 1.5, 
                    caster.posZ + lookVec.z * 1.5, 
                    lookVec.x * 1.5, 
                    lookVec.y * 1.5, 
                    lookVec.z * 1.5
            );
            fireball.shootingEntity = caster;
            world.spawnEntity(fireball);
            world.playSound(null, caster.posX, caster.posY, caster.posZ, SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        return true;
    }
}
