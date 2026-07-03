package com.spege.insanetweaks.network;

import com.spege.insanetweaks.client.gui.GuiSentinelLoot;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketOpenSentinelLoot implements IMessage {

    private int entityId;
    private NBTTagCompound payload;

    public PacketOpenSentinelLoot() {
    }

    public PacketOpenSentinelLoot(int entityId, NBTTagList lootList) {
        this.entityId = entityId;
        this.payload = new NBTTagCompound();
        this.payload.setTag("Loot", lootList);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.payload = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.entityId);
        ByteBufUtils.writeTag(buf, this.payload);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketOpenSentinelLoot, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenSentinelLoot message, MessageContext ctx) {
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> {
                NonNullList<ItemStack> loot = NonNullList.withSize(GuiSentinelLoot.SLOT_COUNT, ItemStack.EMPTY);
                if (message.payload != null && message.payload.hasKey("Loot", 9)) {
                    NBTTagList lootList = message.payload.getTagList("Loot", 10);
                    for (int i = 0; i < lootList.tagCount(); i++) {
                        NBTTagCompound slotTag = lootList.getCompoundTagAt(i);
                        int slot = slotTag.getInteger("Slot");
                        if (slot < 0 || slot >= loot.size()) {
                            continue;
                        }

                        loot.set(slot, new ItemStack(slotTag.getCompoundTag("Stack")));
                    }
                }

                minecraft.displayGuiScreen(new GuiSentinelLoot(message.entityId, loot));
            });
            return null;
        }
    }
}
