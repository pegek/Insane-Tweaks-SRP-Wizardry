package com.spege.srpwizcore.api;

import java.util.ArrayList;
import java.util.List;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.EntityLivingBase;

/**
 * Registry of invincibility-frame multipliers, consumed by the WorseHurtTimer mixins in
 * {@code com.spege.srpwizcore.mixins.betterhurttimer}.
 *
 * <p>Contributions multiply, so two providers at 1.8x and 1.2x give 2.16x, and the product is
 * clamped to {@code whtCompat.maxMultiplier}. A provider returning 1.0 or less contributes
 * nothing and can never drag the result below 1.0 — this is a "grant more invincibility" API,
 * not a general damage-timing API.
 *
 * <p>This class knows nothing about WorseHurtTimer and nothing about baubles. Register providers
 * during {@code FMLInitializationEvent}; the list is never mutated afterwards, so the read path
 * needs no synchronisation.
 *
 * <p>Server side only in practice — all four call sites run on the server thread.
 */
public final class WhtIFrames {

    /** One source of longer invincibility, e.g. a worn item. */
    public interface Provider {
        /**
         * @param victim the entity taking the hit, never null
         * @return the multiplier to apply, 1.0 for "this provider does not apply here"
         */
        float multiplier(EntityLivingBase victim);
    }

    private static final List<String> IDS = new ArrayList<String>(4);
    private static final List<Provider> PROVIDERS = new ArrayList<Provider>(4);

    private WhtIFrames() {
    }

    /**
     * Registers a provider under a unique id. A duplicate id is ignored, so calling this twice
     * from a reloaded config cannot stack the same effect.
     */
    public static void register(String id, Provider provider) {
        if (id == null || provider == null) {
            return;
        }
        if (IDS.contains(id)) {
            return;
        }
        IDS.add(id);
        PROVIDERS.add(provider);
    }

    /** Combined multiplier for this victim. Returns exactly 1.0 when nothing applies. */
    public static float getMultiplier(EntityLivingBase victim) {
        final int count = PROVIDERS.size();
        if (count == 0 || victim == null) {
            return 1.0F;
        }
        float result = 1.0F;
        for (int i = 0; i < count; i++) {
            final float m = PROVIDERS.get(i).multiplier(victim);
            if (m > 1.0F) {
                result *= m;
            }
        }
        float max = (float) SrpWizCoreConfig.whtCompat.maxMultiplier;
        if (max < 1.0F) {
            max = 1.0F;
        }
        return result > max ? max : result;
    }
}
