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
 * <p>Obrażenia dobrane pod formułę Better Survival {@code (3,0 + attackDamage) × 0,5}: 4,5 daje
 * 3,75 obrażeń Livingowi, 12,0 daje 7,5 Sentientowi. Reguła rodziny SRP (Sentient = 2× Living)
 * dotyczy WYNIKU, nie liczby w materiale.
 *
 * <p>🚨 To jest ŚWIADOMIE PONIŻEJ parytetu DPS rodziny. Living robi 3,75 × 3,45 ataku/s = 12,9,
 * Sentient 7,5 × 2,82/s = 21,2 — wobec 17,1 i 26,6 miecza Living/Sentient SRP, czyli ok. 76-80%.
 * Zapłatą za tę różnicę jest {@code TOTAL_REACH = 3,5} wobec waniliowych 3,0: pół kratki zasięgu
 * więcej kosztowało 8,0 → 7,5 obrażeń na Sentiencie. Nie „naprawiaj" tego z powrotem do parytetu
 * bez cofnięcia zasięgu — to jedna decyzja, nie dwie.
 *
 * <p>Wytrzymałość 1000 z {@code Living Weapons Durability} w configu SRP. Harvest level i
 * efficiency są bez znaczenia — nunchaku niczego nie kopie.
 */
public final class ParasiteNunchakuMaterials {

    /** attackDamage 4,5 → (3,0 + 4,5) × 0,5 = 3,75 obrażeń. */
    public static final Item.ToolMaterial LIVING =
            EnumHelper.addToolMaterial("PARASITELIVING", 3, 1000, 6.0F, 4.5F, 12);

    /** attackDamage 12,0 → (3,0 + 12,0) × 0,5 = 7,5 obrażeń — dokładnie 2× Living. */
    public static final Item.ToolMaterial SENTIENT =
            EnumHelper.addToolMaterial("PARASITESENTIENT", 3, 1000, 6.0F, 12.0F, 12);

    private ParasiteNunchakuMaterials() {
    }
}
