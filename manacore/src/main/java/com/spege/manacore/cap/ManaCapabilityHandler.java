package com.spege.manacore.cap;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.net.ManaNetwork;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaCapabilityHandler {

    private ManaCapabilityHandler() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(ManaPoolProvider.KEY, new ManaPoolProvider());
        }
    }

    /**
     * Cloning on death and on passing through the End. `progressionBonus` ALWAYS survives -
     * permanent progress must not be lost to a single death. `current` resets according to the
     * config, but only on a real death (wasDeath), not when returning from the End.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        IManaPool oldPool = ManaCapabilities.get(event.getOriginal());
        IManaPool newPool = ManaCapabilities.get(event.getEntityPlayer());
        if (oldPool == null || newPool == null) {
            return;
        }

        newPool.setProgressionBonus(oldPool.getProgressionBonus());

        if (event.isWasDeath() && ManaCoreConfig.pool.resetCurrentOnDeath) {
            newPool.setCurrent(0.0D);
        } else {
            newPool.setCurrent(oldPool.getCurrent());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        refreshAndSync(event.player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent event) {
        refreshAndSync(event.player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerChangedDimensionEvent event) {
        refreshAndSync(event.player);
    }

    private static void refreshAndSync(EntityPlayer player) {
        if (player.world.isRemote || !(player instanceof EntityPlayerMP)) {
            return;
        }
        ManaAttributes.refreshProgressionModifier(player);
        // syncNow, not syncIfDirty: when the player enters the world the client MUST get the
        // value, even if the pool has not changed since it was last saved.
        ManaNetwork.syncNow((EntityPlayerMP) player);
    }
}
