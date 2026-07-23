package com.spege.srpwizcore.mixins.otg;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pg85.otg.common.LocalBiome;
import com.pg85.otg.forge.world.ForgeWorld;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.util.ForgeWorldAccessor;

/**
 * Null-biome guard for {@code OTGMineshaftGen.func_75047_a} (canSpawnStructureAtCoords).
 *
 * <p>Same root cause as the Nether Fortress crash: in OTG dimensions with virtual biomes,
 * {@code ForgeWorld.getBiome(x,z)} can return {@code null}. The Mineshaft generator dereferences
 * this at line 60 ({@code biome.getBiomeConfig()}) inside the cache-miss branch (bytecode 47-66:
 * {@code forgeWorld.getBiome(blockXCenter, blockZCenter)} → null → NPE).
 *
 * <p>We inject at HEAD and obtain {@code ForgeWorld} via the {@link ForgeWorldAccessor} duck
 * interface (applied to the superclass {@code OTGMapGenStructure} by
 * {@link MixinOTGMapGenStructure}). If the biome lookup returns null, we return {@code false}.
 *
 * <p>Confirmed via {@code javap -p -c -l} on {@code OpenTerrainGenerator-1.12.2-v9.7.jar} (2026-07-20).
 * Gated on {@link ModConfig#otgCompat}.fixStructureGenNullBiome.
 */
@Mixin(value = com.pg85.otg.forge.generator.structure.OTGMineshaftGen.class, remap = false)
public abstract class MixinOTGMineshaftGen {

    @Inject(method = "func_75047_a(II)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void insanetweaks$nullGuardBiome(int chunkX, int chunkZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (!SrpWizCoreConfig.otgCompat.fixStructureGenNullBiome) {
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
