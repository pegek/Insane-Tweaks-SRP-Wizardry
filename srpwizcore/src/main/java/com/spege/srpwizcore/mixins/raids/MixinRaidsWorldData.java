package com.spege.srpwizcore.mixins.raids;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.util.PerfGlueState;

import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/**
 * Raids-Backport's {@code WorldDataRaids.getData} reads from the per-world storage
 * ({@code WorldServer.getPerWorldStorage}) but registers the freshly created instance in the
 * GLOBAL one ({@code getMapStorage}). The per-world lookup therefore misses forever, and the
 * whole block repeats every single world tick: a disk {@code File.exists()} (384 ms in the
 * 2026-07-25 overworld profile), a new {@code WorldDataRaids}, and every dimension stamping the
 * shared {@code "raids"} save key over the last one.
 *
 * <p>Registering in the storage that is actually read fixes all three at once — after the first
 * tick the instance sits in the per-world {@code loadedDataMap} and the disk stat never happens
 * again.
 *
 * <p><b>Data migration:</b> for dim 0 the per-world storage reads the same {@code data/}
 * directory as the global one, so an existing {@code raids.dat} loads normally. Other dimensions
 * start with clean raid state — which was garbage anyway, being overwritten cross-dimension
 * every tick.
 */
@Mixin(targets = "net.smileycorp.raids.common.raid.WorldDataRaids", remap = false)
public class MixinRaidsWorldData {

    @Redirect(
            method = "getData",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldServer;func_175693_T()"
                            + "Lnet/minecraft/world/storage/MapStorage;"),
            remap = false)
    private static MapStorage srpwizcore$registerInPerWorldStorage(WorldServer world) {
        if (!SrpWizCoreConfig.perfGlue.raidsPerWorldStorage) {
            return world.getMapStorage();
        }
        if (!PerfGlueState.raidsStorageFixLogged) {
            PerfGlueState.raidsStorageFixLogged = true;
            SrpWizCore.LOGGER.info(
                    "[srpwizcore] Raids WorldDataRaids registered in per-world storage (dim {}) "
                            + "- per-tick disk stat() eliminated",
                    Integer.valueOf(world.provider.getDimension()));
        }
        return world.getPerWorldStorage();
    }
}
