package com.spege.insanetweaks.events;

import java.util.List;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooM;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooS;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.util.SrpOriginSnapshotHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Event handler dla systemu snapshotów Zhonyas Hourglass.
 *
 * Rejestrowany tylko gdy SRP ("scapeandrunparasites") jest obecne.
 *
 * MECHANIZM CAPTURE — czysto event-based, bez Mixina:
 *
 *   SRP wywołuje transformację w tej kolejności:
 *     1. world.spawnEntity(noweEntity)   ← EntityJoinWorldEvent odpala TU
 *     2. world.removeEntity(stareEntity) ← oryginał usuwany POTEM
 *
 *   W kroku 1, gdy EntityJoinWorldEvent dla InhooM/Infected odpala się,
 *   oryginalne entity NADAL ISTNIEJE w świecie na tej samej pozycji.
 *   Możemy je znaleźć przez world.getEntitiesWithinAABB() i zabrać cały NBT.
 *
 *   Nie wymaga Mixina. Nie wymaga modyfikacji SRP. Odporna na class loading order.
 */
public class ZhonyasEventHandler {

    /**
     * Zasięg szukania oryginalnego entity przy transformacji SRP.
     * SRP spawni InhooM/Infected w tej samej pozycji co oryginał,
     * więc mały promień wystarczy. Zbyt duży mógłby złapać inne entity.
     */
    private static final double ORIGIN_SEARCH_RADIUS = 1.5D;

    /**
     * Przechwytuje oryginalne entity i zapisuje jego NBT do entityData nowego SRP entity.
     *
     * Logika:
     *   1. Nowy InhooM/InhooS/PInfected dołącza do świata.
     *   2. Na tej samej pozycji szukamy żywego, nie-SRP entity (oryginał).
     *   3. Zapisujemy jego NBT (ResourceLocation ID + pełny NBT dump) do entityData
     *      nowego SRP entity pod kluczem KEY_ORIGINAL_ID i KEY_ORIGINAL_NBT.
     *   4. Zhonyas Hourglass czyta te klucze przy przywracaniu.
     *
     * EntityPInfected (znane hosty, np. inf_villager):
     *   SRP zapisuje host ID w "parasitehost" NBT. Nasz snapshot dodaje pełny NBT
     *   (trades villagera, taming wilka, owner UUID) których SRP nie zachowuje.
     *
     * EntityInhooM/S (Incomplete Form):
     *   Host nie ma odpowiednika w SRP (np. ebwizardry:wizard, modded mobs).
     *   Bez naszego snapshotu jedyną informacją jest rozmiar → fallback pig/cow.
     *   Z naszym snapshotem: pełne ID + NBT = perfekcyjne odtworzenie dowolnego moba.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) return;
        Entity entity = event.getEntity();

        boolean isInhoo    = entity instanceof EntityInhooM || entity instanceof EntityInhooS;
        boolean isInfected = entity instanceof EntityPInfected;
        if (!isInhoo && !isInfected) return;

        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] ZhonyasEventHandler TRIGGERED for entity: " + entity.getClass().getSimpleName() + " at " + entity.getPosition());

        // Szukamy oryginalnego entity na pozycji nowego SRP entity.
        // W tym momencie SRP jeszcze go nie usunęło — world.removeEntity()
        // jest wywoływane PRZEZ SRP PO world.spawnEntity().
        EntityLivingBase original = findOriginalEntityAt(
                event.getWorld(), entity, ORIGIN_SEARCH_RADIUS);

        if (original == null) {
            InsaneTweaksMod.LOGGER.info("[IT][DEBUG] findOriginalEntityAt returned NULL. Checking Mixin fallback...");
            // Fallback: sprawdź czy Mixin zdążył zapisać snapshot do PENDING
            SrpOriginSnapshotHelper.OriginalSnapshot pending = SrpOriginSnapshotHelper.popMostRecent();
            if (pending != null) {
                applySnapshotToSrpEntity((EntityLivingBase) entity, pending.resourceId, pending.fullNbt);
                InsaneTweaksMod.LOGGER.info(
                    "[IT][DEBUG][Zhonyas] Fallback Mixin snapshot applied: '{}' -> {}",
                    pending.resourceId, entity.getClass().getSimpleName());
            } else {
                InsaneTweaksMod.LOGGER.info(
                    "[IT][DEBUG][Zhonyas] No original entity found near {} (searching {}r) - no snapshot",
                    entity.getClass().getSimpleName(), ORIGIN_SEARCH_RADIUS);
            }
            return;
        }

        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] Original entity FOUND!: " + original.getName() + " (class: " + original.getClass().getSimpleName() + ")");

        // Pobierz ResourceLocation ID oryginalnego entity
        ResourceLocation originalId = EntityList.getKey(original);
        if (originalId == null) {
            InsaneTweaksMod.LOGGER.warn(
                "[IT][DEBUG][Zhonyas] Could not get key for {}, skipping snapshot",
                original.getClass().getName());
            return;
        }

        // Pełny NBT dump oryginalnego entity (jeszcze żywego!)
        NBTTagCompound fullNbt = new NBTTagCompound();
        original.writeToNBT(fullNbt);
        
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] Original NBT serialized successfully. Keys: " + fullNbt.getKeySet());

        // Sanityzacja: usuń ślady infekcji i death state
        fullNbt.removeTag("DeathTime");
        fullNbt.removeTag("HurtTime");
        fullNbt.removeTag("FallDistance");
        fullNbt.removeTag("srpcothimmunity");
        // Health: ustaw 75% max (entity było zdrowe zanim zostało zainfekowane)
        fullNbt.setFloat("Health", original.getMaxHealth() * 0.75f);
        // Wyczyść SRP potion efecty z NBT
        cleanSrpEffects(fullNbt);

        applySnapshotToSrpEntity((EntityLivingBase) entity, originalId.toString(), fullNbt);

        NBTTagCompound checkData = entity.getEntityData();
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] Snapshot applied to entityData. Keys in entityData: " + checkData.getKeySet());

        InsaneTweaksMod.LOGGER.info(
            "[IT][DEBUG][Zhonyas] Snapshot captured: '{}' ({}) -> {} (id={})",
            originalId, original.getName(),
            entity.getClass().getSimpleName(), entity.getEntityId());
    }

    /**
     * Szuka oryginalnego (nie-SRP) żywego entity w pobliżu nowego SRP entity.
     *
     * Filtruje:
     * - martwe entity
     * - SRP entity (EntityParasiteBase i podklasy)
     * - samo SRP entity które właśnie dołączyło
     *
     * W przypadku wielu kandydatów, bierze najbliższe. W normalnych warunkach
     * SRP spawni InhooM/Infected dokładnie w tym samym miejscu co oryginał,
     * więc kandydat powinien być jeden i blisko 0 odległości.
     *
     * @param world  świat
     * @param srpEntity nowe SRP entity (referencja do wykluczenia)
     * @param radius promień szukania
     * @return oryginalne entity lub null
     */
    private static EntityLivingBase findOriginalEntityAt(
            net.minecraft.world.World world, Entity srpEntity, double radius) {

        AxisAlignedBB box = srpEntity.getEntityBoundingBox().grow(radius);
        List<EntityLivingBase> candidates = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG] findOriginalEntityAt: AABB bounds: " + box + " | Found raw candidates count: " + candidates.size());

