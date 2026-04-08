package com.spege.insanetweaks.events;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.oblivioussp.spartanweaponry.api.IWeaponPropertyContainer;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty;

// No imports needed for custom armor if using qualified names, or add them here:
import com.spege.insanetweaks.items.armor.BattleMageArmorItem;
import com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * CLIENT-ONLY event handler that injects the full custom tooltip for both
 * Spellblades.
 * Replaces the addInformation() approach to prevent double-rendering.
 *
 * Armor synergy checks for a full set of Living Armor (ParasiteWizardArmorItem)
 * or Sentient Armor (BattleMageArmorItem) Eany mix of both counts.
 */
@SideOnly(Side.CLIENT)
public class SpellbladeTooltipHandler {

    private static final java.util.Set<String> SW_BRIDGE_WEAPONS = new java.util.HashSet<>(java.util.Arrays.asList(
            "insanetweaks:sentient_spellblade",
            "insanetweaks:living_spellblade"));

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge)
            return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;

        ResourceLocation regLoc = stack.getItem().getRegistryName();
        if (regLoc == null)
            return;

        String regName = regLoc.toString();
        if (!SW_BRIDGE_WEAPONS.contains(regName))
            return;

        Item item = stack.getItem();
        List<?> props = null;

        // Retrieve weapon properties Etry direct cast first, reflection fallback for
        // classloader edge cases
        if (item instanceof IWeaponPropertyContainer<?>) {
            props = ((IWeaponPropertyContainer<?>) item).getAllWeaponProperties();
        } else {
            try {
                Method getAllWeaponProperties = item.getClass().getMethod("getAllWeaponProperties");
                props = (List<?>) getAllWeaponProperties.invoke(item);
            } catch (Exception e) {
                return; // No properties, nothing to render
            }
        }

        if (props == null)
            return;

        List<String> tooltip = event.getToolTip();
        if (tooltip.isEmpty())
            return;

        // 1. Insert kill count right below the item name (index 1)
        NBTTagCompound swordNbt = stack.getTagCompound();
        int killsDisplay = (swordNbt != null) ? swordNbt.getInteger("SentientKills") : 0;
        boolean isShiftPressed = GuiScreen.isShiftKeyDown();

        if ("insanetweaks:sentient_spellblade".equals(regName) && killsDisplay >= 1800) {
            // A N I H I L A T O R Easter Egg
            String anihilator = "\u00a74A \u00a7cN \u00a76I \u00a7eH \u00a72I \u00a73L \u00a71A \u00a79T \u00a75O \u00a7dR";
            tooltip.add(1, "\u00a79---> " + anihilator);
        } else {
            String suffix = "insanetweaks:living_spellblade".equals(regName) ? " / 900 decimated" : " decimated";
            tooltip.add(1, "\u00a79---> \u00a79" + killsDisplay + suffix);
        }

        // 2. Aggressive junk removal from Battlemage (AncientSpellcraft) tooltip lines
        for (int i = tooltip.size() - 1; i >= 1; i--) {
            String line = tooltip.get(i).toLowerCase();
            if (line.contains("matching battlemage armour") ||
                    line.contains("runeword potency") ||
                    line.contains("current spell") ||
                    line.contains("press sneak") ||
                    line.contains("mana source") ||
                    line.contains("hold a wand") ||
                    line.contains("runic") ||
                    line.contains("runeword") ||
                    line.contains("plundered") ||
                    line.contains("bound") ||
                    line.contains("blade") ||
                    line.contains("battlemages") ||
                    line.contains("creating") ||
                    line.contains("ancient spellcraft")) {
                tooltip.remove(i);
            }
        }

        // 3. Find the best insertion index (before QualityTools and Vanilla attributes)
        int insertIdx = tooltip.size();
        for (int i = 1; i < tooltip.size(); i++) {
            String cleanLine = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(tooltip.get(i));
            if (cleanLine == null)
                continue;
            String lower = cleanLine.toLowerCase();

            // Look for QualityTools or Vanilla modifiers (When in main hand, etc.)
            if (lower.startsWith("quality:") || lower.startsWith("when in ") || lower.startsWith("when on ")
                    || lower.startsWith("attribute modifiers")) {
                insertIdx = i;
                // Capture any empty line before the Quality text
                if (i > 1 && tooltip.get(i - 1).trim().isEmpty()) {
                    insertIdx = i - 1;
                }
                break;
            }
        }

        // Fallback to TooltipUtils if nothing was found
        if (insertIdx == tooltip.size()) {
            insertIdx = com.spege.insanetweaks.util.TooltipUtils.getInsertIdx(tooltip);
        }

        // 4. Build the custom information block
        List<String> myLines = new ArrayList<>();

        if ("insanetweaks:sentient_spellblade".equals(regName)) {
            myLines.add("");
            myLines.add(
                    "\u00a7fThe ulterior evolution of the spellblade, it has gained a corrupted \u00a7bWisdom\u00a7f.");
            myLines.add("");
        } else if ("insanetweaks:living_spellblade".equals(regName)) {
            myLines.add("");
            myLines.add(
                    "\u00a7fResults of various parasites body parts infused with magic, it seeks \u00a7bWisdom\u00a7f.");
            myLines.add("");
        }

        // Properties header — mirrors SpartanWeaponry's SHIFT toggle style exactly
        String shiftHint = isShiftPressed
                ? TextFormatting.DARK_GRAY + "[Showing details]"
                : TextFormatting.DARK_GRAY + "[Press " + TextFormatting.AQUA + "SHIFT" + TextFormatting.DARK_GRAY
                        + " to show details]";
        myLines.add(TextFormatting.GOLD + "Properties: " + shiftHint);
        if (item instanceof com.spege.insanetweaks.items.spellblade.BridgeSpellblade) {
            com.spege.insanetweaks.items.spellblade.BridgeSpellblade spellblade =
                    (com.spege.insanetweaks.items.spellblade.BridgeSpellblade) item;
            int adaptationLevel = spellblade.getArcaneAdaptationLevel(stack);
            int penaltyPercent = spellblade.getArcaneAdaptationPenaltyPercent(stack);
            String penaltyText = penaltyPercent > 0
                    ? "(+" + penaltyPercent + "% Foreign Mana Cost)"
                    : "(No Foreign Mana Penalty)";

            myLines.add(TextFormatting.DARK_RED + "- Adaptation Upgrade " + toRoman(adaptationLevel)
                    + TextFormatting.RED + " " + penaltyText);
            if (isShiftPressed) {
                String desc = com.spege.insanetweaks.util.PropertyDescriptions.getDescription("adaptation_upgrade");
                if (desc != null) {
                    myLines.add(TextFormatting.RED + "" + TextFormatting.ITALIC + "  " + desc);
                }
            }
        }
        myLines.add(TextFormatting.GOLD + "- Ashen Legacy");
        if (isShiftPressed) {
            String desc = com.spege.insanetweaks.util.PropertyDescriptions.getDescription("ashen_legacy");
            if (desc != null) {
                myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " + desc);
            }
        }

        for (Object propObj : props) {
            String type = "";
            int level = 0;

            if (propObj instanceof WeaponProperty) {
                type = ((WeaponProperty) propObj).getType();
                level = ((WeaponProperty) propObj).getLevel();
            } else {
                try {
                    Method getType = propObj.getClass().getMethod("getType");
                    type = (String) getType.invoke(propObj);
                    Method getLevel = propObj.getClass().getMethod("getLevel");
                    level = ((Integer) getLevel.invoke(propObj)).intValue();
                } catch (Exception e) {
                    continue;
                }
            }

            String nameKey = "tooltip.spartanweaponry.property." + type + ".name";
            String name = I18n.format(nameKey);

            if (name.equals(nameKey)) { // fallback: check swparasites
                nameKey = "tooltip.swparasites.property." + type + ".name";
                name = I18n.format(nameKey);
            }

            // Capitalization fallback if no lang key resolves
            if (name.equals(nameKey)) {
                String[] parts = type.split("_");
                StringBuilder capitalized = new StringBuilder();
                for (String part : parts) {
                    if (!part.isEmpty()) {
                        capitalized.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
                    }
                }
                name = capitalized.toString().trim();
            }

            // Polished overrides for known Spartan / swparasites property names
            if (type.contains("virulent"))
                name = "Virulent I";
            if (type.contains("sweep"))
                name = "Sweep I";
            if (type.contains("reach"))
                name = "Reach";
            if ("heavy".equals(type) && level == 1)
                name = "Heavy I";
            if ("heavy".equals(type) && level == 2)
                name = "Heavy II";
            if (type.contains("bleeding"))
                name = "Bleeding " + toRoman(level);
            if (type.contains("uncapped"))
                name = "Uncapped";

            String color = TextFormatting.GREEN.toString();
            if (type.contains("heavy") || type.contains("two_handed")) {
                color = TextFormatting.RED.toString();
            }

            myLines.add(color + "- " + name);

            // SHIFT: add indented description from our own registry
            if (isShiftPressed) {
                String desc = com.spege.insanetweaks.util.PropertyDescriptions.getDescription(type);
                if (desc != null) {
                    myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " + desc);
                }
            }
        }

        // Easter Egg: Awakened Virulent I
        if ("insanetweaks:sentient_spellblade".equals(regName) && killsDisplay >= 1800) {
            myLines.add(TextFormatting.GREEN + "- Virulent I " + TextFormatting.LIGHT_PURPLE + "(Awakened)");
            if (isShiftPressed) {
                myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC
                        + "  The infection has evolved beyond mortal limits.");
            }
        }

        // Magical Adaptation (only visible if PotionCore is installed and providing
        // bonuses)
        if (net.minecraftforge.fml.common.Loader.isModLoaded("potioncore")) {
            if ("insanetweaks:sentient_spellblade".equals(regName)) {
                myLines.add(TextFormatting.GREEN + "- Magically Adapted " + TextFormatting.DARK_AQUA
                        + "(+10% Magic Damage)");
                if (isShiftPressed) {
                    myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " +
                            com.spege.insanetweaks.util.PropertyDescriptions.getDescription("magically_adapted"));
                }
            } else if ("insanetweaks:living_spellblade".equals(regName)) {
                int magicBonus = Math.max(1, Math.min(10, (killsDisplay * 10) / 900));
                myLines.add(TextFormatting.GREEN + "- Magically Adapted " + TextFormatting.DARK_AQUA + "(+" + magicBonus
                        + "% Magic Damage)");
                if (isShiftPressed) {
                    myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " +
                            com.spege.insanetweaks.util.PropertyDescriptions.getDescription("magically_adapted"));
                }
            }
        }

        // 5. Synergy line
        myLines.add("");

        int kills = 0;
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null && nbt.hasKey("SentientKills")) {
                kills = nbt.getInteger("SentientKills");
            }
        }

        int bonusPercent = "insanetweaks:sentient_spellblade".equals(regName) ? 70 : Math.min(70, (kills * 70) / 900);

        boolean hasSynergy = true;
        EntityPlayer player = event.getEntityPlayer();
        if (player != null) {
            for (ItemStack piece : player.inventory.armorInventory) {
                // armorInventory never contains null in 1.12.2 Eempty slots are ItemStack.EMPTY
                if (piece.isEmpty() ||
                        (!(piece.getItem() instanceof BattleMageArmorItem) &&
                                !(piece.getItem() instanceof ParasiteWizardArmorItem))) {
                    hasSynergy = false;
                    break;
                }
            }
        } else {
            hasSynergy = false;
        }

        if (hasSynergy) {
            myLines.add("\u00a76Sentient Synergy: \u00a7c+" + bonusPercent + "% Omnipotency");
        }

        myLines.add(
                "\u00a7b\u00a7oRequires offhand mana source | Unlock true potential with matching Sentient battlemage armor");

        // 6. Inject all lines at the calculated position
        tooltip.addAll(insertIdx, myLines);
    }

    /**
     * Converts 1–3 to Roman numeral string; falls back to Arabic for higher values.
     */
    private static String toRoman(int level) {
        switch (level) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            default:
                return String.valueOf(level);
        }
    }
}
