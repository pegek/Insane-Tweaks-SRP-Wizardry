package com.spege.insanetweaks.items.fruit;

import baubles.api.BaubleTypeEx;
import baubles.api.attribute.AdvancedInstance;
import baubles.api.attribute.AttributeManager;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.BaubleFruitEventHandler;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.BaubleFruitProgress;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
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
 * bauble slot when eaten. All bauble fruits share the same mechanics — subclasses
 * only need to name the BaublesEX slot type they expand.
 *
 * Two runtime code paths:
 *   BaublesEX (v>1.5): real slot expansion via AttributeManager.applyAnonymousModifier.
 *   Legacy Baubles (v<=1.5): +1 generic.luck via vanilla Forge + BaubleFruitEventHandler.
 *
 * Subclasses must NOT override onFoodEaten/addInformation/createEntity — all logic lives here.
 * Only {@link #getBaublesExType()} needs implementing; the tooltip/flavour lang keys are derived
 * from the registry name.
 */
import java.util.Arrays;

import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.api.AdvPropertyRegistry;

@SuppressWarnings("null")
public abstract class BaseBaubleFruitItem extends ItemFood implements ITweaksPropertyHolder {

    /** NBT key for the BaublesEX consumption flag. Survives death (PERSISTED_NBT_TAG). */
    protected final String consumedTag;

    /**
     * NBT key for the legacy-mode consumption flag.
     * Deliberately separate from consumedTag so a player who consumed in legacy mode
     * can eat a NEW fruit after switching to BaublesEX to get the actual slot bonus.
     */
    protected final String consumedTagLegacy;

    /** Registry path, e.g. {@code bauble_fruit_ring} — the stem of every lang key below. */
    private final String fruitName;

    protected BaseBaubleFruitItem(String registryName,
                                   String consumedTag,
                                   String consumedTagLegacy) {
        super(3, 0.3f, false);
        this.consumedTag = consumedTag;
        this.consumedTagLegacy = consumedTagLegacy;
        this.fruitName = registryName;
        this.setAlwaysEdible();
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, registryName));
        this.setUnlocalizedName(registryName);
        this.setCreativeTab(CreativeTabs.FOOD);
        this.setMaxStackSize(1); // One-time consumable — intentionally non-stackable
    }

    // =========================================================================
    // Abstract — subclasses provide the slot type
    // =========================================================================

    /** The BaublesEX slot type this fruit expands (e.g. TypeData.Preset.RING). */
    protected abstract BaubleTypeEx getBaublesExType();

    /**
     * Public view of {@link #getBaublesExType()} — null when the slot type is disabled, or when
     * BaublesEX is not installed at all.
     *
     * <p>The presence check is not optional: under legacy Baubles the {@code baubles.api.registries}
     * classes the subclasses name do not exist, and resolving them throws
     * {@code NoClassDefFoundError} — an {@code Error}, which a {@code catch (Exception)} would
     * sail straight past. Hence the guard first and {@code Throwable} second.
     */
    public BaubleTypeEx getBaublesExSlotType() {
        if (!ModItems.isBaublesExPresent()) {
            return null;
        }
        try {
            return getBaublesExType();
        } catch (Throwable t) {
            return null;
        }
    }

    // =========================================================================
    // Lang keys — derived from the registry name, so a new fruit needs no Java strings
    // =========================================================================

    /** Thematic flavour line, e.g. {@code tooltip.insanetweaks.bauble_fruit_ring.flavor}. */
    public String getFlavorTextKey() {
        return "tooltip.insanetweaks." + fruitName + ".flavor";
    }

    /** Short benefit description, e.g. {@code tooltip.insanetweaks.bauble_fruit_ring.slot}. */
    public String getSlotDescriptionKey() {
        return "tooltip.insanetweaks." + fruitName + ".slot";
    }

    // =========================================================================
    // Consumption state
    // =========================================================================

    /**
     * Server-side truth: has this player already consumed this fruit type?
     * Reads whichever flag matches the currently installed Baubles flavour, so a player who
     * ate fruits under legacy Baubles can still claim the real slots after upgrading.
     */
    public boolean isConsumedBy(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        NBTTagCompound persistent = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        return persistent.getBoolean(ModItems.isBaublesExPresent() ? consumedTag : consumedTagLegacy);
    }

    // =========================================================================
    // Appearance — shared by all fruits
    // =========================================================================

    @Override
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }

    /** Enchantment glint — signals special one-time consumable. */
    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add("§7" + net.minecraft.client.resources.I18n.format(getFlavorTextKey()));
        tooltip.add("§7" + net.minecraft.client.resources.I18n.format(
                "tooltip.insanetweaks.bauble_fruit.perception"));
        tooltip.add("");
        if (ModItems.isBaublesExPresent()) {
            if (getBaublesExSlotType() == null) {
                tooltip.add("§c" + net.minecraft.client.resources.I18n.format(
                        "tooltip.insanetweaks.bauble_fruit.slot_missing"));
                tooltip.add("§c" + net.minecraft.client.resources.I18n.format(
                        "tooltip.insanetweaks.bauble_fruit.slot_missing_hint"));
            } else {
                tooltip.add(net.minecraft.client.resources.I18n.format(
                        "tooltip.insanetweaks.bauble_fruit.grants",
                        net.minecraft.client.resources.I18n.format(getSlotDescriptionKey())));
            }
        } else {
            tooltip.add(net.minecraft.client.resources.I18n.format(
                    "tooltip.insanetweaks.bauble_fruit.legacy"));
        }
        tooltip.add("§8§o" + net.minecraft.client.resources.I18n.format(
                "tooltip.insanetweaks.bauble_fruit.one_time"));

        addProgressTooltip(tooltip);
    }

    /** The live "Fruits consumed: N/max" line, plus a marker when this very fruit is spent. */
    @SideOnly(Side.CLIENT)
    private void addProgressTooltip(List<String> tooltip) {
        if (!com.spege.insanetweaks.config.ModConfig.baubleFruits.showEatenCounter) {
            return;
        }
        EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }

        int eaten = BaubleFruitProgress.countEaten(player);
        int cap = BaubleFruitProgress.getCap(player);
        if (cap > 0) {
            String key = eaten >= cap
                    ? "tooltip.insanetweaks.bauble_fruit.counter_full"
                    : "tooltip.insanetweaks.bauble_fruit.counter_capped";
            tooltip.add(net.minecraft.client.resources.I18n.format(key,
                    Integer.valueOf(eaten), Integer.valueOf(cap)));
        } else {
            tooltip.add(net.minecraft.client.resources.I18n.format(
                    "tooltip.insanetweaks.bauble_fruit.counter", Integer.valueOf(eaten)));
        }

        if (BaubleFruitProgress.isConsumed(player, this)) {
            tooltip.add("§c" + net.minecraft.client.resources.I18n.format(
                    "tooltip.insanetweaks.bauble_fruit.already_eaten"));
        }
    }

    // =========================================================================
    // Refusal — must happen BEFORE the item is consumed
    // =========================================================================

    /**
     * Refuses the fruit up front instead of letting {@link #onFoodEaten} silently waste it.
     *
     * <p>Both sides evaluate the same three conditions — the client can, because
     * {@link BaubleFruitProgress} mirrors the server's per-type mask and cap. Returning FAIL on
     * both sides means the eating animation never starts, so there is nothing to desync.
     */
    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn,
            @Nonnull EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        ItemStack held = playerIn.getHeldItem(handIn);

        String refusal = getRefusalKey(playerIn);
        if (refusal != null) {
            if (!worldIn.isRemote) {
                playerIn.sendStatusMessage(new TextComponentTranslation(refusal), true);
            }
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, held);
        }

        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    /** Lang key describing why this fruit cannot be eaten right now, or null when it can. */
    @Nullable
    public String getRefusalKey(EntityPlayer player) {
        if (BaubleFruitProgress.isConsumed(player, this)) {
            return "msg.insanetweaks.fruit.already_eaten";
        }
        if (BaubleFruitProgress.isCapReached(player)) {
            return "msg.insanetweaks.fruit.cap_reached";
        }
        if (ModItems.isBaublesExPresent() && getBaublesExSlotType() == null) {
            return "msg.insanetweaks.fruit.slot_disabled";
        }
        return null;
    }

    // =========================================================================
    // Core Logic — called server-side after the eating animation completes
    // =========================================================================

    @Override
    public void onFoodEaten(@Nonnull ItemStack stack, @Nonnull World worldIn,
            @Nonnull EntityPlayer player) {
        if (worldIn.isRemote) return;
        if (!(player instanceof EntityPlayerMP)) return;

        EntityPlayerMP playerMP = (EntityPlayerMP) player;

        // Second line of defence: onItemRightClick already refuses these, but the Corrupted
        // Fruit calls this method directly, so the guards have to hold here too.
        String refusal = getRefusalKey(playerMP);
        if (refusal != null) {
            playerMP.sendStatusMessage(new TextComponentTranslation(refusal), true);
            return;
        }

        NBTTagCompound persistent = playerMP.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);

        if (ModItems.isBaublesExPresent()) {
            // ------------------------------------------------------------------
            // BaublesEX path (v > 1.5): real slot expansion
            // ------------------------------------------------------------------
            BaubleTypeEx slotType = getBaublesExType();

            AbstractAttributeMap map = playerMP.getAttributeMap();
            AdvancedInstance instance = AttributeManager.getInstance(map, slotType);
            double current = instance.getAnonymousModifier(0);

            // Use BaublesEX's public helper instead of the low-level AdvancedInstance call.
            // CommonHelper.applyAnonymousModifier does the same +1 modifier AND sends a PacketModifier
            // to the client, so the client's expanded bauble container is rebuilt to the new slot count.
            // Calling instance.applyAnonymousModifier(...) directly updated the server only, leaving the
            // client container one slot short -> IndexOutOfBounds in ContainerPlayerExpanded and an
            // inventory desync (Slot N not in valid range) when the new slot is synced.
            baubles.util.CommonHelper.applyAnonymousModifier(playerMP, slotType, 1);

            persistent.setBoolean(consumedTag, true);
            playerMP.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);

            if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo) {
                playerMP.sendMessage(new TextComponentTranslation(
                        "msg.insanetweaks.fruit.debug_baublesex",
                        new TextComponentTranslation(getSlotDescriptionKey()),
                        Integer.valueOf((int) (current + 1))));
            }
        } else {
            // ------------------------------------------------------------------
            // Legacy Baubles path (v <= 1.5): +1 generic.luck fallback
            // Different NBT tag so players can still get the real slot after
            // switching to BaublesEX.
            // ------------------------------------------------------------------
            persistent.setBoolean(consumedTagLegacy, true);
            BaubleFruitEventHandler.grantLegacyBonus(playerMP, persistent);

            if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo) {
                playerMP.sendMessage(new TextComponentTranslation(
                        "msg.insanetweaks.fruit.debug_legacy",
                        new TextComponentTranslation(getSlotDescriptionKey())));
            }
        }

        // Sound: shared by both paths
        worldIn.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 0.8f);

        // Refresh the client's mirror so the tooltip counter and the next refusal check agree.
        BaubleFruitProgress.sync(playerMP);
    }

    // =========================================================================
    // Indestructible dropped item — prevents the fruit being voided in lava
    // =========================================================================

    @Override
    public List<String> getActiveAdvProperties(ItemStack stack) {
        return Arrays.asList(AdvPropertyRegistry.ASHEN_LEGACY);
    }
}
