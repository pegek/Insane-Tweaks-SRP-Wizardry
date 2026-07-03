package com.spege.insanetweaks.items.spellblade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.spege.insanetweaks.util.AdaptationUpgradeHelper;
import com.spege.insanetweaks.util.PlayerManaCompat;
import com.spege.insanetweaks.util.SoManyEnchantmentsCompat;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import com.google.common.collect.Multimap;
import javax.annotation.Nonnull;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.Arrays;

import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.api.AdvPropertyRegistry;

import com.windanesz.ancientspellcraft.item.ItemBattlemageSword;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;

import com.oblivioussp.spartanweaponry.api.IWeaponPropertyContainer;
import com.oblivioussp.spartanweaponry.api.ToolMaterialEx;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty;
import com.oblivioussp.spartanweaponry.api.weaponproperty.IPropertyCallback;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponPropertyWithCallback;
import com.oblivioussp.spartanweaponry.init.ModelRenderRegistry;

/**
 * fucking piece of shit doest work
 * Abstract bridge: connects ItemBattlemageSword (AncientSpellcraft) with
 * IWeaponPropertyContainer (SpartanWeaponry). Handles weapon properties,
 * kill-based spell synergy and model registration.
 *
 * Armour synergy checks for a full set of SentientWarlockArmorItem or
 * LivingWarlockArmorItem.
 */
