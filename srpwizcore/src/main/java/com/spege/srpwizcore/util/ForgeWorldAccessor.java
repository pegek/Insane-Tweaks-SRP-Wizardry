package com.spege.srpwizcore.util;

/**
 * Duck-type accessor for the {@code forgeWorld} field in
 * {@code com.pg85.otg.forge.generator.structure.OTGMapGenStructure}.
 *
 * <p>Applied via {@code MixinOTGMapGenStructure} to expose the package-private field
 * to sibling mixins targeting {@code OTGNetherFortressGen} and {@code OTGMineshaftGen}
 * (which cannot {@code @Shadow} a field from a superclass).
 *
 * <p>Return type is {@code Object} to avoid hard-linking OTG types from call sites
 * that may load without OTG on the classpath. Callers must cast to
 * {@code com.pg85.otg.forge.world.ForgeWorld} after a null check.
 *
 * <p><b>Important:</b> This interface lives in {@code util/}, not in the mixin package —
 * duck-type accessor interfaces referenced from mixin code must be outside
 * {@code com.spege.srpwizcore.mixins.*} to avoid {@code IllegalClassLoadError}.
 */
public interface ForgeWorldAccessor {

    /** Returns the {@code ForgeWorld} instance, or {@code null} if not set. */
    Object insanetweaks$getForgeWorld();
}
