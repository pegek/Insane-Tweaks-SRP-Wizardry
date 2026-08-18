package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class HudCategory {

    @Config.Comment("Czy rysowac pasek many.")
    public boolean showBar = true;

    @Config.Comment("Czy pisac liczbe obok paska.")
    public boolean showNumber = true;

    @Config.Comment("Przesuniecie paska w poziomie, w pikselach GUI.")
    @Config.RangeInt(min = -1000, max = 1000)
    public int offsetX = 0;

    @Config.Comment("Przesuniecie paska w pionie, w pikselach GUI.")
    @Config.RangeInt(min = -1000, max = 1000)
    public int offsetY = 0;
}
