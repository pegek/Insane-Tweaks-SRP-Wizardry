package com.spege.insanetweaks.events;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.Multimap;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Lightweight runtime probe for comparing final attack speed behaviour between
 * InsaneTweaks spellblades and the native swparasites sentient saber.
 */
public class AttackSpeedDebugHandler {

    private static final String LIVING_SPELLBLADE = "insanetweaks:living_spellblade";
    private static final String SENTIENT_SPELLBLADE = "insanetweaks:sentient_spellblade";
    private static final String SENTIENT_SABER = "swparasites:saber_sentient";

    private final Map<UUID, String> lastDebugSignature = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player == null || event.player.world.isRemote) {
            return;
        }

        if (!ModConfig.client.displayDebugInfo) {
            lastDebugSignature.remove(event.player.getUniqueID());
            return;
        }

        EntityPlayer player = event.player;
        ItemStack held = player.getHeldItemMainhand();
        String regName = getRegistryName(held);

        if (!isTrackedWeapon(regName)) {
            lastDebugSignature.remove(player.getUniqueID());
            return;
        }

        IAttributeInstance attackSpeed = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }

        String signature = buildSignature(regName, held, attackSpeed);
        String previous = lastDebugSignature.put(player.getUniqueID(), signature);
        if (!signature.equals(previous)) {
            logSnapshot(player, regName, held, attackSpeed);
        }
    }

    private boolean isTrackedWeapon(String regName) {
        return LIVING_SPELLBLADE.equals(regName)
                || SENTIENT_SPELLBLADE.equals(regName)
                || SENTIENT_SABER.equals(regName);
    }

    private String getRegistryName(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return "";
        }
        return stack.getItem().getRegistryName().toString();
    }

    private String buildSignature(String regName, ItemStack held, IAttributeInstance attackSpeed) {
        String stackAttackSpeed = formatStackAttackSpeed(held);
        String base = formatDouble(attackSpeed.getBaseValue());
        String total = formatDouble(attackSpeed.getAttributeValue());
        String op0 = formatModifiers(attackSpeed.getModifiersByOperation(0));
        String op1 = formatModifiers(attackSpeed.getModifiersByOperation(1));
        String op2 = formatModifiers(attackSpeed.getModifiersByOperation(2));

        return regName + "|" + stackAttackSpeed + "|" + base + "|" + total + "|" + op0 + "|" + op1 + "|" + op2;
    }

    private void logSnapshot(EntityPlayer player, String regName, ItemStack held, IAttributeInstance attackSpeed) {
        String itemClass = held.getItem().getClass().getName();
        String stackAttackSpeed = formatStackAttackSpeed(held);

        InsaneTweaksMod.LOGGER.info(
                "[AttackSpeedDebug] player={} item={} class={} stack_attack_speed={} attr_base={} attr_total={} op0={} op1={} op2={}",
                player.getName(),
                regName,
                itemClass,
                stackAttackSpeed,
                formatDouble(attackSpeed.getBaseValue()),
                formatDouble(attackSpeed.getAttributeValue()),
                formatModifiers(attackSpeed.getModifiersByOperation(0)),
                formatModifiers(attackSpeed.getModifiersByOperation(1)),
                formatModifiers(attackSpeed.getModifiersByOperation(2)));
    }

    private String formatStackAttackSpeed(ItemStack held) {
        Multimap<String, AttributeModifier> map = held.getAttributeModifiers(EntityEquipmentSlot.MAINHAND);
        Collection<AttributeModifier> modifiers = map.get(SharedMonsterAttributes.ATTACK_SPEED.getName());
        if (modifiers == null || modifiers.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (AttributeModifier modifier : modifiers) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(modifier.getName())
                    .append("=")
                    .append(formatDouble(modifier.getAmount()))
                    .append("(op")
                    .append(modifier.getOperation())
                    .append(")");
            first = false;
        }
        return builder.toString();
    }

    private String formatModifiers(Collection<AttributeModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (AttributeModifier modifier : modifiers) {
            if (!first) {
                builder.append("; ");
            }
            builder.append(modifier.getName())
                    .append("=")
                    .append(formatDouble(modifier.getAmount()))
                    .append("@")
                    .append(modifier.getID());
            first = false;
        }
        builder.append("]");
        return builder.toString();
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }
}
