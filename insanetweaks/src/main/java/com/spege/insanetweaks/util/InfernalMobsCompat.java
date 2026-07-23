package com.spege.insanetweaks.util;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.entity.EntityLivingBase;

/**
 * Reflection bridge to InfernalMobs (atomicstryker.infernalmobs). NOT a mixin.
 * Shared by the Infernal Crown artefact (forceInfernal) and the spectral-dust
 * drop handler (isRare). All methods fail soft when InfernalMobs is absent.
 */
public final class InfernalMobsCompat {

    private static java.lang.reflect.Method instanceMethod;
    private static java.lang.reflect.Method addModifiersMethod;
    private static java.lang.reflect.Method isRareMethod;

    private InfernalMobsCompat() {
    }

    /** Forces the entity to become an infernal elite via addEntityModifiersByString
     *  (bypasses InfernalMobs' normal IEntityOwnable spawn restriction). */
    public static boolean forceInfernal(EntityLivingBase entity) {
        try {
            Object core = getCore();
            if (core == null) return false;

            if (isRare(entity)) {
                return true;
            }

            if (addModifiersMethod == null) {
                addModifiersMethod = core.getClass().getDeclaredMethod(
                        "addEntityModifiersByString", EntityLivingBase.class, String.class);
                addModifiersMethod.setAccessible(true);
            }

            addModifiersMethod.invoke(core, entity, "Regen Sprint");
            return isRare(entity);

        } catch (Exception e) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] InfernalMobs direct API call failed", e);
            return false;
        }
    }

    /** True when InfernalMobs considers the entity an infernal (elite) mob. */
    public static boolean isRare(EntityLivingBase entity) {
        try {
            if (isRareMethod == null) {
                Class<?> clazz = Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore");
                isRareMethod = clazz.getDeclaredMethod("getIsRareEntity", EntityLivingBase.class);
                isRareMethod.setAccessible(true);
            }
            Object result = isRareMethod.invoke(null, entity);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object getCore() throws Exception {
        if (instanceMethod == null) {
            Class<?> clazz = Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore");
            instanceMethod = clazz.getDeclaredMethod("instance");
            instanceMethod.setAccessible(true);
        }
        return instanceMethod.invoke(null);
    }
}
