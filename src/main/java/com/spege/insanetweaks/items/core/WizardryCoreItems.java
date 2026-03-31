package com.spege.insanetweaks.items.core;

import net.minecraft.item.Item;
import net.minecraft.util.text.TextFormatting;

public final class WizardryCoreItems {

    public static final WizardryCoreItem COST_CORE = new WizardryCoreItem(
            "cost_core",
            "SpellCostBonus",
            0.05f,
            TextFormatting.BLUE,
            "Cost Reduction Upgrades",
            "Mana Cost",
            true,
            true,
            null);

    public static final WizardryCoreItem POTENCY_CORE = new WizardryCoreItem(
            "potency_core",
            "SpellPotencyBonus",
            0.05f,
            TextFormatting.RED,
            "Potency Upgrades",
            "Spell Omnipotency",
            true,
            false,
            null);

    public static final WizardryCoreItem SPEEDCAST_CORE = new WizardryCoreItem(
            "speedcast_core",
            "SpellChargeupBonus",
            0.05f,
            TextFormatting.LIGHT_PURPLE,
            "Speedcast Upgrades",
            "Charge-up Time",
            true,
            true,
            null);

    public static final WizardryCoreItem MINION_HEALTH_CORE = new WizardryCoreItem(
            "minion_health_core",
            "SpellMinionHealthBonus",
            0.20f,
            TextFormatting.DARK_GREEN,
            "Minion Health Upgrades",
            "Minion Health",
            true,
            false,
            null);

    public static final WizardryCoreItem MINION_COUNT_CORE = new WizardryCoreItem(
            "minion_count_core",
            "SpellMinionCountBonus",
            1.00f,
            TextFormatting.YELLOW,
            "Minion Count Upgrades",
            "Minion Count",
            false,
            false,
            "Experimental: base SpellMinion does not consume count from SpellModifiers.");

    public static final WizardryCoreItem SUMMON_RADIUS_CORE = new WizardryCoreItem(
            "summon_radius_core",
            "SpellSummonRadiusBonus",
            1.00f,
            TextFormatting.AQUA,
            "Summon Radius Upgrades",
            "Summon Radius",
            false,
            false,
            "Experimental: base SpellMinion does not consume radius from SpellModifiers.");

    public static final WizardryCoreItem SUMMON_DURATION_CORE = new WizardryCoreItem(
            "summon_duration_core",
            "SpellSummonDurationBonus",
            0.20f,
            TextFormatting.GREEN,
            "Summon Duration Upgrades",
            "Summon Duration",
            true,
            false,
            null);

    public static final Item[] ALL_CORES = {
            COST_CORE,
            POTENCY_CORE,
            SPEEDCAST_CORE,
            MINION_HEALTH_CORE,
            MINION_COUNT_CORE,
            SUMMON_RADIUS_CORE,
            SUMMON_DURATION_CORE
    };

    private WizardryCoreItems() {
    }
}
