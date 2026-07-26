package com.spege.srpwizmixins.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.srpwizmixins.SrpWizMixins;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;
import com.spege.srpwizmixins.util.SrpLocks;

import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

/**
 * Fix D — serialize the server-side creation path of {@code SRPSaveData.get}.
 *
 * <p>{@code SRPSaveData.get(World, int)} is {@code static} and unsynchronized. On the server it
 * does {@code mapStorage.getOrLoadData(...)} and, when that returns {@code null},
 * {@code new SRPSaveData()} + {@code mapStorage.setData(...)} + {@code createData(...)}. It is
 * called from entity AI and block code — {@code EntityParasiteBase}, {@code EntityAINexusGrow}
 * and dozens more — i.e. exactly the code EntityThreading ticks on worker threads. Two races
 * follow:
 *
 * <ol>
 * <li>{@code MapStorage.setData} appends to a plain {@code ArrayList} with no synchronization,
 *     so a concurrent {@code add} can leave a {@code null} hole that later truncates the world
 *     save (crash 2026-07-26 00:36 — the vanilla side is hardened by {@code MixinMapStorage} in
 *     srpwizcore, which holds for every registrant, not just SRP).</li>
 * <li>Two threads can both observe {@code null} and both create an instance. Only one survives
 *     in the storage; points written into the orphan are lost. That fits the affected world
 *     having no {@code srparasites} data file at all.</li>
 * </ol>
 *
 * <p>Mixin 0.8 has no "wrap method" primitive, so the only way to hold a lock across the
 * check-then-create is to cancel at HEAD and replay the server branch. The replay mirrors SRP's
 * bytecode exactly (offsets 50–103 of {@code get}), including the order: {@code createData}
 * reads and writes the static {@code instance} field in every branch and returns it, so
 * {@code instance} must be assigned <em>before</em> the call. The client branch
 * ({@code clientInstance}) is left untouched — the handler returns without cancelling.
 *
 * <p>Interaction with the other SaveData fixes: {@code createData} calls {@code setTotalKills},
 * so when creation happens on a worker thread Fix C ({@code MixinSrpSaveDataThreadSafety})
 * re-schedules those writes onto the server thread — the value still lands, a fraction of a tick
 * later. Fix B ({@code MixinSrpSaveDataPoints}) redirects {@code setTotalKills} inside
 * {@code createData} and writes the starting points directly, so it is unaffected. Neither
 * conflicts with a HEAD-cancel on {@code get}.
 *
 * <p>The data id is hardcoded: SRP's {@code DATA_NAME} is a {@code private static final String},
 * which javac inlines at every use site, so shadowing it is unreliable. The literal was read
 * out of the bytecode ({@code ldc "srparasites_global_data"} in {@code get}).
 *
 * <p>GOTCHA: this mixin takes over the whole server-side body of {@code get}. On an SRP version
 * bump, re-check that method with {@code javap -p -c} before trusting the flag.
 */
@Mixin(value = SRPSaveData.class, remap = false)
public abstract class MixinSrpSaveDataGetRace {

    /** Verified against SRP 1.10.7 bytecode: {@code ldc "srparasites_global_data"} in {@code get}. */
    private static final String INSANETWEAKS$DATA_NAME = "srparasites_global_data";

    // The monitor deliberately lives in SrpLocks, NOT in a field here. A `new Object()` field
    // initialiser in a mixin is a load-time crash: Mixin merges this class's <clinit> into
    // SRPSaveData and rewrites invokespecial on the mixin's superclass (Object) into the TARGET's
    // superclass constructor, while leaving the `new` alone — producing
    // `new java/lang/Object` + `invokespecial WorldSavedData.<init>`, which fails verification.
    // See SrpLocks for the full rule.

    private static boolean insanetweaks$createLogged = false;

    @Shadow
    private static SRPSaveData instance;

    @Shadow
    private static SRPSaveData createData(World world, MapStorage storage, int dim) {
        throw new AssertionError("shadow");
    }

    @Inject(
            method = "get(Lnet/minecraft/world/World;I)"
                    + "Lcom/dhanantry/scapeandrunparasites/world/SRPSaveData;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void insanetweaks$serializeGet(final World world, final int dim,
            CallbackInfoReturnable<SRPSaveData> cir) {

        if (!SrpWizMixinsConfig.srpCompat.fixSaveDataGetRace) {
            return;
        }
        // null world and the client branch stay on SRP's own code path.
        if (world == null || world.isRemote) {
            return;
        }

        synchronized (SrpLocks.SAVEDATA_CREATE) {
            MapStorage storage = world.getMapStorage();
            SRPSaveData data = (SRPSaveData) storage.getOrLoadData(SRPSaveData.class,
                    INSANETWEAKS$DATA_NAME);
            if (data == null) {
                data = new SRPSaveData();
                // createData() operates on the static field in every branch, so publish first.
                instance = data;
                storage.setData(INSANETWEAKS$DATA_NAME, data);
                data = createData(world, storage, dim);
                if (SrpWizMixinsConfig.srpCompat.debugLogging && !insanetweaks$createLogged) {
                    insanetweaks$createLogged = true;
                    SrpWizMixins.LOGGER.info(
                            "[srpwizmixins] SRP-diag: SRPSaveData created under lock "
                                    + "(dim={}, thread={})",
                            Integer.valueOf(dim), Thread.currentThread().getName());
                }
            }
            instance = data;
            cir.setReturnValue(data);
        }
    }
}
