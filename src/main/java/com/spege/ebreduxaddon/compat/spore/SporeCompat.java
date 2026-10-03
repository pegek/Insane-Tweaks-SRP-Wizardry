package com.spege.ebreduxaddon.compat.spore;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * Bramka do zawartosci zaleznej od Spore. Ta klasa NIE moze nazywac zadnego typu Spore (ani klasy,
 * ktora go nazywa, w sygnaturze): JVM laduje ja zawsze. SporeContent i reszta pakietu ladowane sa
 * dopiero za ifem, wiec bez Spore nigdy.
 */
public final class SporeCompat {

    public static final String MODID = "spore";

    private SporeCompat() {
    }

    public static boolean present() {
        return ModList.get().isLoaded(MODID);
    }

    public static void init(IEventBus modBus) {
        if (present()) {
            SporeContent.register(modBus);
        }
    }
}
