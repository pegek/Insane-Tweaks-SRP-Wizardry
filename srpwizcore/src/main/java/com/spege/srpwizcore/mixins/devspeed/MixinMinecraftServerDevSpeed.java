package com.spege.srpwizcore.mixins.devspeed;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Dev-only game speed, server half — a carrier with no members. The patch itself is done by
 * {@code DevSpeedMixinPlugin.preApply}, which Mixin only calls for a mixin that applies to the
 * target, so this class is what makes {@code MinecraftServer} reach the plugin (and what the
 * plugin's config gate turns off).
 *
 * <p>{@code MinecraftServer.run()} hardcodes the 50 ms step four times — the "ticks behind"
 * division in the can't-keep-up warning, {@code while (i > 50L)}, {@code i -= 50L} and
 * {@code Thread.sleep(Math.max(1L, 50L - i))}. Replacing all four with one value is a real
 * tick-rate change: {@code getTotalWorldTime()}, day/night, SRP's per-day evolution and every
 * other tick counter advance faster, which {@code /time set} cannot do.
 *
 * <p>Why not {@code @ModifyConstant(longValue = 50L)}, which is what this class used to carry:
 * in DEv 1.2 there is no {@code 50L} left by the time Mixin sees the class. LLibrary's coremod
 * replaces all four with {@code LLibraryHooks.getTickRate()}, so the injector wired 0 of 4 on
 * every Cleanroom version (found 2026-10-20; CleanMix was blamed first, wrongly). The plugin
 * chains a call after each tick-length source, constant or LLibrary call alike.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerDevSpeed {
}
