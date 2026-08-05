package com.spege.srpwizcore.mixins.iceandfire;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.github.alexthe666.iceandfire.entity.projectile.EntityDragonArrow;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

/**
 * Ice and Fire gives the lightning dragon ARROW a 4.0 bonus against dragons, while the matching
 * bolt (Spartan Fire's {@code EntityDragonBolt}) and the melee weapon property both use 6.75.
 * This looks like a typo upstream rather than intent, so the arrow is raised to agree.
 *
 * <p>The mixin always applies — the config flag is read live inside the handler and hands back
 * Ice and Fire's own value when off. Touches nothing but bonus damage dealt to fire and ice dragons.
 *
 * <p>{@code arrowHit} is an override of the vanilla {@code EntityArrow} method, hence the dual
 * dev/SRG selector, the same shape {@link MixinIandfWorldGenMausoleum} uses for {@code generate}.
 *
 * <p>Target verified with {@code javap -p -c} on {@code Ice and Fire-2.2.9.jar} (2026-08-05):
 * {@code func_184548_a} holds exactly one {@code 4.0F} constant — and it is the only one in the
 * whole class — at offset 183, feeding {@code EntityLivingBase.attackEntityFrom} on the
 * {@code EntityFireDragon || EntityIceDragon} branch with {@code DamageSource.LIGHTNING_BOLT}.
 *
 * <p>🚨 On an Ice and Fire update, re-verify with {@code javap} that {@code arrowHit} still
 * contains exactly one {@code 4.0F} constant, or this injection will fail at load.
 */
@Mixin(value = EntityDragonArrow.class, remap = false)
public class MixinIandfDragonArrowLightning {

    @ModifyConstant(method = { "arrowHit", "func_184548_a" },
            constant = @Constant(floatValue = 4.0F), remap = false)
    private float srpwizcore$alignLightningBonus(float original) {
        return SrpWizCoreConfig.dragonRanged.alignLightningArrowBonus ? 6.75F : original;
    }
}
