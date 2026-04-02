package com.spege.insanetweaks.mixins;

import codersafterdark.reskillable.api.skill.Skill;
import codersafterdark.reskillable.api.unlockable.Unlockable;
import codersafterdark.reskillable.client.gui.GuiSkillInfo;
import codersafterdark.reskillable.network.MessageLockUnlockable;
import codersafterdark.reskillable.network.PacketHandler;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.SoundEvents;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskillable normally relies on Ctrl + left click to refund an unlocked trait.
 * Some packs break Ctrl detection in the trait GUI, so we add a middle click
 * fallback and also use raw LWJGL Ctrl state for the original gesture.
 */
@Mixin(value = GuiSkillInfo.class, remap = false)
@SuppressWarnings("deprecation")
public abstract class MixinGuiSkillInfoReskillable extends GuiScreen {

    @Shadow
    @Final
    private Skill skill;

    @Shadow
    private Unlockable hoveredUnlockable;

    @Shadow
    private boolean isUnlocked;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void insanetweaks$handleFallbackLockClick(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (hoveredUnlockable == null || !isUnlocked) {
            return;
        }

        boolean rawCtrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shouldLock = (mouseButton == 0 && rawCtrlDown) || mouseButton == 2;
        if (!shouldLock) {
            return;
        }

        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        PacketHandler.INSTANCE.sendToServer(
                new MessageLockUnlockable(skill.getRegistryName(), hoveredUnlockable.getRegistryName()));
        ci.cancel();
    }
}
