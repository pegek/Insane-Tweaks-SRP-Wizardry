package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.tombtweaks.config.TombTweaksConfig;

import net.minecraft.client.Minecraft;

/**
 * Stops Tombstone 4.8.0 from overwriting the player's Video Settings GUI Scale.
 *
 * <p>{@code TBScreen} is new in 4.8.0 and forces a larger GUI scale while one of Tombstone's own
 * screens is open ({@code ScreenKnowledge}, {@code ScreenCompendium}), restoring the player's value
 * on close. The restore is correct; what undoes it is the line right after it.
 *
 * <p>The loop, every link of it read with {@code javap}:
 * <ol>
 *   <li>{@code TBScreen.<init>} stores {@code oldScale = gameSettings.guiScale}.</li>
 *   <li>{@code initGui} writes {@code guiScale = scaleLimit}.</li>
 *   <li>{@code onGuiClosed} writes {@code guiScale = oldScale}, then calls
 *       {@code Minecraft.resize(displayWidth, displayHeight)}.</li>
 *   <li>🚨 {@code Minecraft.displayGuiScreen} calls {@code onGuiClosed()} at bytecode offset 90 but
 *       only assigns {@code currentScreen} at offset 128 — so during the close, {@code currentScreen}
 *       is still the screen being closed. {@code resize} therefore reaches
 *       {@code currentScreen.onResize} → {@code setWorldAndResolution} → <b>{@code initGui} on that
 *       same screen</b>, which re-applies the forced scale. The player's setting is lost.</li>
 * </ol>
 *
 * <p>Redirecting that one call away is the whole fix. Nothing else in {@code resize} is needed here:
 * the display dimensions have not changed, so the framebuffer resize is a no-op, and whatever screen
 * comes next gets a fresh {@code ScaledResolution} from {@code displayGuiScreen} anyway — while
 * closing to the world, the HUD rebuilds one every frame regardless.
 *
 * <p>🚨 {@code require = 0} is deliberate and does double duty. On Tombstone 4.7.x the target class
 * does not exist at all; and once the author fixes this upstream, the call this redirect anchors on
 * may simply be gone — at {@code require = 1} that would turn his fix into an
 * {@code InvalidInjectionException} on our side. At zero this patch quietly disarms itself. It is
 * meant to be temporary: reported to Corail_31 against 4.8.0, delete it when a fixed version ships.
 *
 * <p>Client only — the target extends {@code GuiScreen}. Listed in the {@code client} block of
 * {@code mixins.tombtweaks.tombstone.json}, never the common one.
 */
@Mixin(targets = "ovh.corail.tombstone.gui.TBScreen", remap = false)
public abstract class MixinTombstoneGuiScale {

    @Redirect(
            method = "func_146281_b",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;func_71370_a(II)V"),
            require = 0)
    private void tombtweaks$skipResizeOnClose(Minecraft mc, int width, int height) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks
                || !TombTweaksConfig.tombstone.fixGuiScaleReset) {
            mc.resize(width, height);
        }
    }
}
