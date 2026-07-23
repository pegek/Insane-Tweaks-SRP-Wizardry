package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

/**
 * Bauble Fruit - Charm
 * When consumed, permanently grants the player +1 Charm bauble slot.
 *
 * NBT keys:
 *   BaublesEX: "ConsumedCharmFruit"
 *   Legacy:    "ConsumedCharmFruitLegacy"
 */
public class CharmFruitItem extends BaseBaubleFruitItem {

    public CharmFruitItem() {
        super("bauble_fruit_charm", "ConsumedCharmFruit", "ConsumedCharmFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType()   { return TypeData.Preset.CHARM; }
    @Override protected String       getSlotDescription() { return "+1 Charm slot"; }
    @Override protected String       getFlavorText()      {
        return "A glimmering fruit with a mysterious, elusive aura.";
    }
}
