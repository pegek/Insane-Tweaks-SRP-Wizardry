package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

/**
 * Bauble Fruit - Head
 * When consumed, permanently grants the player +1 Head bauble slot.
 *
 * NBT keys:
 *   BaublesEX: "ConsumedHeadFruit"
 *   Legacy:    "ConsumedHeadFruitLegacy"
 */
public class HeadFruitItem extends BaseBaubleFruitItem {

    public HeadFruitItem() {
        super("bauble_fruit_head", "ConsumedHeadFruit", "ConsumedHeadFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType()   { return TypeData.Preset.HEAD; }
    @Override protected String       getSlotDescription() { return "+1 Head slot"; }
    @Override protected String       getFlavorText()      {
        return "A peculiar fruit that seems to elevate your thoughts.";
    }
}
