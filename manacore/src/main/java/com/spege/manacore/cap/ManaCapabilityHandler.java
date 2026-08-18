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

    /**
     * Attaches the capability to every EntityPlayer, client-side instances included, even though
     * the client never reads it (it keeps its own state in ManaClientState). This is deliberate:
     * it keeps ManaCapabilities.get(player) behave identically on both sides, so any future
     * client-side code that reaches for it without checking the side first gets a live capability
     * instead of a null it has to guard against.
     */
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
     * <p>
     * This handler deliberately does NOT call {@link ManaAttributes#refreshProgressionModifier}
     * after copying the bonus into the new pool. That is safe only because of the calling
     * contract with {@code onRespawn}: {@code PlayerEvent.Clone} fires inside
     * {@code PlayerList.respawnPlayer} (via {@code recreatePlayerEntity}), and
     * {@code PlayerRespawnEvent} fires unconditionally at the end of that same synchronous
     * method - so {@code onRespawn} -> {@code refreshAndSync} -> refreshProgressionModifier still
     * runs moments later, in the same tick, before the client sees anything. Removing either
     * respawn-path call to {@code refreshAndSync} as "redundant" would silently break this: the
     * new pool's progression bonus would then never be reflected in the MAX_MANA attribute.
     * <p>
     * Known limitation: this reasoning only holds for the vanilla Forge respawn path. Any mod
     * that reconstructs an {@code EntityPlayerMP} outside of {@code PlayerList.respawnPlayer}
     * (a custom respawn, a non-standard cross-dimensional teleport) bypasses
     * {@code PlayerEvent.Clone} entirely. In that case the mana pool's survival depends solely on
     * whether that mod copies the full entity NBT (Forge capabilities then transfer on their own
     * via {@code ManaPoolProvider.deserializeNBT}) or only a hand-picked subset of fields. If mana
     * appears to reset after a teleport performed by some other mod, look there first.
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
