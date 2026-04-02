package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityPurifyingWave;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

public class SpellPurifyingPulse extends Spell {

    private static final int BASE_SIZE = 8;
    private static final float POTENCY_STEP = 0.45F;
    private static final int SIZE_PER_STEP = 2;

    public SpellPurifyingPulse() {
        super(InsaneTweaksMod.MODID, "purifying_pulse", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        RayTraceResult rayTrace = caster.rayTrace(24.0D, 1.0F);
        if (rayTrace == null || rayTrace.hitVec == null) {
            return false;
        }

        float potency = modifiers.get(SpellModifiers.POTENCY);
        int bonusSteps = Math.max(0, (int) Math.floor((potency - 1.0F) / POTENCY_STEP));
        int radius = BASE_SIZE + bonusSteps * SIZE_PER_STEP;
        int verticalRange = Math.max(4, radius / 2);

        EntityPurifyingWave wave = new EntityPurifyingWave(world, rayTrace.hitVec.x, rayTrace.hitVec.y + 0.15D,
                rayTrace.hitVec.z);
        wave.setOwner(caster);
        wave.configureWave(radius, verticalRange);
        world.spawnEntity(wave);
        world.playSound(null, wave.posX, wave.posY, wave.posZ, SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE,
                SoundCategory.PLAYERS, 0.8F, 1.25F);
        return true;
    }
}
