package com.spege.insanetweaks.client.gui.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Config GUI with an explicit section order (gameplay first, client/debug last) instead of the
 * default factory's alphabetical sort. Uses the same public API the default GUI uses
 * (ConfigElement.from(ModConfig.class).getChildElements()) - the only difference is the ordering.
 */
@SideOnly(Side.CLIENT)
public class InsaneTweaksGuiFactory implements IModGuiFactory {

    /** Internal category keys (@Config.Name values on the ModConfig root fields), in display order. */
    private static final List<String> CATEGORY_ORDER = Arrays.asList(
            "modules", "tweaks", "traits", "tombstone", "thrall", "entities", "client");

    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        List<IConfigElement> children = ConfigElement.from(ModConfig.class).getChildElements();

        List<IConfigElement> ordered = new ArrayList<IConfigElement>(children.size());
        for (String name : CATEGORY_ORDER) {
            for (IConfigElement element : children) {
                if (name.equals(element.getName())) {
                    ordered.add(element);
                    break;
                }
            }
        }
        // Anything not in the explicit list (future additions) goes to the end, original order.
        for (IConfigElement element : children) {
            if (!ordered.contains(element)) {
                ordered.add(element);
            }
        }

        return new GuiConfig(parentScreen, ordered, InsaneTweaksMod.MODID, InsaneTweaksMod.MODID,
                false, false, InsaneTweaksMod.NAME);
    }

    @Override
    public java.util.Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
