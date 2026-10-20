package com.spege.srpwizcore.effectpanel;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
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

    /** Just Enough Effects: its mod id, and the class FML registered on the event bus for it. */
    private static final String JEE_MODID = "effectinfo";
    private static final String JEE_RENDER = "com.pinball3d.effectinfo.Render";

    public static void init() {
        IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        if (resources instanceof IReloadableResourceManager) {
            // Registering calls the listener once immediately - that is the first read of the file.
            ((IReloadableResourceManager) resources).registerReloadListener(EffectDescriptions.INSTANCE);
        } else {
            EffectDescriptions.reload();
        }
        if (SrpWizCoreConfig.effectPanel.suppressJeeRenderer) {
            suppressJeeRenderer();
        }
        ClientCommandHandler.instance.registerCommand(new CommandEffectDesc());
    }

    /**
     * Takes Just Enough Effects' popup off the event bus. JEE draws it in {@code DrawScreenEvent.Pre},
     * before the inventory, so the effect tiles cover it in every pack; the panel replaces it. JEE
     * itself stays installed - its lang keys are one of the panel's description sources.
     *
     * <p>JEE's {@code Render} is a {@code @Mod.EventBusSubscriber}, so FML registered it keyed by its
     * {@code Class} object, and unregistering that same object removes it cleanly. Postinit runs after
     * FML's automatic subscriber registration. Named only as a string: no compile dependency on JEE.
     * Failing here must never cost the panel - at worst JEE's unreadable popup stays underneath it.
     */
    private static void suppressJeeRenderer() {
        if (!Loader.isModLoaded(JEE_MODID)) {
            SrpWizCore.LOGGER.info("[srpwizcore] Just Enough Effects not installed - nothing to suppress.");
            return;
        }
        try {
            Class<?> render = Class.forName(JEE_RENDER, false, EffectPanelClient.class.getClassLoader());
            MinecraftForge.EVENT_BUS.unregister(render);
            SrpWizCore.LOGGER.info("[srpwizcore] Just Enough Effects' renderer unregistered - the effect panel replaces it.");
        } catch (Throwable t) {
            SrpWizCore.LOGGER.warn("[srpwizcore] Could not unregister Just Enough Effects' renderer ({}) - "
                    + "its popup may still appear under the effect panel.", t.toString());
        }
    }
}
