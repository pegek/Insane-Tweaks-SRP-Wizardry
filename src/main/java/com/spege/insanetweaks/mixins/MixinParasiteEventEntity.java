package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.util.ParasiteEventEntity;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.util.SrpOriginSnapshotHelper;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * Mixin przechwytujący transformacje SRP w ParasiteEventEntity.
 *
 * Strategia (HEAD + INVOKE):
 *   1. @At(HEAD) na obu metodach: zapisuje oryginalne entity do ThreadLocal.
 *   2. @At(INVOKE world.removeEntity) na obu metodach: pobiera z ThreadLocal
 *      i wywołuje doCapture() — bo removeEntity jest wywoływane na parametrze.
 *
 * Pokrywa WSZYSTKIE ścieżki transformacji:
 *   - spawnInsider() → InhooM / InhooS (nieznany host)
 *   - convertEntity() → EntityPInfected (znany host, ale tracimy pełne NBT:
 *       imię, tamed, owner UUID, atrybuty itp.)
 *
 * require=0 na wszystkich injektach: brak crasha gdy SRP zmieni sygnatury.
 */
@Mixin(value = ParasiteEventEntity.class, remap = false)
public abstract class MixinParasiteEventEntity {

    /**
     * ThreadLocal przechowujący referencję do entity które jest właśnie
     * transformowane. Ustawiany na HEAD metody, kasowany na RETURN.
     * Bezpieczny dla wielowątkowości (każdy tick-thread ma swoją kopię).
     */
    @Unique
    private static final ThreadLocal<EntityLivingBase> INSANETWEAKS$PENDING_ENTITY =
        new ThreadLocal<>();

    // ─────────────────────────────────────────────────────────────────
    //  spawnInsider — HEAD: zapisz entity do ThreadLocal
    // ─────────────────────────────────────────────────────────────────

    @Inject(
        method = "spawnInsider(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;)V",
        at = @At("HEAD"),
        require = 0
    )
    private static void insanetweaks$spawnInsiderHead(
            EntityLivingBase entity, World world, NBTTagCompound tags, CallbackInfo ci) {
        if (entity != null && !world.isRemote) {
            INSANETWEAKS$PENDING_ENTITY.set(entity);
        }
    }

    /** Wykonaj snapshot tuż przed pierwszym removeEntity w spawnInsider. */
    @Inject(
        method = "spawnInsider(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;)V",
        at = @At(
            value  = "INVOKE",
            target = "Lnet/minecraft/world/World;func_72900_e(Lnet/minecraft/entity/Entity;)V",
            ordinal = 0
        ),
        require = 0
    )
    private static void insanetweaks$spawnInsiderBeforeRemove(
            EntityLivingBase entity, World world, NBTTagCompound tags, CallbackInfo ci) {
        insanetweaks$doCapture(INSANETWEAKS$PENDING_ENTITY.get());
    }

    /** Dodatkowy ordinal=1 (cap overflow path). */
    @Inject(
        method = "spawnInsider(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;)V",
        at = @At(
            value  = "INVOKE",
            target = "Lnet/minecraft/world/World;func_72900_e(Lnet/minecraft/entity/Entity;)V",
            ordinal = 1
        ),
        require = 0
    )
    private static void insanetweaks$spawnInsiderBeforeRemove2(
            EntityLivingBase entity, World world, NBTTagCompound tags, CallbackInfo ci) {
        insanetweaks$doCapture(INSANETWEAKS$PENDING_ENTITY.get());
    }

    /** Wyczyść ThreadLocal na wyjściu z spawnInsider. */
    @Inject(
        method = "spawnInsider(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;)V",
        at = @At("RETURN"),
        require = 0
    )
    private static void insanetweaks$spawnInsiderReturn(
            EntityLivingBase entity, World world, NBTTagCompound tags, CallbackInfo ci) {
        INSANETWEAKS$PENDING_ENTITY.remove();
    }

