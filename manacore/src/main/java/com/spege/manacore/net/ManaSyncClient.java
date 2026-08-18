package com.spege.manacore.net;

import com.spege.manacore.client.ManaClientState;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ManaSyncClient {

    private ManaSyncClient() {
    }

    public static void apply(final double current) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ManaClientState.setCurrent(current);
            }
        });
    }
}
