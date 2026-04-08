package com.spege.insanetweaks.events;

import codersafterdark.reskillable.api.skill.Skill;
import codersafterdark.reskillable.api.unlockable.Unlockable;
import codersafterdark.reskillable.client.gui.GuiSkillInfo;
import codersafterdark.reskillable.network.MessageLockUnlockable;
import codersafterdark.reskillable.network.PacketHandler;
import com.spege.insanetweaks.InsaneTweaksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

/**
 * Client-side fallback for Reskillable trait refunds.
 *
 * Some modpacks break the native Ctrl + left click path inside GuiSkillInfo.
 * We bypass that by listening to GUI mouse events directly and sending the
 * normal Reskillable lock packet ourselves. A small hint is also drawn in the
 * GUI whenever the player is hovering an unlocked trait.
 */
@SideOnly(Side.CLIENT)
@SuppressWarnings("null")
public class ReskillableGuiHandler {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int HINT_COLOR = 0xFFF2A8;

    private static final Field FIELD_SKILL = findField("skill");
    private static final Field FIELD_HOVERED_UNLOCKABLE = findField("hoveredUnlockable");
    private static final Field FIELD_IS_UNLOCKED = findField("isUnlocked");

    private static boolean reflectionReady = FIELD_SKILL != null
            && FIELD_HOVERED_UNLOCKABLE != null
            && FIELD_IS_UNLOCKED != null;
    private static boolean loggedReflectionFailure = false;

    private static Field findField(String name) {
        try {
            Field field = GuiSkillInfo.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            InsaneTweaksMod.LOGGER.error("[InsaneTweaks] Failed to access Reskillable GuiSkillInfo field '{}'.", name,
                    e);
            return null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!reflectionReady || !(event.getGui() instanceof GuiSkillInfo) || !Mouse.getEventButtonState()) {
            return;
        }

        int button = Mouse.getEventButton();
        boolean rawCtrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shouldRefund = button == 2 || (button == 0 && rawCtrlDown);
        if (!shouldRefund) {
            return;
        }

        GuiSkillInfo gui = (GuiSkillInfo) event.getGui();
        Unlockable hoveredUnlockable = getHoveredUnlockable(gui);
        if (hoveredUnlockable == null || !isUnlocked(gui)) {
            return;
        }

        Skill skill = getSkill(gui);
        if (skill == null || skill.getRegistryName() == null || hoveredUnlockable.getRegistryName() == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        PacketHandler.INSTANCE
                .sendToServer(new MessageLockUnlockable(skill.getRegistryName(), hoveredUnlockable.getRegistryName()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!reflectionReady || !(event.getGui() instanceof GuiSkillInfo)) {
            return;
        }

        GuiSkillInfo gui = (GuiSkillInfo) event.getGui();
        if (getHoveredUnlockable(gui) == null || !isUnlocked(gui)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        String hint = I18n.format("insanetweaks.reskillable.lock_fallback");
        int left = gui.width / 2 - GUI_WIDTH / 2;
        int top = gui.height / 2 - GUI_HEIGHT / 2;
        int x = left + 12;
        int y = top + 149;

        mc.fontRenderer.drawStringWithShadow(hint, x, y, HINT_COLOR);
    }

    private static Skill getSkill(GuiSkillInfo gui) {
        return (Skill) getFieldValue(FIELD_SKILL, gui);
    }

    private static Unlockable getHoveredUnlockable(GuiSkillInfo gui) {
        return (Unlockable) getFieldValue(FIELD_HOVERED_UNLOCKABLE, gui);
    }

    private static boolean isUnlocked(GuiSkillInfo gui) {
        Object value = getFieldValue(FIELD_IS_UNLOCKED, gui);
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static Object getFieldValue(Field field, GuiSkillInfo gui) {
        if (!reflectionReady || field == null) {
            return null;
        }

        try {
            return field.get(gui);
        } catch (Exception e) {
            if (!loggedReflectionFailure) {
                loggedReflectionFailure = true;
                reflectionReady = false;
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] Failed to read Reskillable GuiSkillInfo fields. Disabling GUI refund fallback.",
                        e);
            }
            return null;
        }
    }
}
