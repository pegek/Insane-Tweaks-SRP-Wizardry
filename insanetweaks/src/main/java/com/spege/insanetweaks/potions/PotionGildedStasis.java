package com.spege.insanetweaks.potions;

import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Gilded Stasis — applied by Zhonya's Hourglass activation.
 *
 * Marker effect: while active, ZhonyaStasisHandler cancels all incoming damage,
 * roots the player and blocks attacks/interactions; ZhonyaClientHandler renders
 * the player model with a golden tint. This class carries no game logic itself.
 */
public class PotionGildedStasis extends Potion {

    @SideOnly(Side.CLIENT)
    private static final ResourceLocation STASIS_TEXTURE =
            new ResourceLocation("insanetweaks", "textures/gui/potion/gilded_stasis.png");

    public PotionGildedStasis() {
        super(false, 0xFFD700); // gold
        this.setPotionName("potion.insanetweaks.gilded_stasis");
        this.setBeneficial();
    }

    @Override
    public boolean isInstant() {
        return false;
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return false; // no performEffect logic
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public boolean hasStatusIcon() {
        return false; // use our own PNG rendering below
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("null") // bindTexture param lacks @Nonnull annotation in Forge 1.12.2 API
    public void renderHUDEffect(int x, int y, @Nonnull PotionEffect effect, @Nonnull Minecraft mc, float alpha) {
        mc.getTextureManager().bindTexture(STASIS_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        drawFullTexture(x + 3, y + 3, 18, 18);
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("null") // bindTexture param lacks @Nonnull annotation in Forge 1.12.2 API
    public void renderInventoryEffect(int x, int y, @Nonnull PotionEffect effect, @Nonnull Minecraft mc) {
        mc.getTextureManager().bindTexture(STASIS_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawFullTexture(x + 3, y + 3, 18, 18);
    }

    @SideOnly(Side.CLIENT)
    @SuppressWarnings("null") // DefaultVertexFormats fields lack @Nonnull in Forge 1.12.2 API
    private static void drawFullTexture(int x, int y, int w, int h) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x,     y + h, 0).tex(0.0, 1.0).endVertex();
        buf.pos(x + w, y + h, 0).tex(1.0, 1.0).endVertex();
        buf.pos(x + w, y,     0).tex(1.0, 0.0).endVertex();
        buf.pos(x,     y,     0).tex(0.0, 0.0).endVertex();
        tess.draw();
    }
}
