package com.spege.insanetweaks.items.shield;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.EntityEquipmentSlot;
import com.google.common.collect.Multimap;
import com.windanesz.ancientspellcraft.item.ItemBattlemageShield;
import com.spege.insanetweaks.items.armor.LivingWarlockArmorItem;
import com.spege.insanetweaks.items.armor.SentientWarlockArmorItem;
import com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem;
import com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem;
import java.util.UUID;
import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.api.AdvPropertyRegistry;

/**
 * The base tier for the Aegis progression system.
 * It has standard durability and lower protection stats compared to the Sentient tier.
 */
public class LivingAegisItem extends ItemBattlemageShield implements ITweaksPropertyHolder {

    private static final UUID ARMOR_MAIN_UUID = UUID.fromString("6d68b6aa-5f96-48eb-bfb0-ad1ee1cd20e4");
    private static final UUID TOUGHNESS_MAIN_UUID = UUID.fromString("f9a2b5ef-1a48-43d9-9ebd-59cb45f4ac1c");
    private static final UUID ARMOR_OFF_UUID = UUID.fromString("4c688c24-5d51-4098-9034-758fa498a467");
    private static final UUID TOUGHNESS_OFF_UUID = UUID.fromString("64299b92-7ef4-4fef-ab53-15be962be8fd");

    @SuppressWarnings("null")
    public LivingAegisItem() {
        super();
        this.setRegistryName("insanetweaks", "living_aegis");
        this.setUnlocalizedName("living_aegis");
        CreativeTabs tab = CreativeTabs.COMBAT;
        if (tab != null) this.setCreativeTab(tab);
        this.setMaxDamage(3000); // Increased durability as requested
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
            
            // Unique UUIDs for shielding - Lower stats (1.0 each)
            UUID uuidArmor = equipmentSlot == EntityEquipmentSlot.MAINHAND ? ARMOR_MAIN_UUID : ARMOR_OFF_UUID;
            UUID uuidToughness = equipmentSlot == EntityEquipmentSlot.MAINHAND ? TOUGHNESS_MAIN_UUID : TOUGHNESS_OFF_UUID;
             
            if (uuidArmor != null) multimap.put(SharedMonsterAttributes.ARMOR.getName(), new AttributeModifier(uuidArmor, "Armor modifier", 2.0D, 0));
            if (uuidToughness != null) multimap.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), new AttributeModifier(uuidToughness, "Armor toughness", 2.0D, 0));
        }
        return multimap;
    }

    @Override
    public int getItemEnchantability() {
        return 14; // Standard tier enchantability
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
