package com.spege.ebreduxaddon.feature.item;

import com.binaris.wizardry.setup.registries.EBItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

/**
 * Materialy zbroi. Grafted na poziomie zelaza, Sentient na poziomie diamentu (spec 3.2). Nazwa
 * materialu wyznacza teksture na modelu: textures/models/armor/&lt;nazwa&gt;_layer_1.png.
 */
public enum ModArmorMaterials implements ArmorMaterial {
    GRAFTED("ebreduxaddon:grafted", 15, new int[] {2, 5, 6, 2}, 12, SoundEvents.ARMOR_EQUIP_LEATHER, 0f),
    SENTIENT("ebreduxaddon:sentient", 33, new int[] {3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_DIAMOND, 2f);

    /** Kolejnosc jak w vanilla ArmorMaterials: buty, spodnie, napiersnik, helm. */
    private static final int[] BASE_DURABILITY = {13, 15, 16, 11};

    private final String name;
    private final int durabilityMultiplier;
    private final int[] defense;
    private final int enchantability;
    private final SoundEvent sound;
    private final float toughness;

    ModArmorMaterials(String name, int durabilityMultiplier, int[] defense, int enchantability, SoundEvent sound, float toughness) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.defense = defense;
        this.enchantability = enchantability;
        this.sound = sound;
        this.toughness = toughness;
    }

    private static int index(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 0;
            case LEGGINGS -> 1;
            case CHESTPLATE -> 2;
            case HELMET -> 3;
        };
    }

    @Override
    public int getDurabilityForType(@NotNull ArmorItem.Type type) {
        return BASE_DURABILITY[index(type)] * durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(@NotNull ArmorItem.Type type) {
        return defense[index(type)];
    }

    @Override
    public int getEnchantmentValue() {
        return enchantability;
    }

    @Override
    public @NotNull SoundEvent getEquipSound() {
        return sound;
    }

    @Override
    public @NotNull Ingredient getRepairIngredient() {
        return Ingredient.of(EBItems.MAGIC_CRYSTAL.get());
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return 0f;
    }
}
