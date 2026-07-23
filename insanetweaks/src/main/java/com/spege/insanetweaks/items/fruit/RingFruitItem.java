package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

/**
 * Bauble Fruit - Ring
 * When consumed, permanently grants the player +1 Ring bauble slot.
 *
 * NBT keys:
 *   BaublesEX: "ConsumedRingFruit"
 *   Legacy:    "ConsumedRingFruitLegacy"
 */
public class RingFruitItem extends BaseBaubleFruitItem {

    public RingFruitItem() {
        super("bauble_fruit_ring", "ConsumedRingFruit", "ConsumedRingFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType()   { return TypeData.Preset.RING; }
    @Override protected String       getSlotDescription() { return "+1 Ring slot"; }
    @Override protected String       getFlavorText()      {
        return "A mysterious fruit pulsing with ring-shaped arcane energy.";
    }
}
