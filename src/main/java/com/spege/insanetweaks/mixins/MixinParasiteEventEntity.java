package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooM;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooS;
import com.dhanantry.scapeandrunparasites.util.ParasiteEventEntity;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.util.SrpOriginSnapshotHelper;
import com.spege.insanetweaks.util.SrpOriginSnapshotHelper.OriginalSnapshot;
import com.spege.insanetweaks.util.SrpWizardryAssimilationHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * Mixin dla systemu snapshotów Zhonyas Hourglass.
 *
 * === ANALIZA TIMINGU (z kodu SRP) ===
 *
 * SRP w spawnInsider() i convertEntity() używa world.func_72838_d() (addEntity),
 * NIE world.spawnEntity(). Forge patchuje tylko spawnEntity() do EntityJoinWorldEvent,
 * więc ZhonyasEventHandler nigdy nie widzi nowych entity SRP.
 * Kolejność: removeEntity(stare) → addEntity(nowe) — stare entity nieobecne przy spawnie.
 *
 * ROZWIĄZANIE: Wstrzyknięcie bezpośrednio w Mixinie:
 *   HEAD  → captureEntity() → zapis do ThreadLocal (entity żywe)
 *   RETURN → findLatestSrpEntity() → applySnapshot() do entityData nowego entity
 *
 * UWAGA: Klasy wewnętrzne (@Unique static class) NIE mogą być definiowane w pakiecie
 * Mixin — MixinTransformer rzuca IllegalClassLoadError. Dlatego używamy
 * SrpOriginSnapshotHelper.OriginalSnapshot z pakietu util (poza pakietem mixin).
 *
 * require=0: nie crasha gdy SRP zmieni sygnatury.
 */
@Mixin(value = ParasiteEventEntity.class, remap = false)
public abstract class MixinParasiteEventEntity {

    /**
     * ThreadLocal trzymający snapshot oryginału między HEAD a RETURN.
     * Używamy SrpOriginSnapshotHelper.OriginalSnapshot (klasa poza pakietem mixin!).
     * Bezpieczny wielowątkowo.
     */
    @Unique
    private static final ThreadLocal<OriginalSnapshot> INSANETWEAKS$CAPTURE = new ThreadLocal<>();

    // ─────────────────────────────────────────────────────────────────
    //  spawnInsider — HEAD: capture, RETURN: apply
    // ─────────────────────────────────────────────────────────────────

