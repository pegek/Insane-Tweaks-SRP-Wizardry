package com.spege.insanetweaks.items.nunchaku;

/**
 * Wąska furtka pozwalająca NASZEJ broni nałożyć na pasożyta efekt, na który jest odporny.
 *
 * <p><b>Po co to w ogóle istnieje.</b> {@code EntityParasiteBase.isPotionApplicable} zwraca
 * {@code false} dla dokładnie czterech efektów: {@code COTH_E}, {@code VIRA_E}, {@code CORRO_E}
 * i {@code DLER_E} (zweryfikowane w bajtkodzie SRParasites 1.10.7). Dwa z nich — Viral i Needler
 * — to cała wartość pasożytniczego nunchaku, więc bez tej furtki broń wykuta z pasożytów nie
 * robi pasożytom nic.
 *
 * <p><b>Dlaczego flaga, a nie mixin zdejmujący odporność na stałe.</b> Ta blacklista prawie na
 * pewno istnieje po to, żeby pasożyty nie zarażały same siebie własnymi atakami obszarowymi.
 * Zdjęcie jej globalnie zmieniłoby zachowanie całego SRParasites. Flaga podnosi się WYŁĄCZNIE
 * na czas jednego naszego {@code addPotionEffect}, więc każde inne źródło efektu — w tym sam
 * SRP — dalej napotyka nietkniętą odporność.
 *
 * <p><b>Dlaczego ThreadLocal.</b> Zwykłe pole {@code static boolean} wystarczyłoby dziś (nasz kod
 * chodzi tylko na wątku serwera, za guardem {@code !world.isRemote}), ale zostawiona podniesiona
 * flaga przeciekłaby na inne wątki i cicho zdjęła odporność komuś innemu. ThreadLocal czyni ten
 * błąd niemożliwym, a koszt jest bez znaczenia przy wywołaniu rzędu raz na sekundę na cel.
 *
 * <p>🚨 Podnoś WYŁĄCZNIE w bloku {@code try/finally}. Wyjątek z {@code addPotionEffect} przy
 * podniesionej fladze zostawiłby ją podniesioną na stałe dla tego wątku.
 */
public final class ParasiteEffectBypass {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private ParasiteEffectBypass() {
    }

    /** Czy TEN wątek jest właśnie w środku naszego nakładania efektu. Czytane przez mixin. */
    public static boolean isActive() {
        return ACTIVE.get().booleanValue();
    }

    public static void begin() {
        ACTIVE.set(Boolean.TRUE);
    }

    public static void end() {
        // remove(), nie set(FALSE): nie zostawiamy wpisu w mapie ThreadLocal wątku.
        ACTIVE.remove();
    }
}
