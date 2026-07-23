package com.spege.srpwizcore.mixins;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safety fix for multithreaded entity ticking (EntityThreading).
 *
 * <p>Vanilla stores tracker entries in a plain {@code HashSet}; worker-thread
 * track/untrack racing the server-thread iteration in {@code tick()} corrupts the set
 * (confirmed crashes 2026-07-21/22: NPE in the set iterator, server tick loop).
 * Swapping the value for {@link ConcurrentHashMap#newKeySet()} keeps full {@code Set}
 * semantics while giving weakly-consistent iterators that never throw on concurrent
 * mutation. Gated on {@code threadingCompat.fixEntityTrackerConcurrent}.
 */
@Mixin(EntityTracker.class)
public class MixinEntityTracker {

    @Shadow(aliases = "field_72793_b", remap = false)
    @Final
    @Mutable
    private Set<EntityTrackerEntry> entries;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void insanetweaks_installConcurrentEntries(WorldServer world, CallbackInfo ci) {
        if (!SrpWizCoreConfig.threadingCompat.fixEntityTrackerConcurrent) {
            return;
        }
        Set<EntityTrackerEntry> concurrent = ConcurrentHashMap.newKeySet();
        concurrent.addAll(this.entries);
        this.entries = concurrent;
        SrpWizCore.LOGGER.info(
                "[srpwizcore] EntityTracker entries swapped to concurrent set for dim {}",
                world.provider.getDimension());
    }
}
