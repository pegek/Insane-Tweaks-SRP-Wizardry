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

public class BattleMageArmorItem extends ItemWizardArmour {

    private static final UUID HEAD_UUID = UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B");
    private static final UUID CHEST_UUID = UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E");
    private static final UUID LEGS_UUID = UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D");
    private static final UUID FEET_UUID = UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150");

    @SuppressWarnings("null")
    public BattleMageArmorItem(EntityEquipmentSlot slot) {
        super(ItemWizardArmour.ArmourClass.BATTLEMAGE, slot, null);

        String slotName = slot == EntityEquipmentSlot.HEAD ? "helmet" :
                         slot == EntityEquipmentSlot.CHEST ? "chestplate" :
                         slot == EntityEquipmentSlot.LEGS ? "leggings" : "boots";

        this.setRegistryName(new ResourceLocation("insanetweaks", "battle_mage_" + slotName));
        this.setUnlocalizedName("battle_mage_" + slotName);
        this.setCreativeTab(CreativeTabs.COMBAT);

        int customDurability = slot == EntityEquipmentSlot.HEAD ? 15000 :
                              slot == EntityEquipmentSlot.CHEST ? 22000 :
                              slot == EntityEquipmentSlot.LEGS ? 20500 : 18000;
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
                this.armorType == EntityEquipmentSlot.CHEST ? 11.0d : 
                this.armorType == EntityEquipmentSlot.LEGS ? 9.0d : 5.0d;
                
            double customToughness = 3.5d;

            multimap.put(SharedMonsterAttributes.ARMOR.getName(), new AttributeModifier(uuid, "Armor modifier", customArmor, 0));
            multimap.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), new AttributeModifier(uuid, "Armor toughness", customToughness, 0));
        }

        return multimap;
    }

    @Override
    public void applySpellModifiers(EntityLivingBase caster, Spell spell, SpellModifiers modifiers) {
        modifiers.set(SpellModifiers.COST, (modifiers.get(SpellModifiers.COST) * 0.97f), false);

        modifiers.set(SpellModifiers.CHARGEUP, (modifiers.get(SpellModifiers.CHARGEUP) * 0.97f), false);

        modifiers.set("cooldown", (modifiers.get("cooldown") * 0.97f), true);
    }

    @Override
    public void addInformation(@javax.annotation.Nonnull ItemStack stack, @javax.annotation.Nullable World world, @javax.annotation.Nonnull java.util.List<String> tooltip, @javax.annotation.Nonnull net.minecraft.client.util.ITooltipFlag flag) {
        // Stop EBWizardry from adding its own mana/cooldown information.
        // Custom tooltips are handled in SpellbladeTooltipHandler to allow for logic-heavy formatting.
    }

    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack itemStack) {
        // Intentionally empty per original Groovy implementation.
    }

    @Override
    @Nonnull
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "insanetweaks:textures/models/armor/custom_battlemage.png";
    }
}
