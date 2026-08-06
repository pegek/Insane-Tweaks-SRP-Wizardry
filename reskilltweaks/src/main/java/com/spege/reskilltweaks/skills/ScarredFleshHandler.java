package com.spege.reskilltweaks.skills;

import java.util.Set;

import com.spege.reskilltweaks.ReskillTweaks;
import com.spege.reskilltweaks.config.ReskillTweaksConfig;
import com.spege.reskilltweaks.config.categories.ScarredFleshCategory;
import com.spege.reskilltweaks.util.SrpEffectRegistry;

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
 * <p>Flesh that has been colonised often enough runs out of room to react. The player carries a
 * fixed <b>total affliction budget</b>: every hostile parasite effect costs its displayed level,
 * and the sum may never exceed {@code scarredFlesh.totalLevelBudget}. An incoming affliction is
 * admitted at whatever level still fits, and refused once nothing does.
 *
 * <p>At the default budget of 15, with Viral VII + Fear IV + Coth III already on the player
 * (7 + 4 + 3 = 14):
 *
 * <pre>
 *   incoming Needler III   1 of 15 left  -&gt; lands as Needler I
 *   incoming Nexus         0 of 15 left  -&gt; refused outright
 * </pre>
 *
 * <p>Cost is the <b>displayed</b> level ({@code amplifier + 1}), not the raw amplifier: it is the
 * number on the player's status bar, so the ceiling is one they can count for themselves. Duration
 * is left alone entirely — the budget is about how much affliction fits, not how long it lingers.
 *
 * <p>Refreshing an affliction the player already carries is measured against the <i>other</i>
 * effects only. Counting it against itself would throttle every refresh of a long-running effect
 * down to nothing, which is the opposite of what a budget is for.
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
 * <p><b>Server side only.</b> {@code PotionApplicableEvent} fires on both, and without the guard
 * the client ran the whole budget a second time over its own synced copy of the effects — so the
 * server would admit Viral at level 10, the client would see that as spent budget, cut it again to
 * 9, and write the result into its own potion map. The player then read a number the server did not
 * hold. Denying on the client would be just as wrong: the server has already decided, and the
 * client's job is to display that decision.
 *
 * <p>Only effects hostile to the host count — see {@link SrpEffectRegistry}, which
 * deliberately excludes the parasite effects that <i>benefit</i> the player.
 *
 * <h3>Legacy behaviour (pre-2026-07-28)</h3>
 * The trait previously worked per-slot: the first two afflictions landed untouched, slots 3 to 7
 * had their amplifier capped along a ladder (V, IV, III, II, I) and their duration cut (100%, 85%,
 * 75%, 65%, 55%), and slot 8 onwards was refused. See {@code ScarredFleshCategory} for the full
 * record — kept because the shape may return in another form. It counted afflictions rather than
 * weighing them, so seven level-I effects hit the ceiling exactly as hard as seven level-VII ones;
 * the budget replaces that with a measure of how much affliction is actually present.
 */
public class ScarredFleshHandler {


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
        if (player.world == null || player.world.isRemote) {
            return;
        }

        PotionEffect incoming = event.getPotionEffect();
        if (incoming == null) {
            return;
        }

        ScarredFleshCategory cfg = ReskillTweaksConfig.scarredFlesh;
        Set<Potion> hostile = SrpEffectRegistry.getHostileEffects(cfg.additionalHostileEffects);
        Potion potion = incoming.getPotion();
        if (!hostile.contains(potion)) {
            return;
        }

        if (!TraitHandle.SCARRED_FLESH.has(player)) {
            return;
        }

        // The incoming effect's own contribution is excluded, so a refresh is weighed against the
        // other afflictions rather than against itself.
        int spentByOthers = SrpEffectRegistry.sumActiveLevels(player, hostile, potion);
        int remaining = cfg.totalLevelBudget - spentByOthers;
        int incomingLevel = incoming.getAmplifier() + 1;

        if (remaining <= 0) {
            event.setResult(Event.Result.DENY);
            if (cfg.debugLogging) {
                ReskillTweaks.LOGGER.info(
                        "[ReskillTweaks] Scarred Flesh refused {} {} on {}: budget {} already spent by other afflictions.",
                        potion.getRegistryName(), Integer.valueOf(incomingLevel), player.getName(),
                        Integer.valueOf(cfg.totalLevelBudget));
            }
            return;
        }

        if (incomingLevel <= remaining) {
            return; // Fits as-is.
        }

        event.setResult(Event.Result.DENY);
        applyWeakened(player, potion, incoming.getDuration(), remaining - 1, incoming);

        if (cfg.debugLogging) {
            ReskillTweaks.LOGGER.info(
                    "[ReskillTweaks] Scarred Flesh reduced {} on {}: level {}->{} ({} of budget {} spent by others).",
                    potion.getRegistryName(), player.getName(), Integer.valueOf(incomingLevel),
                    Integer.valueOf(remaining), Integer.valueOf(spentByOthers),
                    Integer.valueOf(cfg.totalLevelBudget));
        }
    }

    /**
     * Re-applies the affliction at the level that still fits, keeping its duration. The reentrancy
     * flag is cleared in a finally block so a throwing effect cannot wedge the handler off for the
     * rest of the session.
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

}
