package com.spege.insanetweaks.events;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.SentientCodexCategory;
import com.spege.insanetweaks.enchant.EnchantmentSentientCodex;
import com.spege.insanetweaks.enchant.SentientCodexHandler;
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
 * Shows how well fed a Sentient Codex item is: experience banked, what the next growth step costs,
 * how many steps it has taken, and a warning once it starts starving.
 *
 * <p>All of it comes from stack NBT ({@code sentientcodex_fed}, {@code sentientcodex_lastfed},
 * {@code sentientcodex_boost}), which the server already syncs to the client as part of the stack -
 * so this needs no packet of its own.
 *
 * <p>This is <b>not</b> the deleted handler of the same name. That one duplicated the
 * "- Ashen Legacy" property line, which {@link GlobalPropertyTooltipHandler} now draws for every
 * grant route. A feeding status is something no property block has anything to say about.
 *
 * <p>🚨 Class-level {@code @SideOnly(Side.CLIENT)}: instantiating this on a dedicated server throws
 * at <i>load</i>, so its registration in {@code InsaneTweaksMod#init} must stay inside the
 * {@code event.getSide() == Side.CLIENT} guard.
 */
@SideOnly(Side.CLIENT)
public class SentientCodexTooltipHandler {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.isItemEnchanted()) {
            return;
        }
        if (EnchantmentSentientCodex.getSentientCodexLevel(stack) <= 0) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }

        SentientCodexCategory cfg = ModConfig.enchantments.sentientCodex;
        long fed = tag.getLong(EnchantmentSentientCodex.FED_TAG);
        int steps = tag.getInteger(EnchantmentSentientCodex.LAST_BOOST_TAG);

        List<String> lines = new ArrayList<String>();
        lines.add(TextFormatting.DARK_PURPLE + "- Codex fed: " + TextFormatting.GRAY + fed + " XP"
                + TextFormatting.DARK_GRAY + " (" + steps + "/" + cfg.maxSteps + " steps)");

        if (steps < cfg.maxSteps) {
            long needed = SentientCodexHandler.costOfSteps(steps + 1, cfg) - fed;
            lines.add(TextFormatting.DARK_GRAY + "  next step in " + Math.max(0L, needed) + " XP");
        }

        // The tooltip can be drawn from a screen with no player on the event, so fall back to the
        // client world - both report the same total world time.
        World world = event.getEntityPlayer() != null
                ? event.getEntityPlayer().world
                : Minecraft.getMinecraft().world;
        if (world != null) {
            int severity = SentientCodexHandler.starvationSeverity(stack, world.getTotalWorldTime(), cfg);
            if (severity > 0) {
                lines.add(TextFormatting.RED + "  starving (" + severity + "/"
                        + cfg.maxStarvationSeverity + ")");
            }
        }

        event.getToolTip().addAll(TooltipUtils.getInsertIdx(event.getToolTip()), lines);
    }
}
