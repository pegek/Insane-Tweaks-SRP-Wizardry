package com.spege.srpwizcore.effectpanel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * One-time client setup for the effect panel, called from {@code SrpWizCore.postInit} on the client.
 *
 * <p>🚨 Class-level client-only. The call site must stay behind {@code event.getSide().isClient()}:
 * an {@code invokestatic} is only resolved when it runs, so the guard is what keeps a dedicated
 * server from ever loading this class. The draw handler itself needs no such call - it is an
 * annotation-driven {@code Side.CLIENT} subscriber.
 */
@SideOnly(Side.CLIENT)
public final class EffectPanelClient {

    private EffectPanelClient() {
    }

    public static void init() {
        IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        if (resources instanceof IReloadableResourceManager) {
            // Registering calls the listener once immediately - that is the first read of the file.
            ((IReloadableResourceManager) resources).registerReloadListener(EffectDescriptions.INSTANCE);
        } else {
            EffectDescriptions.reload();
        }
    }
}
