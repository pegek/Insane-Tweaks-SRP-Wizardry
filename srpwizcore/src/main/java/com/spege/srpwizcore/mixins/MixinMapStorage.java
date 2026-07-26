package com.spege.srpwizcore.mixins;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safety fix for world-data registration (NoiseThreader / EntityThreading).
 *
 * <p>Symptom (crash 2026-07-26 00:36, server): a {@code null} sat in
 * {@code MapStorage.loadedDataList}; {@code saveAllData()} walks that list by index and calls
 * {@code isDirty()} on every element, so the world save died with an NPE <em>halfway through
 * the list</em> — everything registered after the bad entry never reached disk (the affected
 * world had 17 entries under {@code data/} instead of ~40). The symptom is not reproducible:
 * the same world reloaded saved ten times cleanly and filled in the missing files.
 *
 * <p>Cause: {@code MapStorage} is not thread-safe. Both {@code setData} and
 * {@code getOrLoadData} mutate {@code loadedDataList} — a plain {@code ArrayList} — with no
 * synchronization. Two concurrent {@code add()} calls have the classic {@code ArrayList} race:
 * {@code size} is incremented before the element lands in the backing array, leaving a
 * {@code null} hole even though nobody ever passed {@code null}. That is why the bytecode scan
 * for {@code setData(..., null)} across every jar in the pack found nothing.
 *
 * <p>Who writes off-thread: the pack runs two threading coremods (NoiseThreader threads chunk
 * generation, and structures register their {@code WorldSavedData} while generating;
 * EntityThreading threads {@code World.updateEntities}). SRP's own
 * {@code SRPSaveData.get(World,int)} is a static, unsynchronized create-and-register that entity
 * AI calls every tick — see {@code MixinSrpSaveDataGetRace} in srpwizmixins for the fix at that
 * end. This mixin fixes the vanilla side, so it holds for <em>any</em> off-thread registrant.
 *
 * <p>Three independently gated layers:
 * <ol>
 * <li>{@code fixMapStorageConcurrent} — swap the list for a {@link CopyOnWriteArrayList} in the
 *     constructor. COW rather than {@code Collections.synchronizedList} because the save loop
 *     reads without a lock: only a volatile array snapshot guarantees safe publication. The list
 *     holds a few dozen entries and is mutated a few dozen times per world load, so the copy
 *     cost is noise. This removes the source of the {@code null} for every mutation path.</li>
 * <li>{@code skipNullOnSave} — second line of defence: skip a {@code null} entry during the save
 *     instead of dying on it, so the remaining data still reaches disk.</li>
 * <li>{@code diagOffThreadSetData} — name the culprit: log every {@code setData} arriving from a
 *     thread other than the main ones, with a stack trace. An empty log is also a result.</li>
 * </ol>
 *
 * <p>Early mixin (vanilla target) via the jar-manifest {@code MixinConfigs} route,
 * {@code remap = false} + explicit SRG per project convention. Bytecode of
 * {@code saveAllData}/{@code func_75744_a} verified with {@code javap -p -c} against
 * {@code forge-1.12.2-14.23.5.2860-srg.jar} and the notch client jar: indexed loop, single
 * {@code isDirty} call site, receiver is exactly {@code WorldSavedData} (INVOKEVIRTUAL).
 * Cleanroom does not binpatch {@code MapStorage} (no entry in {@code binpatches.pack.lzma}).
 */
@Mixin(MapStorage.class)
public class MixinMapStorage {

    /** SRG {@code field_75750_c}. Private in vanilla, so the MCP-name + alias shadow works. */
    @Shadow(aliases = "field_75750_c", remap = false)
    @Final
    @Mutable
    private List<WorldSavedData> loadedDataList;

    private static boolean srpwizcore$swapLogged = false;
    private static boolean srpwizcore$nullReported = false;
    private static int srpwizcore$offThreadLogged = 0;

    /** Cap on off-thread {@code setData} lines per game run, so a hot path can't flood the log. */
    private static final int SRPWIZCORE$OFF_THREAD_LOG_LIMIT = 40;

    /**
     * Layer 1 — replace the backing list. Runs at constructor RETURN, i.e. after vanilla has
     * assigned {@code Lists.newArrayList()} and after {@code loadIdCounts()}.
     *
     * <p>The INFO line is also the only proof the mixin applied: the config carries
     * {@code required: false}, so a failed injection is silent and "the game started" proves
     * nothing.
     */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void srpwizcore$installConcurrentDataList(ISaveHandler saveHandler, CallbackInfo ci) {
        if (!SrpWizCoreConfig.threadingCompat.fixMapStorageConcurrent) {
            return;
        }
        this.loadedDataList = new CopyOnWriteArrayList<WorldSavedData>(this.loadedDataList);
        if (!srpwizcore$swapLogged) {
            srpwizcore$swapLogged = true;
            SrpWizCore.LOGGER.info(
                    "[srpwizcore] MapStorage loadedDataList swapped to CopyOnWriteArrayList");
        }
    }

