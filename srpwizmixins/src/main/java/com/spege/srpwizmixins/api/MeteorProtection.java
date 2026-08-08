package com.spege.srpwizmixins.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.spege.srpwizmixins.SrpWizMixins;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The registry behind {@link ProtectedAreaProvider} - the one public entry point of this mod.
 *
 * <p>Register from your mod's {@code init} (or later; registration is safe at any time):
 *
 * <pre>
 * if (Loader.isModLoaded("srpwizmixins")) {
 *     MeteorProtection.register((world, pos) -&gt; MyRegions.covers(world, pos));
 * }
 * </pre>
 *
 * <p>Keep that call in a class of its own that nothing else references, so a pack without this mod
 * never loads it.
 *
 * <p>{@link CopyOnWriteArrayList} rather than a synchronized list: reads happen inside a world tick
 * and writes happen once at start-up, which is exactly the ratio it is built for, and it lets the
 * read loop run without holding a lock.
 */
public final class MeteorProtection {

    private static final List<ProtectedAreaProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private MeteorProtection() {
    }

    /** Adds a provider. Registering the same instance twice simply asks it twice. */
    public static void register(ProtectedAreaProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        PROVIDERS.add(provider);
        SrpWizMixins.LOGGER.info("Meteor protection provider registered: {}",
                provider.getClass().getName());
    }

    /** True when any provider claims the position. See the interface for the threading rules. */
    public static boolean isProtected(World world, BlockPos pos) {
        for (ProtectedAreaProvider provider : PROVIDERS) {
            try {
                if (provider.isProtected(world, pos)) {
                    return true;
                }
            } catch (Throwable t) {
                // A broken provider must not be able to stop a meteor falling at all.
                SrpWizMixins.LOGGER.error("Meteor protection provider {} threw; ignoring it for this"
                        + " position", provider.getClass().getName(), t);
            }
        }
        return false;
    }

    /** Whether asking is worth the trouble at all. */
    public static boolean hasProviders() {
        return !PROVIDERS.isEmpty();
    }
}
