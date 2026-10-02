package com.spege.manacore.mixins.tab;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.manacore.config.ManaCoreConfig;

import xzeroair.trinkets.client.gui.hud.mana.ManaGui;

/**
 * Suppresses the mana bar Trinkets and Baubles draws on the HUD, so the player sees one bar
 * instead of two showing the same number.
 *
 * <p>This is presentation only. The pool takeover itself lives in {@code MixinMagicStats}; without
 * that, hiding the bar would just conceal a second, separate resource. With it, TaB's bar is a
 * duplicate readout of ManaCore's pool, and two identical bars are worse than one.
 *
 * <p>The target is the renderer rather than TaB's own {@code ConfigManaBarHud.shown} flag: writing
 * into another mod's config from here would fight whatever that mod does on its next config sync,
 * and would silently revert if the player ever opened TaB's config screen. Cancelling the draw
 * call is stateless and cannot be undone behind our back.
 *
 * <p>{@code renderManaGui} has exactly one caller in the whole TaB jar
 * ({@code client.events.ScreenOverlayEvents}), so cancelling at HEAD suppresses every path that
 * draws this bar. One consequence worth knowing: TaB also reuses this renderer inside its own
 * bar-positioning screen, which will therefore show nothing while this option is on. That is
 * consistent - there is no point positioning a bar that is hidden - but it does mean the option
 * has to be switched off before TaB's positioning UI is useful again.
 *
 * <p>Registered under the mixin config's {@code client} section, not {@code mixins}:
 * {@link ManaGui} extends {@code net.minecraft.client.gui.Gui}, so the class does not exist on a
 * dedicated server and applying this there would fail at class load.
 */
@Mixin(value = ManaGui.class, remap = false)
public abstract class MixinManaGui {

    @Inject(method = "renderManaGui", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$hideForeignManaBar(CallbackInfo ci) {
        if (ManaCoreConfig.tab.enabled && ManaCoreConfig.hud.hideForeignManaBars) {
            ci.cancel();
        }
    }
}
