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
    public static final int ACTION_OPEN_GUI = 3;
    public static final int ACTION_STANCE_TOGGLE = 4;
    public static final int ACTION_RADIUS_UP = 5;
    public static final int ACTION_RADIUS_DOWN = 6;
    public static final int ACTION_TOGGLE_DEPOSIT = 7;
    public static final int ACTION_TOGGLE_PICKUP_FILTER = 8;

    /** Guard-radius step per button press (blocks). */
    public static final int RADIUS_STEP = 4;

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

            if (message.actionId == ACTION_OPEN_GUI) {
                // Container-backed GUI — vanilla slot sync handles the loot contents.
                player.openGui(com.spege.insanetweaks.InsaneTweaksMod.INSTANCE,
                        com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_SENTINEL,
                        player.world, sentinel.getEntityId(), 0, 0);
                return;
            }

            if (message.actionId == ACTION_STANCE_TOGGLE) {
                sentinel.setAggressiveStance(!sentinel.isAggressiveStance());
                return;
            }

            if (message.actionId == ACTION_RADIUS_UP) {
                sentinel.setGuardRadius(sentinel.getGuardRadius() + RADIUS_STEP);
                return;
            }

            if (message.actionId == ACTION_RADIUS_DOWN) {
                sentinel.setGuardRadius(sentinel.getGuardRadius() - RADIUS_STEP);
                return;
            }

            if (message.actionId == ACTION_TOGGLE_DEPOSIT) {
                sentinel.setAutoDeposit(!sentinel.isAutoDeposit());
                return;
            }

            if (message.actionId == ACTION_TOGGLE_PICKUP_FILTER) {
                sentinel.setCollectAll(!sentinel.isCollectAll());
            }
        }
    }
}
