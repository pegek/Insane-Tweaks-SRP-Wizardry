package com.spege.insanetweaks.util;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooM;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooS;
import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * Pomocnik przywracania entity SRP do ich oryginalnych vanilla/mod form.
 *
 * System działa w dwóch warstwach:
 *
 * 1. SNAPSHOT (przed transformacją):
 *    MixinParasiteEventEntity przechwytuje oryginalne entity tuż przed
 *    wywołaniem world.removeEntity() przez spawnInsider() / convertEntity().
 *    Pełny NBT dump + ResourceLocation ID zapisywane są w PENDING.
 *
 * 2. APPLY ON JOIN:
 *    ZhonyasEventHandler łapie nowo spawnowane EntityInhooM/S lub
 *    EntityPInfected i stosuje snapshot do ich entityData —
 *    pod kluczem itOriginalNBT i itOriginalId.
 *
 * 3. RESTORE:
 *    ItemZhonyasHourglassArtefact odczytuje te klucze z entity data
 *    i wywołuje performRestore() który tworzy nowe entity z pełnym NBT.
 *
 * Obsługa EntityInhooM/S (Incomplete Form):
 *    Mobs bez odpowiednika w SRP (np. EBWizardry wizards, modded mobs) konwertują
 *    do EntityInhooM (duże) lub EntityInhooS (małe). MixinParasiteEventEntity
 *    przechwytuje je PO stronie spawnInsider() i zapisuje pełny snapshot
 *    oryginalnego entity do PENDING. ZhonyasEventHandler stosuje snapshot do
 *    danych InhooM/S. performRestore() może następnie w pełni odtworzyć
 *    oryginalny mob — włącznie z modded entity — z kompletnym NBT.
 */
public final class SrpOriginSnapshotHelper {

    /** NBT klucz: pełny NBT dump oryginalnego entity (w getEntityData() SRP entity). */
    public static final String KEY_ORIGINAL_NBT = "itOriginalNBT";
    /** NBT klucz: ResourceLocation ID oryginalnego entity. */
    public static final String KEY_ORIGINAL_ID  = "itOriginalId";
    /** NBT klucz: cooldown EPEL repel protection po przywróceniu (ticki). */
    public static final String KEY_RESTORE_CD   = "itRestoreCooldown";

    // Czas trwania efektu EPEL_E nakładanego po udanym przywróceniu (30 sekund).
    private static final int RESTORATION_EPEL_DURATION = 600;

    /**
     * Chwilowa mapa oczekujących snapshotów: entityId usuwanego entity → snapshot.
     * Klucz = entityId STAREGO (usuwanego) entity — to samo ID którego używa Mixin.
     * Czyszczona po zastosowaniu lub po 3 sekundach (cleanupStale).
     */
    private static final Map<Integer, OriginalSnapshot> PENDING = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────
    //  Reverse mapping: SRP entity ID → vanilla entity ID (fallback)
    // ─────────────────────────────────────────────────────────────────

    private static final Map<String, String> SRP_TO_VANILLA = new HashMap<>();

