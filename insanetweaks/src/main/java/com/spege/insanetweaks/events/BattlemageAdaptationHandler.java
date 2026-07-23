package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem;
import com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

@SuppressWarnings("null")
public class BattlemageAdaptationHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;
        if (!ModConfig.modules.enableSrpEbWizardryBridge) return;

        DamageSource source = event.getSource();
        if (source.canHarmInCreative() || "outOfWorld".equals(source.damageType) || "drown".equals(source.damageType) || "starve".equals(source.damageType)) {
            return;
        }

        int battlemagePieces = 0;
        for (ItemStack piece : player.inventory.armorInventory) {
            if (!piece.isEmpty() && (piece.getItem() instanceof LivingBattlemageArmorItem || piece.getItem() instanceof SentientBattlemageArmorItem)) {
                battlemagePieces++;
            }
        }

        if (battlemagePieces == 0) return;

        String sourceName = getDamageSourceName(source);

        // Calculate total reduction across all pieces BEFORE modifying them
        float totalReduction = 0f;
        for (ItemStack piece : player.inventory.armorInventory) {
            if (!piece.isEmpty()) {
                if (piece.getItem() instanceof LivingBattlemageArmorItem) {
                    totalReduction += LivingBattlemageArmorItem.getReductionForType(piece, sourceName);
                } else if (piece.getItem() instanceof SentientBattlemageArmorItem) {
                    totalReduction += SentientBattlemageArmorItem.getReductionForType(piece, sourceName);
                }
            }
        }

        // Capture raw damage BEFORE any multiplier for evolution tracking
        float rawDamage = event.getAmount();

        // Make fire deal x4 damage if wearing Battlemage gear (like SRP)
        float damage = rawDamage;
        boolean isFireDmg = source.isFireDamage();
        if (isFireDmg) {
            damage *= 4.0f; // As agreed with user
        } else if (totalReduction > 0f) {
            damage *= Math.max(0f, 1.0f - totalReduction);
        }
        event.setAmount(damage);

        // Learn and evolve logic
        for (int i = 0; i < 4; i++) {
            ItemStack piece = player.inventory.armorInventory.get(i);
            if (piece.isEmpty()) continue;

            if (piece.getItem() instanceof LivingBattlemageArmorItem) {
                // Fire should NEVER be learned as a resistance type (SRP-consistent)
                if (!isFireDmg) {
                    processAdaptation(piece, sourceName, LivingBattlemageArmorItem.MAX_DAMAGE_TYPES, LivingBattlemageArmorItem.CHANCE_TO_LEARN, LivingBattlemageArmorItem.MAX_POINTS_PER_TYPE);
                }
                // Use rawDamage for evolution so fire x4 doesn't accelerate evolution
                trackHitsAbsorbed(player, i, piece, rawDamage, true);
            } else if (piece.getItem() instanceof SentientBattlemageArmorItem) {
                if (!isFireDmg) {
                    int effMaxTypes = SentientBattlemageArmorItem.MAX_DAMAGE_TYPES;
                    float absorbed = piece.hasTagCompound() ? piece.getTagCompound().getFloat("itHitsAbsorbed") : 0f;
                    if (absorbed >= 10000.0f) {
                        effMaxTypes += 1;
                    }
                    processAdaptation(piece, sourceName, effMaxTypes, SentientBattlemageArmorItem.CHANCE_TO_LEARN, SentientBattlemageArmorItem.MAX_POINTS_PER_TYPE);
                }
                trackHitsAbsorbed(player, i, piece, rawDamage, false);
            }
        }
    }

    private void processAdaptation(ItemStack stack, String damageType, int maxTypes, float chanceToLearn, int maxPointsPerType) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) return;

        if (Math.random() > chanceToLearn) return; // Roll chance to learn

        NBTTagList names = nbt.hasKey("itResistNames", 9) ? nbt.getTagList("itResistNames", 8) : new NBTTagList();
        int[] points = nbt.hasKey("itResistPoints") ? nbt.getIntArray("itResistPoints") : new int[0];

        int indexFound = -1;
        for (int i = 0; i < names.tagCount(); i++) {
            if (names.getStringTagAt(i).equals(damageType)) {
                indexFound = i;
                break;
            }
        }

        if (indexFound != -1) {
            if (points.length > indexFound && points[indexFound] < maxPointsPerType) {
                points[indexFound]++;
                nbt.setIntArray("itResistPoints", points); // update
            }
        } else {
            // Add new type if space allows or replace lowest
            if (names.tagCount() < maxTypes) {
                names.appendTag(new NBTTagString(damageType));
                int[] newPoints = new int[names.tagCount()];
                System.arraycopy(points, 0, newPoints, 0, points.length);
                newPoints[newPoints.length - 1] = 1;

                nbt.setTag("itResistNames", names);
                nbt.setIntArray("itResistPoints", newPoints);
            } else {
                // Find minimum adapted type to replace
                int minIdx = 0;
                int minPts = Integer.MAX_VALUE;
                for (int i = 0; i < points.length; i++) {
                    if (points[i] < minPts) {
                        minPts = points[i];
                        minIdx = i;
                    }
                }
                if (minIdx < names.tagCount()) {
                    names.set(minIdx, new NBTTagString(damageType));
                    if (minIdx < points.length) {
                        points[minIdx] = 1;
                    }
                    nbt.setTag("itResistNames", names);
                    nbt.setIntArray("itResistPoints", points);
                }
            }
        }
    }

    private void trackHitsAbsorbed(EntityPlayer player, int slotIdx, ItemStack piece, float damage, boolean canEvolve) {
        NBTTagCompound nbt = piece.getTagCompound();
        if (nbt != null) {
            float absorbed = nbt.getFloat("itHitsAbsorbed");
            absorbed += damage;

            if (canEvolve && absorbed >= LivingBattlemageArmorItem.EVOLUTION_THRESHOLD) {
                evolveBattlemagePiece(player, slotIdx, piece);
            } else {
                nbt.setFloat("itHitsAbsorbed", absorbed);
            }
        }
    }

    private void evolveBattlemagePiece(EntityPlayer player, int slotIdx, ItemStack oldPiece) {
        EntityEquipmentSlot slot = ((LivingBattlemageArmorItem) oldPiece.getItem()).armorType;
        net.minecraft.item.Item newModule = null;

        if (slot == EntityEquipmentSlot.HEAD)
            newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "sentient_battlemage_helmet"));
        else if (slot == EntityEquipmentSlot.CHEST)
            newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "sentient_battlemage_chestplate"));
        else if (slot == EntityEquipmentSlot.LEGS)
            newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "sentient_battlemage_leggings"));
        else
            newModule = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "sentient_battlemage_boots"));

        if (newModule != null) {
            ItemStack newStack = new ItemStack(newModule);
            if (oldPiece.hasTagCompound()) {
                NBTTagCompound copiedTag = oldPiece.getTagCompound().copy();
                copiedTag.setFloat("itHitsAbsorbed", 0.0f); // Reset counter
                newStack.setTagCompound(copiedTag);
            }
            newStack.setItemDamage(oldPiece.getItemDamage());

            player.inventory.armorInventory.set(slotIdx, newStack);

            net.minecraft.util.SoundEvent sound = net.minecraft.init.SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE;
            if (sound != null) {
                player.world.playSound(null, player.posX, player.posY, player.posZ, sound, net.minecraft.util.SoundCategory.PLAYERS, 1.0f, 1.0f);
            }

            if (ModConfig.client.displayDebugInfo)
                player.sendMessage(new net.minecraft.util.text.TextComponentString("\u00a7b[DEBUG] Battlemage Armor absorbed enough damage and transformed into Sentient Battlemage Armor!"));
        }
    }

    private String getDamageSourceName(DamageSource source) {
        String baseType = source.damageType;
        EntityLivingBase attacker = (EntityLivingBase) (source.getTrueSource() instanceof EntityLivingBase ? source.getTrueSource() : null);

        if (attacker != null) {
            EntityRegistry.EntityRegistration reg = EntityRegistry.instance().lookupModSpawn(attacker.getClass(), true);
            if (reg != null && reg.getRegistryName() != null) {
                return reg.getRegistryName().toString(); // e.g. "srparasites:sim_human"
            }
            // fallback to vanilla
            String entityName = net.minecraft.entity.EntityList.getEntityString(attacker);
            if (entityName != null) {
                return entityName; // e.g. "Zombie"
            }
        }
        return baseType; // e.g. "fall", "magic", "mob"
    }
}
