package com.spege.insanetweaks.client.renderer.entity.magic;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Colour multiply around the main model pass: every SRP skin variant (normal, v/b/h, snow) turns
 * purple without shipping a single SRP texture.
 */
@SideOnly(Side.CLIENT)
public final class ArcaneTint {

    private ArcaneTint() {
    }

    public static void begin() {
        double[] rgb = ModConfig.entities.magicParasites.tintRgb;
        GlStateManager.color(channel(rgb, 0), channel(rgb, 1), channel(rgb, 2), 1.0F);
    }

    public static void end() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static float channel(double[] rgb, int i) {
        if (rgb == null || i >= rgb.length) {
            return 1.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, rgb[i]));
    }
}