        EntityLivingBase best = null;
        double bestDist = Double.MAX_VALUE;

        for (EntityLivingBase candidate : candidates) {
            // Wyklucz martwe entity
            if (!candidate.isEntityAlive()) continue;
            // Wyklucz SRP entity (pasożyty)
            if (candidate instanceof EntityParasiteBase) continue;
            // Wyklucz graczy — SRP nie konwertuje graczy przez spawnInsider/convertEntity
            if (candidate instanceof net.minecraft.entity.player.EntityPlayer) continue;
            // Wyklucz same siebie
            if (candidate == srpEntity) continue;

            double dist = candidate.getDistanceSq(srpEntity.posX, srpEntity.posY, srpEntity.posZ);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Zapisuje snapshot do entityData SRP entity.
     * EntityData jest serializowana do world save — dane przeżywają restart serwera.
     */
    private static void applySnapshotToSrpEntity(EntityLivingBase srpEntity,
            String originalId, NBTTagCompound originalNbt) {
        NBTTagCompound data = srpEntity.getEntityData();
        data.setString(SrpOriginSnapshotHelper.KEY_ORIGINAL_ID, originalId);
        data.setTag(SrpOriginSnapshotHelper.KEY_ORIGINAL_NBT, originalNbt.copy());
    }

    /**
     * Usuwa SRP potion effecty z listy ActiveEffects w NBT.
     * SRP efekty mają ID >= 100. Vanilla: 1-32. Mody typowo < 100.
     */
    private static void cleanSrpEffects(NBTTagCompound nbt) {
        if (!nbt.hasKey("ActiveEffects", 9)) return;
        net.minecraft.nbt.NBTTagList effectList = nbt.getTagList("ActiveEffects", 10);
        net.minecraft.nbt.NBTTagList cleaned    = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < effectList.tagCount(); i++) {
            NBTTagCompound effect = effectList.getCompoundTagAt(i);
            int pid = effect.getByte("Id") & 0xFF;
            if (pid < 100) {
                cleaned.appendTag(effect);
            }
        }
        nbt.setTag("ActiveEffects", cleaned);
    }
}