    @Inject(
        method = "spawnInsider(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;)V",
        at = @At("HEAD"),
        require = 0
    )
    private static void insanetweaks$spawnInsiderHead(
            EntityLivingBase entity, World world, NBTTagCompound tags, CallbackInfo ci) {
        if (entity == null || world.isRemote) return;
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG][Mixin] spawnInsiderHead: capturing " + entity.getClass().getSimpleName());
        INSANETWEAKS$CAPTURE.set(insanetweaks$captureEntity(entity));
    }

    @Inject(
        method = "spawnInsider(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;)V",
        at = @At("RETURN"),
        require = 0
    )
    private static void insanetweaks$spawnInsiderReturn(
            EntityLivingBase entity, World world, NBTTagCompound tags, CallbackInfo ci) {
        OriginalSnapshot capture = INSANETWEAKS$CAPTURE.get();
        INSANETWEAKS$CAPTURE.remove();
        if (capture == null || world == null || world.isRemote) return;

        Entity newEntity = insanetweaks$findLatestInhoo(world);
        if (newEntity == null) {
            InsaneTweaksMod.LOGGER.info("[IT][DEBUG][Mixin] spawnInsiderReturn: could not find new InhooM/S in world");
            return;
        }
        insanetweaks$applySnapshot(newEntity, capture);
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG][Mixin] spawnInsiderReturn: applied '{}' -> {}",
            capture.resourceId, newEntity.getClass().getSimpleName());
    }

    // ─────────────────────────────────────────────────────────────────
    //  convertEntity — HEAD: capture, RETURN: apply
    // ─────────────────────────────────────────────────────────────────

    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void insanetweaks$convertEntityHead(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        if (entityin == null || entityin.world == null || entityin.world.isRemote) return;
        if (SrpWizardryAssimilationHelper.tryConvertSupportedWizard(entityin, tags)) {
            ci.cancel();
            return;
        }
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG][Mixin] convertEntityHead: capturing " + entityin.getClass().getSimpleName());
        INSANETWEAKS$CAPTURE.set(insanetweaks$captureEntity(entityin));
    }

    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At("RETURN"),
        require = 0
    )
    private static void insanetweaks$convertEntityReturn(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        OriginalSnapshot capture = INSANETWEAKS$CAPTURE.get();
        INSANETWEAKS$CAPTURE.remove();
        if (capture == null || entityin == null || entityin.world == null || entityin.world.isRemote) return;

        Entity newEntity = insanetweaks$findLatestSrpEntity(entityin.world);
        if (newEntity == null) {
            InsaneTweaksMod.LOGGER.info("[IT][DEBUG][Mixin] convertEntityReturn: could not find new SRP entity in world");
            return;
        }
        insanetweaks$applySnapshot(newEntity, capture);
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG][Mixin] convertEntityReturn: applied '{}' -> {}",
            capture.resourceId, newEntity.getClass().getSimpleName());
    }

    // ─────────────────────────────────────────────────────────────────
    //  Pomocnicze metody
    // ─────────────────────────────────────────────────────────────────

    @Unique
    private static OriginalSnapshot insanetweaks$captureEntity(EntityLivingBase entity) {
        try {
            ResourceLocation id = EntityList.getKey(entity);
            if (id == null) {
                InsaneTweaksMod.LOGGER.warn("[IT][DEBUG][Mixin] captureEntity: null key for {}", entity.getClass().getSimpleName());
                return null;
            }
            NBTTagCompound nbt = new NBTTagCompound();
            entity.writeToNBT(nbt);
            nbt.removeTag("DeathTime");
            nbt.removeTag("HurtTime");
            nbt.removeTag("FallDistance");
            nbt.removeTag("srpcothimmunity");
            nbt.setFloat("Health", entity.getMaxHealth() * 0.75f);
            insanetweaks$cleanSrpEffects(nbt);
            InsaneTweaksMod.LOGGER.info("[IT][DEBUG][Mixin] captureEntity: '{}' NBT keys count: {}", id, nbt.getKeySet().size());
            return new OriginalSnapshot(id.toString(), nbt, System.currentTimeMillis());
        } catch (Exception ex) {
            InsaneTweaksMod.LOGGER.warn("[IT][DEBUG][Mixin] captureEntity failed: {}", ex.getMessage());
            return null;
        }
    }

    @Unique
    private static void insanetweaks$applySnapshot(Entity target, OriginalSnapshot capture) {
        NBTTagCompound data = target.getEntityData();
        data.setString(SrpOriginSnapshotHelper.KEY_ORIGINAL_ID, capture.resourceId);
        data.setTag(SrpOriginSnapshotHelper.KEY_ORIGINAL_NBT, capture.fullNbt.copy());
        InsaneTweaksMod.LOGGER.info("[IT][DEBUG][Mixin] applySnapshot done. entityData keys: {}", data.getKeySet());
    }

    /** Iteruje od końca loadedEntityList — nowo addEntity() jest ostatnim elementem. */
    @Unique
    private static Entity insanetweaks$findLatestInhoo(World world) {
        java.util.List<Entity> list = world.loadedEntityList;
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = list.get(i);
            if (e instanceof EntityInhooM || e instanceof EntityInhooS) return e;
        }
        return null;
    }

    @Unique
    private static Entity insanetweaks$findLatestSrpEntity(World world) {
        java.util.List<Entity> list = world.loadedEntityList;
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = list.get(i);
            if (e instanceof EntityPInfected || e instanceof EntityInhooM || e instanceof EntityInhooS) return e;
        }
        return null;
    }

    @Unique
    private static void insanetweaks$cleanSrpEffects(NBTTagCompound nbt) {
        if (!nbt.hasKey("ActiveEffects", 9)) return;
        net.minecraft.nbt.NBTTagList effectList = nbt.getTagList("ActiveEffects", 10);
        net.minecraft.nbt.NBTTagList cleaned    = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < effectList.tagCount(); i++) {
            NBTTagCompound effect = effectList.getCompoundTagAt(i);
            int pid = effect.getByte("Id") & 0xFF;
            if (pid < 100) cleaned.appendTag(effect);
        }
        nbt.setTag("ActiveEffects", cleaned);
    }
}
