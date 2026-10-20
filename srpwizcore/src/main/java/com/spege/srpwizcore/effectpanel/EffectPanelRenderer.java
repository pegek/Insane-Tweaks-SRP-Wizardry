package com.spege.srpwizcore.effectpanel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Ordering;
import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.config.categories.EffectPanelCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Draws the effect panel: while the mouse is over an effect tile next to an inventory, that tile is
 * painted over with the effect's description in a small font, growing downwards as far as the text
 * needs, with the remaining duration as the last line.
 *
 * <p>Runs in {@code DrawScreenEvent.Post} - after the container has drawn its tiles, which is the
 * whole difference from Just Enough Effects, whose popup is drawn in {@code Pre} and ends up under
 * them. HIGH priority and depth testing off: painter's order puts the panel over the tiles but under
 * whatever later handlers draw, i.e. HEI's bookmark overlay and item tooltips stay on top.
 *
 * <p>The geometry is vanilla's ({@link EffectTileLayout}); whether vanilla drew the tiles at all this
 * frame is read straight from {@code InventoryEffectRenderer.hasActivePotionEffects}, which
 * {@code GuiInventory} also clears while its recipe book is open - so the recipe book needs no case
 * of its own here.
 *
 * <p>🚨 Client-only by annotation ({@code value = Side.CLIENT}): FML checks the side before loading
 * the class, so it never reaches a dedicated server.
 */
@Mod.EventBusSubscriber(modid = SrpWizCore.MODID, value = Side.CLIENT)
public final class EffectPanelRenderer {

    private EffectPanelRenderer() {
    }

    private static final ResourceLocation TILE_TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    /** The tile's place in that texture, and its 4-pixel top and bottom borders. */
    private static final int TILE_V = 166;
    private static final int BORDER = 4;
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 9;

    private static final int TEXT_COLOUR = 0x404040;
    private static final int DURATION_COLOUR = 0x7F7F7F;
    private static final int CURSE_FILL = 0xFF3B1450;
    private static final int CURSE_HIGHLIGHT = 0xFF7A4A9A;
    private static final int CURSE_SHADOW = 0xFF1E0A29;
    private static final int CURSE_TEXT = 0xFFE8D8F0;

    private static final String EBW_CURSE = "electroblob.wizardry.potion.Curse";

