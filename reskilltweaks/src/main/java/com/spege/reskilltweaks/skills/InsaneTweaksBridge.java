package com.spege.reskilltweaks.skills;

/**
 * The one call that hands Insane Tweaks its trait answers. Exists only so that
 * {@code ReskillTweaks} never names an Insane Tweaks type itself.
 *
 * <p>🚨 Why this cannot be inlined into {@code ReskillTweaks.init} behind the
 * {@code Loader.isModLoaded} check: {@code TraitGate.setProvider(new TraitGateProvider())} passes a
 * {@code TraitGateProvider} where a {@code TraitGate.Provider} is declared, and the bytecode verifier
 * checks that assignment when it <em>links</em> the calling class - loading
 * {@code TraitGate$Provider} to do it, before any line of {@code init} has run. On a pack without
 * Insane Tweaks that is a {@code NoClassDefFoundError} out of the {@code @Mod} class. Same failure
 * as the nunchaku crash in CLAUDE.md, reached through a method argument instead of a local.
 * Behind a plain {@code invokestatic} into this class, the check only happens if {@link #arm()} is
 * actually called.
 */
public final class InsaneTweaksBridge {

    private InsaneTweaksBridge() {
    }

    public static void arm() {
        com.spege.insanetweaks.api.TraitGate.setProvider(new TraitGateProvider());
    }
}
