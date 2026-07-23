package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

public class ElytraFruitItem extends BaseBaubleFruitItem {
    public ElytraFruitItem() {
        super("bauble_fruit_elytra", "ConsumedElytraFruit", "ConsumedElytraFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType() { return TypeData.Preset.ELYTRA; }
    @Override protected String getSlotDescription() { return "+1 Elytra slot"; }
    @Override protected String getFlavorText() { return "A weightless fruit echoing with gusts of wind."; }
}
