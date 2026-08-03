package com.spege.insanetweaks;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server-safe half of the sided proxy. Every override lives in
 * {@link com.spege.insanetweaks.client.ClientProxy}; this class deliberately does nothing.
 *
 * <p>🚨 The reason this proxy exists at all: entity/TESR renderer registration touches
 * {@code net.minecraftforge.fml.client.registry.*} and {@code net.minecraft.client.*}, which do
 * not exist on a dedicated server. Keeping those calls (and the anonymous {@code IRenderFactory}
 * implementations, which become inner classes of their enclosing class) inside
 * {@link InsaneTweaksMod} made the dedicated server fail while *loading* the {@code @Mod} class in
 * {@code FMLModContainer.constructMod} — the verifier resolves {@code IRenderFactory} before any
 * {@code if (side == CLIENT)} guard inside the method body ever gets to run. A side check in the
 * method is therefore useless here; the client code has to live in a separate class that the
 * dedicated server never loads.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }
}
