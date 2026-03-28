package com.spege.insanetweaks.items.armor;

import java.util.UUID;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import javax.annotation.Nonnull;

import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class ParasiteWizardArmorItem extends ItemWizardArmour {

    private static final UUID HEAD_UUID = UUID.fromString("1A2B3C4D-5E6F-7A8B-9C0D-1E2F3A4B5C6D");
    private static final UUID CHEST_UUID = UUID.fromString("2B3C4D5E-6F7A-8B9C-0D1E-2F3A4B5C6D7E");
    private static final UUID LEGS_UUID = UUID.fromString("3C4D5E6F-7A8B-9C0D-1E2F-3A4B5C6D7E8F");
    private static final UUID FEET_UUID = UUID.fromString("4D5E6F7A-8B9C-0D1E-2F3A-4B5C6D7E8F9A");

    @SuppressWarnings("null")
    public ParasiteWizardArmorItem(EntityEquipmentSlot slot) {
        super(ItemWizardArmour.ArmourClass.BATTLEMAGE, slot, null);

        String slotName = slot == EntityEquipmentSlot.HEAD ? "helmet" :
                         slot == EntityEquipmentSlot.CHEST ? "chestplate" :
                         slot == EntityEquipmentSlot.LEGS ? "leggings" : "boots";

        this.setRegistryName(new ResourceLocation("insanetweaks", "parasite_mage_" + slotName));
        this.setUnlocalizedName("parasite_mage_" + slotName);
        this.setCreativeTab(CreativeTabs.COMBAT);

        int customDurability = slot == EntityEquipmentSlot.HEAD ? 10000 :
                              slot == EntityEquipmentSlot.CHEST ? 15000 :
                              slot == EntityEquipmentSlot.LEGS ? 12500 : 10000;
        this.setMaxDamage(customDurability);
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot equipmentSlot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = HashMultimap.create();

        if (equipmentSlot == this.armorType) {
            UUID uuid = equipmentSlot == EntityEquipmentSlot.HEAD ? HEAD_UUID :
                       equipmentSlot == EntityEquipmentSlot.CHEST ? CHEST_UUID :
                       equipmentSlot == EntityEquipmentSlot.LEGS ? LEGS_UUID : FEET_UUID;

            double customArmor = 
                this.armorType == EntityEquipmentSlot.HEAD ? 5.0d : 
                this.armorType == EntityEquipmentSlot.CHEST ? 9.0d : 
                this.armorType == EntityEquipmentSlot.LEGS ? 7.0d : 4.0d;
                
            double customToughness = 2.5d;

            multimap.put(SharedMonsterAttributes.ARMOR.getName(), new AttributeModifier(uuid, "Armor modifier", customArmor, 0));
            multimap.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), new AttributeModifier(uuid, "Armor toughness", customToughness, 0));
        }

        return multimap;
    }

    @Override
    @SuppressWarnings("null")
    public void applySpellModifiers(EntityLivingBase caster, Spell spell, SpellModifiers modifiers) {
        // TODO: Future adaptive scaling — reduce cost/chargeup based on absorbed damage.
        // The NBT tag 'adaptation_points' was planned for this, but is not currently written
        // anywhere. Until the adaptation tracking system is implemented, apply a flat 0.01f
        // reduction per piece (unchanged from the original Groovy implementation).
        float reduction = 0.01f;

        float multiplier = 1.0f - reduction;
        modifiers.set(SpellModifiers.COST, (modifiers.get(SpellModifiers.COST) * multiplier), false);
        modifiers.set(SpellModifiers.CHARGEUP, (modifiers.get(SpellModifiers.CHARGEUP) * multiplier), false);
        modifiers.set("cooldown", (modifiers.get("cooldown") * multiplier), true);
    }

    @Override
    public void addInformation(@javax.annotation.Nonnull ItemStack stack, @javax.annotation.Nullable World world, @javax.annotation.Nonnull java.util.List<String> tooltip, @javax.annotation.Nonnull net.minecraft.client.util.ITooltipFlag flag) {
        // Stop EBWizardry from adding its own mana/cooldown information.
    }

    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack itemStack) {
        // Intentionally empty per original Groovy implementation.
    }

    @Override
    public boolean hasCustomEntity(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @Nonnull
    public net.minecraft.entity.Entity createEntity(@Nonnull World world, @Nonnull net.minecraft.entity.Entity location, @Nonnull ItemStack itemstack) {
        com.spege.insanetweaks.entities.EntityItemIndestructible entity = new com.spege.insanetweaks.entities.EntityItemIndestructible(world, location.posX, location.posY, location.posZ, itemstack);
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
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "insanetweaks:textures/models/armor/parasite_mage.png";
    }
}
