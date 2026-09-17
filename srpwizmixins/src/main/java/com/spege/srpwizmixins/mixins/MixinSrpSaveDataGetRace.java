package com.spege.srpwizmixins.mixins;

import org.spongepowered.asm.mixin.Mixin;
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
 * does {@code mapStorage.getOrLoadData(...)} and, when that returns {@code null}, creates the
 * instance and registers it with {@code mapStorage.setData(...)}. It is called from entity AI and
 * block code — {@code EntityParasiteBase}, {@code EntityAINexusGrow} and dozens more — i.e.
 * exactly the code EntityThreading ticks on worker threads. Two races follow:
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
 * check-then-create is to cancel at HEAD and replay the server branch.
 *
 * <p>SRP VERSION PIN — this mixin replays SRP's own {@code get} body, so it is tied to that
 * body. Verified against <b>1.10.9</b> with {@code javap -p -c}; it does <b>not</b> work on
 * 1.10.8 or earlier. What 1.10.9 changed:
 *
 * <ul>
 * <li>the {@code private static instance} / {@code clientInstance} singleton fields are gone,
 *     and with them the client branch — {@code get} is now purely
 *     {@code getOrLoadData} → {@code new SRPSaveData(world, dim)} → {@code setData} → return;</li>
 * <li>{@code createData} became {@code private void createData(World, int)} and is invoked from
 *     the new {@code public SRPSaveData(World, int)} constructor (bytecode offset 170), so
 *     constructing the object already seeds the per-dimension records. Nothing has to be
 *     published before that call any more, which is why the old {@code @Shadow} on
 *     {@code instance} and on the static {@code createData} is gone from this class.</li>
 * </ul>
 *
 * <p>Order matters and mirrors SRP: construct first, register second. {@code createData} runs
 * inside the constructor and calls {@code markDirty()} on an instance not yet known to the
 * {@code MapStorage} — that only flips a dirty flag, so it is safe, and it is what SRP itself
 * does.
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

    /** Verified against SRP 1.10.9 bytecode: {@code ldc "srparasites_global_data"} in {@code get}. */
    private static final String INSANETWEAKS$DATA_NAME = "srparasites_global_data";

    // The monitor deliberately lives in SrpLocks, NOT in a field here. A `new Object()` field
    // initialiser in a mixin is a load-time crash: Mixin merges this class's <clinit> into
    // SRPSaveData and rewrites invokespecial on the mixin's superclass (Object) into the TARGET's
    // superclass constructor, while leaving the `new` alone — producing
    // `new java/lang/Object` + `invokespecial WorldSavedData.<init>`, which fails verification.
    // See SrpLocks for the full rule.

    private static boolean insanetweaks$createLogged = false;

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
        // A null world and the client stay on SRP's own code path. SRP 1.10.9 no longer keeps a
        // separate client instance, but leaving the client alone keeps this fix server-only,
        // which is all it was ever meant to cover.
        if (world == null || world.isRemote) {
            return;
        }

        synchronized (SrpLocks.SAVEDATA_CREATE) {
            MapStorage storage = world.getMapStorage();
            SRPSaveData data = (SRPSaveData) storage.getOrLoadData(SRPSaveData.class,
                    INSANETWEAKS$DATA_NAME);
            if (data == null) {
                // The constructor runs createData(world, dim) itself; register afterwards, as SRP does.
                data = new SRPSaveData(world, dim);
                storage.setData(INSANETWEAKS$DATA_NAME, data);
                if (SrpWizMixinsConfig.srpCompat.debugLogging && !insanetweaks$createLogged) {
                    insanetweaks$createLogged = true;
                    SrpWizMixins.LOGGER.info(
                            "[srpwizmixins] SRP-diag: SRPSaveData created under lock "
                                    + "(dim={}, thread={})",
                            Integer.valueOf(dim), Thread.currentThread().getName());
                }
            }
            cir.setReturnValue(data);
        }
    }
}
