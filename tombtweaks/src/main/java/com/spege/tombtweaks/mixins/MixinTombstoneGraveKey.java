package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Jailer: how much each level improves the odds of a grave key being re-enchanted on death.
 *
 * <p>Tombstone builds {@code chance = chanceEnchantedGraveKey + perkLevel * 20} and rolls 1..100
 * against it, short-circuiting to a guaranteed success at 100 or more — which is why a maxed Jailer
 * natively never fails.
 *
 * <p>The {@code bipush 20} is the only int 20 in {@code reenchantOnDeath} on 4.7.6; the two 100s
 * around it are the roll bounds and are deliberately left alone.
 */
@Mixin(targets = "ovh.corail.tombstone.item.ItemGraveKey", remap = false)
public abstract class MixinTombstoneGraveKey {

    @ModifyConstant(method = "reenchantOnDeath", constant = @Constant(intValue = 20), remap = false)
    private int tombtweaks$jailerChancePerLevel(int original) {
        return PerkTuningHelper.jailerKeyChancePerLevel(original);
    }
}
