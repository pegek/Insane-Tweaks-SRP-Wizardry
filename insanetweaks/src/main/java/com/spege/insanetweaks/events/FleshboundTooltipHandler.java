package com.spege.insanetweaks.events;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.FleshboundCategory;
import com.spege.insanetweaks.util.TooltipUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders Fleshbound's <b>status</b> on the tooltip: the regrowth countdown while the bond is
 * severed, and the rip-out state while {@code fleshbound.enableRipOut} is on.
 *
 * <p>Not the "- Fleshbound" property line - {@link GlobalPropertyTooltipHandler} draws that from the
 * {@code grip} advanced property, and printing it here as well would show it twice. A status is
 * exactly what the property block has nothing to say about.
 *
 * <p>This replaces a block that used to live in {@link SpellbladeTooltipHandler}, gated on the one
 * registry name {@code insanetweaks:sentient_spellblade} and with the kill requirement hardcoded to
 * 50. Any stack carrying {@code grip} deserves the countdown, and the numbers have to come from the
 * config that actually sets them, so it moved here and became general.
 *
 * <p>🚨 Class-level {@code @SideOnly(Side.CLIENT)}: {@code new FleshboundTooltipHandler()} is fatal
 * on a dedicated server (Forge's SideTransformer throws at <i>load</i>), so the registration in
 * {@code InsaneTweaksMod#init} MUST stay inside its {@code event.getSide() == Side.CLIENT} guard.
 */
@SideOnly(Side.CLIENT)
public class FleshboundTooltipHandler {

    /** Ticks in a minute of world time, for rendering the regrowth countdown. */
    private static final double TICKS_PER_MINUTE = 1200.0D;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        FleshboundCategory cfg = ModConfig.fleshbound;
        if (!cfg.showStatusTooltip) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !FleshboundEventHandler.isMechanicUnlocked(stack)) {
            return;
        }
        // The tooltip can be drawn from an inventory screen with no player attached to the event,
        // so fall back to the client world - both give the same total world time.
        World world = event.getEntityPlayer() != null
                ? event.getEntityPlayer().world
                : Minecraft.getMinecraft().world;
        if (world == null) {
            return;
        }

        List<String> lines = new ArrayList<String>();
        long now = world.getTotalWorldTime();
        NBTTagCompound tag = stack.getTagCompound();

        long rippedUntil = FleshboundEventHandler.rippedUntil(stack);
        if (now < rippedUntil) {
            int seconds = (int) Math.ceil((rippedUntil - now) / 20.0D);
            lines.add(TextFormatting.RED + "- Torn loose: " + TextFormatting.GRAY + seconds + "s");
        } else if (cfg.enableRipOut) {
            lines.add(TextFormatting.GRAY + "- Grip: "
                    + FleshboundEventHandler.recoveryCount(stack) + "/" + cfg.recoveryLimit
                    + " recoveries");
        }

        if (tag != null && tag.hasKey("FleshboundRegrowTime")) {
            long remainingTicks = tag.getLong("FleshboundRegrowTime") - now;
            int remainingMinutes = Math.max(0, (int) Math.ceil(remainingTicks / TICKS_PER_MINUTE));

            // Kills REMAINING, not a progress fraction. The requirement was stamped at death as
            // "kills then + severRegrowKills", so a denominator taken from today's config would be
            // wrong for every weapon severed before the value was last changed.
            int needed = Math.max(0, tag.getInteger("FleshboundRegrowKills")
                    - tag.getInteger("SentientKills"));

            // Test the two conditions directly rather than asking isFleshbound: that also returns
            // false while the weapon is torn loose, which would print a spent "[0m] / [0 kills]"
            // countdown from some earlier death every time a rip-out happened.
            if (remainingTicks > 0L || needed > 0) {
                lines.add(TextFormatting.GRAY + "- Fleshbound Regrowth: [" + remainingMinutes
                        + "m] / [" + needed + " kills]");
            }
        }

        if (!lines.isEmpty()) {
            event.getToolTip().addAll(TooltipUtils.getInsertIdx(event.getToolTip()), lines);
        }
    }
}
