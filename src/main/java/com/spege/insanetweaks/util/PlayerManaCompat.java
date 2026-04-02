package com.spege.insanetweaks.util;

import java.lang.reflect.Method;

import com.spege.insanetweaks.InsaneTweaksMod;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Loader;

/**
 * Optional reflection-based compat for the player_mana mod.
 */
public final class PlayerManaCompat {

    private static boolean initialized = false;
    private static boolean available = false;

    private static Method getSoulMethod;
    private static Method getCurrentManaMethod;
    private static Method getMaxManaMethod;
    private static Method getSpellCostMethod;
    private static Method addMaxManaForPlayerMethod;
    private static Object manaPoolConfig;
    private static java.lang.reflect.Field maxManaCapField;
    private static Item chantCostUpgrade;

    private PlayerManaCompat() {
    }

    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    public static boolean hasUsableMana(EntityPlayer player) {
        return getCurrentMana(player) > 0.0D;
    }

    public static boolean canModifyMaxMana() {
        ensureInitialized();
        return available && addMaxManaForPlayerMethod != null;
    }

    public static double getCurrentMana(EntityPlayer player) {
        if (player == null || !isAvailable()) {
            return 0.0D;
        }

        try {
            Object soul = getSoulMethod.invoke(null, player);
            if (soul == null) {
                return 0.0D;
            }

            Object value = getCurrentManaMethod.invoke(soul);
            return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
        } catch (Exception e) {
            logDebugFailure("read current player_mana MP", e);
            return 0.0D;
        }
    }

    public static double getMaxMana(EntityPlayer player) {
        if (player == null || !isAvailable() || getMaxManaMethod == null) {
            return 0.0D;
        }

        try {
            Object soul = getSoulMethod.invoke(null, player);
            if (soul == null) {
                return 0.0D;
            }

            Object value = getMaxManaMethod.invoke(soul);
            return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
        } catch (Exception e) {
            logDebugFailure("read current player_mana max MP", e);
            return 0.0D;
        }
    }

    public static double getMaxManaCap() {
        if (!isAvailable() || manaPoolConfig == null || maxManaCapField == null) {
            return 0.0D;
        }

        try {
            Object value = maxManaCapField.get(manaPoolConfig);
            return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
        } catch (Exception e) {
            logDebugFailure("read player_mana max MP cap", e);
            return 0.0D;
        }
    }

    public static boolean addMaxMana(EntityPlayer player, double amount) {
        if (player == null || amount <= 0.0D || !canModifyMaxMana()) {
            return false;
        }

        try {
            Object soul = getSoulMethod.invoke(null, player);
            if (soul == null) {
                return false;
            }

            addMaxManaForPlayerMethod.invoke(soul, player, amount);
            return true;
        } catch (Exception e) {
            logDebugFailure("increase player_mana max MP", e);
            return false;
        }
    }

    public static float getActualCostMultiplier(SpellModifiers modifiers) {
        if (modifiers == null) {
            return 1.0f;
        }

        if (isAvailable() && chantCostUpgrade != null) {
            float storedMultiplier = modifiers.get(chantCostUpgrade);
            if (storedMultiplier > 0.0f) {
                return storedMultiplier;
            }
        }

        return modifiers.get(SpellModifiers.COST);
    }

    public static double getSpellCost(Spell spell) {
        if (spell == null) {
            return 0.0D;
        }

        if (!isAvailable()) {
            return spell.getCost();
        }

        try {
            Object result = getSpellCostMethod.invoke(null, spell);
            return result instanceof Number ? ((Number) result).doubleValue() : spell.getCost();
        } catch (Exception e) {
            logDebugFailure("read player_mana spell cost", e);
            return spell.getCost();
        }
    }

    public static double getConsumedMana(SpellCastEvent event) {
        if (event == null || event.getSpell() == null) {
            return 0.0D;
        }

        Spell spell = event.getSpell();
        float multiplier = getActualCostMultiplier(event.getModifiers());

        if (isAvailable()) {
            if (spell.isContinuous && event instanceof SpellCastEvent.Finish) {
                int activeTicks = ((SpellCastEvent.Finish) event).getCount();
                return getContinuousConsumedMana(spell, activeTicks) * multiplier;
            }

            return getSpellCost(spell) * multiplier;
        }

        double baseCost = spell.getCost() * multiplier;
        if (spell.isContinuous && event instanceof SpellCastEvent.Finish) {
            return (baseCost * ((SpellCastEvent.Finish) event).getCount()) / 20.0D;
        }

        return baseCost;
    }

    private static double getContinuousConsumedMana(Spell spell, int activeTicks) {
        double spellCost = getSpellCost(spell);
        double totalCost = 0.0D;

        for (int tick = 1; tick <= activeTicks; tick++) {
            totalCost += getDistributedCost(spellCost, tick);
        }

        return totalCost;
    }

    private static int getDistributedCost(double spellCost, int tickCount) {
        if (tickCount % 20 == 0) {
            return (int) ((spellCost / 2.0D) + (spellCost % 2.0D));
        }

        if (tickCount % 10 == 0) {
            return (int) (spellCost / 2.0D);
        }

        return 0;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        initialized = true;

        if (!Loader.isModLoaded("player_mana")) {
            return;
        }

        try {
            Class<?> manaClass = Class.forName("zettasword.player_mana.cap.Mana");
            Class<?> soulClass = Class.forName("zettasword.player_mana.cap.ISoul");
            Class<?> eventsHandlerClass = Class.forName("zettasword.player_mana.events.EventsHandler");
            Class<?> sageClass = Class.forName("zettasword.player_mana.api.Sage");
            Class<?> talesClass = Class.forName("zettasword.player_mana.Tales");
            Class<?> manaPoolSystemClass = Class.forName("zettasword.player_mana.Tales$ManaPoolSystem");

            getSoulMethod = manaClass.getMethod("getSoul", EntityPlayer.class);
            getCurrentManaMethod = soulClass.getMethod("getMP");
            getMaxManaMethod = soulClass.getMethod("getMaxMP");
            getSpellCostMethod = eventsHandlerClass.getMethod("getCost", Spell.class);
            addMaxManaForPlayerMethod = soulClass.getMethod("addMaxMana", EntityPlayer.class, Double.TYPE);

            Object item = sageClass.getField("CHANT_COST").get(null);
            if (item instanceof Item) {
                chantCostUpgrade = (Item) item;
            }

            manaPoolConfig = talesClass.getField("mp").get(null);
            maxManaCapField = manaPoolSystemClass.getField("max");

            available = getSoulMethod != null && getCurrentManaMethod != null && getSpellCostMethod != null;
        } catch (Throwable t) {
            available = false;
            logDebugFailure("initialize player_mana compat", t);
        }
    }

    private static void logDebugFailure(String action, Throwable t) {
        if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Failed to {}: {}", action, t.getMessage());
        }
    }
}
