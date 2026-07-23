package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

/**
 * Bauble Fruit - Amulet
 * When consumed, permanently grants the player +1 Amulet bauble slot.
 *
 * NBT keys:
 *   BaublesEX: "ConsumedAmuletFruit"
 *   Legacy:    "ConsumedAmuletFruitLegacy"
 */
public class AmuletFruitItem extends BaseBaubleFruitItem {

    public AmuletFruitItem() {
        super("bauble_fruit_amulet", "ConsumedAmuletFruit", "ConsumedAmuletFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType()   { return TypeData.Preset.AMULET; }
    @Override protected String       getSlotDescription() { return "+1 Amulet slot"; }
    @Override protected String       getFlavorText()      {
        return "A radiant fruit glowing with a soft, guiding light.";
    }
}
