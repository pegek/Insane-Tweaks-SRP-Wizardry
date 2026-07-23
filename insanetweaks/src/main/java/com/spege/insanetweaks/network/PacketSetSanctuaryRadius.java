package com.spege.insanetweaks.network;

import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -> server: set a Creative Sanctuary's forced radius (16..256). */
public class PacketSetSanctuaryRadius implements IMessage {

    private BlockPos pos;
    private int radius;

    public PacketSetSanctuaryRadius() {}

    public PacketSetSanctuaryRadius(BlockPos pos, int radius) {
        this.pos = pos;
        this.radius = radius;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.radius = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(radius);
    }

    public static class Handler implements IMessageHandler<PacketSetSanctuaryRadius, IMessage> {
        @Override
        public IMessage onMessage(PacketSetSanctuaryRadius msg, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    // Creative-only guard: only creative-mode players may reconfigure.
                    if (!player.capabilities.isCreativeMode) { return; }
                    if (player.getDistanceSq(msg.pos) > 64.0D) { return; }
                    TileEntity te = player.world.getTileEntity(msg.pos);
                    if (te instanceof TileEntitySanctuaryCore) {
                        ((TileEntitySanctuaryCore) te).setCreativeRadius(msg.radius);
                    }
                }
            });
            return null;
        }
    }
}
