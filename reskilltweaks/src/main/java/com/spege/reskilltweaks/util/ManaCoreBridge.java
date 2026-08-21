package com.spege.reskilltweaks.util;

import java.lang.reflect.Method;

import com.spege.reskilltweaks.ReskillTweaks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

/**
 * Adds mana to the player's ManaCore pool, when ManaCore is installed.
 *
 * <p>🚨 <b>Reflective on purpose, not out of laziness.</b> A compile dependency would work and
 * would be type-safe, but it would make this the SECOND mod in this repo reaching sideways into
 * another one, and the first was documented as "a deliberate exception, and not a precedent"
 * (see CLAUDE.md on why {@code reskilltweaks} may depend on the content mod). One trait's
 * regeneration is not worth spending that a second time; the mods stay independently buildable
 * and independently shippable.
 *
 * <p>The cost of reflection here is genuinely nil: this is called at most once per second per
 * player, and the {@link Method} is resolved once and cached. That is the opposite of the
 * Spider's Grace mistake recorded in the repo notes, where reflection ran every tick with no
 * cache and showed up in a profile.
 *
 * <p>The price paid is losing compile-time checking of one signature. It is contained: resolution
 * failure is logged once, at warn, naming the method it looked for - so a future rename of
 * {@code ManaAPI.add} produces a line in the log rather than silence.
 */
public final class ManaCoreBridge {

    private static final String MANACORE_MODID = "manacore";
    private static final String API_CLASS = "com.spege.manacore.api.ManaAPI";
    private static final String ADD_METHOD = "add";

    /** Set once resolution has been attempted, successfully or not. Never retried. */
    private static boolean resolved;
    private static Method addMana;

    private ManaCoreBridge() {
    }

    /**
     * Adds {@code amount} mana to the player's pool.
     *
     * @return whether the mana was actually delivered. {@code false} means ManaCore is absent or
     *         unusable, and the caller should fall back to whatever it did before - it must NOT
     *         treat this as "the player was topped up".
     */
    public static boolean addMana(EntityPlayer player, double amount) {
        if (player == null || Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0.0D) {
            return false;
        }
        Method method = resolve();
        if (method == null) {
            return false;
        }
        try {
            method.invoke(null, player, Double.valueOf(amount));
            return true;
        } catch (Exception e) {
            disable("invoking " + API_CLASS + "." + ADD_METHOD, e);
            return false;
        } catch (LinkageError e) {
            // Not caught by the clause above: Error is not an Exception. Reachable here because
            // invoking a method triggers initialisation of classes it touches.
            disable("invoking " + API_CLASS + "." + ADD_METHOD, e);
            return false;
        }
    }

    private static synchronized Method resolve() {
        if (resolved) {
            return addMana;
        }
        resolved = true;

        if (!Loader.isModLoaded(MANACORE_MODID)) {
            return null;
        }
        try {
            // Three-arg Class.forName with initialize = false: the single-arg form runs the
            // class's static initialiser, which can throw ExceptionInInitializerError from a
            // place that has nothing to do with us.
            Class<?> api = Class.forName(API_CLASS, false, ManaCoreBridge.class.getClassLoader());
            addMana = api.getMethod(ADD_METHOD, EntityPlayer.class, double.class);
            ReskillTweaks.LOGGER.info("[ReskillTweaks] ManaCore found - Meditation will regenerate the "
                    + "player's mana pool instead of item mana.");
        } catch (Exception e) {
            disable("resolving " + API_CLASS + "." + ADD_METHOD, e);
        } catch (LinkageError e) {
            disable("resolving " + API_CLASS + "." + ADD_METHOD, e);
        }
        return addMana;
    }

    private static void disable(String what, Throwable cause) {
        addMana = null;
        ReskillTweaks.LOGGER.warn("[ReskillTweaks] ManaCore bridge disabled while {} - Meditation falls "
                + "back to recharging item mana. Cause: {}", what, cause.toString());
    }
}
