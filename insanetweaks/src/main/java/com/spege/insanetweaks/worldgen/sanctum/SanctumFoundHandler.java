package com.spege.insanetweaks.worldgen.sanctum;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Grants "Something Sleeps Below" the first time a player stands within 8 blocks of a sanctum's
 * footprint, at any height - so the warning comes at the surface entrance, before the descent.
 * Checked every 40 ticks per player, and only until the player has it.
 */
public class SanctumFoundHandler {

    private static final int INTERVAL = 40;
    private static final int MARGIN = 8;
    private static final long WARN_COOLDOWN_MS = 10L * 60L * 1000L;
    /** Last warning per player, wall clock; server memory only. */
    private static final Map<UUID, Long> LAST_WARNING = new HashMap<UUID, Long>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player.ticksExisted % INTERVAL != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!SanctumPlacement.dimensionAllowed(player.world)) {
            return;
        }
        int x = (int) Math.floor(player.posX);
        int z = (int) Math.floor(player.posZ);
        SanctumWorldData data = SanctumWorldData.get(player.world);
        if (!SanctumAdvancements.has(player, SanctumAdvancements.FOUND) && data.nearSanctum(x, z, MARGIN)) {
            SanctumAdvancements.grant(player, SanctumAdvancements.FOUND);
        }
        if (data.nearUnclearedSanctum(x, z, MARGIN)) {
            warn(player);
        }
    }

    /** BEWARE title, subtitle and a chat warning - at most once per player per ten minutes. */
    private static void warn(EntityPlayerMP player) {
        long now = System.currentTimeMillis();
        Long last = LAST_WARNING.get(player.getUniqueID());
        if (last != null && now - last.longValue() < WARN_COOLDOWN_MS) {
            return;
        }
        LAST_WARNING.put(player.getUniqueID(), Long.valueOf(now));
        ITextComponent title = new TextComponentTranslation("msg.insanetweaks.sanctum.beware.title");
        title.getStyle().setColor(TextFormatting.DARK_RED).setBold(Boolean.TRUE);
        ITextComponent subtitle = new TextComponentTranslation("msg.insanetweaks.sanctum.beware.subtitle");
        subtitle.getStyle().setColor(TextFormatting.RED);
        player.connection.sendPacket(new SPacketTitle(10, 80, 20));
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE, subtitle));
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, title));
        ITextComponent chat = new TextComponentTranslation("msg.insanetweaks.sanctum.beware.chat");
        chat.getStyle().setColor(TextFormatting.DARK_PURPLE);
        player.sendMessage(chat);
    }
}
