package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

public class ElytraFruitItem extends BaseBaubleFruitItem {
    public ElytraFruitItem() {
        super("bauble_fruit_elytra", "ConsumedElytraFruit", "ConsumedElytraFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType() { return TypeData.Preset.ELYTRA; }
}
