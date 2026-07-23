package com.spege.insanetweaks.items.shield;

import java.util.UUID;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

import com.google.common.collect.Multimap;
import com.windanesz.ancientspellcraft.item.ItemBattlemageShield;
import com.spege.insanetweaks.items.armor.LivingWarlockArmorItem;
import com.spege.insanetweaks.items.armor.SentientWarlockArmorItem;
import com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem;
import com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.api.AdvPropertyRegistry;

public class SentientAegisItem extends ItemBattlemageShield implements ITweaksPropertyHolder {

    private static final UUID ARMOR_MAIN_UUID = UUID.fromString("8db1c4eb-37dd-4bbf-851f-6a77d130a08e");
    private static final UUID TOUGHNESS_MAIN_UUID = UUID.fromString("3dcf50db-1428-44de-9e2c-e1fc8dbad549");
    private static final UUID ARMOR_OFF_UUID = UUID.fromString("9f000b21-4ea7-4279-bb7d-b5b47a16af61");
    private static final UUID TOUGHNESS_OFF_UUID = UUID.fromString("0a3597d3-7d2d-425b-bb70-8b090df49a9e");

    @SuppressWarnings("null")
    public SentientAegisItem() {
        super();
        this.setRegistryName("insanetweaks", "sentient_aegis");
        this.setUnlocalizedName("sentient_aegis");
        CreativeTabs tab = CreativeTabs.COMBAT;
        if (tab != null) this.setCreativeTab(tab);
        this.setMaxDamage(4500); // Base Mana capacity / Durability
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public Multimap<String, AttributeModifier> getAttributeModifiers(@Nonnull EntityEquipmentSlot equipmentSlot, @Nonnull ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(equipmentSlot, stack);
        
        if (multimap == null) {
            multimap = com.google.common.collect.HashMultimap.create();
        }

        if (equipmentSlot == EntityEquipmentSlot.MAINHAND || equipmentSlot == EntityEquipmentSlot.OFFHAND) {
            multimap.removeAll(SharedMonsterAttributes.ARMOR.getName());
            multimap.removeAll(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName());
            
            // Unique UUIDs for shielding — prefix must be unique per item type
            UUID uuidArmor = equipmentSlot == EntityEquipmentSlot.MAINHAND ? ARMOR_MAIN_UUID : ARMOR_OFF_UUID;
            UUID uuidToughness = equipmentSlot == EntityEquipmentSlot.MAINHAND ? TOUGHNESS_MAIN_UUID : TOUGHNESS_OFF_UUID;
             
            if (uuidArmor != null) multimap.put(SharedMonsterAttributes.ARMOR.getName(), new AttributeModifier(uuidArmor, "Armor modifier", 3.0D, 0));
            if (uuidToughness != null) multimap.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), new AttributeModifier(uuidToughness, "Armor toughness", 3.0D, 0));
        }
        return multimap;
    }

    @Override
    public int getItemEnchantability() {
        return 14; // Standard tier enchantability (requested)
    }
    
    @Override
    public boolean isEnchantable(@Nonnull ItemStack stack) {
        return true;
    }
    
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return true;
    }

    /**
     * Override ASC's native check that requires ArmourClass.BATTLEMAGE full set.
     * Our Living/Sentient Warlock and Battlemage armor sets should also unlock
     * the shield's full functionality (spell casting, artefact ticking, GUI access).
     * Without this, only native EBWizardry Battlemage armor would work.
     */
    @Override
    public boolean isBattlemage(@Nonnull net.minecraft.entity.player.EntityPlayer player) {
        // First check native BATTLEMAGE set via super
        if (super.isBattlemage(player)) return true;
        // Check our custom sets: all 4 armor slots must be exclusively one of our types
        int warlockCount = 0;
        int battlemageCount = 0;
        for (net.minecraft.item.ItemStack piece : player.inventory.armorInventory) {
            if (piece.isEmpty()) continue;
            if (piece.getItem() instanceof LivingWarlockArmorItem || piece.getItem() instanceof SentientWarlockArmorItem) {
                warlockCount++;
            } else if (piece.getItem() instanceof LivingBattlemageArmorItem || piece.getItem() instanceof SentientBattlemageArmorItem) {
                battlemageCount++;
            }
        }
        return warlockCount == 4 || battlemageCount == 4;
    }

    @Override
    public List<String> getActiveAdvProperties(ItemStack stack) {
        return Arrays.asList(AdvPropertyRegistry.ASHEN_LEGACY);
    }
}
