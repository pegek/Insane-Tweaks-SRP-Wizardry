package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

/**
 * Bauble Fruit - Body
 * When consumed, permanently grants the player +1 Body bauble slot.
 *
 * NBT keys:
 *   BaublesEX: "ConsumedBodyFruit"
 *   Legacy:    "ConsumedBodyFruitLegacy"
 */
public class BodyFruitItem extends BaseBaubleFruitItem {

    public BodyFruitItem() {
        super("bauble_fruit_body", "ConsumedBodyFruit", "ConsumedBodyFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType()   { return TypeData.Preset.BODY; }
    @Override protected String       getSlotDescription() { return "+1 Body slot"; }
    @Override protected String       getFlavorText()      {
        return "A dense fruit wrapped in a faintly armored husk.";
    }
}
