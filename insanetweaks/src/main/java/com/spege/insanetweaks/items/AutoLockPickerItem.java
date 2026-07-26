package com.spege.insanetweaks.items;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.AutoLockPickerCategory;
import com.spege.insanetweaks.enchant.EnchantmentSwiftPicking;
import com.spege.insanetweaks.util.LocksCompat;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

/**
 * Auto Lock Picker — opens a Locks lock by holding right-click on it instead of playing Locks'
 * pin minigame. Channel time and durability cost both scale with the lock's pin count, so a
 * diamond lock (11 pins) is the slowest and priciest.
 *
 * <p>On success the lock is merely <b>opened</b> ({@code Lock.setLocked(false)}, exactly what a key
 * does) — it stays attached to the block and can be closed again. Nothing is destroyed or dropped.
 *
 * <p>Built entirely on vanilla's active-hand machinery ({@link EntityLivingBase#setActiveHand}), so
 * there is no mixin, no tick handler and no packet of our own:
 * <ol>
 *   <li>{@link #onItemUse} finds the lock, applies the Complexity gate, stores the computed channel
 *       length in the stack's NBT and starts the hand. Runs on BOTH sides, and both compute the
 *       same number from the same synced {@code Lockable} — so the HUD bar cannot desync.</li>
 *   <li>{@link #getMaxItemUseDuration} feeds that number back to vanilla.</li>
 *   <li>{@link #onUsingTick} re-validates and gives feedback.</li>
 *   <li>{@link #onItemUseFinish} unlocks and charges durability, server side.</li>
 *   <li>{@link #onPlayerStoppedUsing} handles a player-initiated abort (Shocking, no durability).</li>
 * </ol>
 *
 * <p>All Locks API access goes through {@link LocksCompat} — Locks is optional, and this class must
 * stay loadable without it.
 *
 * <p>Gated by {@code ModConfig.modules.enableAutoLockPicker}, tuned by
 * {@code ModConfig.autoLockPicker.*}. The item is registered unconditionally so that turning the
 * module off never drops a registry entry from an existing world.
 */
public class AutoLockPickerItem extends Item {

    /** int: channel length in ticks, computed in {@link #onItemUse} for the current target. */
    public static final String TAG_CHANNEL_TICKS = "itweaks_channel_ticks";
    /** int: network ID of the {@code Lockable} being picked. */
    public static final String TAG_TARGET_ID = "itweaks_lock_id";

    /** Vanilla's "hold forever" duration, used when no channel is in progress. */
    private static final int IDLE_USE_DURATION = 72000;

    /**
     * Only ever used to make {@link #isDamageable()} true. The real figure comes from
     * {@link #getMaxDamage(ItemStack)} — see the constructor.
     */
    private static final int FALLBACK_MAX_DAMAGE = 250;

    public AutoLockPickerItem() {
        setRegistryName(InsaneTweaksMod.MODID, "auto_lock_picker");
        setUnlocalizedName("auto_lock_picker");
        setCreativeTab(CreativeTabs.TOOLS);
        setMaxStackSize(1);
        // Deliberately a literal, not the config value: this constructor runs when ModItems
        // class-loads, and that can precede Forge populating the @Config fields. isDamageable()
        // only cares that this is > 0; getMaxDamage(ItemStack) below supplies the live number.
        setMaxDamage(FALLBACK_MAX_DAMAGE);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return Math.max(1, ModConfig.autoLockPicker.maxDurability);
    }

    // ------------------------------------------------------------------
    // Maths (static so the HUD handler can reuse them)
    // ------------------------------------------------------------------

    /** Channel length in ticks for a lock with {@code pins} pins, after Swift Picking. */
    public static int computeChannelTicks(ItemStack stack, int pins) {
        AutoLockPickerCategory cfg = ModConfig.autoLockPicker;
        int base = cfg.baseChannelTicks + cfg.ticksPerPin * Math.max(0, pins);
        double multiplier = 1.0D - cfg.swiftReductionPerLevel * EnchantmentSwiftPicking.getLevel(stack);
        if (multiplier < 0.05D) {
            multiplier = 0.05D; // never let a stacked reduction reach zero/negative ticks
        }
        return Math.max(1, (int) Math.round(base * multiplier));
    }

