package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

public class TrinketFruitItem extends BaseBaubleFruitItem {
    public TrinketFruitItem() {
        super("bauble_fruit_trinket", "ConsumedTrinketFruit", "ConsumedTrinketFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType() { return TypeData.Preset.TRINKET; }
}
