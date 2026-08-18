package com.spege.manacore.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Lustro `current` wylacznie na potrzeby HUD-u. Nigdy nie jest zrodlem prawdy. */
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
