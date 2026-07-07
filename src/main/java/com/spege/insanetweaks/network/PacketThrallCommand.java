package com.spege.insanetweaks.network;

import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallMode;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketThrallCommand implements IMessage {

    public static final int ACTION_FOLLOW      = 0;
    public static final int ACTION_STAY        = 1;
    public static final int ACTION_DROP_ITEMS  = 2;
    public static final int ACTION_DISMISS     = 3;
    public static final int ACTION_SET_HOME    = 4;
    public static final int ACTION_WOODCUTTING = 5;
    public static final int ACTION_MINESHAFT   = 6;
    public static final int ACTION_OPEN_INV    = 7; // triggers gui open via IGuiHandler
    public static final int ACTION_FARMING     = 8;
    public static final int ACTION_PORTER      = 9;
    public static final int ACTION_RETURN_HOME = 10;
    public static final int ACTION_COLLECTING  = 11;

    private int entityId;
    private int actionId;

    public PacketThrallCommand() {}

    public PacketThrallCommand(int entityId, int actionId) {
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

    // -------------------------------------------------------------------------
    // Server-side handler
    // -------------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketThrallCommand, IMessage> {

        @Override
        public IMessage onMessage(PacketThrallCommand message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketThrallCommand msg) {
            Entity entity = player.world.getEntityByID(msg.entityId);
            if (!(entity instanceof EntityThrallMinion)) return;

            EntityThrallMinion thrall = (EntityThrallMinion) entity;
            if (!thrall.canPlayerCommand(player)) return;

            switch (msg.actionId) {
                case ACTION_FOLLOW:
                    thrall.setMode(ThrallMode.FOLLOW);
                    thrall.setStatusText("Following");
                    thrall.setAttackTarget(null);
                    thrall.getNavigator().clearPath();
                    thrall.playSoundFollow();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.follow"), true);
                    break;

                case ACTION_STAY:
                    thrall.setMode(ThrallMode.STAY);
                    thrall.setStatusText("Staying");
                    thrall.setAttackTarget(null);
                    thrall.getNavigator().clearPath();
                    thrall.playSoundOrder();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.stay"), true);
                    break;



                case ACTION_DISMISS:
                    // Spawn DARK_MAGIC particles and remove
                    if (!thrall.world.isRemote) {
                        thrall.world.spawnParticle(
                                net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,
                                thrall.posX, thrall.posY + 1.0, thrall.posZ,
                                0.0, 0.0, 0.0);
                    }
                    // Clear UUID from slot but preserve inventory backup for re-summon
                    int dismissSlot = thrall.getThrallSlot();
                    if (dismissSlot > 0) {
                        com.spege.insanetweaks.entities.ThrallSlotManager.saveSlot(player, thrall);
                        com.spege.insanetweaks.entities.ThrallSlotManager.clearSlotUUID(player, dismissSlot);
                    }
                    thrall.getThrallInventory().dropAllItems(player.world, thrall.posX, thrall.posY + 0.5, thrall.posZ);
                    thrall.setDead();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.action.dismiss.done"), true);
                    break;

                case ACTION_SET_HOME:
                    thrall.setHomePoint(new BlockPos(thrall));
                    thrall.setStatusText("Home Set");
                    thrall.playSoundOrder();
                    thrall.smartDeposit();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.action.set_home.done"), true);
                    break;

                case ACTION_WOODCUTTING:
                    thrall.setMode(ThrallMode.WOODCUTTING);
                    thrall.setStatusText("Woodcutting...");
                    thrall.playSoundOrder();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.woodcutting"), true);
                    break;

                case ACTION_MINESHAFT:
                    thrall.resetMineshaftAI(); // reset shaft state for fresh start
                    thrall.setMode(ThrallMode.MINESHAFT);
                    thrall.setStatusText("Mining...");
                    thrall.playSoundOrder();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.mineshaft"), true);
                    break;

                case ACTION_FARMING:
                    if (!com.spege.insanetweaks.config.ModConfig.thrall.farming.enableFarmingMode) {
                        player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.disabled"), true);
                        break;
                    }
                    thrall.setMode(ThrallMode.FARMING);
                    thrall.setStatusText("Farming...");
                    thrall.playSoundOrder();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.farming"), true);
                    break;

                case ACTION_PORTER:
                    if (!com.spege.insanetweaks.config.ModConfig.thrall.porter.enablePorterMode) {
                        player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.disabled"), true);
                        break;
                    }
                    thrall.setMode(ThrallMode.PORTER);
                    thrall.setStatusText("Standing by...");
                    thrall.playSoundOrder();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.porter"), true);
                    break;

                case ACTION_COLLECTING:
                    if (!com.spege.insanetweaks.config.ModConfig.thrall.collecting.enableCollectingMode) {
                        player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.disabled"), true);
                        break;
                    }
                    thrall.startOrResumeCollectingMode();
                    thrall.playSoundOrder();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.collecting"), true);
                    break;

                case ACTION_RETURN_HOME:
                    if (thrall.getHomePoint() == null) {
                        player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.action.return_home.no_home"), true);
                        break;
                    }
                    thrall.commandReturnHome();
                    thrall.playSoundOrder();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.action.return_home.done"), true);
                    break;

                case ACTION_OPEN_INV:
                    // Open via IGuiHandler — Forge syncs slots server→client automatically
                    // x parameter carries the entity ID
                    player.openGui(com.spege.insanetweaks.InsaneTweaksMod.INSTANCE,
                            com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_THRALL_INV,
                            player.world, thrall.getEntityId(), 0, 0);
                    break;
            }
        }
    }
}
