package com.spege.insanetweaks.client.renderer.tile;

import java.util.Map;
import java.util.WeakHashMap;

import org.lwjgl.opengl.GL11;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.sanctuary.SanctuaryDebug;
import com.spege.insanetweaks.sanctuary.SanctuaryStatus;
import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Static translucent protection dome (full sphere) drawn around an ACTIVE Sanctuary Core at its
 * effective radius. This is a proper {@link TileEntitySpecialRenderer}, NOT particles - so it is
 * inherently static (no pulsing; the old EBW SPHERE particle expanded because its scale is tied to
 * particle age). Shell + faint lat/long grid, neutral pale-cyan, very low alpha, depth-write off so
 * it reads as a soft force field from both inside and outside.
 *
 * <p>Registered client-side in {@code InsaneTweaksMod#preInit}; gated live by
 * {@code ModConfig.sanctuary.renderDome}. The dome center + radius come from the client TE's
 * {@code getEffectiveRadius()} (kept in sync via the display-tag update packet).
 */
@SideOnly(Side.CLIENT)
public class RenderSanctuaryDome extends TileEntitySpecialRenderer<TileEntitySanctuaryCore> {

    /** Sphere surface tessellation. */
    private static final int SEGMENTS = 32; // longitude divisions
    private static final int RINGS = 16;    // latitude divisions
    /** Grid density (independent of the surface). */
    private static final int MERIDIANS = 16;
    private static final int PARALLELS = 10;

    // Neutral pale-cyan; tuned for clear visibility at large radius. Tune here (or promote to config later).
    private static final float R = 0.60F;
    private static final float G = 0.82F;
    private static final float B = 0.90F;
    private static final float SURFACE_ALPHA = 0.10F;
    private static final float LINE_ALPHA = 0.45F;
    private static final float LINE_WIDTH = 2.0F;

    /** Extra blocks beyond the radius within which we still bother drawing the dome. */
    private static final double RENDER_MARGIN = 24.0D;

    /**
     * What this renderer last reported about each dome, so a callback that runs once per FRAME
     * reports transitions instead of state. Weak keys: an unloaded dome falls out by itself, which
     * is what keeps the table bounded without any eviction policy of its own. Touched only from the
     * client render thread, so it needs no synchronisation.
     */
    private static final Map<TileEntitySanctuaryCore, String> LAST_DIAG =
            new WeakHashMap<TileEntitySanctuaryCore, String>();

    /**
     * Reports a CHANGE in what the dome renderer is doing, through the Sanctuary module's own
     * deduplicating logger.
     *
     * <p>This used to be a bare LOGGER.info behind a hardcoded one-second throttle, which made it
     * the one place in the module that ignored the player's own suppression settings: a line per
     * second, every second, for as long as a dome was on screen. {@link SanctuaryDebug} already
     * knows how to keep a repeat quiet and how to cap a burst - going through it means those
     * settings mean here what they mean everywhere else.
     *
     * <p>Throttling alone would not have been enough. The render callback fires every frame, and
     * the interesting thing about it is WHEN it starts or stops drawing, not that it is still
     * drawing. Hence the comparison: {@code state} must hold nothing that varies continuously -
     * the viewer's distance changes every frame, so keying on it would defeat the whole point.
     * Varying numbers go in {@code detail}, which is printed but never compared.
     *
     * <p>Returning before touching the table when logging is off is deliberate: switching the flag
     * on mid-session then reports the current state on the next frame, rather than staying silent
     * until something happens to change.
     */
    private static void diag(TileEntitySanctuaryCore te, String state, String detail) {
        if (!ModConfig.sanctuary.debugLogging) {
            return;
        }
        if (state.equals(LAST_DIAG.get(te))) {
            return;
        }
        LAST_DIAG.put(te, state);
        SanctuaryDebug.log("render", detail == null ? state : state + " " + detail);
    }

    @Override
    public void render(TileEntitySanctuaryCore te, double x, double y, double z,
            float partialTicks, int destroyStage, float alpha) {
        if (te == null) {
            return;
        }
        if (!ModConfig.sanctuary.renderDome) {
            diag(te, "skip: renderDome config OFF", null);
            return;
        }
        SanctuaryStatus status = te.getStatus();
        if (status != SanctuaryStatus.ACTIVE) {
            diag(te, "skip: status=" + status + " (not ACTIVE)", null);
            return;
        }
        int radius = te.getEffectiveRadius();
        if (radius <= 0) {
            diag(te, "skip: radius=" + radius, null);
            return;
        }

        // Only draw when the viewer is inside the bubble or just outside it.
        EntityPlayer viewer = Minecraft.getMinecraft().player;
        if (viewer != null) {
            double reach = radius + RENDER_MARGIN;
            double distSq = te.getDistanceSq(viewer.posX, viewer.posY, viewer.posZ);
            if (distSq > reach * reach) {
                diag(te, "skip: too far",
                        "dist=" + (int) Math.sqrt(distSq) + " reach=" + (int) reach);
                return;
            }
            diag(te, "DRAW radius=" + radius, "dist=" + (int) Math.sqrt(distSq));
        } else {
            diag(te, "DRAW radius=" + radius + " (no viewer)", null);
        }

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();   // visible from inside and outside
        GlStateManager.depthMask(false); // translucent: don't occlude / z-fight with itself
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        drawSurface(buf, tess, radius);
        GlStateManager.glLineWidth(LINE_WIDTH);
        drawGrid(buf, tess, radius);

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static void drawSurface(BufferBuilder buf, Tessellator tess, double radius) {
        for (int ring = 0; ring < RINGS; ring++) {
            double lat0 = Math.PI * (-0.5D + (double) ring / RINGS);
            double lat1 = Math.PI * (-0.5D + (double) (ring + 1) / RINGS);
            double y0 = Math.sin(lat0), c0 = Math.cos(lat0);
            double y1 = Math.sin(lat1), c1 = Math.cos(lat1);
            buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int seg = 0; seg <= SEGMENTS; seg++) {
                double lon = 2.0D * Math.PI * seg / SEGMENTS;
                double cx = Math.cos(lon), sz = Math.sin(lon);
                buf.pos(radius * c1 * cx, radius * y1, radius * c1 * sz).color(R, G, B, SURFACE_ALPHA).endVertex();
                buf.pos(radius * c0 * cx, radius * y0, radius * c0 * sz).color(R, G, B, SURFACE_ALPHA).endVertex();
            }
            tess.draw();
        }
    }

    private static void drawGrid(BufferBuilder buf, Tessellator tess, double radius) {
        // meridians: pole-to-pole longitude lines
        for (int m = 0; m < MERIDIANS; m++) {
            double lon = 2.0D * Math.PI * m / MERIDIANS;
            double cx = Math.cos(lon), sz = Math.sin(lon);
            buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= RINGS; i++) {
                double lat = Math.PI * (-0.5D + (double) i / RINGS);
                double cc = Math.cos(lat), yy = Math.sin(lat);
                buf.pos(radius * cc * cx, radius * yy, radius * cc * sz).color(R, G, B, LINE_ALPHA).endVertex();
            }
            tess.draw();
        }
        // parallels: latitude circles (skip the poles)
        for (int p = 1; p < PARALLELS; p++) {
            double lat = Math.PI * (-0.5D + (double) p / PARALLELS);
            double cc = Math.cos(lat), yy = Math.sin(lat);
            buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
            for (int seg = 0; seg < SEGMENTS; seg++) {
                double lon = 2.0D * Math.PI * seg / SEGMENTS;
                buf.pos(radius * cc * Math.cos(lon), radius * yy, radius * cc * Math.sin(lon))
                        .color(R, G, B, LINE_ALPHA).endVertex();
            }
            tess.draw();
        }
    }
}
