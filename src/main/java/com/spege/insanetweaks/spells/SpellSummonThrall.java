package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.ThrallSlotManager;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

/**
 * Summon Thrall spell.
 *
 * <p>Each player has up to 3 persistent thrall slots (#1, #2, #3).
 * Casting this spell:
 * <ul>
 *   <li>Finds the first occupied slot and <b>recalls</b> that thrall to the caster.</li>
 *   <li>If all occupied thralls are already close (&lt;8 blocks), falls through to the next slot.</li>
 *   <li>If a slot has a dead/unloaded thrall UUID, <b>re-spawns</b> the thrall with its backed-up inventory.</li>
 *   <li>If a free slot exists, <b>spawns</b> a new thrall.</li>
 *   <li>If all 3 slots are occupied and all thralls are nearby, the cast fails.</li>
 * </ul>
 */
@SuppressWarnings("null")
public class SpellSummonThrall extends Spell {

    private static final SoundEvent SOUND_CAST = new SoundEvent(
            new ResourceLocation("insanetweaks", "thrall/minionspawn"))
            .setRegistryName("insanetweaks:thrall_spawn_cast");

    public SpellSummonThrall() {
        super(InsaneTweaksMod.MODID, "summon_thrall", SpellActions.SUMMON, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true; // client-side: visual only
        }

        if (!(caster instanceof EntityPlayerMP)) return false;
        EntityPlayerMP playerMP = (EntityPlayerMP) caster;

        boolean success = ThrallSlotManager.recallOrSpawn(playerMP, world);
        if (!success) {
            // All 3 slots are live and nearby — cast fails visually
            return false;
        }

        // Sound at caster position
        world.playSound(null, caster.posX, caster.posY, caster.posZ,
                SOUND_CAST, SoundCategory.NEUTRAL, 1.0F, 1.0F);
        return true;
    }
}
