package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

/**
 * Bauble Fruit - Belt
 * When consumed, permanently grants the player +1 Belt bauble slot.
 *
 * NBT keys:
 *   BaublesEX: "ConsumedBeltFruit"
 *   Legacy:    "ConsumedBeltFruitLegacy"
 */
public class BeltFruitItem extends BaseBaubleFruitItem {

    public BeltFruitItem() {
        super("bauble_fruit_belt", "ConsumedBeltFruit", "ConsumedBeltFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType()   { return TypeData.Preset.BELT; }
    @Override protected String       getSlotDescription() { return "+1 Belt slot"; }
    @Override protected String       getFlavorText()      {
        return "A resilient fruit with a warm, grounding energy.";
    }
}
