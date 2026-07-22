package com.spege.insanetweaks.sanctuary;

import java.util.UUID;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;

/**
 * Layer A of the Sanctuary "Cost of Power": the always-on presence tax on every player standing
 * inside an active, non-ascended Sanctuary. Both effects are applied through direct mechanics
 * (attribute modifier + event cancellation), never a {@link net.minecraft.potion.PotionEffect},
 * so no milk / trinket / cleanse can remove them.
 *
 * <p>Layers B and C (mana / owner-HP drain) live in {@link TileEntitySanctuaryCore#tickCost()},
 * which owns the fuel tank and owner reference. This handler only reads
 * {@link SanctuaryRegistry#governing} to decide membership.
 */
public final class SanctuaryCostHandler {

    private static final UUID MAX_HP_MODIFIER_ID = UUID.fromString("b6a7c3e2-1f4d-4a8b-9c2e-7d5f0a1b2c3d");
    private static final String MAX_HP_MODIFIER_NAME = "insanetweaks:sanctuary_maxhp_tithe";

    /** Max-HP tithe: reduce max health while inside; halved for the owner if U2 is active. */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent event) {
        if (event.phase != Phase.END) { return; }
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) { return; }

        if (!ModConfig.sanctuaryCost.enableCost) { removeMaxHp(player); return; }

        TileEntitySanctuaryCore te = SanctuaryRegistry.governing(player.world, player.posX, player.posZ);
        double penalty = 0.0D;
        if (te != null) {
            penalty = ModConfig.sanctuaryCost.maxHpPenalty;
            if (te.isOwner(player) && te.u2Active()) { penalty *= 0.5D; }
        }
        applyMaxHp(player, penalty);
    }

    /** Regen suppression: cancel passive healing (natural regen + Regeneration potion) inside. */
    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!ModConfig.sanctuaryCost.enableCost || !ModConfig.sanctuaryCost.suppressRegen) { return; }
        if (!(event.getEntityLiving() instanceof EntityPlayer)) { return; }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world == null || player.world.isRemote) { return; }
        if (SanctuaryRegistry.governing(player.world, player.posX, player.posZ) != null) {
            event.setCanceled(true);
        }
    }

    private void applyMaxHp(EntityPlayer player, double penalty) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attr == null) { return; }
        AttributeModifier existing = attr.getModifier(MAX_HP_MODIFIER_ID);
        if (penalty <= 0.0D) {
            if (existing != null) { attr.removeModifier(existing); }
            return;
        }
        double desired = -penalty;
        if (existing != null) {
            if (existing.getAmount() == desired) { return; } // already correct, avoid churn
            attr.removeModifier(existing);
        }
        attr.applyModifier(new AttributeModifier(MAX_HP_MODIFIER_ID, MAX_HP_MODIFIER_NAME, desired, 0));
        if (player.getHealth() > player.getMaxHealth()) { player.setHealth(player.getMaxHealth()); }
    }

    private void removeMaxHp(EntityPlayer player) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attr == null) { return; }
        AttributeModifier existing = attr.getModifier(MAX_HP_MODIFIER_ID);
        if (existing != null) { attr.removeModifier(existing); }
    }
}