    static {
        // ── Assimilated (EntityPInfected subclasses) ──────────────────
        SRP_TO_VANILLA.put("srparasites:inf_cow",           "minecraft:cow");
        SRP_TO_VANILLA.put("srparasites:inf_horse",         "minecraft:horse");
        SRP_TO_VANILLA.put("srparasites:inf_pig",           "minecraft:pig");
        SRP_TO_VANILLA.put("srparasites:inf_wolf",          "minecraft:wolf");
        SRP_TO_VANILLA.put("srparasites:inf_sheep",         "minecraft:sheep");
        SRP_TO_VANILLA.put("srparasites:inf_villager",      "minecraft:villager");
        SRP_TO_VANILLA.put("srparasites:inf_enderman",      "minecraft:enderman");
        SRP_TO_VANILLA.put("srparasites:inf_squid",         "minecraft:squid");
        SRP_TO_VANILLA.put("srparasites:inf_human",         "minecraft:zombie");
        SRP_TO_VANILLA.put("srparasites:inf_player",        "minecraft:zombie");
        SRP_TO_VANILLA.put("srparasites:inf_bear",          "minecraft:polar_bear");
        SRP_TO_VANILLA.put("srparasites:inf_dragone",       "minecraft:ender_dragon");
        SRP_TO_VANILLA.put("srparasites:inf_mooshroom",     "minecraft:mooshroom");
        SRP_TO_VANILLA.put("srparasites:inf_bat",           "minecraft:bat");
        SRP_TO_VANILLA.put("srparasites:inf_ocelot",        "minecraft:ocelot");
        SRP_TO_VANILLA.put("srparasites:inf_rabbit",        "minecraft:rabbit");

        // ── Assimilated Heads ─────────────────────────────────────────
        SRP_TO_VANILLA.put("srparasites:inf_cowhead",       "minecraft:cow");
        SRP_TO_VANILLA.put("srparasites:inf_horsehead",     "minecraft:horse");
        SRP_TO_VANILLA.put("srparasites:inf_pighead",       "minecraft:pig");
        SRP_TO_VANILLA.put("srparasites:inf_wolfhead",      "minecraft:wolf");
        SRP_TO_VANILLA.put("srparasites:inf_sheephead",     "minecraft:sheep");
        SRP_TO_VANILLA.put("srparasites:inf_villagerhead",  "minecraft:villager");
        SRP_TO_VANILLA.put("srparasites:inf_endermanhead",  "minecraft:enderman");
        SRP_TO_VANILLA.put("srparasites:inf_humanhead",     "minecraft:zombie");

        // ── Feral forms ───────────────────────────────────────────────
        SRP_TO_VANILLA.put("srparasites:fer_cow",           "minecraft:cow");
        SRP_TO_VANILLA.put("srparasites:fer_horse",         "minecraft:horse");
        SRP_TO_VANILLA.put("srparasites:fer_pig",           "minecraft:pig");
        SRP_TO_VANILLA.put("srparasites:fer_wolf",          "minecraft:wolf");
        SRP_TO_VANILLA.put("srparasites:fer_sheep",         "minecraft:sheep");
        SRP_TO_VANILLA.put("srparasites:fer_villager",      "minecraft:villager");
        SRP_TO_VANILLA.put("srparasites:fer_enderman",      "minecraft:enderman");
        SRP_TO_VANILLA.put("srparasites:fer_human",         "minecraft:zombie");
        SRP_TO_VANILLA.put("srparasites:fer_bear",          "minecraft:polar_bear");
        SRP_TO_VANILLA.put("srparasites:fer_mooshroom",     "minecraft:mooshroom");
        SRP_TO_VANILLA.put("srparasites:fer_rabbit",        "minecraft:rabbit");

        // ── Simulated (disguised) forms ───────────────────────────────
        SRP_TO_VANILLA.put("srparasites:sim_cow",           "minecraft:cow");
        SRP_TO_VANILLA.put("srparasites:sim_horse",         "minecraft:horse");
        SRP_TO_VANILLA.put("srparasites:sim_pig",           "minecraft:pig");
        SRP_TO_VANILLA.put("srparasites:sim_wolf",          "minecraft:wolf");
        SRP_TO_VANILLA.put("srparasites:sim_sheep",         "minecraft:sheep");
        SRP_TO_VANILLA.put("srparasites:sim_villager",      "minecraft:villager");
        SRP_TO_VANILLA.put("srparasites:sim_enderman",      "minecraft:enderman");
        SRP_TO_VANILLA.put("srparasites:sim_human",         "minecraft:zombie");
        SRP_TO_VANILLA.put("srparasites:sim_bear",          "minecraft:polar_bear");

        // ── InhooM / InhooS — obsługiwane przez snapshot lub size-fallback ──
        // Nie dodajemy tu stałego mapowania, bo Incomplete Forms mogą być
        // DOWOLNYM modem (np. ebwizardry:wizard → InhooM). Snapshot z Mixin
        // przechowuje pełne ID + NBT, więc fallback pig/cow jest używany
        // tylko gdy snapshot jest niedostępny.
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
     * Heurystyka dla InhooM/S (spawnInsider): ostatni snapshot = ostatnia transformacja.
     * Używać tylko gdy nie ma możliwości matchowania po kluczu (tj. entity nie ma parent ID).
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

    /** Czyści snapshoty starsze niż 3 sekundy (zabezpieczenie przed wyciekiem). */
    public static void cleanupStale() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(e -> now - e.getValue().timestamp > 3000L);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Identyfikacja SRP entity
    // ─────────────────────────────────────────────────────────────────

    /**
     * Zwraca true jeśli entity to pasożyt SRP który można przywrócić.
     * Pokrywa:
     * - EntityPInfected i podklasy (zarażone znane hosy)
     * - EntityInhooM / EntityInhooS (Incomplete Form — niezidentyfikowane hosty)
     * - wszystkie formy z tabeli SRP_TO_VANILLA (fer_, sim_, inf_)
     */
    public static boolean isSrpConvertedEntity(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) return false;
        // Najpierw sprawdź instanceOf — te klasy nie wymagają ResourceLocation lookup
        if (entity instanceof EntityPInfected
                || entity instanceof EntityInhooM
                || entity instanceof EntityInhooS) {
            return true;
        }
        // Dla pozostałych: sprawdź domain i tabelę
        ResourceLocation id = EntityList.getKey(entity);
        if (id == null || !"srparasites".equals(id.getResourceDomain())) return false;
        return SRP_TO_VANILLA.containsKey(id.toString());
    }

