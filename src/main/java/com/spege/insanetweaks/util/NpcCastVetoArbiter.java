package com.spege.insanetweaks.util;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.InteractionsCategory.NpcVetoSecondOpinion;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Second-opinion arbiter for NPC spell casts vetoed via {@code SpellCastEvent.Pre}
 * (see spec 2026-07-17, "AM2 veto workaround"). Ars Magica 2's EB Wizardry compat cancels
 * NPC casts on an AM2 burnout/mana gate that our SRP-based casters (sim wizard, sentinel)
 * can never satisfy — they have no AM2 mana. We cannot attribute a Pre cancel to a specific
 * mod at runtime, so instead of honoring every cancel we re-evaluate the KNOWN legitimate
 * veto conditions ourselves: if any of them actually applies to this caster we honor the
 * cancel; otherwise we attribute it to AM2's false positive and let the cast proceed.
 *
 * <p>The honored conditions were bytecode-verified against the installed base mods on
 * 2026-07-17 (EB Wizardry 4.3.19, Ancient Spellcraft 1.8.2). Two conditions from the
 * original design were dropped after that verification because they can NOT fire for our
 * casters (which are neither players nor ASC {@code EntityAnimatedItem}s):
 * <ul>
 *   <li>ASC {@code IClassSpell} — for a non-player caster ASC only cancels when the caster
 *       is an {@code EntityAnimatedItem}; our entities never are.</li>
 *   <li>ASC {@code magical_exhaustion} — never tested in {@code onSpellCastPreEvent}; it is
 *       only applied as an aura in {@code onLivingUpdateEvent}, so it is not a cast veto.</li>
 * </ul>
 * Conversely {@code DimensionalAnchor.shouldPreventSpell} IS reachable by non-player casters
 * and IS honored.
 *
 * <p>All Ancient Spellcraft class references are isolated in {@link #ascWouldVeto} which is
 * only entered under {@code Loader.isModLoaded("ancientspellcraft")}, keeping classloading
 * safe when ASC is absent (established pattern; cf. {@code SrpInfestationHelper}). AM2 is not
 * a compile-time dependency, so its silence potion is resolved from the Forge registry.
 */
public final class NpcCastVetoArbiter {

    private static final String AM2 = "arsmagica2";
    private static final String ASC = "ancientspellcraft";

    /** ASC Tier ordinal above which the suppression charm applies (ADVANCED = 2, MASTER = 3). */
    private static final int SUPPRESSION_MIN_TIER_ORDINAL = 1;
    /** distanceSq threshold for the suppression charm (ASC uses < 20.0). */
    private static final double SUPPRESSION_DIST_SQ = 20.0D;

    private NpcCastVetoArbiter() {
    }

    /**
     * Resolves the config tri-state: OFF never overrides, ON always considers overriding,
     * AUTO only when Ars Magica 2 is installed (the one mod the workaround targets).
     */
    public static boolean isActive() {
        NpcVetoSecondOpinion mode = ModConfig.interactions.npcCastVetoSecondOpinion;
        if (mode == NpcVetoSecondOpinion.OFF) {
            return false;
        }
        if (mode == NpcVetoSecondOpinion.ON) {
            return true;
        }
        return Loader.isModLoaded(AM2);
    }

    /**
     * @return true when an already-cancelled NPC cast should proceed anyway (no known
     *         legitimate veto condition matched); false to honor the veto as before.
     */
    public static boolean shouldOverrideVeto(EntityLiving caster, Spell spell) {
        if (!isActive() || caster == null || spell == null) {
            return false;
        }

        // Honor the veto if ANY known-legitimate condition actually applies to this caster.
        if (isDisabledForNpcs(spell)) {
            return false;
        }
        if (Loader.isModLoaded(ASC) && ascWouldVeto(caster, spell)) {
            return false;
        }
        if (Loader.isModLoaded(AM2) && am2Silenced(caster)) {
            return false;
        }

        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] Overriding NPC cast veto for {} by {} — no known legitimate veto condition matched",
                caster.getName(), spell.getRegistryName());
        return true;
    }

    /** EB Wizardry's {@code WizardryEventHandler} cancels casts disabled in the NPC context. */
    private static boolean isDisabledForNpcs(Spell spell) {
        return !spell.isEnabled(SpellProperties.Context.NPCS);
    }

    /** AM2's silence potion legitimately blocks all casting; resolve it by registry name. */
    private static boolean am2Silenced(EntityLiving caster) {
        Potion silence = ForgeRegistries.POTIONS.getValue(new ResourceLocation(AM2, "silence"));
        return silence != null && caster.isPotionActive(silence);
    }

    /**
     * Ancient Spellcraft's two {@code onSpellCastPreEvent} veto paths that a non-player caster
     * can actually reach (bytecode-verified, ASC 1.8.2). Kept in its own method so ASC classes
     * are only linked when ASC is present.
     */
    @SuppressWarnings("null")
    private static boolean ascWouldVeto(EntityLiving caster, Spell spell) {
        // (1) Charm of Spell Suppression: an ADVANCED+ spell (tier ordinal > 1) cancelled when
        //     any player wearing an active suppression orb is within distanceSq < 20.
        if (spell.getTier() != null && spell.getTier().ordinal() > SUPPRESSION_MIN_TIER_ORDINAL) {
            for (EntityPlayer player : caster.world.playerEntities) {
                if (caster.getDistanceSq(player) < SUPPRESSION_DIST_SQ
                        && electroblob.wizardry.item.ItemArtefact.isArtefactActive(
                                player, com.windanesz.ancientspellcraft.registry.ASItems.charm_suppression_orb)) {
                    return true;
                }
            }
        }

        // (2) Dimensional Anchor: ASC's gate for teleport-type spells near an active anchor.
        //     Reachable by non-player casters (offset 5112-5132 in ASEventHandler.onSpellCastPreEvent).
        return com.windanesz.ancientspellcraft.spell.DimensionalAnchor.shouldPreventSpell(
                caster, caster.world, spell);
    }
}
