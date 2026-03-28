package com.spege.insanetweaks.items.shield;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.world.World;
import com.google.common.collect.Multimap;
import com.windanesz.ancientspellcraft.item.ItemBattlemageShield;
import com.spege.insanetweaks.entities.EntityItemIndestructible;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * The base tier for the Aegis progression system.
 * It has standard durability and lower protection stats compared to the Sentient tier.
 */
public class LivingAegisItem extends ItemBattlemageShield {

    @SuppressWarnings("null")
    public LivingAegisItem() {
        super();
        this.setRegistryName("insanetweaks", "living_aegis");
        this.setUnlocalizedName("living_aegis");
        CreativeTabs tab = CreativeTabs.COMBAT;
        if (tab != null) this.setCreativeTab(tab);
        this.setMaxDamage(1750); // Increased durability as requested
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
            String owner   = ((net.minecraft.entity.item.EntityItem) location).getOwner();
            if (thrower != null) entity.setThrower(thrower);
            if (owner   != null) entity.setOwner(owner);
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
            
            // Unique UUIDs for shielding - Lower stats (1.0 each)
            byte[] armorBytes = ("living_aegis_armor_" + equipmentSlot.name()).getBytes();
            byte[] toughnessBytes = ("living_aegis_toughness_" + equipmentSlot.name()).getBytes();
            
            UUID uuidArmor = UUID.nameUUIDFromBytes(armorBytes);
            UUID uuidToughness = UUID.nameUUIDFromBytes(toughnessBytes);
             
            if (uuidArmor != null) multimap.put(SharedMonsterAttributes.ARMOR.getName(), new AttributeModifier(uuidArmor, "Armor modifier", 2.0D, 0));
            if (uuidToughness != null) multimap.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), new AttributeModifier(uuidToughness, "Armor toughness", 2.0D, 0));
        }
        return multimap;
    }

    @Override
    public int getItemEnchantability() {
        return 14; // Standard tier enchantability
    }
}
