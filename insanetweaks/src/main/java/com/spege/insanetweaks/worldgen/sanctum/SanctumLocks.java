package com.spege.insanetweaks.worldgen.sanctum;

import java.util.List;
import java.util.UUID;

import electroblob.wizardry.packet.PacketSyncArcaneLock;
import electroblob.wizardry.packet.WizardryPacketHandler;
import electroblob.wizardry.spell.ArcaneLock;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * A sanctum's containers wear EBW's own Arcane Lock (tile data {@code arcaneLockOwner}), owned by
 * a UUID derived from the sanctum id - stored with {@code setUniqueId}, the only form EBW reads - so EBW's handlers refuse opening, breaking and blasting and
 * EBW renders the lock. The lock is lifted - key removed, EBW's sync packet sent - when the last
 * anchor of the sanctum falls, the way EBW's shrine core does it.
 */
public final class SanctumLocks {

    public static final String SANCTUM_KEY = "InsaneTweaksSanctum";
    private static final long OWNER_SALT = 0x53414E4354554DL;
    private static final double SYNC_RANGE = 64.0D;

    private SanctumLocks() {
    }

    public static UUID ownerOf(long sanctumId) {
        return new UUID(sanctumId, OWNER_SALT);
    }

    public static void lock(TileEntity te, long sanctumId) {
        NBTTagCompound data = te.getTileData();
        data.setUniqueId(ArcaneLock.NBT_KEY, ownerOf(sanctumId));
        data.setLong(SANCTUM_KEY, sanctumId);
        te.markDirty();
    }

    /** The sanctum id of a sanctum-locked tile entity, or null. */
    public static Long sanctumOf(TileEntity te) {
        NBTTagCompound data = te.getTileData();
        return data.hasKey(SANCTUM_KEY) && data.hasUniqueId(ArcaneLock.NBT_KEY) ? Long.valueOf(data.getLong(SANCTUM_KEY))
                : null;
    }

    public static void unlock(World world, BlockPos pos, TileEntity te) {
        NBTTagCompound data = te.getTileData();
        long id = data.getLong(SANCTUM_KEY);
        data.removeTag(ArcaneLock.NBT_KEY + "Most");
        data.removeTag(ArcaneLock.NBT_KEY + "Least");
        data.removeTag(SANCTUM_KEY);
        te.markDirty();
        WizardryPacketHandler.net.sendToAllAround(new PacketSyncArcaneLock.Message(pos, false, ownerOf(id)),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D, SYNC_RANGE));
    }

    public static boolean isAnchorDone(World world, long sanctumId, BlockPos anchor) {
        return SanctumWorldData.get(world).isAnchorDone(sanctumId, anchor.toLong());
    }

    /** Records one defeated anchor; lifts every lock of the sanctum when it was the last. */
    public static void anchorDone(World world, long sanctumId, BlockPos anchor) {
        if (world.isRemote || !SanctumWorldData.get(world).anchorDone(sanctumId, anchor.toLong())) {
            return;
        }
        List<Long> containers = SanctumWorldData.get(world).containers(sanctumId);
        BlockPos announce = null;
        for (Long packed : containers) {
            BlockPos pos = BlockPos.fromLong(packed.longValue());
            if (announce == null) {
                announce = pos;
            }
            if (!world.isBlockLoaded(pos)) {
                continue; // unlocked lazily by SanctumLockHandler on first touch
            }
            TileEntity te = world.getTileEntity(pos);
            Long id = te == null ? null : sanctumOf(te);
            if (id != null && id.longValue() == sanctumId) {
                unlock(world, pos, te);
            }
        }
        if (announce == null) {
            announce = anchor;
        }
        for (EntityPlayer player : world.playerEntities) {
            if (player.getDistanceSq(announce) < SYNC_RANGE * SYNC_RANGE) {
                player.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sanctum.unsealed"), true);
            }
        }
    }
}
