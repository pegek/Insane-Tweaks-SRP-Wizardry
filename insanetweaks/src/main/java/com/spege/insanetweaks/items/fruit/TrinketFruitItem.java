package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

public class TrinketFruitItem extends BaseBaubleFruitItem {
    public TrinketFruitItem() {
        super("bauble_fruit_trinket", "ConsumedTrinketFruit", "ConsumedTrinketFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType() { return TypeData.Preset.TRINKET; }
    @Override protected String getSlotDescription() { return "+1 Trinket slot"; }
    @Override protected String getFlavorText() { return "A shiny fruit glimmering with miscellaneous possibilities."; }
}
