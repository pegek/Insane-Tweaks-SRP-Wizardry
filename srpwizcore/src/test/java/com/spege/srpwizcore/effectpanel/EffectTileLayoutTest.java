package com.spege.srpwizcore.effectpanel;

import static com.spege.srpwizcore.effectpanel.EffectTileLayout.TILE_H;
import static com.spege.srpwizcore.effectpanel.EffectTileLayout.hovered;
import static com.spege.srpwizcore.effectpanel.EffectTileLayout.panelTop;
import static com.spege.srpwizcore.effectpanel.EffectTileLayout.spacing;
import static com.spege.srpwizcore.effectpanel.EffectTileLayout.tileX;
import static com.spege.srpwizcore.effectpanel.EffectTileLayout.tileY;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EffectTileLayoutTest {

    @Test
    public void spacingMatchesVanilla() {
        assertEquals(33, spacing(1));
        assertEquals(33, spacing(5));
        assertEquals(26, spacing(6));
        assertEquals(22, spacing(7));
    }

    @Test
    public void tilePosition() {
        assertEquals(76, tileX(200));
        assertEquals(116, tileY(50, 2, 33));
    }

    @Test
    public void hoverHitsTheRightTileAndEdgesAreHalfOpen() {
        assertEquals(0, hovered(80, 50, 200, 50, 3, 33));
        assertEquals(1, hovered(80, 83, 200, 50, 3, 33));
        assertEquals(0, hovered(215, 60, 200, 50, 3, 33));   // x range [76, 216)
        assertEquals(-1, hovered(216, 60, 200, 50, 3, 33));
        assertEquals(-1, hovered(75, 60, 200, 50, 3, 33));
        assertEquals(-1, hovered(80, 49, 200, 50, 3, 33));
        assertEquals(2, hovered(80, 147, 200, 50, 3, 33));   // tile 2 spans [116, 148)
        assertEquals(-1, hovered(80, 148, 200, 50, 3, 33));
    }

    @Test
    public void overlappingTilesPickTheLastDrawn() {
        assertEquals(1, hovered(80, 50 + 22 + 5, 200, 50, 7, 22)); // inside both tile 0 and tile 1
    }

    @Test
    public void hiddenEffectsShrinkSpacingButNotPositions() {
        int s = spacing(7);                                        // 7 active, only 6 drawn
        assertEquals(5, hovered(80, 50 + 5 * s + 10, 200, 50, 6, s));
        assertEquals(-1, hovered(80, 50 + 5 * s + TILE_H, 200, 50, 6, s));
    }

    @Test
    public void panelStaysOnScreen() {
        assertEquals(100, panelTop(100, 50, 300));
        assertEquals(250, panelTop(280, 50, 300));
        assertEquals(0, panelTop(10, 400, 300));
    }
}
