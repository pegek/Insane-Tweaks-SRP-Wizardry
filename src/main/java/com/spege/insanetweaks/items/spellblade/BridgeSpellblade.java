package com.spege.insanetweaks.items.spellblade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import com.google.common.collect.Multimap;
import javax.annotation.Nonnull;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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
 * Abstract bridge: connects ItemBattlemageSword (AncientSpellcraft) with
 * IWeaponPropertyContainer (SpartanWeaponry). Handles weapon properties,
 * kill-based spell synergy and model registration.
 *
 * Armour synergy checks for a full set of BattleMageArmorItem or ParasiteWizardArmorItem.
 */
public abstract class BridgeSpellblade extends ItemBattlemageSword implements IWeaponPropertyContainer<net.minecraft.item.Item> {

    protected final List<WeaponProperty> swProperties = new ArrayList<>();
    protected String swModelPath = null;
    protected String bridgeName;
    protected String bridgeModId;
    protected ToolMaterialEx bridgeMaterial = null;

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

    /** Fluent helper — returns BridgeSpellblade for chaining during registration. */
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
                    bridgeName, "$nothing", bridgeModId, -1, -1, 4000, 22, 1.0f, (float) getBaseAttackDamage(), -1, propArray
                );
                if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) System.out.println("[BridgeSpellblade] getMaterialEx built with " + propArray.length + " properties for " + bridgeModId + ":" + bridgeName);
            } catch (Throwable t) {
                if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) System.out.println("[BridgeSpellblade] getMaterialEx with props failed (" + t.getMessage() + ") -- simple fallback");
                bridgeMaterial = new ToolMaterialEx(
                    bridgeName, "$nothing", bridgeModId, -1, -1, 4000, 22, 1.0f, (float) getBaseAttackDamage(), -1
                );
            }
        }
        return bridgeMaterial;
    }

    @Override
    public float getDirectAttackDamage() {
        return (float) getBaseAttackDamage();
    }

    public float getBaseAttackDamage() {
        return 20.0f;
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier> getAttributeModifiers(@Nonnull EntityEquipmentSlot slot, @Nonnull ItemStack stack) {
        return super.getAttributeModifiers(slot, stack);
    }

    // ------------------------------------------------------------------
    // Event Callbacks & Modifiers
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("null")
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
                        if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) System.out.println("[BridgeSpellblade] onHit callback error for " + prop + ": " + t.getMessage());
                    }
                }
            }
        }

        return result;
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public SpellModifiers calculateModifiers(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, @Nonnull Spell spell) {
        SpellModifiers modifiers = super.calculateModifiers(stack, player, spell);

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
            bonusPercent = 85;
        } else {
            bonusPercent = Math.min(85, (killCount / 100) * 5);
        }

        if (bonusPercent > 0) {
            boolean hasSynergy = true;
            for (ItemStack piece : player.inventory.armorInventory) {
                if (piece == null || piece.isEmpty() || 
                   (!(piece.getItem() instanceof com.spege.insanetweaks.items.armor.BattleMageArmorItem) && 
                    !(piece.getItem() instanceof com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem))) {
                    hasSynergy = false;
                    break;
                }
            }
            if (hasSynergy) {
                float multiplier = 1.0f + (bonusPercent / 100.0f);
                modifiers.set(SpellModifiers.POTENCY, multiplier, true);
            }
        }

        return modifiers;
    }

    // ------------------------------------------------------------------
    // Model Registration
    // ------------------------------------------------------------------

    @SideOnly(Side.CLIENT)
    @SuppressWarnings("null")
    public void registerModel() {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) return;

        ResourceLocation regName = this.getRegistryName();
        if (regName == null) return;

        // 1. Always register standard model (inventory icon)
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(regName, "inventory"));
        if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) System.out.println("[BridgeSpellblade] Registered standard model for: " + regName);

        // 2. Register Spartan Weaponry model if path is provided
        if (swModelPath != null) {
            try {
                String modId = regName.getResourceDomain();
                ModelRenderRegistry.addItemToRegistry(this, new ResourceLocation(modId, swModelPath));
                if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) System.out.println("[BridgeSpellblade] Registered Spartan model: " + modId + ":" + swModelPath);
            } catch (Throwable t) {
                if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) System.out.println("[BridgeSpellblade] Spartan ModelRenderRegistry skipped or failed: " + t.getMessage());
            }
        }
    }
}
