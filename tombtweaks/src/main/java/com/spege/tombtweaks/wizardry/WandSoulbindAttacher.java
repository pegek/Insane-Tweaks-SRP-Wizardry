package com.spege.tombtweaks.wizardry;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spege.tombtweaks.TombstoneTweaks;

import electroblob.wizardry.item.ItemWand;
import ovh.corail.tombstone.capability.TBSoulConsumerProvider;

/**
 * Gives every wand stack Tombstone's soul-consumer capability, so a grave will talk to it.
 *
 * <p>🚨 This fires for <b>every ItemStack that is ever constructed</b>, which on a loaded server is
 * a great many per tick. It must stay exactly this cheap: one {@code instanceof} and return. Do not
 * add config reads, registry lookups or logging here — all of that belongs in
 * {@link WandSoulbindConsumer}, which only runs when a player actually uses a grave.
 *
 * <p>Deliberately not a {@code @Mod.EventBusSubscriber}: this class names Wizardry and Tombstone
 * types, so it must not be loaded when either mod is absent. {@code TombstoneTweaks.init} registers
 * an instance behind a presence check, and passing it as an {@code Object} keeps the verifier from
 * resolving the class on the way in.
 */
public class WandSoulbindAttacher {

    private static final ResourceLocation KEY =
            new ResourceLocation(TombstoneTweaks.MODID, "wand_soulbind");

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        // getItem() is set before Forge fires this; isEmpty() is not safe to call here, and is not
        // needed - an empty stack holds air, which is not a wand.
        if (!(event.getObject().getItem() instanceof ItemWand)) {
            return;
        }
        event.addCapability(KEY, new TBSoulConsumerProvider(new WandSoulbindConsumer()));
    }
}
