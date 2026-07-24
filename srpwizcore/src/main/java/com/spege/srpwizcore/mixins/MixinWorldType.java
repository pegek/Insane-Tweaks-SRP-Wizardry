package com.spege.srpwizcore.mixins;

import com.spege.srpwizcore.config.SrpWizCoreConfig;
import net.minecraft.world.WorldType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides Open Terrain Generator's "OTG" world type from the vanilla Create-World
 * world-type selector button, without breaking existing OTG-type saves.
 *
 * <p>Since B1 the pack auto-injects the Underneath (dim 150) into the vanilla Default
 * overworld via OTG's ModPack DimensionsConfig, so the standalone "OTG" world type is
 * redundant and, if picked, would route the player onto OTG's own generation flow
 * instead of the intended Default + auto-inject path. {@code GuiCreateWorld} shows a
 * world type in its cycle button only when {@code WorldType.canBeCreated()} is true, so
 * returning false for the "OTG" instance removes it from the button.
 *
 * <p>Loading existing OTG worlds is unaffected: {@code WorldType.parseWorldType(String)}
 * matches by name via {@code equalsIgnoreCase} and never consults {@code canBeCreated()},
 * so OTGTEST / OTGNEWTEST and any other OTG-type save still resolve and load.
 *
 * <p>Early mixin (vanilla target) via the jar-manifest {@code MixinConfigs} route
 * ({@code mixins.srpwizcore.early.json}). Self-gated by the runtime name check, so it is a
 * no-op when OTG is absent (no world type is named "OTG"), and by
 * {@code otgCompat.hideOtgWorldType}. remap=false + explicit SRG per project convention.
 */
@Mixin(WorldType.class)
public class MixinWorldType {

    @Shadow(aliases = "field_77133_f", remap = false)
    @Final
    private String name;

    @Inject(method = "func_77126_d", at = @At("HEAD"), cancellable = true, remap = false)
    private void srpwizcore$hideOtgWorldType(CallbackInfoReturnable<Boolean> cir) {
        if (SrpWizCoreConfig.otgCompat.hideOtgWorldType && "OTG".equals(this.name)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
