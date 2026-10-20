package com.spege.srpwizcore.effectpanel;

/**
 * Where vanilla draws the effect tiles next to an inventory, so the panel can tell which one the
 * mouse is on. Every number is vanilla's, from
 * {@code InventoryEffectRenderer.drawActivePotionEffects} (Forge 14.23.5.2860).
 *
 * <p>🚨 Vanilla mixes two counts and so must callers: {@link #spacing} takes the number of
 * <b>all</b> active effects ({@code collection.size()}), but a tile's position advances only for
 * effects actually drawn ({@code Potion.shouldRender}). Feed both the same count and, with a hidden
 * effect present, the hover lands one tile off.
 *
 * <p>No Minecraft types, so it runs under plain JUnit.
 */
public final class EffectTileLayout {

    private EffectTileLayout() {
    }

    public static final int TILE_W = 140;
    public static final int TILE_H = 32;

    /** 33 pixels between tiles, squeezed to {@code 132 / (n - 1)} once there are more than 5. */
    public static int spacing(int totalEffects) {
        return totalEffects > 5 ? 132 / (totalEffects - 1) : 33;
    }

    public static int tileX(int guiLeft) {
        return guiLeft - 124;
    }

    public static int tileY(int guiTop, int drawnIndex, int spacing) {
        return guiTop + drawnIndex * spacing;
    }

    /**
     * The drawn index of the tile under the mouse, or -1. When squeezed tiles overlap, the later
     * one is painted on top, so the search runs from the last tile back.
     */
    public static int hovered(int mouseX, int mouseY, int guiLeft, int guiTop, int drawnCount, int spacing) {
        int x = tileX(guiLeft);
        if (mouseX < x || mouseX >= x + TILE_W) {
            return -1;
        }
        for (int i = drawnCount - 1; i >= 0; i--) {
            int y = tileY(guiTop, i, spacing);
            if (mouseY >= y && mouseY < y + TILE_H) {
                return i;
            }
        }
        return -1;
    }

    /** The panel's top edge: the tile's own, raised just enough to stay on screen, never above 0. */
    public static int panelTop(int tileY, int panelHeight, int screenHeight) {
        return Math.max(0, Math.min(tileY, screenHeight - panelHeight));
    }
}