    private static Field hasActivePotionEffects;
    private static boolean reflectionFailed;
    private static Class<?> curseClass;
    private static boolean curseLookedUp;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        EffectPanelCategory cfg = SrpWizCoreConfig.effectPanel;
        if (!cfg.enabled || !(event.getGui() instanceof InventoryEffectRenderer)) {
            return;
        }
        InventoryEffectRenderer gui = (InventoryEffectRenderer) event.getGui();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !tilesDrawn(gui)) {
            return;
        }

        Collection<PotionEffect> active = mc.player.getActivePotionEffects();
        List<PotionEffect> drawn = new ArrayList<>();
        for (PotionEffect effect : Ordering.natural().sortedCopy(active)) {
            if (effect.getPotion().shouldRender(effect)) {
                drawn.add(effect);
            }
        }
        // Spacing from ALL effects, positions from the drawn ones - vanilla's own asymmetry.
        int spacing = EffectTileLayout.spacing(active.size());
        int index = EffectTileLayout.hovered(event.getMouseX(), event.getMouseY(),
                gui.getGuiLeft(), gui.getGuiTop(), drawn.size(), spacing);
        if (index < 0) {
            return;
        }

        PotionEffect effect = drawn.get(index);
        List<String> lines = buildLines(effect, cfg, mc.fontRenderer);
        if (lines == null) {
            return;
        }
        boolean curse = isCurse(effect.getPotion());
        boolean withDuration = effect.getPotion().shouldRenderInvText(effect);

        int x = EffectTileLayout.tileX(gui.getGuiLeft());
        int height = Math.max(EffectTileLayout.TILE_H,
                PADDING * 2 + (int) Math.ceil(lines.size() * LINE_HEIGHT * cfg.fontScale));
        int top = EffectTileLayout.panelTop(EffectTileLayout.tileY(gui.getGuiTop(), index, spacing), height, gui.height);

        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            if (curse) {
                drawCurseBackground(x, top, height);
            } else {
                drawTileBackground(mc, gui, x, top, height);
            }
            drawText(mc.fontRenderer, lines, withDuration, curse, x, top, (float) cfg.fontScale);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableDepth();
        }
    }

    /**
     * The panel's text: the wrapped description (or the where-to-add hint), then the duration when
     * vanilla would show one. Null means "leave this tile alone".
     */
    private static List<String> buildLines(PotionEffect effect, EffectPanelCategory cfg, FontRenderer font) {
        Potion potion = effect.getPotion();
        DescriptionChain.Result result = EffectDescriptions.describe(potion, effect.getAmplifier());
        List<String> lines = new ArrayList<>();
        int wrapWidth = (int) ((EffectTileLayout.TILE_W - PADDING * 2) / cfg.fontScale);
        if (result.rule == DescriptionChain.Rule.NONE) {
            if (!cfg.showMissingHint) {
                return null;
            }
            ResourceLocation name = potion.getRegistryName();
            lines.addAll(font.listFormattedStringToWidth(I18n.format("srpwizcore.effectpanel.missing.title"), wrapWidth));
            lines.addAll(font.listFormattedStringToWidth(I18n.format("srpwizcore.effectpanel.missing.where"), wrapWidth));
            lines.addAll(font.listFormattedStringToWidth(
                    DescriptionChain.fileLine(name == null ? "?" : name.toString()), wrapWidth));
        } else {
            lines.addAll(font.listFormattedStringToWidth(result.text, wrapWidth));
        }
        // Room for part 3 (effect source): one more line slots in here, before the duration.
        if (potion.shouldRenderInvText(effect)) {
            lines.add(Potion.getPotionDurationString(effect, 1.0F));
        }
        return lines;
    }

    /** Vanilla's tile, stretched: its 4-pixel top and bottom borders, its middle rows repeated between. */
    private static void drawTileBackground(Minecraft mc, GuiScreen gui, int x, int top, int height) {
        mc.getTextureManager().bindTexture(TILE_TEXTURE);
        int w = EffectTileLayout.TILE_W;
        int middle = EffectTileLayout.TILE_H - BORDER * 2;
        gui.drawTexturedModalRect(x, top, 0, TILE_V, w, BORDER);
        int bottom = top + height - BORDER;
        for (int y = top + BORDER; y < bottom; y += middle) {
            gui.drawTexturedModalRect(x, y, 0, TILE_V + BORDER, w, Math.min(middle, bottom - y));
        }
        gui.drawTexturedModalRect(x, bottom, 0, TILE_V + EffectTileLayout.TILE_H - BORDER, w, BORDER);
    }

    private static void drawCurseBackground(int x, int top, int height) {
        int right = x + EffectTileLayout.TILE_W;
        int bottom = top + height;
        Gui.drawRect(x, top, right, bottom, CURSE_FILL);
        Gui.drawRect(x, top, right, top + 1, CURSE_HIGHLIGHT);
        Gui.drawRect(x, top, x + 1, bottom, CURSE_HIGHLIGHT);
        Gui.drawRect(x, bottom - 1, right, bottom, CURSE_SHADOW);
        Gui.drawRect(right - 1, top, right, bottom, CURSE_SHADOW);
    }

    private static void drawText(FontRenderer font, List<String> lines, boolean withDuration, boolean curse,
            int x, int top, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + PADDING, top + PADDING, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        int textColour = curse ? CURSE_TEXT : TEXT_COLOUR;
        for (int i = 0; i < lines.size(); i++) {
            boolean durationLine = withDuration && i == lines.size() - 1;
            font.drawString(lines.get(i), 0, i * LINE_HEIGHT, durationLine ? DURATION_COLOUR : textColour);
        }
        GlStateManager.popMatrix();
    }

    private static boolean tilesDrawn(InventoryEffectRenderer gui) {
        if (reflectionFailed) {
            return false;
        }
        try {
            if (hasActivePotionEffects == null) {
                // SRG name of hasActivePotionEffects; the helper maps it to the dev name in a dev run.
                hasActivePotionEffects = ObfuscationReflectionHelper.findField(InventoryEffectRenderer.class,
                        "field_147045_u");
            }
            return hasActivePotionEffects.getBoolean(gui);
        } catch (Exception e) {
            reflectionFailed = true;
            SrpWizCore.LOGGER.warn("[srpwizcore] Effect panel disabled: cannot read InventoryEffectRenderer"
                    + ".hasActivePotionEffects ({})", e.toString());
            return false;
        }
    }

    /** EBW curses draw their own purple tile; looked up by name so there is no compile dependency on EBW. */
    private static boolean isCurse(Potion potion) {
        if (!curseLookedUp) {
            curseLookedUp = true;
            try {
                curseClass = Class.forName(EBW_CURSE, false, EffectPanelRenderer.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                curseClass = null;
            }
        }
        return curseClass != null && curseClass.isAssignableFrom(potion.getClass());
    }
}
