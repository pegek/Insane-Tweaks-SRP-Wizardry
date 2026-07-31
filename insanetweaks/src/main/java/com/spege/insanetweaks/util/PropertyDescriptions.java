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
        // Word-for-word the line SpellbladeTooltipHandler already showed for Fleshbound, so the
        // book-granted property and the earned one read identically.
        DESCRIPTIONS.put("grip",
                "Grafted to the wielder's flesh. Prevents drops and disarm.");
        // Present so the substring fallback below can never reach this id, but never returned:
        // getDescription builds the real line from the configured percentages instead.
        DESCRIPTIONS.put("arcane_sundering",
                "The blade's corrupted wisdom bleeds into its edge.");
        }

        public static String getDescription(String type) {
                if (type == null)
                        return null;
                String lower = type.toLowerCase();

                // Built rather than stored: the two shares are config-driven, and a description
                // quoting stale numbers is worse than no numbers at all.
                if (com.spege.insanetweaks.api.AdvPropertyRegistry.ARCANE_SUNDERING.equals(lower)) {
                        return describeArcaneSundering();
                }

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

        private static String describeArcaneSundering() {
                com.spege.insanetweaks.config.categories.ArcaneSunderingCategory cfg =
                                com.spege.insanetweaks.config.ModConfig.arcaneSundering;
                return "The blade's corrupted wisdom bleeds into its edge. \u00a7f"
                                + cfg.trueDamagePercent
                                + "%\u00a77 of every strike lands as \u00a7ftrue damage\u00a77,"
                                + " answering to neither armour, resistance nor absorption, and a"
                                + " further \u00a7f" + cfg.magicDamagePercent
                                + "%\u00a77 is rewritten from steel into \u00a7dmagic\u00a77,"
                                + " slipping past armour but not past wards.";
        }
}
