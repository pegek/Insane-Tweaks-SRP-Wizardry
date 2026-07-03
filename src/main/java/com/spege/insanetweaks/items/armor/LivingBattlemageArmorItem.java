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

public class LivingBattlemageArmorItem extends ItemWizardArmour implements ITweaksPropertyHolder {

    public static final String NBT_ADAPTATION_NAMES = "itResistNames";
    public static final String NBT_ADAPTATION_POINTS = "itResistPoints";
    public static final String NBT_HITS_ABSORBED = "itHitsAbsorbed";
    
    // Testing value is 1000.0f, original SRP-style value is 90000.0f
    public static final float EVOLUTION_THRESHOLD = 1000.0f; // 90000.0f
    
    public static final int MAX_DAMAGE_TYPES = 3;
    public static final int MAX_POINTS_PER_TYPE = 12;
    public static final float POINT_REDUCTION = 0.010f; // 1.0% per point
    public static final float CHANCE_TO_LEARN = 0.20f; // 20%

    private static final UUID HEAD_UUID = UUID.fromString("1C2A3B4C-5D6E-7F8A-9B0C-1D2E3F4A5B6C");
    private static final UUID CHEST_UUID = UUID.fromString("2D3B4C5D-6E7F-8A9B-0C1D-2E3F4A5B6C7D");
    private static final UUID LEGS_UUID = UUID.fromString("3E4C5D6E-7F8A-9B0C-1D2E-3F4A5B6C7D8E");
    private static final UUID FEET_UUID = UUID.fromString("4F5D6E7F-8A9B-0C1D-2E3F-4A5B6C7D8E9F");

    @SuppressWarnings("null")
    public LivingBattlemageArmorItem(EntityEquipmentSlot slot) {
        super(ItemWizardArmour.ArmourClass.BATTLEMAGE, slot, null);

        String slotName = slot == EntityEquipmentSlot.HEAD ? "helmet" :
                         slot == EntityEquipmentSlot.CHEST ? "chestplate" :
                         slot == EntityEquipmentSlot.LEGS ? "leggings" : "boots";

        this.setRegistryName(new ResourceLocation("insanetweaks", "living_battlemage_" + slotName));
        this.setUnlocalizedName("living_battlemage_" + slotName);
        this.setCreativeTab(CreativeTabs.COMBAT);

        int customDurability = slot == EntityEquipmentSlot.HEAD ? 11000 :
                              slot == EntityEquipmentSlot.CHEST ? 16000 :
                              slot == EntityEquipmentSlot.LEGS ? 15000 : 13000;
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
                this.armorType == EntityEquipmentSlot.HEAD ? 4.0d : 
                this.armorType == EntityEquipmentSlot.CHEST ? 10.0d : 
                this.armorType == EntityEquipmentSlot.LEGS ? 8.0d : 4.0d;
                
            double customToughness = 3.0d;

            multimap.put(SharedMonsterAttributes.ARMOR.getName(), new AttributeModifier(uuid, "Armor modifier", customArmor, 0));
            multimap.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), new AttributeModifier(uuid, "Armor toughness", customToughness, 0));
        }

        return multimap;
    }

    @Override
    @SuppressWarnings("null")
    public void applySpellModifiers(EntityLivingBase caster, Spell spell, SpellModifiers modifiers) {
        // Flat 1% reduction per piece for Living Battlemage
        float multiplier = 1.0f - 0.01f;
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
        return "insanetweaks:textures/models/armor/living_battlemage.png";
    }

    public static float getReductionForType(ItemStack stack, String damageType) {
        if (!stack.hasTagCompound()) return 0f;
        NBTTagCompound nbt = stack.getTagCompound();
        NBTTagList names = nbt.getTagList(NBT_ADAPTATION_NAMES, 8); // 8 = NBT String type
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
