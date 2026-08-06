package com.spege.reskilltweaks.skills;

import com.spege.insanetweaks.api.TraitGate;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Answers Insane Tweaks' trait questions for the two mechanics that could not follow the traits out
 * of that mod: the charge jump (its packet is discriminator 5 on the {@code insanetweaks} channel,
 * so moving it would mean a second channel) and the parasite XP fallback.
 *
 * <p>This is the only class in this mod that touches Insane Tweaks at all, and the touch is
 * one-directional: it implements an interface whose whole surface is {@code (EntityPlayer, String)
 * -> boolean}. Nothing here goes back the other way.
 *
 * <p>The string switch costs a hash and one {@code equals} per call and allocates nothing; the real
 * work is {@link TraitHandle#has}, which already caches its two registry lookups. An unknown id
 * answers {@code false} rather than throwing, so adding a constant to {@code TraitGate} without
 * adding it here degrades to "nobody has the trait" instead of crashing a tick loop.
 */
public class TraitGateProvider implements TraitGate.Provider {

    @Override
    public boolean has(EntityPlayer player, String unlockableId) {
        if (unlockableId == null) {
            return false;
        }

        switch (unlockableId) {
            case TraitGate.COILED_SPRING:
                return TraitHandle.COILED_SPRING.has(player);
            case TraitGate.ASSIMILATED_WARFARE:
                return TraitHandle.ASSIMILATED_WARFARE.has(player);
            default:
                return false;
        }
    }
}
