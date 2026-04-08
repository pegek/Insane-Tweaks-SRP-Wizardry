package com.spege.insanetweaks.events;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import electroblob.wizardry.event.SpellCastEvent;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.PlayerManaCompat;

@SuppressWarnings("null")
public class WandEventHandler {

    private static final int EVOLUTION_THRESHOLD = 5000;
    private static final int MANA_PER_POINT = 5;

    // ========================================================================
    // HYBRYDOWA EWOLUCJA: 1 Punkt = 1 Zabójstwo LUB 5 Zużytej Many
    // ========================================================================

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge) return;

        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) trueSource;
        if (player.world.isRemote) return;

        ItemStack wandStack = ItemStack.EMPTY;
        EnumHand wandHand = EnumHand.MAIN_HAND;

        if (player.getHeldItemMainhand().getItem() == ModItems.LIVING_WAND) {
            wandStack = player.getHeldItemMainhand();
            wandHand = EnumHand.MAIN_HAND;
        } else if (player.getHeldItemOffhand().getItem() == ModItems.LIVING_WAND) {
            wandStack = player.getHeldItemOffhand();
            wandHand = EnumHand.OFF_HAND;
        }

        if (!wandStack.isEmpty()) {
            addEvolutionPoints(wandStack, player, wandHand, 1);
        }
    }

    @SubscribeEvent
    public void onSpellCastPost(SpellCastEvent.Post event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge) return;

        if (event.getCaster() == null) return;
        if (event.isCanceled()) return;
        
        EntityLivingBase caster = event.getCaster();
        if (caster instanceof EntityPlayer && !caster.world.isRemote) {
            // Instant spells only
            if (!event.getSpell().isContinuous) {
                handleManaConsumption((EntityPlayer) caster, event);
            }
        }
    }

    @SubscribeEvent
    public void onSpellCastFinish(SpellCastEvent.Finish event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge) return;

        if (event.getCaster() == null) return;
        if (event.isCanceled()) return;

        EntityLivingBase caster = event.getCaster();
        if (caster instanceof EntityPlayer && !caster.world.isRemote) {
            // Continuous spells only
            if (event.getSpell().isContinuous) {
                handleManaConsumption((EntityPlayer) caster, event);
            }
        }
    }

    private void handleManaConsumption(EntityPlayer player, SpellCastEvent event) {
        double consumed = PlayerManaCompat.getConsumedMana(event);
        if (consumed <= 0.0D) return;

        ItemStack wandStack = ItemStack.EMPTY;
        EnumHand wandHand = EnumHand.MAIN_HAND;

        if (player.getHeldItemMainhand().getItem() == ModItems.LIVING_WAND) {
            wandStack = player.getHeldItemMainhand();
            wandHand = EnumHand.MAIN_HAND;
        } else if (player.getHeldItemOffhand().getItem() == ModItems.LIVING_WAND) {
            wandStack = player.getHeldItemOffhand();
            wandHand = EnumHand.OFF_HAND;
        }

        if (!wandStack.isEmpty()) {
            // Konwersja many na punkty ewolucji. 
            // Aby uniknąć straty resztek many (np. rzucając spell za 8 many dostajesz 1 pkt i tracisz 3),
            // zapisujemy "kieszonkową manę" w NBT.
            if (!wandStack.hasTagCompound()) {
                wandStack.setTagCompound(new NBTTagCompound());
            }

            NBTTagCompound nbt = wandStack.getTagCompound();
            if (nbt != null) {
                double partialMana = nbt.getDouble("WandPartialMana") + consumed;
                int pointsEarned = (int) Math.floor(partialMana / MANA_PER_POINT);
                double remainder = partialMana - (pointsEarned * (double) MANA_PER_POINT);
                nbt.setDouble("WandPartialMana", remainder < 0.0001D ? 0.0D : remainder);

                if (pointsEarned > 0) {
                    addEvolutionPoints(wandStack, player, wandHand, pointsEarned);
                }
            }
        }
    }

    private void addEvolutionPoints(ItemStack stack, EntityPlayer player, EnumHand hand, int points) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null) {
            int current = nbt.getInteger("WandEvolutionPoints");
            current += points;
            nbt.setInteger("WandEvolutionPoints", current);

            if (current >= EVOLUTION_THRESHOLD) {
                evolveWand(stack, player, hand);
            }
        }
    }

    private void evolveWand(ItemStack stack, EntityPlayer player, EnumHand hand) {
        Item sentientWand = ForgeRegistries.ITEMS.getValue(new ResourceLocation("insanetweaks", "sentient_wand"));
        
        if (sentientWand != null) {
            ItemStack newStack = new ItemStack(sentientWand, 1);
            NBTTagCompound stackTag = stack.getTagCompound();
            if (stackTag != null) {
                NBTTagCompound newTag = stackTag.copy();
                // Reset evolution counter so the new Sentient Wand doesn't
                // re-trigger evolveWand() on the very next kill/spell cast.
                newTag.setInteger("WandEvolutionPoints", 0);
                newTag.setDouble("WandPartialMana", 0.0D);
                newStack.setTagCompound(newTag);
            }

            // Transfer stack correctly into the correct hand slot
            player.setHeldItem(hand, newStack);

            SoundEvent spawnSound = SoundEvents.ENTITY_WITHER_SPAWN;
            if (spawnSound != null) {
                player.world.playSound(null, player.posX, player.posY, player.posZ, spawnSound, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }

            if (ModConfig.client.displayDebugInfo) {
                player.sendMessage(new TextComponentString("\u00a7c\u00a7l[!] \u00a7cYour wand writhes as it achieves perfect Synergy!\n" +
                        "It has evolved into the Sentient Wand!"));
            }
        }
    }
}
