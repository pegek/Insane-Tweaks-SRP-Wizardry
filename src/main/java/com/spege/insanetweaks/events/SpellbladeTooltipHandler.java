package com.spege.insanetweaks.events;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.oblivioussp.spartanweaponry.api.IWeaponPropertyContainer;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty;

// No imports needed for custom armor if using qualified names, or add them here:
import com.spege.insanetweaks.items.armor.BattleMageArmorItem;
import com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem;

import com.spege.insanetweaks.config.ModConfig;

/**
 * CLIENT-ONLY event handler that injects the full custom tooltip for both
 * Spellblades.
 * Replaces the addInformation() approach to prevent double-rendering.
 *
 * Armor synergy checks for a full set of BattleMageArmorItem or
 * ParasiteWizardArmorItem.
 */
public class SpellbladeTooltipHandler {

    private static final java.util.Set<String> SW_BRIDGE_WEAPONS = new java.util.HashSet<>(java.util.Arrays.asList(
            "insanetweaks:sentient_spellblade",
            "insanetweaks:living_spellblade"));

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!ModConfig.enableSrpEbWizardryBridge)
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

        // Retrieve weapon properties — try direct cast first, reflection fallback for
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
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null && nbt.hasKey("SentientKills")) {
                int kills = nbt.getInteger("SentientKills");
                if (kills >= 5000) {
                    // A N I H I L A T O R Easter Egg
                    String anihilator = "\u00a74A \u00a7cN \u00a76I \u00a7eH \u00a72I \u00a73L \u00a71A \u00a79T \u00a75O \u00a7dR";
                    tooltip.add(1, "\u00a79---> " + anihilator);
                } else {
                    tooltip.add(1, "\u00a79---> \u00a79" + kills + " decimated");
                }
            }
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

        // 3. Find the best insertion index (before Forge/JEI info footers)
        int insertIdx = com.spege.insanetweaks.util.TooltipUtils.getInsertIdx(tooltip);

        // 4. Build the custom information block
        List<String> myLines = new ArrayList<>();

        if ("insanetweaks:sentient_spellblade".equals(regName)) {
            myLines.add("");
            myLines.add(
                    "\u00a7fThe ulterior evolution of the spellblade, it has gained a twisted \u00a7bWisdom\u00a7f.");
            myLines.add("");
        } else if ("insanetweaks:living_spellblade".equals(regName)) {
            myLines.add("");
            myLines.add(
                    "\u00a7fResults of various parasites body parts infused with magic, corrupted power shines inside.");
            myLines.add("");
        }

        myLines.add("\u00a76Properties:");

        for (Object propObj : props) {
            String type = "";

            if (propObj instanceof WeaponProperty) {
                type = ((WeaponProperty) propObj).getType();
            } else {
                try {
                    Method getType = propObj.getClass().getMethod("getType");
                    type = (String) getType.invoke(propObj);
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
            if (type.contains("viral"))
                name = "Viral I";
            if (type.contains("sweep"))
                name = "Sweep I";
            if (type.contains("reach"))
                name = "Reach";
            if (type.contains("heavy"))
                name = "Heavy";
            if (type.contains("bleeding"))
                name = "Bleeding III";
            if (type.contains("uncapped"))
                name = "Uncapped";

            String color = "\u00a7a";
            if (type.contains("heavy") || type.contains("two_handed")) {
                color = "\u00a7c";
            }

            myLines.add(color + "- " + name);
        }
        myLines.add("\u00a78inspired by scape and spartan - parasites");

        // 5. Synergy line
        myLines.add("");

        int kills = 0;
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null && nbt.hasKey("SentientKills")) {
                kills = nbt.getInteger("SentientKills");
            }
        }

        int bonusPercent = "insanetweaks:sentient_spellblade".equals(regName) ? 85 : Math.min(85, (kills / 100) * 5);

        boolean hasSynergy = true;
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            for (ItemStack piece : player.inventory.armorInventory) {
                if (piece == null || piece.isEmpty() ||
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
            myLines.add("\u00a76Sentient Synergy: \u00a76+" + bonusPercent + "% Potency");
        }

        myLines.add(
                "\u00a7c\u00a7oRequires offhand mana source | Unlock true potential with matching Sentient battlemage armor");

        // 6. Inject all lines at the calculated position
        tooltip.addAll(insertIdx, myLines);
    }
}
