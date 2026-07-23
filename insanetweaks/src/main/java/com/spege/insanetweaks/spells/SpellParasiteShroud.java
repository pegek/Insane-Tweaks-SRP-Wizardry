package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.ParasiteShroudEventHandler;
import com.spege.insanetweaks.util.SpellCastFeedback;

import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellParasiteShroud extends Spell {

    // Raised from 160 in exchange for the shroud breaking when the player attacks.
    private static final int BASE_DURATION_TICKS = 240;
    private static final int MIN_DURATION_TICKS = 60;
    private static final float ARMOUR_DURATION_BONUS = 0.15F;
    private static final float FULL_SHROUD_POTENCY_THRESHOLD = 1.3F;

    public SpellParasiteShroud() {
        super(InsaneTweaksMod.MODID, "parasite_shroud", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (!world.isRemote) {
            float armourMultiplier = 1.0F + ARMOUR_DURATION_BONUS * countWizardArmourPieces(caster);
            int duration = Math.max(MIN_DURATION_TICKS, Math.round(BASE_DURATION_TICKS
                    * modifiers.get(WizardryItems.duration_upgrade) * armourMultiplier));
            int tier = modifiers.get(SpellModifiers.POTENCY) >= FULL_SHROUD_POTENCY_THRESHOLD ? 2 : 1;

            ParasiteShroudEventHandler.applyShroud(caster, duration, tier);

            SpellCastFeedback.impactAt(world, caster, 0.6D,
                    EnumParticleTypes.SPELL_MOB_AMBIENT, 20, 0.45D, 0.7D, 0.45D, 0.02D,
                    SoundEvents.ENTITY_ENDERMEN_AMBIENT, SoundCategory.PLAYERS,
                    0.8F, 0.7F + world.rand.nextFloat() * 0.1F);
        }

        return true;
    }

    private static int countWizardArmourPieces(EntityPlayer player) {
        int count = 0;
        for (ItemStack stack : player.inventory.armorInventory) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemWizardArmour) {
                count++;
            }
        }
        return count;
    }
}
