package com.spege.insanetweaks.potions;

import net.minecraft.potion.Potion;

public class PotionImmuneBond extends Potion {

    public PotionImmuneBond() {
        super(false, 0xFFDD00); // false = beneficial, color = gold/yellow
        this.setPotionName("potion.insanetweaks.immune_bond");
    }

    @Override
    public boolean isInstant() {
        return false;
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        // We handle logic in the ImmuneBondHandler instead of here
        return false;
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
    
    @Override
    public boolean hasStatusIcon() {
        // Target is typically a mob, so no HUD rendering needed
        return false; 
    }
}
