package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.ParasiteShroudEventHandler;
import com.spege.insanetweaks.util.SpellCastFeedback;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellParasiteShroud extends Spell {

    private static final int BASE_DURATION_TICKS = 160;

    public SpellParasiteShroud() {
        super(InsaneTweaksMod.MODID, "parasite_shroud", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        int duration = Math.max(60, Math.round(BASE_DURATION_TICKS * modifiers.get(WizardryItems.duration_upgrade)));

        if (!world.isRemote) {
            ParasiteShroudEventHandler.applyShroud(caster, duration);

            SpellCastFeedback.impactAt(world, caster, 0.6D,
                    EnumParticleTypes.SPELL_MOB_AMBIENT, 20, 0.45D, 0.7D, 0.45D, 0.02D,
                    SoundEvents.ENTITY_ENDERMEN_AMBIENT, SoundCategory.PLAYERS,
                    0.8F, 0.7F + world.rand.nextFloat() * 0.1F);
        }

        return true;
    }
}
