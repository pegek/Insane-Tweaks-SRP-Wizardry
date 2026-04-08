package com.spege.insanetweaks.util;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooM;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooS;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * Pomocnik przywracania entity SRP do ich oryginalnych vanilla form.
 *
 * System działa w dwóch warstwach:
 *
 * 1. SNAPSHOT (przed transformacją):
 *    MixinParasiteEventEntity przechwytuje oryginalne entity tuż przed
 *    wywołaniem world.removeEntity() przez spawnInsider() / convertEntity().
 *    Pełny NBT dump + ResourceLocation ID zapisywane są w PENDING.
 *
 * 2. APPLY ON JOIN:
 *    EntityJoinWorldEvent łapie nowo spawnowane EntityInhooM/S lub
 *    EntityPInfected i stosuje snapshot do ich entityData —
 *    pod kluczem "itOriginalNBT" i "itOriginalId".
 *
 * 3. RESTORE:
 *    ItemZhonyasHourglassArtefact odczytuje te klucze z entity data
 *    i wywołuje performRestore() który tworzy nowe entity z pełnym NBT.
 */
public final class SrpOriginSnapshotHelper {

    /** NBT klucz: pełny NBT dump oryginalnego entity (w getEntityData() SRP entity). */
    public static final String KEY_ORIGINAL_NBT = "itOriginalNBT";
    /** NBT klucz: ResourceLocation ID oryginalnego entity. */
    public static final String KEY_ORIGINAL_ID  = "itOriginalId";
    /** NBT klucz: cooldown przywracania (ticki). */
    public static final String KEY_RESTORE_CD   = "itRestoreCooldown";

    /**
     * Chwilowa mapa oczekujących snapshotów: entityId usuwanego entity → snapshot.
     * Klucz = entityId STAREGO (usuwanego) entity — to samo ID którego używa Mixin.
     * Czyszczona po zastosowaniu lub po 2 sekundach.
     */
    private static final Map<Integer, OriginalSnapshot> PENDING = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────
    //  Reverse mapping: SRP entity ID → vanilla entity ID (fallback)
    // ─────────────────────────────────────────────────────────────────

    private static final Map<String, String> SRP_TO_VANILLA = new HashMap<>();

    static {
        // ── Assimilated (EntityPInfected subclasses) ──────────────────
        SRP_TO_VANILLA.put("srparasites:inf_cow",      "minecraft:cow");
        SRP_TO_VANILLA.put("srparasites:inf_horse",    "minecraft:horse");
        SRP_TO_VANILLA.put("srparasites:inf_pig",      "minecraft:pig");
        SRP_TO_VANILLA.put("srparasites:inf_wolf",     "minecraft:wolf");
        SRP_TO_VANILLA.put("srparasites:inf_sheep",    "minecraft:sheep");
        SRP_TO_VANILLA.put("srparasites:inf_villager", "minecraft:villager");
        SRP_TO_VANILLA.put("srparasites:inf_enderman", "minecraft:enderman");
        SRP_TO_VANILLA.put("srparasites:inf_squid",    "minecraft:squid");
        SRP_TO_VANILLA.put("srparasites:inf_human",    "minecraft:zombie");
        SRP_TO_VANILLA.put("srparasites:inf_player",   "minecraft:zombie");
        SRP_TO_VANILLA.put("srparasites:inf_bear",     "minecraft:polar_bear");
        SRP_TO_VANILLA.put("srparasites:inf_dragone",  "minecraft:ender_dragon");

        // ── Assimilated Heads ─────────────────────────────────────────
        SRP_TO_VANILLA.put("srparasites:inf_cowhead",      "minecraft:cow");
        SRP_TO_VANILLA.put("srparasites:inf_horsehead",    "minecraft:horse");
        SRP_TO_VANILLA.put("srparasites:inf_pighead",      "minecraft:pig");
        SRP_TO_VANILLA.put("srparasites:inf_wolfhead",     "minecraft:wolf");
        SRP_TO_VANILLA.put("srparasites:inf_sheephead",    "minecraft:sheep");
        SRP_TO_VANILLA.put("srparasites:inf_villagerhead", "minecraft:villager");
        SRP_TO_VANILLA.put("srparasites:inf_endermanhead", "minecraft:enderman");
        SRP_TO_VANILLA.put("srparasites:inf_humanhead",    "minecraft:zombie");

        // ── Feral forms ───────────────────────────────────────────────
        SRP_TO_VANILLA.put("srparasites:fer_cow",      "minecraft:cow");
        SRP_TO_VANILLA.put("srparasites:fer_horse",    "minecraft:horse");
        SRP_TO_VANILLA.put("srparasites:fer_pig",      "minecraft:pig");
        SRP_TO_VANILLA.put("srparasites:fer_wolf",     "minecraft:wolf");
        SRP_TO_VANILLA.put("srparasites:fer_sheep",    "minecraft:sheep");
        SRP_TO_VANILLA.put("srparasites:fer_villager", "minecraft:villager");
        SRP_TO_VANILLA.put("srparasites:fer_enderman", "minecraft:enderman");
        SRP_TO_VANILLA.put("srparasites:fer_human",    "minecraft:zombie");
        SRP_TO_VANILLA.put("srparasites:fer_bear",     "minecraft:polar_bear");

        // ── Simulated (disguised) forms ───────────────────────────────
        SRP_TO_VANILLA.put("srparasites:sim_cow",      "minecraft:cow");
        SRP_TO_VANILLA.put("srparasites:sim_horse",    "minecraft:horse");
        SRP_TO_VANILLA.put("srparasites:sim_pig",      "minecraft:pig");
        SRP_TO_VANILLA.put("srparasites:sim_wolf",     "minecraft:wolf");
        SRP_TO_VANILLA.put("srparasites:sim_sheep",    "minecraft:sheep");
        SRP_TO_VANILLA.put("srparasites:sim_villager", "minecraft:villager");
        SRP_TO_VANILLA.put("srparasites:sim_enderman", "minecraft:enderman");
        SRP_TO_VANILLA.put("srparasites:sim_human",    "minecraft:zombie");
        SRP_TO_VANILLA.put("srparasites:sim_bear",     "minecraft:polar_bear");

        // ── InhooM / InhooS → size-based fallback (handled below) ────
    }

