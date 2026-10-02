package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.decay.GraveDecayService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave;

/**
 * Wpina rozklad we wlasny ticker grobu - jeden wywolanie na grob na tick, bez skanowania
 * wszystkich block entity jak na 1.12.2.
 *
 * <p>remap = false, bo serverTick to metoda Tombstone'a, ktorej nazwa nie jest obfuskowana.
 */
@Mixin(BlockEntityPlayerGrave.class)
public class MixinBlockEntityPlayerGrave {

    @Inject(method = "serverTick", at = @At("HEAD"), remap = false)
    private static void tombtweaks$decay(Level level, BlockPos pos, BlockState state,
                                         BlockEntityPlayerGrave grave, CallbackInfo ci) {
        GraveDecayService.tick(level, pos, grave);
    }
}
