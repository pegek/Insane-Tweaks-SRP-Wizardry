package com.spege.insanetweaks.util;

import net.minecraft.entity.player.EntityPlayer;

public class PlayerManaContext {
    public static final ThreadLocal<EntityPlayer> CURRENT_PLAYER = new ThreadLocal<>();
}
