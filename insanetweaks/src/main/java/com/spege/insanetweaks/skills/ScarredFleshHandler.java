package com.spege.insanetweaks.skills;

import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.ScarredFleshCategory;
import com.spege.insanetweaks.util.SrpEffectRegistry;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Scarred Flesh ({@code compatskills:scarred_flesh}) — Defense tree.
 *
 * <p>Flesh that has been colonised often enough stops reacting cleanly. The more parasite
 * afflictions are stacked on the player at once, the harder each further one struggles to
 * take hold: its amplifier is capped and its duration cut, and past a hard ceiling it
 * simply fails to land.
 *
 * <p>"Slot" is the position the incoming affliction would occupy. With the default ladder:
 *
 * <pre>
 *   slot 1-2  untouched
 *   slot 3    amplifier capped at V,   full duration
 *   slot 4    amplifier capped at IV,  85% duration
 *   slot 5    amplifier capped at III, 75% duration
 *   slot 6    amplifier capped at II,  65% duration
 *   slot 7    amplifier capped at I,   55% duration
 *   slot 8+   refused outright
 * </pre>
 *
 * <p>Implemented on {@link PotionEvent.PotionApplicableEvent}, which is {@code @HasResult} and
 * is posted from {@code EntityLivingBase.isPotionApplicable} — the gate
 * {@code addPotionEffect} consults before storing anything. Denying there is the only place
 * that reliably stops an effect no matter who applies it.
 *
 * <p>Weakening rather than denying needs a second pass: the event cannot rewrite the effect,
 * so the original is denied and a modified copy re-applied. {@link #reentrant} suppresses the
 * handler for that inner call, otherwise the copy would be re-evaluated forever.
 *
 * <p>Only effects hostile to the host count — see {@link SrpEffectRegistry}, which
 * deliberately excludes the parasite effects that <i>benefit</i> the player.
 */
public class ScarredFleshHandler {

    private static final String SKILL_ID = "reskillable:defense";
    private static final String TRAIT_ID = SkillsModule.DOMAIN + ":scarred_flesh";

    /**
     * Guards the re-apply below. A ThreadLocal rather than a plain boolean because the
     * client and server threads both run this in single-player.
     */
    private static final ThreadLocal<Boolean> reentrant = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    @SubscribeEvent
    public void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        if (reentrant.get().booleanValue()) {
            return;
        }

        EntityLivingBase living = event.getEntityLiving();
        if (!(living instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) living;

        PotionEffect incoming = event.getPotionEffect();
        if (incoming == null) {
            return;
        }

        ScarredFleshCategory cfg = ModConfig.scarredFlesh;
        Set<Potion> hostile = SrpEffectRegistry.getHostileEffects(cfg.additionalHostileEffects);
        Potion potion = incoming.getPotion();
        if (!hostile.contains(potion)) {
            return;
        }

        if (!TraitBase.hasTrait(player, SKILL_ID, TRAIT_ID)) {
            return;
        }

        // Slot the incoming affliction would occupy. Refreshing an effect the player already
        // has does not consume a new slot, so it is evaluated at the current count.
        int active = SrpEffectRegistry.countActive(player, hostile);
        int slot = player.isPotionActive(potion) ? active : active + 1;

        if (slot <= cfg.freeDebuffs) {
            return;
        }

        if (slot > cfg.maxDebuffs) {
            event.setResult(Event.Result.DENY);
            if (cfg.debugLogging) {
                InsaneTweaksMod.LOGGER.info(
                        "[InsaneTweaks] Scarred Flesh refused {} on {} (slot {} > max {}).",
                        potion.getRegistryName(), player.getName(), Integer.valueOf(slot),
                        Integer.valueOf(cfg.maxDebuffs));
            }
            return;
        }

        int ladderIndex = slot - cfg.freeDebuffs - 1;
        int cappedAmplifier = Math.min(incoming.getAmplifier(), amplifierCapFor(cfg, ladderIndex));
        int scaledDuration = (int) Math.round(incoming.getDuration() * durationMultiplierFor(cfg, ladderIndex));
        if (scaledDuration < 1) {
            scaledDuration = 1;
        }

        if (cappedAmplifier == incoming.getAmplifier() && scaledDuration == incoming.getDuration()) {
            return; // Ladder happens to be a no-op at this slot.
        }

        event.setResult(Event.Result.DENY);
        applyWeakened(player, potion, scaledDuration, cappedAmplifier, incoming);

        if (cfg.debugLogging) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Scarred Flesh weakened {} on {} at slot {}: amp {}->{}, duration {}->{}.",
                    potion.getRegistryName(), player.getName(), Integer.valueOf(slot),
                    Integer.valueOf(incoming.getAmplifier()), Integer.valueOf(cappedAmplifier),
                    Integer.valueOf(incoming.getDuration()), Integer.valueOf(scaledDuration));
        }
    }

    /**
     * Re-applies the affliction in its weakened form. The reentrancy flag is cleared in a
     * finally block so a throwing effect cannot wedge the handler off for the rest of the
     * session.
     */
    private static void applyWeakened(EntityPlayer player, Potion potion, int duration, int amplifier,
            PotionEffect source) {
        reentrant.set(Boolean.TRUE);
        try {
            player.addPotionEffect(new PotionEffect(potion, duration, amplifier,
                    source.getIsAmbient(), source.doesShowParticles()));
        } finally {
            reentrant.set(Boolean.FALSE);
        }
    }

    /** Ladder lookup that reuses the last entry once the configured list runs out. */
    private static int amplifierCapFor(ScarredFleshCategory cfg, int index) {
        int[] caps = cfg.amplifierCaps;
        if (caps == null || caps.length == 0) {
            return Integer.MAX_VALUE;
        }
        return caps[Math.min(index, caps.length - 1)];
    }

    private static double durationMultiplierFor(ScarredFleshCategory cfg, int index) {
        double[] multipliers = cfg.durationMultipliers;
        if (multipliers == null || multipliers.length == 0) {
            return 1.0D;
        }
        return multipliers[Math.min(index, multipliers.length - 1)];
    }
}
