package com.spege.tombtweaks.client.gui.config;

import java.util.List;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.TombTweaksConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Config GUI that opens straight into the settings.
 *
 * <p>The config has a single root category, so the stock screen would spend its first page on one
 * button leading to the only thing there is. This unwraps that category and shows its children
 * directly — same elements, same editing, one click less. If the shape ever changes and the
 * unwrapping does not apply, it falls back to the stock listing rather than showing nothing.
 */
@SideOnly(Side.CLIENT)
public class TombTweaksGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        List<IConfigElement> roots = ConfigElement.from(TombTweaksConfig.class).getChildElements();

        List<IConfigElement> shown = roots;
        if (roots.size() == 1 && roots.get(0).isProperty() == false) {
            List<IConfigElement> children = roots.get(0).getChildElements();
            if (children != null && !children.isEmpty()) {
                shown = children;
            }
        }

        return new GuiConfig(parentScreen, shown, TombstoneTweaks.MODID, TombstoneTweaks.MODID,
                false, false, TombstoneTweaks.NAME);
    }

    @Override
    public java.util.Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
