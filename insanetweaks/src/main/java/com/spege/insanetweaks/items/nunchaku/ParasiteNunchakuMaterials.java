package com.spege.insanetweaks.items.nunchaku;

import net.minecraft.item.Item;
import net.minecraftforge.common.util.EnumHelper;

/**
 * Materiały narzędziowe pod pasożytnicze nunchaku.
 *
 * <p>🚨 NAZWA MATERIAŁU STERUJE NAZWĄ REJESTROWĄ. Konstruktor {@code ItemNunchaku} skleja ją jako
 * {@code "item" + material.name().toLowerCase() + "nunchaku"}, więc zmiana stringa poniżej
 * przemianowuje item, unieważnia modele, lang i recepturę — i wypaca broń z zapisanych światów.
 *
 * <p>Obrażenia dobrane pod formułę Better Survival {@code (3,0 + attackDamage) × 0,5}: 3,0 daje
 * 5,5 obrażeń Livingowi, 19,0 daje 11,0 Sentientowi. Reguła rodziny SRP (Sentient = 2× Living)
 * dotyczy WYNIKU, nie liczby w materiale.
 *
 * <p>Te wartości dają PEŁNY parytet DPS z rodziną: 5,5 × 3,14 ataku/s = 17,3 wobec 17,1 miecza
 * Living SRP, a 11,0 × 2,44/s = 26,8 wobec 26,6 miecza Sentient. Pierwotne 3,0/6,0 zakładały,
 * że połowę obrażeń dowiozą efekty statusowe — po zmianie na Bleeding, który jest DODATKIEM,
 * a nie substytutem, wróciliśmy do parytetu.
 *
 * <p>Wytrzymałość 1000 z {@code Living Weapons Durability} w configu SRP. Harvest level i
 * efficiency są bez znaczenia — nunchaku niczego nie kopie.
 */
public final class ParasiteNunchakuMaterials {

    /** attackDamage 8,0 → (3,0 + 8,0) × 0,5 = 5,5 obrażeń. */
    public static final Item.ToolMaterial LIVING =
            EnumHelper.addToolMaterial("PARASITELIVING", 3, 1000, 6.0F, 8.0F, 12);

    /** attackDamage 19,0 → (3,0 + 19,0) × 0,5 = 11,0 obrażeń — dokładnie 2× Living. */
    public static final Item.ToolMaterial SENTIENT =
            EnumHelper.addToolMaterial("PARASITESENTIENT", 3, 1000, 6.0F, 19.0F, 12);

    private ParasiteNunchakuMaterials() {
    }
}
