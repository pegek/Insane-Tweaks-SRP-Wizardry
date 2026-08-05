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
 * 3,0 obrażeń Livingowi, 9,0 daje 6,0 Sentientowi. Reguła rodziny SRP (Sentient = 2× Living)
 * dotyczy WYNIKU, nie liczby w materiale.
 *
 * <p>Wytrzymałość 1000 z {@code Living Weapons Durability} w configu SRP. Harvest level i
 * efficiency są bez znaczenia — nunchaku niczego nie kopie.
 */
public final class ParasiteNunchakuMaterials {

    /** attackDamage 3,0 → (3,0 + 3,0) × 0,5 = 3,0 obrażeń. */
    public static final Item.ToolMaterial LIVING =
            EnumHelper.addToolMaterial("PARASITELIVING", 3, 1000, 6.0F, 3.0F, 12);

    /** attackDamage 9,0 → (3,0 + 9,0) × 0,5 = 6,0 obrażeń — dokładnie 2× Living. */
    public static final Item.ToolMaterial SENTIENT =
            EnumHelper.addToolMaterial("PARASITESENTIENT", 3, 1000, 6.0F, 9.0F, 12);

    private ParasiteNunchakuMaterials() {
    }
}
