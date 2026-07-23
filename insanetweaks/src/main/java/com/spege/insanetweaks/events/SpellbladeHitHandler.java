package com.spege.insanetweaks.events;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

import com.spege.insanetweaks.config.ModConfig;

public class SpellbladeHitHandler {

    /**
     * Items that participate in kill-tracking and evolution logic.
     * Note: living_aegis is included here for future kill-tracking (commented out
     * below).
     * Weapon property dispatching is NOT handled here  Eit is done by
     * BridgeSpellblade.hitEntity()
     * which is the correct, single trigger point for per-hit callbacks.
     */
    private static final Set<String> SENTIENT_ITEMS = new HashSet<>(Arrays.asList(
            "insanetweaks:sentient_spellblade",
            "insanetweaks:living_spellblade",
            "insanetweaks:living_aegis")); // fixed: was parasite_aegis (non-existent registry name)

    // onLivingHurt REMOVED  Eweapon property callbacks (Bleeding, Viral, Heavy,
    // etc.) are already
    // dispatched by BridgeSpellblade.hitEntity(), which Minecraft calls directly on
    // every hit.
    // Having a second dispatch in an event handler caused every
    // WeaponPropertyWithCallback
    // (e.g. BLEEDING_3, HEAVY_2, UNCAPPED) to fire TWICE per hit.
    //
    // REACH (WeaponProperties.REACH_1) was unaffected because its callback returns
    // null  E    // it works via attribute modifiers, not per-hit callbacks  Ewhich is why it
    // appeared correct.
    //
    // The reflection fallback block is also removed as it was dead code:
    // BridgeSpellblade
    // implements IWeaponPropertyContainer directly, so instanceof always returns
    // true.

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDeath(LivingDeathEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge)
            return;

        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityPlayer))
            return;

        EntityPlayer player = (EntityPlayer) trueSource;

        // Secure modifications from executing on the Client (prevents Desync / Ghost
        // Data)
        if (player.world.isRemote)
            return;

        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (stack.isEmpty())
                continue;

            ResourceLocation regLoc = stack.getItem().getRegistryName();
            if (regLoc == null)
                continue;

            String regName = regLoc.toString();
            if (!SENTIENT_ITEMS.contains(regName))
                continue;

            if (!stack.hasTagCompound()) {
                stack.setTagCompound(new NBTTagCompound());
            }

            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null) {
                int kills = nbt.getInteger("SentientKills") + 1;
                nbt.setInteger("SentientKills", kills);

                // EVOLUTION TRIGGER: 900 kills with the Living Spellblade -> evolve into
                // Sentient
                if (!player.world.isRemote && "insanetweaks:living_spellblade".equals(regName) && kills >= 900) {
                    Item sentientSword = ForgeRegistries.ITEMS
                            .getValue(new ResourceLocation("insanetweaks", "sentient_spellblade"));

                    if (sentientSword != null) {
                        ItemStack newStack = new ItemStack(sentientSword);
                        NBTTagCompound stackTag = stack.getTagCompound();
                        if (stackTag != null) {
                            newStack.setTagCompound(stackTag.copy());
                        }
                        player.setHeldItem(hand, newStack);

                        SoundEvent spawnSound = SoundEvents.ENTITY_WITHER_SPAWN;
                        if (spawnSound != null) {
                            player.world.playSound(null, player.posX, player.posY, player.posZ,
                                    spawnSound, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        }
                        if (ModConfig.client.displayDebugInfo) {
                            player.sendMessage(new TextComponentString(
                                    "\u00a7c\u00a7l[!] \u00a7cYour weapon writhes as it achieves perfect Synergy!\n" +
                                            "It has evolved into the Sentient Spellblade!"));
                        }
                    }
                }
            }
        }
    }
}