    /**
     * Layer 2 — the actual repair. With the flag off this is byte-for-byte vanilla behaviour
     * (a {@code null} still throws the NPE); with it on the entry is skipped and the save
     * finishes the rest of the list.
     */
    @Redirect(
            method = { "saveAllData", "func_75744_a" },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/storage/WorldSavedData;func_76188_b()Z"),
            remap = false)
    private boolean srpwizcore$skipNullData(WorldSavedData data) {
        if (data == null && SrpWizCoreConfig.threadingCompat.skipNullOnSave) {
            srpwizcore$reportNullEntry();
            return false;
        }
        return data.isDirty();
    }

    /**
     * Layer 3 — the §4b test. {@code SaveDataMemoryStorage} overrides {@code setData}, so the
     * client world never reaches this and can't pollute the log. Anything other than the main
     * threads showing up here closes the case on what to synchronize.
     */
    @Inject(method = { "setData", "func_75745_a" }, at = @At("HEAD"), remap = false)
    private void srpwizcore$diagOffThreadSetData(String dataId, WorldSavedData data,
            CallbackInfo ci) {
        if (!SrpWizCoreConfig.threadingCompat.diagOffThreadSetData) {
            return;
        }
        if (srpwizcore$offThreadLogged >= SRPWIZCORE$OFF_THREAD_LOG_LIMIT) {
            return;
        }
        String thread = Thread.currentThread().getName();
        if (srpwizcore$isMainThread(thread)) {
            return;
        }
        srpwizcore$offThreadLogged++;
        SrpWizCore.LOGGER.warn(
                "[srpwizcore] MapStorage.setData off-thread: id={} type={} thread={} listSize={}",
                dataId,
                data == null ? "null" : data.getClass().getName(),
                thread,
                Integer.valueOf(this.loadedDataList.size()),
                new Throwable("off-thread MapStorage.setData"));
    }

    private static boolean srpwizcore$isMainThread(String threadName) {
        return "Server thread".equals(threadName)
                || "Client thread".equals(threadName)
                || "Render thread".equals(threadName);
    }

    /**
     * One-shot forensic dump. The symmetric difference of list and map is the decisive
     * evidence: a complete map next to a list with a hole means {@code put} went through and
     * {@code add} tore.
     */
    private void srpwizcore$reportNullEntry() {
        if (srpwizcore$nullReported) {
            return;
        }
        srpwizcore$nullReported = true;

        List<WorldSavedData> list = this.loadedDataList;
        List<Integer> nullIndices = new ArrayList<Integer>();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                nullIndices.add(Integer.valueOf(i));
            }
        }
        SrpWizCore.LOGGER.error(
                "[srpwizcore] null entry in MapStorage.loadedDataList — save would have been "
                        + "TRUNCATED here; skipping it. indices={} size={} thread={}",
                nullIndices, Integer.valueOf(list.size()), Thread.currentThread().getName(),
                new Throwable("null world data at save"));

        srpwizcore$reportListVsMap(list);
    }

    /**
     * Best-effort only. {@code loadedDataMap} is {@code protected}, and shadowing a non-private
     * vanilla field is exactly what keeps {@code MixinLocale} from applying on CleanMix 0.6.6+ —
     * so it is read reflectively and a failure degrades the diagnostic instead of killing the
     * whole mixin.
     */
    private void srpwizcore$reportListVsMap(List<WorldSavedData> list) {
        try {
            Field mapField;
            try {
                mapField = MapStorage.class.getDeclaredField("field_75749_b");
            } catch (NoSuchFieldException devMappings) {
                mapField = MapStorage.class.getDeclaredField("loadedDataMap");
            }
            mapField.setAccessible(true);
            Object raw = mapField.get(this);
            if (!(raw instanceof Map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, WorldSavedData> map = (Map<String, WorldSavedData>) raw;

            List<String> inMapNotInList = new ArrayList<String>();
            List<String> nullValuedKeys = new ArrayList<String>();
            for (Map.Entry<String, WorldSavedData> entry : map.entrySet()) {
                WorldSavedData value = entry.getValue();
                if (value == null) {
                    nullValuedKeys.add(entry.getKey());
                } else if (!srpwizcore$containsIdentity(list, value)) {
                    inMapNotInList.add(entry.getKey());
                }
            }
            int inListNotInMap = 0;
            for (int i = 0; i < list.size(); i++) {
                WorldSavedData data = list.get(i);
                if (data != null && !map.containsValue(data)) {
                    inListNotInMap++;
                }
            }
            SrpWizCore.LOGGER.error(
                    "[srpwizcore] list-vs-map diff: mapSize={} listSize={} inMapNotInList={} "
                            + "nullValuedMapKeys={} inListNotInMap={} "
                            + "(complete map + holed list == torn ArrayList.add)",
                    Integer.valueOf(map.size()), Integer.valueOf(list.size()), inMapNotInList,
                    nullValuedKeys, Integer.valueOf(inListNotInMap));
        } catch (Throwable reflectionFailed) {
            SrpWizCore.LOGGER.warn(
                    "[srpwizcore] could not read MapStorage.loadedDataMap for the list-vs-map "
                            + "diff; the null-index dump above still stands",
                    reflectionFailed);
        }
    }

    private static boolean srpwizcore$containsIdentity(List<WorldSavedData> list,
            WorldSavedData value) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == value) {
                return true;
            }
        }
        return false;
    }
}
