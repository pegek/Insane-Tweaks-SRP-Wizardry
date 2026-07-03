package com.spege.insanetweaks.network;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.SentinelCommandMode;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketSentinelCommand implements IMessage {

    public static final int ACTION_FOLLOW = 0;
    public static final int ACTION_GUARD_HERE = 1;
    public static final int ACTION_OPEN_LOOT = 2;

    private int entityId;
    private int actionId;

    public PacketSentinelCommand() {
    }

    public PacketSentinelCommand(int entityId, int actionId) {
        this.entityId = entityId;
        this.actionId = actionId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.actionId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.actionId);
    }

    public static class Handler implements IMessageHandler<PacketSentinelCommand, IMessage> {

        @Override
        public IMessage onMessage(PacketSentinelCommand message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> this.handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketSentinelCommand message) {
            Entity entity = player.world.getEntityByID(message.entityId);
            if (!(entity instanceof EntitySentinel)) {
                return;
            }

            EntitySentinel sentinel = (EntitySentinel) entity;
            if (!sentinel.canPlayerCommand(player)) {
                return;
            }

            if (sentinel.getOwnerId() == null) {
                sentinel.setOwnerId(player.getUniqueID());
            }

            if (message.actionId == ACTION_FOLLOW) {
                sentinel.setCommandMode(SentinelCommandMode.FOLLOW);
                sentinel.setAttackTarget(null);
                sentinel.getNavigator().clearPath();
                player.sendStatusMessage(new TextComponentTranslation("entity.insanetweaks.sentinel.mode.follow"), true);
                return;
            }

            if (message.actionId == ACTION_GUARD_HERE) {
                sentinel.setCommandMode(SentinelCommandMode.GUARD);
                sentinel.setGuardAnchor(new BlockPos(sentinel));
                sentinel.setAttackTarget(null);
                sentinel.getNavigator().clearPath();
                player.sendStatusMessage(new TextComponentTranslation("entity.insanetweaks.sentinel.mode.guard"), true);
                return;
            }

            if (message.actionId == ACTION_OPEN_LOOT) {
                InsaneTweaksNetwork.CHANNEL.sendTo(new PacketOpenSentinelLoot(message.entityId,
                        sentinel.writeLootInventoryToNBT()), player);
            }
        }
    }
}
