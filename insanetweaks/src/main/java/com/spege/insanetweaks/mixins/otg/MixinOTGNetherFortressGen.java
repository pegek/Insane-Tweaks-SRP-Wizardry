package com.spege.insanetweaks.mixins.otg;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pg85.otg.common.LocalBiome;
import com.pg85.otg.forge.world.ForgeWorld;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.ForgeWorldAccessor;

/**
 * Null-biome guard for {@code OTGNetherFortressGen.func_75047_a} (canSpawnStructureAtCoords).
 *
 * <p>In OTG dimensions that use virtual biomes (all biomes defined via {@code ReplaceToBiomeName},
 * e.g. the Underneath dim 150), {@code ForgeWorld.getBiome(x,z)} can return {@code null} when the
 * internal OTG biome-id lookup misses the registered pool. The original method dereferences this
 * without a null check at line 62: {@code biome.getBiomeConfig().netherFortressesEnabled}, causing
 * {@code NullPointerException: Cannot invoke "LocalBiome.getBiomeConfig()" because "biome" is null}.
 *
 * <p>We inject at HEAD and obtain {@code ForgeWorld} via the {@link ForgeWorldAccessor} duck
 * interface (applied to the superclass {@code OTGMapGenStructure} by
 * {@link MixinOTGMapGenStructure}). If the biome lookup returns null, we return {@code false}
 * (= structure cannot spawn here), matching the vanilla contract of {@code canSpawnStructureAtCoords}.
 *
 * <p>Confirmed via {@code javap -p -c -l} on {@code OpenTerrainGenerator-1.12.2-v9.7.jar} (2026-07-20).
 * Gated on {@link ModConfig#otgCompat}.fixStructureGenNullBiome.
 */
@Mixin(value = com.pg85.otg.forge.generator.structure.OTGNetherFortressGen.class, remap = false)
public abstract class MixinOTGNetherFortressGen {

    @Inject(method = "func_75047_a(II)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void insanetweaks$nullGuardBiome(int chunkX, int chunkZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.otgCompat.fixStructureGenNullBiome) {
            return;
        }
        // Obtain ForgeWorld via the accessor mixin on OTGMapGenStructure (where the field lives).
        Object fw = ((ForgeWorldAccessor) (Object) this).insanetweaks$getForgeWorld();
        if (fw == null) {
            cir.setReturnValue(false);
            return;
        }
        LocalBiome biome = ((ForgeWorld) fw).getBiome(chunkX * 16 + 8, chunkZ * 16 + 8);
        if (biome == null) {
            cir.setReturnValue(false);
        }
    }
}
