package com.spege.insanetweaks.events;

import com.spege.insanetweaks.items.armor.BattleMageArmorItem;
import com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem;

import electroblob.wizardry.item.ItemWizardArmour;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class ArmorEventHandler {

    private static final String NBT_ADAPTATION = "ArmorDamageBlocked";
    private static final String TAG_COOLDOWN = "ArmorCapCooldown";
    /**
     * Stores the world-time when immunity was granted. Immunity lasts 10 ticks
     * (0.5s) from that moment.
     * Tracked INDEPENDENTLY from PotionCleanse, which lasts 40 ticks (2s) and
     * handles debuff removal.
     */
    private static final String TAG_IMMUNITY = "ArmorCapImmunity";

    /**
     * Hardcap cooldown in ticks.
     * 1800t = 90 seconds.
     */
    private static final long HARDCAP_COOLDOWN_TICKS = 1800L;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote)
            return;

        if (!com.spege.insanetweaks.config.ModConfig.enableSrpEbWizardryBridge)
            return;

        // Nomenclature: ParasiteWizardArmorItem = "Living Armor" (pre-evolution)
        //               BattleMageArmorItem      = "Sentient Armor" (post-evolution)
        int livingArmorCount = 0;
        int sentientArmorCount = 0;

        for (ItemStack piece : player.inventory.armorInventory) {
            if (!piece.isEmpty()) {
                if (piece.getItem() instanceof ParasiteWizardArmorItem)
                    livingArmorCount++;
                else if (piece.getItem() instanceof BattleMageArmorItem)
                    sentientArmorCount++;
            }
        }

        int totalPieces = livingArmorCount + sentientArmorCount;

        // 1. Sentient Armor set reduction (1% per piece) - Applied in Hurt phase (pre-armor).
        // Note: capture rawDamage BEFORE this reduction so evolution tracking (below)
        // always measures the true incoming hit, not the already-discounted value.
        final float rawDamage = event.getAmount();
        if (sentientArmorCount > 0) {
            float reduction = 1.0f - (0.01f * sentientArmorCount);
            event.setAmount(rawDamage * reduction);
        }

        // 2. ARMOR DAMAGE TRACKING (Evolution for Living, Visual for Sentient)
        // Bug fix: capture damage BEFORE Battlemage reduction so evolution speed
        // is consistent regardless of how many Battlemage pieces the player wears.
        if (totalPieces > 0) {
            float damageTaken = rawDamage;
            for (int i = 0; i < 4; i++) {
                ItemStack piece = player.inventory.armorInventory.get(i);
                if (piece.isEmpty())
                    continue;

                boolean isLivingArmor  = piece.getItem() instanceof ParasiteWizardArmorItem; // pre-evolution
                boolean isSentientArmor = piece.getItem() instanceof BattleMageArmorItem;     // post-evolution

                if (isLivingArmor || isSentientArmor) {
                    if (!piece.hasTagCompound())
                        piece.setTagCompound(new NBTTagCompound());
                    NBTTagCompound nbt = piece.getTagCompound();
                    if (nbt == null)
                        continue;
                    float blocked = nbt.getFloat(NBT_ADAPTATION);

                    if (blocked >= 10000.0f)
                        continue;

                    blocked += damageTaken;

                    if (isLivingArmor && blocked >= 1500.0f) {
                        evolveArmorPiece(player, i, piece);
                    } else {
                        nbt.setFloat(NBT_ADAPTATION, blocked);
                    }
                }
            }
        }
    }

    // =========================================================================
    // OLD HARDCAP (LivingDamageEvent HP-simulation) — ARCHIVED, DO NOT DELETE
    // Reason: Unreliable. HP math with absorption/potions had too many edge cases.
    // Replaced by onLivingDeath (Totem-style) below.
    // =========================================================================
    // @SubscribeEvent(priority = EventPriority.HIGHEST)
    // public void onLivingDamage(LivingDamageEvent event) {
    // if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
    // EntityPlayer player = (EntityPlayer) event.getEntityLiving();
    // if (player.world.isRemote) return;
    // if (!ModConfig.enableSrpEbWizardryBridge) return;
    // int livingCount = 0; int battleCount = 0;
    // for (ItemStack piece : player.inventory.armorInventory) {
    // if (!piece.isEmpty()) {
    // if (piece.getItem() instanceof ParasiteWizardArmorItem) livingCount++;
    // else if (piece.getItem() instanceof BattleMageArmorItem) battleCount++;
    // }
    // }
    // if (livingCount + battleCount == 4) {
    // float amount = event.getAmount();
    // float currentEffectiveHP = player.getHealth() + player.getAbsorptionAmount();
    // float expectedHP = currentEffectiveHP - amount;
    // float expectedHpPercent = expectedHP / player.getMaxHealth();
    // boolean isLethal = expectedHP <= 0.0f;
    // boolean lowHPCondition = expectedHpPercent <= 0.40f && amount >= 1.5f;
    // boolean trigger = isLethal || lowHPCondition;
    // if (trigger) {
    // NBTTagCompound playerData = player.getEntityData();
    // long currentTime = player.world.getTotalWorldTime();
    // boolean isReady = !playerData.hasKey(TAG_COOLDOWN)
    // || (currentTime - playerData.getLong(TAG_COOLDOWN) >= 80);
    // if (isReady) {
    // float reducedAmount = Math.min(amount * 0.1f, 2.0f);
    // event.setAmount(reducedAmount);
    // playerData.setLong(TAG_COOLDOWN, currentTime);
    // }
    // }
    // }
    // }

    // =========================================================================
    // NEW HARDCAP — Totem of Undying style (LivingDeathEvent)
    // Mirrors EntityLivingBase.checkTotemDeathProtection() from vanilla source.
    // Triggers ONLY when the player would actually die — no HP simulation needed.
    // Cooldown is still tracked via NBT (TAG_COOLDOWN) the same way as before.
    // =========================================================================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("null") // ModPotions.CLEANSE and SoundEvents.* are guaranteed non-null at runtime
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote)
            return;
        if (!com.spege.insanetweaks.config.ModConfig.enableSrpEbWizardryBridge)
            return;

        // Require: all 4 armor slots must be exclusively Living or Sentient pieces.
        // Nomenclature: ParasiteWizardArmorItem = "Living Armor" (pre-evolution)
        //               BattleMageArmorItem      = "Sentient Armor" (post-evolution)
        // Hardcap requires all 4 slots to be Living OR Sentient armor (any mix).
        int livingArmorCount = 0;
        int sentientArmorCount = 0;
        for (ItemStack piece : player.inventory.armorInventory) {
            if (!piece.isEmpty()) {
                if (piece.getItem() instanceof ParasiteWizardArmorItem)
                    livingArmorCount++;
                else if (piece.getItem() instanceof BattleMageArmorItem)
                    sentientArmorCount++;
            }
        }
        if (livingArmorCount + sentientArmorCount != 4)
            return;

        // Cooldown check (NBT on player entity data, same tag as before).
        // New-world guard: if the tag has never been written, isReady = true.
        NBTTagCompound playerData = player.getEntityData();
        long currentTime = player.world.getTotalWorldTime();
        boolean isReady = !playerData.hasKey(TAG_COOLDOWN)
                || (currentTime - playerData.getLong(TAG_COOLDOWN) >= HARDCAP_COOLDOWN_TICKS);

        if (!isReady)
            return;

        // --- CANCEL DEATH (mirrors checkTotemDeathProtection) ---
        event.setCanceled(true);

        // Step 1: Restore health to 1.0f + heal by 2 HP = 3.0f total (1.5 hearts).
        player.setHealth(1.0f);
        player.heal(1.0f);

        // Step 2: Save immunity start time (10 ticks = 0.5s window, see
        // onLivingAttack).
        playerData.setLong(TAG_IMMUNITY, currentTime);

        // Step 3: Apply Cleanse for 40 ticks (2s).
        // PotionCleanse.performEffect() fires every 10t and removes all non-beneficial
        // effects.
        // Damage immunity is NOT provided by Cleanse — it is separate (TAG_IMMUNITY
        // above).
        player.addPotionEffect(new PotionEffect(
                com.spege.insanetweaks.init.ModPotions.CLEANSE, 40, 0, false, false));

        // Step 4: Save cooldown timestamp.
        playerData.setLong(TAG_COOLDOWN, currentTime);

        // Step 5: Play the totem sound (only audio, no particle animation).
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo)
            player.sendMessage(new TextComponentString(
                    "\u00a7b[DEBUG-HARDCAP] Death cancelled! HP=3.0, Immunity(10t), Cleanse(40t) active."));
    }

    /**
     * Damage immunity window — 10 ticks (0.5s) after hardcap triggers.
     * Tracked via TAG_IMMUNITY NBT, INDEPENDENT of PotionCleanse duration.
     * Covers ALL DamageSource types (fire, fall, magic, void, etc.).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        if (event.getEntityLiving().world.isRemote)
            return;
        if (!com.spege.insanetweaks.config.ModConfig.enableSrpEbWizardryBridge)
            return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        NBTTagCompound playerData = player.getEntityData();

        if (!playerData.hasKey(TAG_IMMUNITY))
            return;

        long currentTime = player.world.getTotalWorldTime();
        long immunityStart = playerData.getLong(TAG_IMMUNITY);

        // Immunity window: 10 ticks (0.5s) from the moment hardcap triggered.
        if (currentTime - immunityStart < 10L) {
            event.setCanceled(true);

            if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo)
                player.sendMessage(new TextComponentString(
                        "\u00a78[DEBUG-IMMUNITY] Damage blocked! Immunity: "
                                + (10L - (currentTime - immunityStart)) + "t remaining."));
        }
    }

    private void evolveArmorPiece(EntityPlayer player, int slotIdx, ItemStack oldPiece) {
        EntityEquipmentSlot slot = ((ItemWizardArmour) oldPiece.getItem()).armorType;
        Item newModule;

        if (slot == EntityEquipmentSlot.HEAD)
            newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "battle_mage_helmet"));
        else if (slot == EntityEquipmentSlot.CHEST)
            newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "battle_mage_chestplate"));
        else if (slot == EntityEquipmentSlot.LEGS)
            newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "battle_mage_leggings"));
        else
            newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "battle_mage_boots"));

        if (newModule != null) {
            ItemStack newStack = new ItemStack(newModule);
            if (oldPiece.hasTagCompound()) {
                NBTTagCompound tag = oldPiece.getTagCompound();
                if (tag != null) {
                    NBTTagCompound copiedTag = tag.copy();
                    // Bug fix: reset the evolution counter on the new BattleMage piece.
                    // Without this, the copied NBT value (>= 1500) would cause the
                    // BattleMage armor to immediately re-trigger evolveArmorPiece
                    // on the very next LivingHurtEvent.
                    copiedTag.setFloat(NBT_ADAPTATION, 0.0f);
                    newStack.setTagCompound(copiedTag);
                }
            }
            newStack.setItemDamage(oldPiece.getItemDamage());

            player.inventory.armorInventory.set(slotIdx, newStack);

            SoundEvent sound = SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE;
            if (sound != null) {
                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }

            if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo)
                player.sendMessage(new TextComponentString(
                        "\u00a7b[DEBUG] Living Armor absorbed 1500.0 DMG and transformed into Sentient Armor!"));
        }
    }
}
