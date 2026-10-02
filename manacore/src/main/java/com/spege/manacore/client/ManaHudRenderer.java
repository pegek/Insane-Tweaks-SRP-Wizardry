package com.spege.manacore.client;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.config.ManaCoreConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Draws the mana display as a row of icons, in the same idiom as vanilla hearts and hunger.
 *
 * <p>The placeholder texture borrowed from the player_mana mod is 27x9 and holds three 9x9 frames
 * side by side, not a continuous bar. Only two of them are used here: the frame at u=0 and the one
 * at u=9 have an identical opaque-pixel count, i.e. the same silhouette in two colours, which makes
 * them the empty/full pair. The third frame at u=18 has a different silhouette and is left alone
 * rather than guessed at - when this placeholder is replaced with our own art, decide then what a
 * third state should mean.
 *
 * <p>Because only whole icons are drawn, the display quantises to {@link #ICON_COUNT} steps. That
 * is a property of the borrowed art, not a design decision worth preserving.
 */
@SideOnly(Side.CLIENT)
public class ManaHudRenderer {

    private static final ResourceLocation ICONS =
            new ResourceLocation(ManaCoreMod.MODID, "textures/gui/bar_mana.png");

    private static final int TEXTURE_WIDTH = 27;
    private static final int TEXTURE_HEIGHT = 9;
    private static final int ICON_SIZE = 9;

    private static final int FRAME_EMPTY_U = 0;
    private static final int FRAME_FULL_U = 9;

    private static final int ICON_COUNT = 10;
    /** Horizontal step between icons, one pixel tighter than the icon itself, as vanilla does. */
    private static final int ICON_STEP = 8;

    /** Distance above the bottom of the screen, one row clear of the hunger bar. */
    private static final int ROW_OFFSET_FROM_BOTTOM = 49;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        // The two switches are independent, which is what gives all four display modes: icons and
        // numbers, icons only, numbers only, or nothing at all. Bailing out on `showBar` alone -
        // as this did originally - made "numbers only" impossible to select, because the number is
        // drawn further down this same method.
        boolean drawBar = ManaCoreConfig.hud.showBar;
        boolean drawNumber = ManaCoreConfig.hud.showNumber;
        if (!drawBar && !drawNumber) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.gameSettings.showDebugInfo) {
            return;
        }

        double max = ManaAttributes.getMaxMana(player);
        if (max <= 0.0D) {
            return;
        }

        double current = ManaClientState.getCurrent();
        double fraction = current / max;
        if (fraction < 0.0D) {
            fraction = 0.0D;
        } else if (fraction > 1.0D) {
            fraction = 1.0D;
        }

        ScaledResolution res = new ScaledResolution(mc);
        int rowWidth = (ICON_COUNT - 1) * ICON_STEP + ICON_SIZE;
        int left = res.getScaledWidth() / 2 + 91 - rowWidth + ManaCoreConfig.hud.offsetX;
        int top = res.getScaledHeight() - ROW_OFFSET_FROM_BOTTOM + ManaCoreConfig.hud.offsetY;

        if (drawBar) {
            int filled = (int) Math.round(fraction * ICON_COUNT);

            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(ICONS);

            for (int i = 0; i < ICON_COUNT; i++) {
                int u = i < filled ? FRAME_FULL_U : FRAME_EMPTY_U;
                Gui.drawModalRectWithCustomSizedTexture(left + i * ICON_STEP, top, u, 0,
                        ICON_SIZE, ICON_SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            }

            GlStateManager.disableBlend();
        }

        // Kept at the same anchor whether or not the icons are drawn, so switching the bar off
        // does not also move the readout - the offsets keep meaning the same thing in every mode.
        if (drawNumber) {
            String text = ((int) Math.floor(current)) + " / " + ((int) Math.floor(max));
            int textX = left + rowWidth - mc.fontRenderer.getStringWidth(text);
            mc.fontRenderer.drawStringWithShadow(text, textX, top - 10, 0x55AAFF);
        }
    }
}