    // ─────────────────────────────────────────────────────────────────
    //  convertEntity — HEAD: zapisz entity do ThreadLocal
    // ─────────────────────────────────────────────────────────────────

    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At("HEAD"),
        require = 0
    )
    private static void insanetweaks$convertEntityHead(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        if (entityin != null && entityin.world != null && !entityin.world.isRemote) {
            INSANETWEAKS$PENDING_ENTITY.set(entityin);
        }
    }

    /**
     * Wstrzyknij się PRZED pierwszym world.removeEntity w convertEntity.
     * SRP woła removeEntity po ustawieniu EntityPInfected/innego spawna.
     * Ordinal 0 = pierwsza ścieżka (normalny infected spawn).
     */
    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At(
            value  = "INVOKE",
            target = "Lnet/minecraft/world/World;func_72900_e(Lnet/minecraft/entity/Entity;)V",
            ordinal = 0
        ),
        require = 0
    )
    private static void insanetweaks$convertEntityBeforeRemove0(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        insanetweaks$doCapture(INSANETWEAKS$PENDING_ENTITY.get());
    }

    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At(
            value  = "INVOKE",
            target = "Lnet/minecraft/world/World;func_72900_e(Lnet/minecraft/entity/Entity;)V",
            ordinal = 1
        ),
        require = 0
    )
    private static void insanetweaks$convertEntityBeforeRemove1(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        insanetweaks$doCapture(INSANETWEAKS$PENDING_ENTITY.get());
    }

    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At(
            value  = "INVOKE",
            target = "Lnet/minecraft/world/World;func_72900_e(Lnet/minecraft/entity/Entity;)V",
            ordinal = 2
        ),
        require = 0
    )
    private static void insanetweaks$convertEntityBeforeRemove2(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        insanetweaks$doCapture(INSANETWEAKS$PENDING_ENTITY.get());
    }

    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At(
            value  = "INVOKE",
            target = "Lnet/minecraft/world/World;func_72900_e(Lnet/minecraft/entity/Entity;)V",
            ordinal = 3
        ),
        require = 0
    )
    private static void insanetweaks$convertEntityBeforeRemove3(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        insanetweaks$doCapture(INSANETWEAKS$PENDING_ENTITY.get());
    }

    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At(
            value  = "INVOKE",
            target = "Lnet/minecraft/world/World;func_72900_e(Lnet/minecraft/entity/Entity;)V",
            ordinal = 4
        ),
        require = 0
    )
    private static void insanetweaks$convertEntityBeforeRemove4(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        insanetweaks$doCapture(INSANETWEAKS$PENDING_ENTITY.get());
    }

    /** Wyczyść ThreadLocal na wyjściu z convertEntity. */
    @Inject(
        method = "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)V",
        at = @At("RETURN"),
        require = 0
    )
    private static void insanetweaks$convertEntityReturn(
            EntityLivingBase entityin, NBTTagCompound tags, boolean ignoreKey,
            String[] list, CallbackInfo ci) {
        INSANETWEAKS$PENDING_ENTITY.remove();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Wspólna logika przechwycenia
    // ─────────────────────────────────────────────────────────────────

    /**
     * Zapisuje pełen NBT dump entity do SrpOriginSnapshotHelper.
     * Idempotentne: jeśli ten sam entityId już jest w mapie, pomija.
     */
    @Unique
    private static void insanetweaks$doCapture(EntityLivingBase entity) {
        if (entity == null || entity.world == null || entity.world.isRemote) return;
        // Idempotencja: nie zapisuj dwa razy tego samego entity
        if (SrpOriginSnapshotHelper.hasPending(entity.getEntityId())) return;

        try {
            ResourceLocation id = EntityList.getKey(entity);
            if (id == null) return;

            // Pełny NBT dump
            NBTTagCompound fullNbt = new NBTTagCompound();
            entity.writeToNBT(fullNbt);

            // Sanityzacja stanu życia żeby przywrócone entity było zdrowe
            fullNbt.removeTag("DeathTime");
            fullNbt.setFloat("Health", Math.min(entity.getMaxHealth(), entity.getMaxHealth() * 0.75f));
            fullNbt.removeTag("HurtTime");
            fullNbt.removeTag("FallDistance");
            // Resetuj COTH immunity counter
            fullNbt.removeTag("srpcothimmunity");

            // Wyczyść SRP efekty (COTH, EPEL itp.)
            insanetweaks$cleanSrpEffects(fullNbt);

            SrpOriginSnapshotHelper.savePending(entity.getEntityId(), id.toString(), fullNbt);

            InsaneTweaksMod.LOGGER.debug(
                "[IT][Snapshot] Captured '{}' (id={}) before SRP conversion",
                id, entity.getEntityId());
        } catch (Exception ex) {
            InsaneTweaksMod.LOGGER.warn(
                "[IT][Snapshot] Failed to capture entity: {}", ex.getMessage());
        }
    }

    /**
     * Usuwa SRP potion effecty z listy aktywnych efektów w NBT.
     * SRP efekty mają ID > 100 — vanilla efekty są 1-32, mody typowo < 100.
     */
    @Unique
    private static void insanetweaks$cleanSrpEffects(NBTTagCompound nbt) {
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
