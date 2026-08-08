package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.loot.RandomSpell;

/**
 * Keeps Abomination out of the {@code random_spell} loot function's default element pool.
 *
 * <p>When a loot entry names no element, {@code pickRandomSpell} falls back to
 * {@code Arrays.asList(Element.values())} and then filters spells by tier and element.
 *
 * <p>🚨 The candidate set is emphatically <b>not</b> empty. Twelve of this mod's fourteen spells
 * ship
 * {@code "treasure"}, {@code "trades"} and {@code "looting"} all {@code true} - only
 * {@code call_of_demise} and the disabled {@code test_projectile} close them. Without this redirect
 * a registered Abomination element becomes a legitimate loot theme, and those twelve spells - most
 * of them master-tier minion summons with no other gating - surface in vanilla dungeon chests and
 * mob drops.
 */
@Mixin(value = RandomSpell.class, remap = false)
public abstract class MixinRandomSpellElements {

    @Redirect(method = "pickRandomSpell",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowLootSpellElements() {
        return NativeElements.values();
    }
}