    /** Durability charged on success: per-pin cost, scaled by the lock's Sturdy level. */
    public static int computeDurabilityCost(World world, int lockableId, int pins) {
        AutoLockPickerCategory cfg = ModConfig.autoLockPicker;
        double cost = Math.max(1, pins) * (double) cfg.durabilityPerPin;
        if (cfg.applySturdyDurability) {
            int sturdy = LocksCompat.getSturdyLevel(world, lockableId);
            cost *= 1.0D + cfg.sturdyDurabilityPerLevel * sturdy;
        }
        return Math.max(0, (int) Math.ceil(cost));
    }

    /** True when the picker is strong enough for this lock's Complexity level. */
    private static boolean beatsComplexity(World world, int lockableId) {
        AutoLockPickerCategory cfg = ModConfig.autoLockPicker;
        if (!cfg.respectComplexity) {
            return true;
        }
        // Locks' own rule, from LockPickItem.canPick: strength > complexity * 0.25
        return cfg.pickStrength > LocksCompat.getComplexityLevel(world, lockableId) * 0.25D;
    }

    private static boolean isEnabled() {
        return ModConfig.modules.enableAutoLockPicker && LocksCompat.isLoaded();
    }

    // ------------------------------------------------------------------
    // Channel lifecycle
    // ------------------------------------------------------------------

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!isEnabled()) {
            return EnumActionResult.PASS;
        }

        int lockableId = LocksCompat.findLockedLockableId(world, pos);
        if (lockableId == LocksCompat.NO_LOCK) {
            return EnumActionResult.PASS;
        }

        if (!beatsComplexity(world, lockableId)) {
            if (world.isRemote) {
                // Same status line Locks shows for an under-strength pick.
                player.sendStatusMessage(new TextComponentTranslation("locks.status.too_complex"), true);
            }
            return EnumActionResult.PASS;
        }

        ItemStack stack = player.getHeldItem(hand);
        int ticks = computeChannelTicks(stack, LocksCompat.getPinCount(world, lockableId));

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(TAG_CHANNEL_TICKS, ticks);
        tag.setInteger(TAG_TARGET_ID, lockableId);

        player.setActiveHand(hand);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(TAG_CHANNEL_TICKS)) {
            return Math.max(1, tag.getInteger(TAG_CHANNEL_TICKS));
        }
        return IDLE_USE_DURATION;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        // Deliberately NOT EnumAction.BLOCK: that flips isActiveItemStackBlocking(), which would
        // hand the player free shield-style damage blocking for the whole channel. NONE keeps the
        // vanilla use-slowdown without the side effect; feedback comes from the HUD bar and sound.
        return EnumAction.NONE;
    }

    @Override
    public void onUsingTick(ItemStack stack, EntityLivingBase living, int count) {
        if (!isEnabled() || !(living instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) living;
        World world = player.world;
        int lockableId = readTargetId(stack);
        if (lockableId == LocksCompat.NO_LOCK) {
            return;
        }

        // Someone else opened or removed the lock, or the player walked off: abort silently.
        // resetActiveHand() does NOT route through onPlayerStoppedUsing, so this cannot shock.
        if (!LocksCompat.isStillLocked(world, lockableId)
                || !LocksCompat.isWithinRange(world, lockableId, player,
                        ModConfig.autoLockPicker.maxChannelDistance)) {
            living.resetActiveHand();
            return;
        }

        if (world.isRemote) {
            spawnChannelParticles(world, lockableId);
            return;
        }
        // Server side only: World#playSound with a null player broadcasts to every tracking
        // client, so calling it on both sides would play the rattle twice for the picker.
        int elapsed = getMaxItemUseDuration(stack) - count;
        if (elapsed > 0 && elapsed % 20 == 0) {
            LocksCompat.playRattle(world, lockableId);
        }
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase living) {
        int lockableId = readTargetId(stack);
        clearChannelTags(stack);

        if (world.isRemote || !isEnabled() || !(living instanceof EntityPlayer)
                || lockableId == LocksCompat.NO_LOCK) {
            return stack;
        }
        EntityPlayer player = (EntityPlayer) living;

        // Re-validate: the channel ran for seconds, anything could have changed.
        if (!LocksCompat.isStillLocked(world, lockableId)
                || !LocksCompat.isWithinRange(world, lockableId, player,
                        ModConfig.autoLockPicker.maxChannelDistance)
                || !beatsComplexity(world, lockableId)) {
            return stack;
        }

        int pins = LocksCompat.getPinCount(world, lockableId);
        if (!LocksCompat.unlock(world, lockableId)) {
            return stack;
        }

        LocksCompat.playLockOpen(world, lockableId);
        // Vanilla attemptDamageItem applies Unbreaking here, so it needs no handling of our own.
        stack.damageItem(computeDurabilityCost(world, lockableId, pins), player);
        return stack;
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityLivingBase living, int timeLeft) {
        int lockableId = readTargetId(stack);
        clearChannelTags(stack);

        // Only a player-initiated release reaches this method (resetActiveHand bypasses it), which
        // is exactly the case Shocking should punish. Aborting costs no durability.
        if (world.isRemote || !isEnabled() || !(living instanceof EntityPlayer)
                || lockableId == LocksCompat.NO_LOCK
                || !ModConfig.autoLockPicker.applyShockingOnInterrupt) {
            return;
        }

        int shocking = LocksCompat.getShockingLevel(world, lockableId);
        if (shocking > 0) {
            float damage = (float) (shocking * ModConfig.autoLockPicker.shockDamagePerLevel);
            LocksCompat.shock(world, (EntityPlayer) living, lockableId, damage);
        }
    }

    // ------------------------------------------------------------------
    // Item properties
    // ------------------------------------------------------------------

    @Override
    public int getItemEnchantability() {
        return ModConfig.autoLockPicker.enchantability;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == Items.IRON_INGOT || super.getIsRepairable(toRepair, repair);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        if (!LocksCompat.isLoaded()) {
            tooltip.add(TextFormatting.RED + I18n.translateToLocal("tooltip.insanetweaks.auto_lock_picker.no_locks"));
            return;
        }
        if (!ModConfig.modules.enableAutoLockPicker) {
            tooltip.add(TextFormatting.RED + I18n.translateToLocal("tooltip.insanetweaks.auto_lock_picker.disabled"));
            return;
        }

        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("tooltip.insanetweaks.auto_lock_picker.usage"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocalFormatted(
                "tooltip.insanetweaks.auto_lock_picker.strength",
                String.format(Locale.ROOT, "%.2f", Double.valueOf(ModConfig.autoLockPicker.pickStrength))));

        // Concrete numbers for the two ends of the stock lock range (wood 5 pins, diamond 11).
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocalFormatted(
                "tooltip.insanetweaks.auto_lock_picker.timing",
                formatSeconds(computeChannelTicks(stack, 5)),
                formatSeconds(computeChannelTicks(stack, 11))));
    }

    private static String formatSeconds(int ticks) {
        // Locale.ROOT: a decimal comma from the client's locale would read oddly next to "s".
        return String.format(Locale.ROOT, "%.1f", Float.valueOf(ticks / 20.0F));
    }

    // ------------------------------------------------------------------
    // NBT helpers
    // ------------------------------------------------------------------

    /** Lockable network ID stored by {@link #onItemUse}, or {@link LocksCompat#NO_LOCK}. */
    public static int readTargetId(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_TARGET_ID)) {
            return LocksCompat.NO_LOCK;
        }
        return tag.getInteger(TAG_TARGET_ID);
    }

    private static void clearChannelTags(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.removeTag(TAG_CHANNEL_TICKS);
            tag.removeTag(TAG_TARGET_ID);
        }
    }

    private static void spawnChannelParticles(World world, int lockableId) {
        Vec3d centre = LocksCompat.getCenter(world, lockableId);
        if (centre == null) {
            return;
        }
        double spread = 0.25D;
        world.spawnParticle(EnumParticleTypes.CRIT,
                centre.x + (world.rand.nextDouble() - 0.5D) * spread,
                centre.y + (world.rand.nextDouble() - 0.5D) * spread,
                centre.z + (world.rand.nextDouble() - 0.5D) * spread,
                0.0D, 0.0D, 0.0D);
    }
}
