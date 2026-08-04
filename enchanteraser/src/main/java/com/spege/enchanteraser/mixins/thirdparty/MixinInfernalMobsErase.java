package com.spege.enchanteraser.mixins.thirdparty;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.enchanteraser.util.EraserState;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.util.math.MathHelper;

/**
 * Third-party source: Infernal Mobs elite drops.
 *
 * <p>{@code InfernalMobsCore.getRandomEnchantment(Random)} lazily builds a private
 * {@code ArrayList<Enchantment>} straight off {@code Enchantment.REGISTRY.iterator()} the first time it
 * is called, then indexes it with its own RNG. It consults neither {@code isTreasureEnchantment()} nor
 * {@code canApplyAtEnchantingTable()} and never touches {@code EnchantmentHelper}, so none of the four
 * vanilla choke points see it. That list feeds {@code dropRandomEnchantedItems}, i.e. the gear an
 * infernal mob drops on death.
 *
 * <p>Filtering the iterator at build time would be the natural anchor, but InsaneTweaks already
 * redirects it for its quest gate and redirects are exclusive. Injecting at RETURN instead is both
 * collision-free and strictly more robust: the pool is cached for the whole session, so a build-time
 * filter cannot see a config change made after the first elite has died, whereas this post-filter
 * re-checks every single pick.
 *
 * <p>🚨 Never hand back {@code null} — Infernal Mobs dereferences the result immediately. When the
 * registry somehow offers no replacement (only reachable if literally every enchantment is erased) the
 * original is kept: one unwanted enchantment on one drop is a far better failure mode than an NPE in
 * the middle of a death.
 *
 * <p>Gated on {@code infernalmobs} — lowercase, per the {@code @Mod} annotation, not the mcmod.info —
 * by {@code EnchantEraserLateBooter}.
 */
@Mixin(targets = "atomicstryker.infernalmobs.common.InfernalMobsCore", remap = false)
public class MixinInfernalMobsErase {

    @Inject(method = "getRandomEnchantment", at = @At("RETURN"), cancellable = true, remap = false)
    private void enchanteraser$replaceErasedDrop(Random random,
            CallbackInfoReturnable<EnchantmentData> cir) {
        if (EraserState.isEmpty()) {
            return;
        }
        EnchantmentData rolled = cir.getReturnValue();
        if (rolled == null || !EraserState.isDisabled(rolled.enchantment)) {
            return;
        }
        EraserState.logOnce(rolled.enchantment, "Infernal Mobs drop");
        Enchantment replacement = EraserState.pickReplacement(random);
        if (replacement == null) {
            return;
        }
        int level = MathHelper.getInt(random, replacement.getMinLevel(), replacement.getMaxLevel());
        cir.setReturnValue(new EnchantmentData(replacement, level));
    }
}
