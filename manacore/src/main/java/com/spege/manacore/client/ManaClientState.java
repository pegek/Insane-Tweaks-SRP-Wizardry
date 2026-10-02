package com.spege.manacore.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Mirror of `current` for HUD purposes only. Never a source of truth. */
@SideOnly(Side.CLIENT)
public final class ManaClientState {

    private static double current;

    private ManaClientState() {
    }

    public static double getCurrent() {
        return current;
    }

    public static void setCurrent(double value) {
        current = value;
    }
}
