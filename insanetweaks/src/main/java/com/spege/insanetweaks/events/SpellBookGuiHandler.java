package com.spege.insanetweaks.events;

import java.lang.reflect.Field;

import com.spege.insanetweaks.util.SpellDisplayUtils;

import electroblob.wizardry.client.gui.GuiSpellInfo;
import electroblob.wizardry.spell.Spell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings("null")
public class SpellBookGuiHandler {

    private static Field xSizeField;
    private static Field ySizeField;
    private static boolean fieldsResolved = false;

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiSpellInfo)) {
            return;
        }

        GuiSpellInfo spellInfo = (GuiSpellInfo) gui;
        Spell spell = spellInfo.getSpell();
        if (!SpellDisplayUtils.usesAbominationStyling(spell)) {
            return;
        }

        int xSize = getGuiDimension(spellInfo, true);
        int ySize = getGuiDimension(spellInfo, false);
        if (xSize <= 0 || ySize <= 0) {
            return;
        }

        int guiLeft = (gui.width - xSize) / 2;
        int guiTop = (gui.height - ySize) / 2;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawString(SpellDisplayUtils.getFormattedSpellDisplayName(spell), guiLeft + 17, guiTop + 15, 0, false);

        String elementLine = "Element: " + SpellDisplayUtils.getFormattedElementDisplayName(spell);
        font.drawString(elementLine, guiLeft + 17, guiTop + 57, 0, false);
    }

    private static int getGuiDimension(GuiSpellInfo gui, boolean xAxis) {
        resolveFields();
        try {
            Field field = xAxis ? xSizeField : ySizeField;
            if (field == null) {
                return -1;
            }
            return field.getInt(gui);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static void resolveFields() {
        if (fieldsResolved) {
            return;
        }
        fieldsResolved = true;

        try {
            xSizeField = GuiSpellInfo.class.getDeclaredField("xSize");
            xSizeField.setAccessible(true);
        } catch (Exception ignored) {
            xSizeField = null;
        }

        try {
            ySizeField = GuiSpellInfo.class.getDeclaredField("ySize");
            ySizeField.setAccessible(true);
        } catch (Exception ignored) {
            ySizeField = null;
        }
    }
}
