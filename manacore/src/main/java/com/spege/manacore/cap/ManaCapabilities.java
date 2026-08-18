package com.spege.manacore.cap;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

public final class ManaCapabilities {

    @CapabilityInject(IManaPool.class)
    public static Capability<IManaPool> MANA_POOL = null;

    private ManaCapabilities() {
    }

    public static void register() {
        CapabilityManager.INSTANCE.register(IManaPool.class, new ManaPoolStorage(), ManaPool::new);
    }

    @Nullable
    public static IManaPool get(@Nullable EntityPlayer player) {
        if (player == null || MANA_POOL == null) {
            return null;
        }
        return player.getCapability(MANA_POOL, null);
    }
}
