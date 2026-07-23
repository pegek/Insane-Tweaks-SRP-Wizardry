package com.spege.insanetweaks.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry of weapon property descriptions for InsaneTweaks spellblades
 * and wand properties.
 */
public class PropertyDescriptions {

        private static final Map<String, String> DESCRIPTIONS = new HashMap<String, String>();

        static {
        // --- SpartanWeaponry / swparasites properties ---
        // Descriptions match the in-game text shown by the native tooltip system.

        DESCRIPTIONS.put("reach",
                "Increased melee damage range. Higher Reach levels have more range.");
        DESCRIPTIONS.put("sweep",
                "All targets in sweep range takes 1.0 damage.");
        DESCRIPTIONS.put("bleeding",
                "Inflicts \u00a7clife-threatening\u00a77 injuries. Higher levels of Bleeding will apply more a potent bleed more often.");
        DESCRIPTIONS.put("uncapped",
                "You \u00a7cwon't\u00a77 be limited.");
        DESCRIPTIONS.put("heavy",
                "This weapon is 50% slower due to being made of Parasite parts.");

        // "Viral" renamed to "Virulent" across the codebase
        DESCRIPTIONS.put("virulent",
                "Lashes out \u00a7avirulent\u00a77 strikes. Higher levels of Viral will apply more potent toxin more often.");

        // --- Custom / InsaneTweaks properties ---
        DESCRIPTIONS.put("magically_adapted",
                "The catalyst is suffused with refined arcane tissue, amplifying magic damage output beyond that of ordinary foci.");
        DESCRIPTIONS.put("arcane_adaptation",
                "This focus has been adapted to channel Abomination magic. Higher levels reduce the mana penalty imposed on foreign spells.");
        DESCRIPTIONS.put("adaptation_upgrade",
                "Allows a focus to channel Abomination spells. Further adaptation softens, then removes, the mana backlash imposed on non-InsaneTweaks spells.");
        DESCRIPTIONS.put("ashen_legacy",
                "This relic refuses to be claimed by simple ruin. Even when discarded, it endures flame, lava, cactus and violent blasts, lingering far longer than ordinary gear.");
        DESCRIPTIONS.put("living_armor_lore",
                "A living, breathing arcane carapace, infused with Parasitic biomass.");
        DESCRIPTIONS.put("sentient_armor_lore",
                "The ulterior evolution of arcane carapace. It has achieved Ethereal Stasis.");
        DESCRIPTIONS.put("armor_last_stand",
                "When the full shell is assembled, it may refuse a killing blow, wrenching the bearer back from death and purging hostile afflictions.");
        DESCRIPTIONS.put("living_armor_adaptation",
                "Adapts as it absorbs punishment, gradually sharpening its spellcasting efficiency before the final evolution.");
        DESCRIPTIONS.put("ethereal_shell",
                "Shrouds the bearer in stabilized ether, granting a flat -1.0% resistance against all incoming damage.");
        DESCRIPTIONS.put("ethereal_shell_awakened",
                "The stasis veil awakens and thickens, granting a flat -1.5% resistance against all incoming damage.");
        }

        public static String getDescription(String type) {
                if (type == null)
                        return null;
                String lower = type.toLowerCase();

                if (DESCRIPTIONS.containsKey(lower)) {
                        return DESCRIPTIONS.get(lower);
                }

                for (Map.Entry<String, String> entry : DESCRIPTIONS.entrySet()) {
                        if (lower.contains(entry.getKey())) {
                                return entry.getValue();
                        }
                }

                return null;
        }
}
