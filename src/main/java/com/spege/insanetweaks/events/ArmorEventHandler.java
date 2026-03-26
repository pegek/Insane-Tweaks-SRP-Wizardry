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
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class ArmorEventHandler {

    private static final String NBT_ADAPTATION = "ArmorDamageBlocked";
    private static final String TAG_COOLDOWN = "ArmorCapCooldown";

    @SubscribeEvent(priority = EventPriority.HIGH) // EventPriority.HIGHEST
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        int livingCount = 0;
        int battleCount = 0;
        ItemStack[] armor = new ItemStack[4];
        int idx = 0;

        for (ItemStack piece : player.inventory.armorInventory) {
            if (!piece.isEmpty()) {
                if (piece.getItem() instanceof ParasiteWizardArmorItem) {
                    livingCount++;
                    armor[idx] = piece;
                } else if (piece.getItem() instanceof BattleMageArmorItem) {
                    battleCount++;
                    armor[idx] = piece;
                }
            }
            idx++;
        }

        int totalPieces = livingCount + battleCount;
        float amount = event.getAmount();

        // 1. 4-Piece Set Bonus: HP-based Damage Reduction & Cure
        if (totalPieces == 4) {
            float hpPercent = player.getHealth() / player.getMaxHealth();

            if (hpPercent <= 0.25f) {
                NBTTagCompound playerData = player.getEntityData();
                long currentTime = player.world.getTotalWorldTime();
                long lastProc = playerData.getLong(TAG_COOLDOWN);

                if (amount >= 10.0f && (currentTime - lastProc >= 80)) { // 400 ticks = 20s (Current: 80 = 4s for testing)
                    event.setAmount(amount * 0.4f); // 60% reduction
                    amount = event.getAmount(); 
                    playerData.setLong(TAG_COOLDOWN, currentTime);

                    if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) 
                        player.sendMessage(new TextComponentString("\u00a7c[DEBUG] Hardcap triggered! 60% reduction applied! 20s Cooldown."));

                    Potion curePotion = ForgeRegistries.POTIONS.getValue(new ResourceLocation("potioncore", "cure"));
                    if (curePotion != null) {
                        player.addPotionEffect(new PotionEffect(curePotion, 40, 0, false, false));
                        if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) 
                            player.sendMessage(new TextComponentString("\u00a7a[DEBUG] 2s Cure applied! (Test Cooldown: 4s)"));
                    }
                }
            }
        }

        // 2. Battlemage set reduction (1.5% per piece)
        if (battleCount > 0) {
            float reduction = 1.0f - (0.01f * battleCount);
            event.setAmount(event.getAmount() * reduction);
        }

        // ARMOR EVOLUTION (Now tracks total damage absorbed)
        if (livingCount > 0) {
            float damageTaken = event.getAmount();
            for (int i = 0; i < 4; i++) {
                ItemStack piece = player.inventory.armorInventory.get(i);
                if (!piece.isEmpty() && piece.getItem() instanceof ParasiteWizardArmorItem) {
                    if (!piece.hasTagCompound()) piece.setTagCompound(new NBTTagCompound());
                    NBTTagCompound nbt = piece.getTagCompound();
                    if (nbt == null) continue;
                    float blocked = nbt.getFloat(NBT_ADAPTATION);

                    if (blocked >= 10000.0f) return;

                    blocked += damageTaken;
                    
                    if (blocked >= 1500.0f) {
                        evolveArmorPiece(player, i, piece);
                    } else {
                        nbt.setFloat(NBT_ADAPTATION, blocked);
                    }
                }
            }
        }
    }

    private void evolveArmorPiece(EntityPlayer player, int slotIdx, ItemStack oldPiece) {
        EntityEquipmentSlot slot = ((ItemWizardArmour) oldPiece.getItem()).armorType;
        Item newModule;

        if (slot == EntityEquipmentSlot.HEAD) newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "battle_mage_helmet"));
        else if (slot == EntityEquipmentSlot.CHEST) newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "battle_mage_chestplate"));
        else if (slot == EntityEquipmentSlot.LEGS) newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "battle_mage_leggings"));
        else newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "battle_mage_boots"));

        if (newModule != null) {
            ItemStack newStack = new ItemStack(newModule);
            if (oldPiece.hasTagCompound()) {
                NBTTagCompound tag = oldPiece.getTagCompound();
                if (tag != null) {
                    newStack.setTagCompound(tag.copy());
                    NBTTagCompound newTag = newStack.getTagCompound();
                    if (newTag != null) newTag.removeTag(NBT_ADAPTATION);
                }
            }
            newStack.setItemDamage(oldPiece.getItemDamage());
            
            player.inventory.armorInventory.set(slotIdx, newStack);
            
            SoundEvent sound = SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE;
            if (sound != null) {
                player.world.playSound(null, player.posX, player.posY, player.posZ, 
                    sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
            
            if (com.spege.insanetweaks.config.ModConfig.displayDebugInfo) player.sendMessage(new TextComponentString("\u00a7b[DEBUG] Living Armor has absorbed 1500.0 DMG and transforms into BattleMage Armor!"));
        }
    }
}
