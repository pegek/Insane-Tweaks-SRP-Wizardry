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
 * <p>Obrażenia dobrane pod formułę Better Survival {@code (3,0 + attackDamage) × 0,5}: 5,0 daje
 * 4,0 obrażeń Livingowi, 13,0 daje 8,0 Sentientowi. Reguła rodziny SRP (Sentient = 2× Living)
 * dotyczy WYNIKU, nie liczby w materiale.
 *
 * <p>Living robi 4,0 × 3,45 ataku/s = 13,8, Sentient 8,0 × 2,82/s = 22,6 — wobec 17,1 i 26,6
 * miecza Living/Sentient SRP, czyli ok. 81-85%. Broń nadrabia to częstotliwością nakładania
 * Bleedingu, który tnie za procent CAŁKOWITEGO HP celu.
 *
 * <p>Była tu jeszcze próba dołożenia zasięgu przez {@code IHaveReach} (za cenę 8,0 → 7,5).
 * Wycofana: mechanizm SRP nie zadziałał na tej broni, mimo że bramka configu była otwarta
 * i {@code instanceof} się zgadzał. Szczegóły w notes/specs/2026-08-05-living-nunchaku-design.md.
 *
 * <p>Wytrzymałość 1000 z {@code Living Weapons Durability} w configu SRP. Harvest level i
 * efficiency są bez znaczenia — nunchaku niczego nie kopie.
 */
public final class ParasiteNunchakuMaterials {

    /** attackDamage 5,0 → (3,0 + 5,0) × 0,5 = 4,0 obrażeń. */
    public static final Item.ToolMaterial LIVING =
            EnumHelper.addToolMaterial("PARASITELIVING", 3, 1000, 6.0F, 5.0F, 12);

    /** attackDamage 13,0 → (3,0 + 13,0) × 0,5 = 8,0 obrażeń — dokładnie 2× Living. */
    public static final Item.ToolMaterial SENTIENT =
            EnumHelper.addToolMaterial("PARASITESENTIENT", 3, 1000, 6.0F, 13.0F, 12);

    private ParasiteNunchakuMaterials() {
    }
}
