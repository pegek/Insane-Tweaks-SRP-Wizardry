package com.spege.srpwizmixins.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.srpwizmixins.SrpWizMixins;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/**
 * Fix C (thread safety) + perf early-reject for {@code SRPSaveData.setTotalKills}.
 *
 * <p>State in {@code SRPSaveData} is a family of parallel ArrayLists indexed by a shared
 * scan over {@code dimEPid}. EntityThreading ticks entities on worker threads, and SRP
 * entity code (COTH duration refresh, code 20) calls {@code setTotalKills} from there —
 * racing the server thread and corrupting the lists. Off-thread calls are re-scheduled
 * onto the main thread ({@code fixSaveDataThreadSafety}).
 *
 * <p>Separately, SRP computes the choice multiplier and debug-log payload BEFORE its own
 * rejection checks (gaining off / phase -2). {@code perfEarlyRejectSetTotalKills} mirrors
 * those exact conditions at HEAD so doomed calls (locked overworld: every infestation
 * random tick) return {@code false} without the wasted work.
 *
 * <p>GOTCHA: the public getters ({@code getEvolutionPhase} etc.) call {@code addDim} —
 * i.e. MUTATE the lists — for an unknown dim. The early-reject therefore only ever runs
 * on the server thread (thread-guard is evaluated first).
 *
 * <p>{@code setTotalKills} is SRP's own method (not MCP-mapped) — matched with
 * {@code remap = false} and an explicit descriptor pinning the 7-arg worker overload
 * (the 6-arg overload delegates to it, so hooking the worker catches every caller).
 */
@Mixin(value = SRPSaveData.class, remap = false)
public abstract class MixinSrpSaveDataThreadSafety {

    private static int insanetweaks$bounceLogCount = 0;

    @Inject(
            method = "setTotalKills(IIZLnet/minecraft/world/World;ZZI)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void insanetweaks$guardSetTotalKills(final int dim, final int value,
            final boolean canChangePhase, final World world, final boolean p5,
            final boolean p6, final int code, CallbackInfoReturnable<Boolean> cir) {

        final SRPSaveData self = (SRPSaveData) (Object) this;

        // --- 1. Thread-guard: off-main write -> re-schedule on the server thread ---
        if (SrpWizMixinsConfig.srpCompat.fixSaveDataThreadSafety && world != null && !world.isRemote) {
            MinecraftServer server = world.getMinecraftServer();
            if (server != null && !server.isCallingFromMinecraftThread()) {
                if (SrpWizMixinsConfig.srpCompat.debugLogging && insanetweaks$bounceLogCount < 20) {
                    insanetweaks$bounceLogCount++;
                    SrpWizMixins.LOGGER.info(
                            "[InsaneTweaks] SRP-diag: setTotalKills bounced to main thread "
                                    + "(dim={}, value={}, code={}, thread={})",
                            dim, value, code, Thread.currentThread().getName());
                }
                server.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        self.setTotalKills(dim, value, canChangePhase, world, p5, p6, code);
                    }
                });
                cir.setReturnValue(false);
                return;
            }
        }

        // --- 2. Early-reject: mirror SRP's own rejection conditions, main thread only ---
        // (getters mutate via addDim on unknown dims -> never call them off-thread)
        if (SrpWizMixinsConfig.srpCompat.perfEarlyRejectSetTotalKills) {
            if (self.getEvolutionPhase(dim) == -2
                    || (value > 0 && canChangePhase && !self.getCanGain(dim))
                    || (value < 0 && canChangePhase && !self.getCanLoss(dim))) {
                cir.setReturnValue(false);
            }
        }
    }
}
