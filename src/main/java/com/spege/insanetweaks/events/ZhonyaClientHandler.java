package com.spege.insanetweaks.events;

import com.spege.insanetweaks.init.ModPotions;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Złoty tint modelu gracza podczas Gilded Stasis (Zhonya rework).
 * EB-owa petryfikacja nie działa na graczach (zamienia EntityLiving w BlockStatue),
 * więc "złota petryfikacja" jest naszą iluzją: multiplikatywny złoty kolor na renderze.
 */
@SideOnly(Side.CLIENT)
public class ZhonyaClientHandler {

    @SubscribeEvent
    @SuppressWarnings("null") // ModPotions.GILDED_STASIS is guaranteed non-null at runtime
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.getEntityPlayer().isPotionActive(ModPotions.GILDED_STASIS)) {
            GlStateManager.color(1.0F, 0.82F, 0.15F, 1.0F);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (event.getEntityPlayer().isPotionActive(ModPotions.GILDED_STASIS)) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
