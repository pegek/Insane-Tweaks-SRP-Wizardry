package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.slots.SlotSnapshotStore;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Binds the slot snapshot taken at death to the grave that was built for it.
 *
 * <p>The snapshot has to be written during {@code LivingDeathEvent}, where the inventory still has
 * indices — but no grave exists yet, so there is nothing to key it to. {@code DeathHandler} places
 * the grave a few instructions later and stamps it here with {@code TimeHelper.systemTime()},
 * which is plain {@code System.currentTimeMillis()} and is exactly what
 * {@code RestoreInventoryEvent.getOwnerDeathTime()} returns later. Capturing that value is what
 * lets two deaths in a row be recovered in either order and still land in their own layout.
 *
 * <p>Verified with {@code javap -p -c}: {@code DeathHandler} offset 1078-1086 loads the fresh
 * {@code TileEntityPlayerGrave}, the player, {@code TimeHelper.systemTime()} and the needAccess
 * flag, then invokes this method. It is the only caller of the three-argument overload.
 *
 * <p>Not load-bearing: {@code SlotSnapshotStore} falls back to matching a pending snapshot by
 * capture time, so if a Tombstone update ever moves this method the feature degrades rather than
 * disappears.
 */
@Mixin(targets = "ovh.corail.tombstone.tileentity.TileEntityPlayerGrave", remap = false)
public abstract class MixinTombstonePlayerGrave {

    @Inject(method = "setOwner(Lnet/minecraft/entity/player/EntityPlayer;JZ)V",
            at = @At("HEAD"), remap = false)
    private void tombtweaks$bindSlotSnapshot(EntityPlayer player, long deathTime,
            boolean needAccess, CallbackInfo ci) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks || !TombTweaksConfig.tombstone.restoreOriginalSlots) {
            return;
        }
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        SlotSnapshotStore.bindPending(player, deathTime);
    }
}
