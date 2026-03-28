package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.attribute.AdvancedInstance;
import baubles.api.attribute.AttributeManager;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityItemIndestructible;
import com.spege.insanetweaks.events.BaubleFruitEventHandler;
import com.spege.insanetweaks.init.ModItems;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Base class for all Bauble Fruit items.
 *
 * A Bauble Fruit is a one-time consumable that permanently expands a specific
 * bauble slot when eaten. All bauble fruits share the same mechanics  Esubclasses
 * only need to provide slot-specific parameters via the three abstract methods.
 *
 * Two runtime code paths:
 *   BaublesEX (v>1.5): real slot expansion via AttributeManager.applyAnonymousModifier.
 *   Legacy Baubles (v<=1.5): +1 generic.luck via vanilla Forge + BaubleFruitEventHandler.
 *
 * Subclasses must NOT override onFoodEaten/addInformation/createEntity  E * all logic lives here. Only the three abstract methods need to be implemented.
 */
@SuppressWarnings("null")
public abstract class BaseBaubleFruitItem extends ItemFood {

    /** NBT key for the BaublesEX consumption flag. Survives death (PERSISTED_NBT_TAG). */
    protected final String consumedTag;

    /**
     * NBT key for the legacy-mode consumption flag.
     * Deliberately separate from consumedTag so a player who consumed in legacy mode
     * can eat a NEW fruit after switching to BaublesEX to get the actual slot bonus.
     */
    protected final String consumedTagLegacy;

    protected BaseBaubleFruitItem(String registryName,
                                   String consumedTag,
                                   String consumedTagLegacy) {
        super(3, 0.3f, false);
        this.consumedTag = consumedTag;
        this.consumedTagLegacy = consumedTagLegacy;
        this.setAlwaysEdible();
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, registryName));
        this.setUnlocalizedName(registryName);
        this.setCreativeTab(CreativeTabs.FOOD);
        this.setMaxStackSize(1); // One-time consumable  Eintentionally non-stackable
    }

    // =========================================================================
    // Abstract  Esubclasses provide slot-specific parameters
    // =========================================================================

    /** The BaublesEX slot type this fruit expands (e.g. TypeData.Preset.RING). */
    protected abstract BaubleTypeEx getBaublesExType();

    /** Short description of the benefit, e.g. "+1 Ring slot". Used in tooltip. */
    protected abstract String getSlotDescription();

    /** First tooltip line  Ethematic flavour text unique to each fruit. */
    protected abstract String getFlavorText();

    // =========================================================================
    // Appearance  Eshared by all fruits
    // =========================================================================

    @Override
    public EnumRarity getRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }

    /** Enchantment glint  Esignals special one-time consumable. */
    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add("\u00a77" + getFlavorText());
        tooltip.add("\u00a77It feels like it could expand your perception of space.");
        tooltip.add("");
        if (ModItems.isBaublesExPresent()) {
            try {
                if (getBaublesExType() == null) {
                    tooltip.add("\u00a7cWARNING, such slot was not detected!");
                    tooltip.add("\u00a7cYou can add this slot type in Baubles config!");
                } else {
                    tooltip.add("\u00a7d\u00a7oPermanently grants \u00a7b" + getSlotDescription() + "\u00a7d\u00a7o.");
                }
            } catch (Exception e) {
                tooltip.add("\u00a7cWARNING, such slot was not detected!");
                tooltip.add("\u00a7cYou can add this slot type in Baubles config!");
            }
        } else {
            tooltip.add("\u00a7d\u00a7oPermanently grants \u00a7a+1 Luck\u00a7d\u00a7o "
                    + "\u00a78(Legacy Baubles mode)\u00a7d\u00a7o.");
        }
        tooltip.add("\u00a78\u00a7oOne-time effect per player.");
    }

    // =========================================================================
    // Core Logic  Ecalled server-side after the eating animation completes
    // =========================================================================

    @Override
    public void onFoodEaten(@Nonnull ItemStack stack, @Nonnull World worldIn,
            @Nonnull EntityPlayer player) {
        if (worldIn.isRemote) return;
        if (!(player instanceof EntityPlayerMP)) return;

        EntityPlayerMP playerMP = (EntityPlayerMP) player;
        NBTTagCompound persistent = playerMP.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);

        if (ModItems.isBaublesExPresent()) {
            // ------------------------------------------------------------------
            // BaublesEX path (v > 1.5): real slot expansion
            // ------------------------------------------------------------------
            if (persistent.getBoolean(consumedTag)) return; // Fruit wasted by design

            BaubleTypeEx slotType = getBaublesExType();
            
            // Safety guard: if the server config disabled this dynamic slot type (e.g., totem), 
            // BaublesEX will return null. We must catch this to prevent a NullPointerException crash.
            if (slotType == null) {
                playerMP.sendMessage(new TextComponentString("\u00a7c[!] \u00a77The ancient fruit slips from your grasp... its corresponding slot is disabled in this world's physics."));
                return;
            }
            
            AbstractAttributeMap map = playerMP.getAttributeMap();
            AdvancedInstance instance = AttributeManager.getInstance(map, slotType);
            double current = instance.getAnonymousModifier(0);
            instance.applyAnonymousModifier(0, current + 1);

            persistent.setBoolean(consumedTag, true);
            playerMP.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);

            if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo) {
                playerMP.sendMessage(new TextComponentString(
                        "\u00a7d[DEBUG-BaubleFruit] " + getSlotDescription()
                        + " granted (BaublesEX). Modifier: " + (int)(current + 1)));
            }
        } else {
            // ------------------------------------------------------------------
            // Legacy Baubles path (v <= 1.5): +1 generic.luck fallback
            // Different NBT tag so players can still get the real slot after
            // switching to BaublesEX.
            // ------------------------------------------------------------------
            if (persistent.getBoolean(consumedTagLegacy)) return;

            persistent.setBoolean(consumedTagLegacy, true);
            BaubleFruitEventHandler.grantLegacyBonus(playerMP, persistent);

            if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo) {
                playerMP.sendMessage(new TextComponentString(
                        "\u00a7d[DEBUG-BaubleFruit] Legacy bonus granted for "
                        + getSlotDescription() + " (Luck+1 applied)."));
            }
        }

        // Sound: shared by both paths
        worldIn.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    // =========================================================================
    // Indestructible dropped item  Eprevents the fruit being voided in lava
    // =========================================================================

    @Override
    public boolean hasCustomEntity(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @Nullable
    public Entity createEntity(@Nonnull World world, @Nonnull Entity location,
            @Nonnull ItemStack itemstack) {
        EntityItemIndestructible entity = new EntityItemIndestructible(
                world, location.posX, location.posY, location.posZ, itemstack);
        entity.motionX = location.motionX;
        entity.motionY = location.motionY;
        entity.motionZ = location.motionZ;
        entity.setDefaultPickupDelay();

        if (location instanceof EntityItem) {
            String thrower = ((EntityItem) location).getThrower();
            String owner   = ((EntityItem) location).getOwner();
            if (thrower != null) entity.setThrower(thrower);
            if (owner   != null) entity.setOwner(owner);
        }
        return entity;
    }
}
