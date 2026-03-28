package com.spege.insanetweaks.items.shield;

import java.util.UUID;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.google.common.collect.Multimap;
import com.windanesz.ancientspellcraft.item.ItemBattlemageShield;

import com.spege.insanetweaks.entities.EntityItemIndestructible;

import javax.annotation.Nonnull;

public class SentientAegisItem extends ItemBattlemageShield {
    @SuppressWarnings("null")
    public SentientAegisItem() {
        super();
        this.setRegistryName("insanetweaks", "sentient_aegis");
        this.setUnlocalizedName("sentient_aegis");
        CreativeTabs tab = CreativeTabs.COMBAT;
        if (tab != null) this.setCreativeTab(tab);
        this.setMaxDamage(2500); // Base Mana capacity / Durability
    }

    @Override
    public boolean hasCustomEntity(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @Nonnull
    public Entity createEntity(@Nonnull World world, @Nonnull Entity location, @Nonnull ItemStack itemstack) {
        EntityItemIndestructible entity = new EntityItemIndestructible(world, location.posX, location.posY, location.posZ, itemstack);
        entity.motionX = location.motionX;
        entity.motionY = location.motionY;
        entity.motionZ = location.motionZ;
        entity.setDefaultPickupDelay();
        
        if (location instanceof net.minecraft.entity.item.EntityItem) {
            String thrower = ((net.minecraft.entity.item.EntityItem) location).getThrower();
            String owner = ((net.minecraft.entity.item.EntityItem) location).getOwner();
            if (thrower != null) entity.setThrower(thrower);
            if (owner != null) entity.setOwner(owner);
        }
        return entity;
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
            byte[] armorBytes = ("sentient_aegis_armor_" + equipmentSlot.name()).getBytes();
            byte[] toughnessBytes = ("sentient_aegis_toughness_" + equipmentSlot.name()).getBytes();
            
            UUID uuidArmor = UUID.nameUUIDFromBytes(armorBytes);
            UUID uuidToughness = UUID.nameUUIDFromBytes(toughnessBytes);
             
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
}