public abstract class BridgeSpellblade extends ItemBattlemageSword
        implements IWeaponPropertyContainer<net.minecraft.item.Item>, ITweaksPropertyHolder {

    protected final List<WeaponProperty> swProperties = new ArrayList<>();
    protected String swModelPath = null;
    protected String bridgeName;
    protected String bridgeModId;
    protected ToolMaterialEx bridgeMaterial = null;
    protected static final int ARCANE_ADAPTATION_LEVEL = 1;
    private static final boolean HAS_POTIONCORE = net.minecraftforge.fml.common.Loader.isModLoaded("potioncore");

    @SuppressWarnings("null")
    public BridgeSpellblade(String name, String modId, Tier tier, int maxUpgrades) {
        super(tier, maxUpgrades);
        this.bridgeName = name;
        this.bridgeModId = modId;
        this.setRegistryName(new ResourceLocation(modId, name));
        this.setUnlocalizedName(name);
    }

    // ------------------------------------------------------------------
    // IWeaponPropertyContainer Implementation
    // ------------------------------------------------------------------

    @Override
    @Nonnull
    public net.minecraft.item.Item addWeaponProperty(WeaponProperty prop) {
        if (prop != null && !swProperties.contains(prop)) {
            swProperties.add(prop);
        }
        return this;
    }

    /** Fluent helper Ereturns BridgeSpellblade for chaining during registration. */
    public BridgeSpellblade addBridgeProperty(WeaponProperty prop) {
        addWeaponProperty(prop);
        return this;
    }

    @Override
    public boolean hasWeaponProperty(WeaponProperty prop) {
        return swProperties.contains(prop);
    }

    @Override
    public WeaponProperty getFirstWeaponPropertyWithType(String type) {
        for (WeaponProperty prop : swProperties) {
            if (type.equals(prop.getType())) {
                return prop;
            }
        }
        return null;
    }

    @Override
    public List<WeaponProperty> getAllWeaponPropertiesWithType(String type) {
        List<WeaponProperty> result = new ArrayList<>();
        for (WeaponProperty prop : swProperties) {
            if (type.equals(prop.getType())) {
                result.add(prop);
            }
        }
        return result;
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public List<WeaponProperty> getAllWeaponProperties() {
        return Collections.unmodifiableList(swProperties);
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public ToolMaterialEx getMaterialEx() {
        if (bridgeMaterial == null) {
            try {
                WeaponProperty[] propArray = swProperties.toArray(new WeaponProperty[0]);
                bridgeMaterial = new ToolMaterialEx(
                        bridgeName, "$nothing", bridgeModId, -1, -1, 4000, 22, 1.0f, getBaseAttackDamage(), -1,
                        propArray);
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                    System.out.println("[BridgeSpellblade] getMaterialEx built with " + propArray.length
                            + " properties for " + bridgeModId + ":" + bridgeName);
            } catch (Throwable t) {
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                    System.out.println("[BridgeSpellblade] getMaterialEx with props failed (" + t.getMessage()
                            + ") -- simple fallback");
                bridgeMaterial = new ToolMaterialEx(
                        bridgeName, "$nothing", bridgeModId, -1, -1, 4000, 22, 1.0f, getBaseAttackDamage(), -1);
            }
        }
        return bridgeMaterial;
    }

    @Override
    public float getDirectAttackDamage() {
        return getBaseAttackDamage();
    }

    public float getBaseAttackDamage() {
        return 20.0f;
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier> getAttributeModifiers(
            @Nonnull EntityEquipmentSlot slot, @Nonnull ItemStack stack) {
        Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier> multimap = com.google.common.collect.HashMultimap
                .create();
        multimap.putAll(super.getAttributeModifiers(slot, stack));
        if (slot == EntityEquipmentSlot.MAINHAND) {
            SoManyEnchantmentsCompat.addAttackSpeedModifiers(stack, multimap);

            // Magically Adapted - magic damage modifier for PotionCore
            // Only activates if PotionCore is actually loaded, preventing NPEs on the
            // AttributeMap
            if (HAS_POTIONCORE) {
                ResourceLocation regLoc = this.getRegistryName();
                if (regLoc != null) {
                    String regName = regLoc.toString();
                    double magicDmg = 0;

                    if ("insanetweaks:sentient_spellblade".equals(regName)) {
                        magicDmg = 0.10D; // 10% bonus for Sentient
                    } else if ("insanetweaks:living_spellblade".equals(regName)) {
                        int kills = 0;
                        if (stack.hasTagCompound()) {
                            NBTTagCompound nbt = stack.getTagCompound();
                            if (nbt != null && nbt.hasKey("SentientKills")) {
                                kills = nbt.getInteger("SentientKills");
                            }
                        }
                        int magicBonus = Math.max(1, Math.min(10, (kills * 10) / 900));
                        magicDmg = magicBonus / 100.0D;
                    }

                    if (magicDmg > 0) {
                        multimap.put(
                                "potioncore.magicDamage",
                                new net.minecraft.entity.ai.attributes.AttributeModifier(
                                        java.util.UUID.fromString("6C2F3E8A-5182-421A-B01B-BCCE9786A000"),
                                        "Magically Adapted", magicDmg, 0));
                    }
                }
            }
        }

        return multimap;
    }

    // ------------------------------------------------------------------
    // Event Callbacks & Modifiers
    // ------------------------------------------------------------------

    @Override
    public boolean hitEntity(@Nonnull ItemStack stack, @Nonnull EntityLivingBase target, @Nonnull EntityLivingBase attacker) {
        boolean result = super.hitEntity(stack, target, attacker);
        ToolMaterialEx mat = getMaterialEx();

        for (WeaponProperty prop : swProperties) {
            if (prop instanceof WeaponPropertyWithCallback) {
                IPropertyCallback cb = ((WeaponPropertyWithCallback) prop).getCallback();
                if (cb != null) {
                    try {
                        cb.onHitEntity(mat, stack, target, attacker, (net.minecraft.entity.Entity) null);
                    } catch (Throwable t) {
                        if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                            System.out.println(
                                    "[BridgeSpellblade] onHit callback error for " + prop + ": " + t.getMessage());
                    }
                }
            }
        }

        return result;
    }

    @Override
    public void onUpdate(@Nonnull ItemStack stack, @Nonnull World world, @Nonnull Entity entity, int itemSlot,
            boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);

        if (world.isRemote || !(entity instanceof EntityPlayer) || !PlayerManaCompat.isAvailable()) {
            return;
        }

        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && PlayerManaCompat.hasUsableMana((EntityPlayer) entity)) {
            nbt.setBoolean("mana_available", true);
        }
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public SpellModifiers calculateModifiers(@Nonnull ItemStack stack, @Nonnull EntityPlayer player,
            @Nonnull Spell spell) {
        
        int warlockCount = 0;
        int battlemageCount = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack piece = player.inventory.armorInventory.get(i);
            if (!piece.isEmpty()) {
                if (piece.getItem() instanceof com.spege.insanetweaks.items.armor.SentientWarlockArmorItem ||
                    piece.getItem() instanceof com.spege.insanetweaks.items.armor.LivingWarlockArmorItem) {
                    warlockCount++;
                } else if (piece.getItem() instanceof com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem ||
                           piece.getItem() instanceof com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem) {
                    battlemageCount++;
                }
            }
        }
        
        boolean hasSynergy = (warlockCount == 4 || battlemageCount == 4);
        ItemStack[] originalArmor = new ItemStack[4];
        boolean tricked = false;
        
        // Artificial native synergy for Warlock armor
        if (warlockCount == 4) {
            tricked = true;
            for (int i = 0; i < 4; i++) {
                originalArmor[i] = player.inventory.armorInventory.get(i);
                ItemStack dummy = null;
                if (i == 0) dummy = new ItemStack(com.spege.insanetweaks.init.ModItems.LIVING_BATTLEMAGE_BOOTS);
                else if (i == 1) dummy = new ItemStack(com.spege.insanetweaks.init.ModItems.LIVING_BATTLEMAGE_LEGGINGS);
                else if (i == 2) dummy = new ItemStack(com.spege.insanetweaks.init.ModItems.LIVING_BATTLEMAGE_CHESTPLATE);
                else if (i == 3) dummy = new ItemStack(com.spege.insanetweaks.init.ModItems.LIVING_BATTLEMAGE_HELMET);
                
                player.inventory.armorInventory.set(i, dummy);
            }
        }

        SpellModifiers modifiers;
        try {
            modifiers = super.calculateModifiers(stack, player, spell);
        } finally {
            if (tricked) {
                for (int i = 0; i < 4; i++) {
                    player.inventory.armorInventory.set(i, originalArmor[i]);
                }
            }
        }

        int killCount = 0;
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null && nbt.hasKey("SentientKills")) {
                killCount = nbt.getInteger("SentientKills");
            }
        }

        int bonusPercent;
        ResourceLocation regLoc = stack.getItem().getRegistryName();
        if (regLoc != null && "insanetweaks:sentient_spellblade".equals(regLoc.toString())) {
            bonusPercent = 70;
        } else {
            bonusPercent = Math.min(70, (killCount * 70) / 900);
        }

        if (bonusPercent > 0) {
            if (hasSynergy) {
                float multiplier = 1.0f + (bonusPercent / 100.0f);

                // Nerf runewords: reduce our custom synergy bonus by half specifically for
                // Runewords
                // This prevents game-breaking overlap with their innate 1.20x * 1.20x
                // BATTLEMAGE class bonuses
                if (spell.getRegistryName() != null
                        && "ancientspellcraft".equals(spell.getRegistryName().getResourceDomain())
                        && spell.getRegistryName().getResourcePath().contains("runeword")) {
                    multiplier = 1.0f + ((bonusPercent / 100.0f) * 0.5f);
                }

                modifiers.set(SpellModifiers.POTENCY, multiplier, true);
            }
        }

        return modifiers;
    }

    protected int getDefaultAdaptationLevel() {
        return ARCANE_ADAPTATION_LEVEL;
    }

    public int getArcaneAdaptationLevel(ItemStack stack) {
        return Math.min(3, this.getDefaultAdaptationLevel()
                + AdaptationUpgradeHelper.getAppliedAdaptationUpgradeLevel(stack));
    }

    public int getArcaneAdaptationPenaltyPercent(ItemStack stack) {
        return AdaptationUpgradeHelper.getForeignSpellCostPenaltyPercent(this.getArcaneAdaptationLevel(stack));
    }

    // ------------------------------------------------------------------
    // Model Registration
    // ------------------------------------------------------------------

    @SideOnly(Side.CLIENT)
    @SuppressWarnings("null")
    public void registerModel() {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT)
            return;

        ResourceLocation regName = this.getRegistryName();
        if (regName == null)
            return;

        // 1. Always register standard model (inventory icon)
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(regName, "inventory"));
        if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
            System.out.println("[BridgeSpellblade] Registered standard model for: " + regName);

        // 2. Register Spartan Weaponry model if path is provided
        if (swModelPath != null) {
            try {
                String modId = regName.getResourceDomain();
                ModelRenderRegistry.addItemToRegistry(this, new ResourceLocation(modId, swModelPath));
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                    System.out.println("[BridgeSpellblade] Registered Spartan model: " + modId + ":" + swModelPath);
            } catch (Throwable t) {
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                    System.out.println(
                            "[BridgeSpellblade] Spartan ModelRenderRegistry skipped or failed: " + t.getMessage());
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public List<String> getActiveAdvProperties(ItemStack stack) {
        return Arrays.asList(AdvPropertyRegistry.ASHEN_LEGACY);
    }
}
