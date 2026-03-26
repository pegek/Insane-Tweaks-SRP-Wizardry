package com.spege.insanetweaks.events;

import java.lang.reflect.Method;
import java.util.List;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.oblivioussp.spartanweaponry.api.IWeaponPropertyContainer;
import com.oblivioussp.spartanweaponry.api.ToolMaterialEx;
import com.oblivioussp.spartanweaponry.api.weaponproperty.IPropertyCallback;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponPropertyWithCallback;

import com.spege.insanetweaks.config.ModConfig;

public class SpellbladeHitHandler {

    private static final Set<String> SENTIENT_ITEMS = new HashSet<>(Arrays.asList(
            "insanetweaks:sentient_spellblade",
            "insanetweaks:living_spellblade",
            "insanetweaks:parasite_aegis"));

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!ModConfig.enableSrpEbWizardryBridge)
            return;

        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityPlayer))
            return;

        EntityPlayer player = (EntityPlayer) trueSource;
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.isEmpty())
            return;

        ResourceLocation regLoc = stack.getItem().getRegistryName();
        if (regLoc == null)
            return;

        String regName = regLoc.toString();
        if (!SENTIENT_ITEMS.contains(regName))
            return;

        Item item = stack.getItem();
        EntityLivingBase target = event.getEntityLiving();

        if (item instanceof IWeaponPropertyContainer<?>) {
            IWeaponPropertyContainer<?> container = (IWeaponPropertyContainer<?>) item;
            List<WeaponProperty> props = container.getAllWeaponProperties();

            if (props != null) {
                for (WeaponProperty prop : props) {
                    if (prop instanceof WeaponPropertyWithCallback) {
                        IPropertyCallback cb = ((WeaponPropertyWithCallback) prop).getCallback();
                        if (cb != null) {
                            try {
                                cb.onHitEntity(container.getMaterialEx(), stack, target, player, (Entity) null);
                            } catch (Throwable t) {
                                if (ModConfig.displayDebugInfo) System.out.println("[SpellbladeHitHandler] ERROR applying property onHitEntity: "
                                        + t.getMessage());
                            }
                        }
                    }
                }
            }
        } else {
            // Reflection fallback in case of classloader mismatch (instanceof returns false
            // across loaders)
            try {
                Method getAllProps = item.getClass().getMethod("getAllWeaponProperties");
                Method getMat = item.getClass().getMethod("getMaterialEx");
                List<?> reflectProps = (List<?>) getAllProps.invoke(item);
                Object mat = getMat.invoke(item);

                if (reflectProps != null && mat != null) {
                    for (Object prop : reflectProps) {
                        try {
                            if (prop == null)
                                continue;
                            Method getCb = prop.getClass().getMethod("getCallback");
                            Object cb = getCb.invoke(prop);
                            if (cb != null) {
                                Method onHitEntity = cb.getClass().getMethod("onHitEntity",
                                        ToolMaterialEx.class,
                                        ItemStack.class,
                                        EntityLivingBase.class,
                                        EntityLivingBase.class,
                                        Entity.class);
                                onHitEntity.invoke(cb, mat, stack, target, player, (Entity) null);
                            }
                        } catch (Throwable t2) {
                            // Silently ignore individual property errors
                        }
                    }
                }
            } catch (Throwable t) {
                if (ModConfig.displayDebugInfo) System.out.println("[SpellbladeHitHandler] Reflection fallback failed: " + t.getMessage());
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDeath(LivingDeathEvent event) {
        if (!ModConfig.enableSrpEbWizardryBridge)
            return;

        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityPlayer))
            return;

        EntityPlayer player = (EntityPlayer) trueSource;
        
        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (stack.isEmpty()) continue;

            ResourceLocation regLoc = stack.getItem().getRegistryName();
            if (regLoc == null) continue;

            String regName = regLoc.toString();
            if (!SENTIENT_ITEMS.contains(regName)) continue;

            if (!stack.hasTagCompound()) {
                stack.setTagCompound(new NBTTagCompound());
            }

            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null) {
                int kills = nbt.getInteger("SentientKills") + 1;
                nbt.setInteger("SentientKills", kills);

                // EVOLUTION TRIGGER: 1700 kills with the Living Spellblade -> evolve into Sentient
                if (!player.world.isRemote && "insanetweaks:living_spellblade".equals(regName) && kills >= 1200) {
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
                        if (ModConfig.displayDebugInfo) {
                            player.sendMessage(new TextComponentString(
                                    "\u00a7c\u00a7l[!] \u00a7cYour weapon writhes as it achieves perfect Synergy!\n" +
                                            "It has evolved into the Sentient Spellblade!"));
                        }
                    }
                }

                // TODO: FUTURE AEGIS EVOLUTION
                // This is a placeholder for the Sentient Aegis evolution system.
                // Currently, the killcount is purely cosmetic for the shield.
                /*
                if (!player.world.isRemote && "insanetweaks:parasite_aegis".equals(regName) && kills >= 2500) {
                    // Logic for evolving the shield into a higher tier form goes here.
                }
                */
            }
        }
    }
}
