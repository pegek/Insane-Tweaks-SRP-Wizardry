package com.spege.insanetweaks.api;

import net.minecraft.entity.player.EntityPlayer;

/**
 * The one place this mod is allowed to ask "does this player have Reskillable trait X?".
 *
 * <h3>Why this exists</h3>
 * The Reskillable integration lives in a separate mod ({@code reskilltweaks}) precisely so that
 * this one can boot without Reskillable installed. On a server without it, the old arrangement did
 * not fail with a missing-dependency message — it exploded a minute into loading with
 * {@code TypeNotPresentException: codersafterdark.reskillable.api.unlockable.Unlockable}, because
 * the type stood in a handler's <em>method signature</em> and {@code @Mod.EventBusSubscriber}
 * registers a class whatever its body says. No {@code Loader.isModLoaded} inside a method can save
 * a type in its header; only a jar boundary can.
 *
 * <p>Two mechanics could not follow the traits out, because they are this mod's, not Reskillable's:
 * the charge jump (its packet is discriminator 5 on the {@code insanetweaks} channel — moving it
 * would mean a second channel) and the parasite XP fallback. Both merely <em>gate</em> on a trait.
 * So they ask here instead, and {@code reskilltweaks} fills the hook in during its init.
 *
 * <h3>The rule for this class</h3>
 * 🚨 <b>Not one Reskillable type may ever appear here</b> — not in a field, not in a signature, not
 * in an {@code extends}. Ids are plain strings and the answer is a {@code boolean} for exactly that
 * reason. Break this and the crash the split was built to remove comes straight back, in the one
 * class that is loaded unconditionally.
 *
 * <p>With no provider registered — {@code reskilltweaks} absent, or its module switched off — every
 * query answers {@code false}, which is the same answer the old code gave when the skills module
 * was disabled. Callers therefore need no presence check of their own.
 */
public final class TraitGate {

    /**
     * Ids stay under the legacy {@code compatskills} domain, which is where the traits have always
     * registered and what player saves contain. See {@code SkillsModule.DOMAIN} in
     * {@code reskilltweaks} — the two must agree.
     */
    public static final String COILED_SPRING = "compatskills:coiled_spring";
    public static final String ASSIMILATED_WARFARE = "compatskills:assimilated_warfare";

    /** Implemented by {@code reskilltweaks}, which resolves ids through its own trait handles. */
    public interface Provider {
        boolean has(EntityPlayer player, String unlockableId);
    }

    /**
     * Volatile because it is written once on the main thread during init and read afterwards from
     * both the server and client threads.
     */
    private static volatile Provider provider;

    private TraitGate() {
    }

    /** Called once by {@code reskilltweaks}. Passing {@code null} disarms every query again. */
    public static void setProvider(Provider newProvider) {
        provider = newProvider;
    }

    /** Whether an integration is actually answering — for logging and diagnostics only. */
    public static boolean isArmed() {
        return provider != null;
    }

    /**
     * @param unlockableId one of the constants on this class
     * @return {@code false} whenever no integration is present, so callers may ask unconditionally
     */
    public static boolean has(EntityPlayer player, String unlockableId) {
        if (player == null) {
            return false;
        }
        // Read the field once: the provider could in principle be cleared between the null check
        // and the call.
        Provider current = provider;
        return current != null && current.has(player, unlockableId);
    }
}