    private SrpOriginSnapshotHelper() {}

    // ─────────────────────────────────────────────────────────────────
    //  Pending Snapshot API (used by mixin)
    // ─────────────────────────────────────────────────────────────────

    /** Sprawdza czy jest już pending snapshot dla danego entityId (idempotencja). */
    public static boolean hasPending(int entityId) {
        return PENDING.containsKey(entityId);
    }

    /** Zapisuje snapshot oryginalnego entity. Wywoływane przez Mixin. */
    public static void savePending(int originalEntityId, String originalResourceId, NBTTagCompound fullNbt) {
        PENDING.put(originalEntityId, new OriginalSnapshot(originalResourceId, fullNbt.copy(), System.currentTimeMillis()));
    }

    /**
     * Pobiera i usuwa NAJŚWIEŻSZY snapshot.
     * Używane w EntityJoinWorldEvent dla InhooM/S — nie znamy EntityID nowego entity
     * w momencie spawnu, heurystyka: ostatni snapshot = ostatnia transformacja.
     */
    @Nullable
    public static OriginalSnapshot popMostRecent() {
        if (PENDING.isEmpty()) return null;
        int newestKey   = -1;
        long newestTime = Long.MIN_VALUE;
        for (Map.Entry<Integer, OriginalSnapshot> entry : PENDING.entrySet()) {
            if (entry.getValue().timestamp > newestTime) {
                newestTime = entry.getValue().timestamp;
                newestKey  = entry.getKey();
            }
        }
        return (newestKey >= 0) ? PENDING.remove(newestKey) : null;
    }

    /** Czyści snapshoty starsze niż 3 sekundy (dla bezpieczeństwa). */
    public static void cleanupStale() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(e -> now - e.getValue().timestamp > 3000L);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Identyfikacja SRP entity
    // ─────────────────────────────────────────────────────────────────

