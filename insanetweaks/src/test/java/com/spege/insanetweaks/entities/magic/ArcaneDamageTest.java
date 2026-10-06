package com.spege.insanetweaks.entities.magic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArcaneDamageTest {

    @Test
    public void plainDamageIsUntouched() {
        assertEquals(10.0F, ArcaneDamage.scale(10.0F, false, false, 0.75D, 1.25D), 1.0E-6F);
    }

    @Test
    public void magicDamageIsReduced() {
        assertEquals(7.5F, ArcaneDamage.scale(10.0F, true, false, 0.75D, 1.25D), 1.0E-6F);
    }

    @Test
    public void fireDamageIsIncreased() {
        assertEquals(12.5F, ArcaneDamage.scale(10.0F, false, true, 0.75D, 1.25D), 1.0E-6F);
    }

    @Test
    public void magicFireAppliesBothMultipliers() {
        assertEquals(9.375F, ArcaneDamage.scale(10.0F, true, true, 0.75D, 1.25D), 1.0E-6F);
    }
}
