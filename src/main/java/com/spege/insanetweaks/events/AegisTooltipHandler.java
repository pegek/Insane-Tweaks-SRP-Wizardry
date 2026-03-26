package com.spege.insanetweaks.events;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.TooltipUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class AegisTooltipHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;
        boolean isLiving = stack.getItem() == ModItems.LIVING_AEGIS;
        boolean isSentient = stack.getItem() == ModItems.SENTIENT_AEGIS;
        if (!isLiving && !isSentient)
            return;

        List<String> tooltip = event.getToolTip();

        // 1. Remove garbage from default Ancient Spellcraft Battlemage Shield
        for (int i = tooltip.size() - 1; i >= 1; i--) {
            String line = tooltip.get(i).toLowerCase();
            if (line.contains("battlemage") ||
                    line.contains("mana source") ||
                    line.contains("wand") ||
                    line.contains("spell") ||
                    line.contains("runeword") ||
                    line.contains("ancient spellcraft") ||
                    line.contains("item.insanetweaks") ||
                    line.contains("aegis.desc") ||
                    line.contains(".desc")) {
                tooltip.remove(i);
            }
        }

        // 2. Find position for injection
        int insertIdx = TooltipUtils.getInsertIdx(tooltip);

        List<String> myLines = new ArrayList<>();

        // 3. Damage Blocked Counter
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null && nbt.hasKey("AegisDamageBlocked")) {
                float blocked = nbt.getFloat("AegisDamageBlocked");
                if (blocked >= 10000.0f) {
                    // W A R D E N Easter Egg
                    String warden = TextFormatting.DARK_BLUE + "W " + TextFormatting.DARK_AQUA + "A " +
                            TextFormatting.BLUE + "R " + TextFormatting.AQUA + "D " +
                            TextFormatting.WHITE + "E " + TextFormatting.DARK_AQUA + "N";
                    tooltip.add(1, TextFormatting.BLUE + "---> " + warden);
                } else {
                    tooltip.add(1, TextFormatting.BLUE + "---> " + TextFormatting.BLUE + String.format("%.1f", blocked)
                            + " / 1500.0 Blocked");
                }
                insertIdx++;
            }
        }

        // 4. Descriptions
        myLines.add("");
        if (isSentient) {
            myLines.add(TextFormatting.GRAY + "The ulterior evolution of Runic shield. It has gained a hunger for "
                    + TextFormatting.DARK_RED + "BLOOD" + TextFormatting.GRAY + ".");
        } else {
            myLines.add(TextFormatting.GRAY + "A Runic shield infused with parasitic parts. It feels "
                    + TextFormatting.DARK_RED + "Hungry" + TextFormatting.GRAY + ".");
        }
        myLines.add("");

        myLines.add(TextFormatting.GOLD + "- Extinguishes you when on fire.");
        myLines.add(TextFormatting.GOLD + "- Cannot be disabled by weapons normally effective against shields.");

        if (isSentient) {
            myLines.add(TextFormatting.GOLD + "- Punishes attackers with Fire, " + TextFormatting.DARK_GREEN
                    + "Corrosion I" + TextFormatting.GOLD + ", and " + TextFormatting.DARK_PURPLE + "Immaleable I"
                    + TextFormatting.GOLD + " when blocking melee damage.");
            myLines.add(TextFormatting.GOLD
                    + "- Successful blocks grant increasing Blazing Might effect, but any damage received takes it away.");
        } else {
            myLines.add(TextFormatting.GOLD + "- Punishes attackers with Fire and " + TextFormatting.DARK_PURPLE
                    + "Immaleable I" + TextFormatting.GOLD + " when blocking melee damage.");
        }

        myLines.add(TextFormatting.RED + "- Enemy attacks from behind deal 50% more damage.");

        myLines.add("");
        myLines.add(TextFormatting.DARK_GREEN + "Sneak+Right click to place charms");
        myLines.add(TextFormatting.DARK_GRAY + "inspired by Enigmatic legacy");

        tooltip.addAll(insertIdx, myLines);
    }
}
