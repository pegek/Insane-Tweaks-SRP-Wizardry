package com.spege.srpwizcore.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Runs Bloodmoon's three lightmap hooks over the finished lightmap, for
 * {@code MixinEntityRendererBloodmoonLightmap}.
 *
 * <p>Bloodmoon is reached through method handles rather than a compile dependency: the mixin only
 * applies when Bloodmoon is installed ({@code BloodmoonMixinPlugin}), so this class never runs
 * without it, and the jar would otherwise have to sit in {@code libs/}, where content's
 * {@code fileTree} would deobf a coremod into its own dev runtime. The handles are resolved once,
 * on the first lightmap update, and bound to {@code ClientBloodmoonHandler.INSTANCE} — a public
 * static that Bloodmoon assigns in its {@code <clinit>} and never again.
 *
 * <p>Render thread only. Any failure disables the hook for the session with one ERROR line; a
 * broken colour tint is not worth a crashed client.
 */
public final class BloodmoonLightmap {

    private static final Logger LOGGER = LogManager.getLogger("srpwizcore");

    private static final String HANDLER_CLASS = "lumien.bloodmoon.client.ClientBloodmoonHandler";

    private static final MethodType CHANNEL = MethodType.methodType(int.class, int.class, int.class);

    private static boolean resolved;
    private static boolean disabled;
    private static boolean announced;

    private static MethodHandle active;
    private static MethodHandle red;
    private static MethodHandle green;
    private static MethodHandle blue;

    private BloodmoonLightmap() {
    }

    public static void apply(int[] colors) {
        if (!resolved) {
            resolve();
        }
        if (disabled || colors == null) {
            return;
        }
        try {
            if (!(boolean) active.invokeExact()) {
                return;
            }
            for (int i = 0; i < colors.length; i++) {
                int c = colors[i];
                int r = (int) red.invokeExact(i, (c >> 16) & 0xFF);
                int g = (int) green.invokeExact(i, (c >> 8) & 0xFF);
                int b = (int) blue.invokeExact(i, c & 0xFF);
                colors[i] = (c & 0xFF000000) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
            if (!announced) {
                announced = true;
                LOGGER.info("[srpwizcore] Bloodmoon: blood moon active, red lightmap applied");
            }
        } catch (Throwable t) {
            disabled = true;
            LOGGER.error("[srpwizcore] Bloodmoon: lightmap hook failed, disabled for this session",
                    t);
        }
    }

    /** Bloodmoon keeps its results in 0..255; the clamp only guards the packing. */
    private static int clamp(int channel) {
        return channel < 0 ? 0 : (channel > 255 ? 255 : channel);
    }

    private static void resolve() {
        resolved = true;
        try {
            Class<?> handler = Class.forName(HANDLER_CLASS, true,
                    BloodmoonLightmap.class.getClassLoader());
            Object instance = handler.getField("INSTANCE").get(null);
            if (instance == null) {
                throw new IllegalStateException(HANDLER_CLASS + ".INSTANCE is null");
            }
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            active = lookup.findVirtual(handler, "isBloodmoonActive",
                    MethodType.methodType(boolean.class)).bindTo(instance);
            red = lookup.findVirtual(handler, "manipulateRed", CHANNEL).bindTo(instance);
            green = lookup.findVirtual(handler, "manipulateGreen", CHANNEL).bindTo(instance);
            blue = lookup.findVirtual(handler, "manipulateBlue", CHANNEL).bindTo(instance);
        } catch (Throwable t) {
            disabled = true;
            LOGGER.error("[srpwizcore] Bloodmoon: could not resolve {} - no red lightmap this "
                    + "session", HANDLER_CLASS, t);
        }
    }
}
