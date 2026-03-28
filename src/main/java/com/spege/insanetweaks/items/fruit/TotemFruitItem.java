package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.registries.TypeData;

public class TotemFruitItem extends BaseBaubleFruitItem {

    public TotemFruitItem() {
        super("bauble_fruit_totem", "ConsumedTotemFruit", "ConsumedTotemFruitLegacy");
    }

    @Override protected BaubleTypeEx getBaublesExType() {
        return TypeData.getTypeByName("totem");
    }
    
    @Override protected String getSlotDescription() { return "+1 Totem slot"; }
    @Override protected String getFlavorText() { return "An immortal fruit gleaming with undying vitality."; }
}
