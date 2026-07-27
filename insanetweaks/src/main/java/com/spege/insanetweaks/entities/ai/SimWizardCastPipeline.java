package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySimWizard;
import com.spege.insanetweaks.util.NpcCastVetoArbiter;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.packet.PacketNPCCastSpell;
import electroblob.wizardry.packet.WizardryPacketHandler;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * The single place a sim_wizard actually casts. Shared by the combat task, its panic reaction and
 * the ally-support task so the three cannot drift apart on the parts that are easy to get wrong:
 * the {@code SpellCastEvent} contract, the AM2 veto arbitration, and telling the client anything
 * happened at all.
 *
 * <p>The sequence mirrors EBW's own {@code EntityAIAttackSpell.attemptCastSpell}, verified against
 * the 4.3.19 bytecode - including the detail that a continuous spell takes the
 * {@code setContinuousSpellAndNotify} branch INSTEAD of the Post event and the one-shot packet.
 */
public final class SimWizardCastPipeline {

    /** How far (blocks) a continuous-spell state change is broadcast; matches EBW's own value. */
    private static final double CONTINUOUS_NOTIFY_RANGE = 128.0D;

    private SimWizardCastPipeline() {
    }

    public enum Outcome {
        /** The spell was cast. For a continuous spell, the channel has been opened. */
        CAST,
        /** Another mod vetoed through {@code SpellCastEvent.Pre} and the arbiter upheld the veto. */
        VETOED,
        /** The spell itself declined to fire (bad position, no valid effect, ...). */
        REFUSED
    }

    /**
     * Runs the full cast sequence.
     *
     * @param target the entity the spell is aimed at; pass the wizard itself for self-targeted
     *               spells. Never null - EBW's client handler drops any packet whose target id
     *               does not resolve.
     */
    public static Outcome fire(EntitySimWizard wizard, Spell spell, EntityLivingBase target,
            SpellModifiers modifiers) {

        if (MinecraftForge.EVENT_BUS.post(
                new SpellCastEvent.Pre(SpellCastEvent.Source.NPC, spell, wizard, modifiers))) {
            // Some OTHER mod's listener vetoed this NPC cast. Second-opinion arbiter: if no KNOWN
            // legitimate veto condition applies to us, this is AM2's burnout false positive - cast
            // anyway. Otherwise honour it, and log which spell so real pack conflicts stay visible.
            if (!NpcCastVetoArbiter.shouldOverrideVeto(wizard, spell)) {
                log(wizard, "SpellCastEvent.Pre CANCELLED, second opinion UPHELD (" + spell.getRegistryName() + ")");
                return Outcome.VETOED;
            }
            log(wizard, "SpellCastEvent.Pre cancelled but second opinion OVERRODE it - casting anyway ("
                    + spell.getRegistryName() + ")");
        }

        if (!spell.cast(wizard.world, wizard, EnumHand.MAIN_HAND, 0, target, modifiers)) {
            return Outcome.REFUSED;
        }

        wizard.swingArm(EnumHand.MAIN_HAND);

        if (spell.isContinuous) {
            // EBW skips Post and the one-shot packet for continuous spells; the channel notify
            // below is what tells the client to start rendering, and the caller ticks it.
            setContinuousSpellAndNotify(wizard, spell, target, modifiers);
            return Outcome.CAST;
        }

        MinecraftForge.EVENT_BUS.post(
                new SpellCastEvent.Post(SpellCastEvent.Source.NPC, spell, wizard, modifiers));
        sendCastPacket(wizard, spell, target, modifiers);
        return Outcome.CAST;
    }

    /**
     * Notifies nearby clients of a completed one-shot NPC cast.
     *
     * <p>Spells whose {@code requiresPacket()} is true render NOTHING client-side without this -
     * {@code ClientProxy.handleNPCCastSpellPacket} is what runs the visual cast on the client.
     * Only {@code SpellProjectile} / {@code SpellArrow} / {@code SpellMinion} return false, which
     * is exactly why the projectile spells always looked right while banish, life_drain and heal
     * fired completely invisibly.
     */
    public static void sendCastPacket(EntitySimWizard wizard, Spell spell, EntityLivingBase target,
            SpellModifiers modifiers) {
        if (!spell.requiresPacket()) {
            return;
        }
        WizardryPacketHandler.net.sendToDimension(
                new PacketNPCCastSpell.Message(wizard.getEntityId(),
                        target == null ? -1 : target.getEntityId(),
                        EnumHand.MAIN_HAND, spell, modifiers),
                wizard.world.provider.getDimension());
    }

    /**
     * Publishes a continuous-spell state change and notifies nearby clients, mirroring EBW's
     * {@code setContinuousSpellAndNotify}. Deliberately NOT gated on requiresPacket - the client
     * needs both the start transition and the {@code Spells.none} stop transition.
     *
     * <p>The target is passed through even when stopping: the client handler bails out before it
     * touches the caster's continuous-spell state when the target id does not resolve to an
     * {@code EntityLivingBase}, so a null there would make the stop packet a silent no-op.
     */
    public static void setContinuousSpellAndNotify(EntitySimWizard wizard, Spell spell,
            EntityLivingBase target, SpellModifiers modifiers) {
        wizard.setContinuousSpell(spell);
        WizardryPacketHandler.net.sendToAllAround(
                new PacketNPCCastSpell.Message(wizard.getEntityId(),
                        target == null ? -1 : target.getEntityId(),
                        EnumHand.MAIN_HAND, spell, modifiers),
                new NetworkRegistry.TargetPoint(wizard.dimension,
                        wizard.posX, wizard.posY, wizard.posZ, CONTINUOUS_NOTIFY_RANGE));
    }

    private static void log(EntitySimWizard wizard, String message) {
        if (!ModConfig.client.enableSimWizardDebugLogs) {
            return;
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks][SimWizard#{}] {}", wizard.getEntityId(), message);
    }
}
