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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import java.util.Arrays;
import java.util.List;

import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.api.AdvPropertyRegistry;

public class SentientBattlemageArmorItem extends ItemWizardArmour implements ITweaksPropertyHolder {

    public static final String NBT_ADAPTATION_NAMES = "itResistNames";
    public static final String NBT_ADAPTATION_POINTS = "itResistPoints";
    
    public static final int MAX_DAMAGE_TYPES = 5;
    public static final int MAX_POINTS_PER_TYPE = 10;
    public static final float POINT_REDUCTION = 0.014f; // 1.4% per point
    public static final float CHANCE_TO_LEARN = 0.45f; // 45%

    private static final UUID HEAD_UUID = UUID.fromString("9A8B7C6D-5E4F-3A2B-1C0D-E9F8A7B6C5D4");
    private static final UUID CHEST_UUID = UUID.fromString("8B7C6D5E-4F3A-2B1C-0D9E-F8A7B6C5D4E3");
    private static final UUID LEGS_UUID = UUID.fromString("7C6D5E4F-3A2B-1C0D-9E8F-A7B6C5D4E3F2");
    private static final UUID FEET_UUID = UUID.fromString("6D5E4F3A-2B1C-0D9E-8F7A-B6C5D4E3F2A1");

    @SuppressWarnings("null")
    public SentientBattlemageArmorItem(EntityEquipmentSlot slot) {
        super(ItemWizardArmour.ArmourClass.BATTLEMAGE, slot, null);

        String slotName = slot == EntityEquipmentSlot.HEAD ? "helmet" :
                         slot == EntityEquipmentSlot.CHEST ? "chestplate" :
                         slot == EntityEquipmentSlot.LEGS ? "leggings" : "boots";

        this.setRegistryName(new ResourceLocation("insanetweaks", "sentient_battlemage_" + slotName));
        this.setUnlocalizedName("sentient_battlemage_" + slotName);
        this.setCreativeTab(CreativeTabs.COMBAT);

        int customDurability = slot == EntityEquipmentSlot.HEAD ? 16500 :
                              slot == EntityEquipmentSlot.CHEST ? 24000 :
                              slot == EntityEquipmentSlot.LEGS ? 22500 : 19500;
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
                this.armorType == EntityEquipmentSlot.CHEST ? 12.0d : 
                this.armorType == EntityEquipmentSlot.LEGS ? 10.0d : 5.0d;
                
            double customToughness = 4.0d;

            multimap.put(SharedMonsterAttributes.ARMOR.getName(), new AttributeModifier(uuid, "Armor modifier", customArmor, 0));
            multimap.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), new AttributeModifier(uuid, "Armor toughness", customToughness, 0));
        }

        return multimap;
    }

    @Override
    @SuppressWarnings("null")
    public void applySpellModifiers(EntityLivingBase caster, Spell spell, SpellModifiers modifiers) {
        // Flat 2% reduction per piece for Sentient Battlemage (and NO ward as per user's request)
        float multiplier = 1.0f - 0.02f;
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
    }

    @Override
    @Nonnull
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "insanetweaks:textures/models/armor/sentient_battlemage.png";
    }

    public static float getReductionForType(ItemStack stack, String damageType) {
        if (!stack.hasTagCompound()) return 0f;
        NBTTagCompound nbt = stack.getTagCompound();
        NBTTagList names = nbt.getTagList(NBT_ADAPTATION_NAMES, 8); // 8 is String
        int[] ptsArray = nbt.getIntArray(NBT_ADAPTATION_POINTS);

        for (int i = 0; i < names.tagCount(); i++) {
            if (names.getStringTagAt(i).equals(damageType)) {
                if (ptsArray.length > i) {
                    int effectivePoints = Math.min(ptsArray[i], MAX_POINTS_PER_TYPE);
                    return effectivePoints * POINT_REDUCTION;
                }
            }
        }
        return 0f;
    }

    @Override
    public List<String> getActiveAdvProperties(ItemStack stack) {
        return Arrays.asList(AdvPropertyRegistry.ASHEN_LEGACY);
    }
}
