package com.spege.srpwizmixins.mixins;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.util.config.SRPConfigWorld;
import com.dhanantry.scapeandrunparasites.util.handlers.SRPEventHandlerBus;
import com.spege.srpwizmixins.SrpWizMixins;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;
import com.spege.srpwizmixins.util.SrpMeteorTimers;

import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Fix - the meteor countdown is one shared number for every dimension, so most attempts are spent
 * somewhere the meteor can never fall.
 *
 * <p>{@code SRPEventHandlerBus} is registered once, but {@code worldTick} runs separately for every
 * loaded dimension, and they all advance the same {@code meteor} field:
 *
 * <pre>
 * public void worldTick(TickEvent.WorldTickEvent event) {
 *     if (event.phase == END) return;
 *     ...
 *     this.meteor++;
 *     if (this.meteor &lt;= SRPConfigWorld.meteorTick) return;   // 6000 by default
 *     this.meteor = 0;                                        // reset happens HERE
 *     if (rand &lt; meteorChance
 *      &amp;&amp; SRPConfig.spawnDays &lt;= world.getTotalWorldTime()
 *      &amp;&amp; SRPWorldData.get(world).getTriggerMet()             // false in blacklisted dimensions
 *      &amp;&amp; SRPSaveData.get(world, -549).getEvolutionPhase(dim) &gt;= 0) { ... spawn ... }
 * }
 * </pre>
 *
 * <p>Two details combine badly. The counter is <b>reset before any of the remaining conditions are
 * read</b>, so a rejected attempt costs the full countdown rather than being retried. And whichever
 * dimension happens to tick as the counter crosses the threshold is the dimension the conditions are
 * then evaluated against - so the attempt is effectively raffled off among everything currently
 * loaded.
 *
 * <p>With {@code Meteor Blacklisted Dimensions} covering most of what a modded pack keeps loaded,
 * the overwhelming majority of attempts land on a dimension whose {@code getTriggerMet()} is false,
 * are thrown away, and cost another full countdown. Measured on a fresh world with four dimensions
 * ticking: no meteor in eleven minutes, against a configured countdown of five.
 *
 * <p>This gives every dimension its own countdown. Each one then reaches the threshold on its own
 * schedule and is judged against its own conditions, which is what the single-world reading of the
 * config already implies. A blacklisted dimension still fails, but it fails on its own time and can
 * no longer consume the overworld's attempt.
 *
 * <p>Deliberately <em>not</em> done by moving the reset below the conditions. That would also work,
 * but it would leave every blacklisted dimension re-evaluating a condition that can never become
 * true, on every tick, forever - trading a correctness bug for a pointless one.
 *
 * <p>Every access to {@code meteor} in the entire class sits in this one method, so redirecting them
 * is complete rather than partial. SRP 1.10.7 has two reads and two writes; 1.10.8 added a third
 * write (a reset when {@code meteorActive} is off) and is handled by the same pair of injectors,
 * which is why {@code require = 2} is a floor rather than an exact count.
 *
 * <p>Gated on {@code srpCompat.meteorTimerPerDimension}; with the flag off every access goes to the
 * original field and the timing is bit-for-bit SRP's.
 */
@Mixin(value = SRPEventHandlerBus.class, remap = false)
public abstract class MixinSrpMeteorPerDimCounter {

    @Shadow
    private int meteor;

    /**
     * Which dimension's tick we are inside. Captured at the head of the method and read by the
     * redirects below, all within the same invocation on the same thread - the field never has to
     * survive longer than that.
     */
    @Unique
    private int srpwizmixins$tickingDimension;

    @Inject(method = "worldTick", at = @At("HEAD"), remap = false)
    private void srpwizmixins$captureDimension(TickEvent.WorldTickEvent event, CallbackInfo ci) {
        if (event.world != null && event.world.provider != null) {
            this.srpwizmixins$tickingDimension = event.world.provider.getDimension();
        }
    }

    @Redirect(
            method = "worldTick",
            at = @At(value = "FIELD",
                    target = "Lcom/dhanantry/scapeandrunparasites/util/handlers/SRPEventHandlerBus;"
                            + "meteor:I",
                    opcode = Opcodes.GETFIELD),
            require = 2,
            remap = false)
    private int srpwizmixins$readMeteorTimer(SRPEventHandlerBus self) {
        if (!SrpWizMixinsConfig.srpCompat.meteorTimerPerDimension) {
            return this.meteor;
        }
        return SrpMeteorTimers.get(this.srpwizmixins$tickingDimension);
    }

    @Redirect(
            method = "worldTick",
            at = @At(value = "FIELD",
                    target = "Lcom/dhanantry/scapeandrunparasites/util/handlers/SRPEventHandlerBus;"
                            + "meteor:I",
                    opcode = Opcodes.PUTFIELD),
            require = 2,
            remap = false)
    private void srpwizmixins$writeMeteorTimer(SRPEventHandlerBus self, int value) {
        int dimension = this.srpwizmixins$tickingDimension;
        boolean perDim = SrpWizMixinsConfig.srpCompat.meteorTimerPerDimension;

        if (!perDim) {
            this.meteor = value;
            return;
        }
        SrpMeteorTimers.set(dimension, value);
    }
}
