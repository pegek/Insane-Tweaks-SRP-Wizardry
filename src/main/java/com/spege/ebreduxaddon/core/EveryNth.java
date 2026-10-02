package com.spege.ebreduxaddon.core;

/**
 * Licznik serii: co n-ty krok wyzwala. Stan to jedna liczba (trzymana w NBT stacka).
 *
 * @param counter stan po tym kroku, do zapisania
 * @param fires   czy ten krok wyzwala
 */
public record EveryNth(int counter, boolean fires) {

    /** n &lt;= 0 wylacza wyzwalanie; licznik wraca wtedy do 0. Popsuty stan (ujemny) tez. */
    public static EveryNth step(int counter, int n) {
        if (n <= 0) {
            return new EveryNth(0, false);
        }
        int next = (counter < 0 || counter >= n ? 0 : counter) + 1;
        return next >= n ? new EveryNth(0, true) : new EveryNth(next, false);
    }
}