    /**
     * Zwraca true jeśli entity to Incomplete Form (InhooM lub InhooS).
     * Używane do logowania i debugowania.
     */
    public static boolean isIncompleteForm(Entity entity) {
        return entity instanceof EntityInhooM || entity instanceof EntityInhooS;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Resolve ID oryginalnego entity
    // ─────────────────────────────────────────────────────────────────

    /**
     * Zwraca ID vanilla/mod entity dla danego SRP entity.
     *
     * Priorytet:
     *   1. itOriginalId z getEntityData() — ustawiony przez ZhonyasEventHandler po snapshocie
     *      (dotyczy wszystkich form, w tym InhooM/S z modded hostami)
     *   2. EntityPInfected.getHost() — SRP przechowuje to samodzielnie dla znanych hostów
     *   3. Tabela SRP_TO_VANILLA (statyczne mapowanie dla standardowych form)
     *   4. Rozmiarowy fallback dla InhooM/S (ostatnia deska ratunku gdy snapshot niedostępny)
     *
     * Zwraca null jeśli brak rozwiązania (nie powinniśmy przywracać).
     */
    @Nullable
    public static String resolveVanillaId(EntityLivingBase entity) {
        NBTTagCompound data = entity.getEntityData();

        // 1. Snapshot zapisany przez ZhonyasEventHandler (najwyższy priorytet, pokrywa InhooM/S + modded)
        if (data.hasKey(KEY_ORIGINAL_ID, 8)) {
            String id = data.getString(KEY_ORIGINAL_ID);
            if (!id.isEmpty()) {
                InsaneTweaksMod.LOGGER.info("[IT][DEBUG] getIdForSrpEntity: Found KEY_ORIGINAL_ID: " + id);
                return id;
            }
        }

        // 2. EntityPInfected native host field
        if (entity instanceof EntityPInfected) {
            String host = ((EntityPInfected) entity).getHost();
            if (host != null && !host.isEmpty()) {
                InsaneTweaksMod.LOGGER.info("[IT][DEBUG] getIdForSrpEntity: Found EntityPInfected host: " + host);
                return host;
            }
        }

        // 3. Statyczna tabela mapowania
        ResourceLocation srpId = EntityList.getKey(entity);
        if (srpId != null) {
            String mapped = SRP_TO_VANILLA.get(srpId.toString());
            if (mapped != null) {
                InsaneTweaksMod.LOGGER.info("[IT][DEBUG] getIdForSrpEntity: Found static map mapping: " + mapped);
                return mapped;
            }
        }

        // 4. Rozmiarowy fallback (InhooM/S bez snapshotu — niezidentyfikowany host)
        if (entity instanceof EntityInhooS) {
            InsaneTweaksMod.LOGGER.info("[IT][DEBUG] getIdForSrpEntity: Fallback EntityInhooS -> minecraft:pig");
            return "minecraft:pig";
        }
        if (entity instanceof EntityInhooM) {
            InsaneTweaksMod.LOGGER.info("[IT][DEBUG] getIdForSrpEntity: Fallback EntityInhooM -> minecraft:cow");
            return "minecraft:cow";
        }

        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] getIdForSrpEntity: Could not resolve ID returning null.");
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Przywracanie entity
    // ─────────────────────────────────────────────────────────────────

    /**
     * Wykonuje pełne przywrócenie:
     * 1. Tworzy nowe entity z danym vanillaId (może być modded, np. ebwizardry:wizard).
     * 2. Jeśli SRP entity ma zapisane itOriginalNBT — aplikuje je (pełny restore z NBT).
     *    Health ustawiana jest PO readFromNBT, żeby używać rzeczywistego MaxHealth.
     * 3. W przeciwnym razie tworzy świeżą instancję z pozycją i imieniem.
     * 4. Usuwa SRP entity, spawnuje przywrócone.
     * 5. Aplikuje 30-sekundową ochronę anty-asymilacji (EPEL_E + clearCoth).
     */
    @Nullable
    public static Entity performRestore(EntityLivingBase srpEntity, World world, String vanillaId) {
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] performRestore BEGUN for target id: " + vanillaId);
        ResourceLocation rl;
        try {
            rl = new ResourceLocation(vanillaId);
        } catch (Exception ex) {
            InsaneTweaksMod.LOGGER.warn("[IT][DEBUG] performRestore Failed to parse resource location: " + vanillaId);
            return null;
        }

        NBTTagCompound data = srpEntity.getEntityData();
        boolean hasOriginalNbt = data.hasKey(KEY_ORIGINAL_NBT, 10);
        
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] performRestore: entityData holds keys: " + data.getKeySet());
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] performRestore: hasOriginalNbt = " + hasOriginalNbt);

        Entity restored;

        if (hasOriginalNbt) {
            InsaneTweaksMod.LOGGER.info("[IT][DEBUG] performRestore: Taking Path 1: FULL NBT RESTORE");
            // ── Ścieżka 1: Pełny NBT restore ─────────────────────────
            restored = EntityList.createEntityByIDFromName(rl, world);
            if (restored == null) return null;

            NBTTagCompound originalNbt = data.getCompoundTag(KEY_ORIGINAL_NBT).copy();

            // Usuń pole "id" z NBT — Entity.readFromNBT() w 1.12.2 weryfikuje id
            // i może pominąć wczytywanie danych (w tym villager Offers/Career)
            // jeśli id w NBT nie pasuje do klasy entity. Bez tego pola
            // readEntityFromNBT() jest wywoływana bezwarunkowo.
            originalNbt.removeTag("id");

            // Nadpisz pozycję — entity ma się pojawić w miejscu SRP entity, nie oryginału
            NBTTagList posList = new NBTTagList();
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(srpEntity.posX));
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(srpEntity.posY));
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(srpEntity.posZ));
            originalNbt.setTag("Pos", posList);

            // Usuń ślady stanu śmierci/infekcji
            originalNbt.removeTag("DeathTime");
            originalNbt.removeTag("HurtTime");
            originalNbt.removeTag("FallDistance");
            // Ustaw Health tymczasowo na 1.0f — po readFromNBT (które załaduje atrybuty)
            // poprawiamy na prawdziwe 90% MaxHealth poniżej.
            originalNbt.setFloat("Health", 1.0f);

            try {
                // readFromNBT → readEntityFromNBT: wczytuje Offers/Career (villager),
                // owner/tamed (wilk/kot), modded entity data, CustomName, atrybuty itp.
                restored.readFromNBT(originalNbt);
            } catch (Exception ex) {
                InsaneTweaksMod.LOGGER.warn(
                    "[IT][Restore] readFromNBT failed for '{}' ({}): {}",
                    vanillaId, restored.getClass().getSimpleName(), ex.getMessage());
                // Continue: entity zostaje spawnowana bez pełnego NBT (partial restore)
            }

            // ── Ustaw Health PO readFromNBT, używając rzeczywistego MaxHealth ──
            // readFromNBT załadowało już AttributeMap z NBT (w tym custom MaxHealth).
            if (restored instanceof EntityLivingBase) {
                EntityLivingBase restoredLiving = (EntityLivingBase) restored;
                float maxHp = restoredLiving.getMaxHealth();
                restoredLiving.setHealth(Math.max(1.0f, maxHp * 0.9f));
            }

            // Finalnie: ustaw pozycję z pozycji SRP entity (readFromNBT mogło nadpisać)
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

            // Skopiuj imię jeśli SRP entity je miało
            if (srpEntity.hasCustomName() && restored instanceof EntityLivingBase) {
                ((EntityLivingBase) restored).setCustomNameTag(srpEntity.getCustomNameTag());
                ((EntityLivingBase) restored).setAlwaysRenderNameTag(srpEntity.getAlwaysRenderNameTag());
            }
        }

        // Usuń SRP entity i spawnuj przywrócone
        srpEntity.setDead();
        world.spawnEntity(restored);

        // ── Krok 5: Nałóż 30-sekundową ochronę anty-asymilacji ───────
        if (restored instanceof EntityLivingBase) {
            applyRestorationProtection((EntityLivingBase) restored);
        }

        return restored;
    }

    /**
     * Nakłada zabezpieczenie na właśnie przywrócone entity:
     * - clearCoth: usuwa aktywny COTH_E (Call of the Hive) jeśli jakiś pozostał
     * - EPEL_E (600 ticki = 30 sekund): blokuje ponowną asymilację/transformację przez SRP
     *
     * Identyczna logika jak dla EntitySummonedCreature — użyta świadomie
     * bo SummonInfectionSafetyHelper.clearCoth() jest publiczna.
     */
    public static void applyRestorationProtection(EntityLivingBase entity) {
        if (entity == null) return;
        // Usuń COTH_E jeśli aktywny (efekt asymilacji)
        SummonInfectionSafetyHelper.clearCoth(entity);
        // Nałóż EPEL_E na 30 sekund (600 ticki), niewidoczne, brak cząsteczek
        entity.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E, RESTORATION_EPEL_DURATION, 0, false, false));
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