    /** Zwraca true jeśli entity to pasożyt SRP który można przywrócić. */
    public static boolean isSrpConvertedEntity(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) return false;
        ResourceLocation id = EntityList.getKey(entity);
        if (id == null || !"srparasites".equals(id.getResourceDomain())) return false;
        return entity instanceof EntityPInfected
                || entity instanceof EntityInhooM
                || entity instanceof EntityInhooS
                || SRP_TO_VANILLA.containsKey(id.toString());
    }

    // ─────────────────────────────────────────────────────────────────
    //  Resolve ID oryginalnego entity
    // ─────────────────────────────────────────────────────────────────

    /**
     * Zwraca ID vanilla entity dla danego SRP entity.
     *
     * Priorytet:
     *   1. itOriginalId z getEntityData() — ustawiony przez EntityJoinWorldEvent po snapshocie
     *   2. EntityPInfected.getHost() — SRP przechowuje to samodzielnie
     *   3. Tabela SRP_TO_VANILLA (statyczne mapowanie)
     *   4. Rozmiarowy fallback dla InhooM/S (ostatnia deska ratunku)
     *
     * Zwraca null jeśli brak rozwiązania (nie powinniśmy przywracać).
     */
    @Nullable
    public static String resolveVanillaId(EntityLivingBase entity) {
        NBTTagCompound data = entity.getEntityData();

        // 1. Snapshot snapshot zapisany przez EntityJoinWorldEvent
        if (data.hasKey(KEY_ORIGINAL_ID, 8)) {
            String id = data.getString(KEY_ORIGINAL_ID);
            if (!id.isEmpty()) return id;
        }

        // 2. EntityPInfected native host field
        if (entity instanceof EntityPInfected) {
            String host = ((EntityPInfected) entity).getHost();
            if (host != null && !host.isEmpty()) return host;
        }

        // 3. Statyczna tabela mapowania
        ResourceLocation srpId = EntityList.getKey(entity);
        if (srpId != null) {
            String mapped = SRP_TO_VANILLA.get(srpId.toString());
            if (mapped != null) return mapped;
        }

        // 4. Rozmiarowy fallback (najmniej precyzyjny)
        if (entity instanceof EntityInhooS) return "minecraft:pig";
        if (entity instanceof EntityInhooM) return "minecraft:cow";

        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Przywracanie entity
    // ─────────────────────────────────────────────────────────────────

    /**
     * Wykonuje pełne przywrócenie:
     * 1. Tworzy nowe entity z danym vanillaId.
     * 2. Jeśli SRP entity ma zapisane itOriginalNBT — aplicuje je (pełny restore).
     * 3. W przeciwnym razie tworzy świeżą instancję z pozycją i imieniem.
     * 4. Usuwa SRP entity, spawnuje przywrócone.
     */
    @Nullable
    public static Entity performRestore(EntityLivingBase srpEntity, World world, String vanillaId) {
        ResourceLocation rl;
        try {
            rl = new ResourceLocation(vanillaId);
        } catch (Exception ex) {
            return null;
        }

        NBTTagCompound data = srpEntity.getEntityData();
        boolean hasOriginalNbt = data.hasKey(KEY_ORIGINAL_NBT, 10);

        Entity restored;

        if (hasOriginalNbt) {
            // ── Ścieżka 1: Pełny NBT restore ─────────────────────────
            // Tworzymy entity, a potem wczytujemy pełne NBT
            restored = EntityList.createEntityByIDFromName(rl, world);
            if (restored == null) return null;

            NBTTagCompound originalNbt = data.getCompoundTag(KEY_ORIGINAL_NBT).copy();

            // Nadpisz pozycję — nie chcemy spawnu w miejscu oryginalnej infekcji
            // NBT przechowuje Pos jako NBTTagList [x, y, z]
            NBTTagList posList = new NBTTagList();
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(srpEntity.posX));
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(srpEntity.posY));
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(srpEntity.posZ));
            originalNbt.setTag("Pos", posList);

            // Usuń wszelkie ślady stanu śmierci/infekcji
            originalNbt.removeTag("DeathTime");
            originalNbt.removeTag("HurtTime");
            originalNbt.removeTag("FallDistance");
            originalNbt.setFloat("Health", restored instanceof EntityLivingBase
                ? ((EntityLivingBase) restored).getMaxHealth() * 0.9f
                : 10.0f);

            try {
                restored.readFromNBT(originalNbt);
            } catch (Exception ex) {
                // NBT niezgodne — spawnuj bez NBT z zachowanym imieniem
            }

            // Zawsze ustaw pozycję na aktualną pozycję SRP entity
            restored.setLocationAndAngles(
                srpEntity.posX, srpEntity.posY, srpEntity.posZ,
                srpEntity.rotationYaw, srpEntity.rotationPitch);

        } else {
            // ── Ścieżka 2: Brak snapshotu — tylko pozycja i ewentualne imię ──
            restored = EntityList.createEntityByIDFromName(rl, world);
            if (restored == null) return null;
            restored.setLocationAndAngles(
                srpEntity.posX, srpEntity.posY, srpEntity.posZ,
                srpEntity.rotationYaw, srpEntity.rotationPitch);

            // Skopiuj imię jeśli SRP entity je miało (SRP kopiuje CustomName w convertEntity)
            if (srpEntity.hasCustomName() && restored instanceof EntityLivingBase) {
                ((EntityLivingBase) restored).setCustomNameTag(srpEntity.getCustomNameTag());
                ((EntityLivingBase) restored).setAlwaysRenderNameTag(srpEntity.getAlwaysRenderNameTag());
            }
        }

        // Usuń SRP entity i spawnuj przywrócone
        srpEntity.setDead();
        world.spawnEntity(restored);
        return restored;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Data class
    // ─────────────────────────────────────────────────────────────────

    public static final class OriginalSnapshot {
        public final String         resourceId;
        public final NBTTagCompound fullNbt;
        public final long           timestamp;

        public OriginalSnapshot(String resourceId, NBTTagCompound fullNbt, long timestamp) {
            this.resourceId = resourceId;
            this.fullNbt    = fullNbt;
            this.timestamp  = timestamp;
        }
    }
}
