package com.spege.insanetweaks.network;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.skills.ChargeJumpHandler;
import com.spege.insanetweaks.skills.SkillsModule;
import com.spege.insanetweaks.skills.TraitBase;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client -> server notification that a Coiled Spring leap just launched.
 *
 * <p>Carries only the charge fraction. The upward motion is already applied client-side; the
 * server needs this purely to arm the fall-damage waiver, which it computes itself. The
 * server re-checks the trait and clamps the value, so a forged packet can at best waive its
 * own fall damage — which a player holding the trait could do legitimately anyway.
 */
public class PacketChargeJump implements IMessage {

    private float charge;

    public PacketChargeJump() {
    }

    public PacketChargeJump(float charge) {
        this.charge = charge;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.charge = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(this.charge);
    }

    public static class Handler implements IMessageHandler<PacketChargeJump, IMessage> {

        @Override
        public IMessage onMessage(PacketChargeJump message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> this.handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketChargeJump message) {
            if (!ModConfig.modules.enableSkillsModule) {
                return;
            }
            if (!TraitBase.hasTrait(player, "reskillable:agility", SkillsModule.DOMAIN + ":coiled_spring")) {
                return;
            }

            float charge = message.charge;
            if (Float.isNaN(charge) || charge <= 0.0F) {
                return;
            }
            if (charge > 1.0F) {
                charge = 1.0F;
            }

            ChargeJumpHandler.armFallProtection(player, charge);
        }
    }
}
