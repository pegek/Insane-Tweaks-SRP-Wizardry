package com.spege.insanetweaks.mixins.futuremc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

/**
 * Guards FutureMC's bamboo worldgen against an OTG-populate race crash.
 *
 * <p>{@code BlockBamboo.grow} (func_176474_b) reads
 * {@code world.getBlockState(pos.up(numOfAboveBamboo)).getValue(MATURE)} assuming the block
 * above the stalk is bamboo. During OTG chunk population that block can already be air, whose
 * state has no MATURE property, so {@code getValue} throws
 * {@code IllegalArgumentException: Cannot get property PropertyBool{mature} ...
 * block=minecraft:air} and crashes chunk gen (crash-2026-07-21_06.26.53; dim 150 jungle-wrapper
 * biomes StormSpires/GloomJungle).
 *
 * <p><b>Why this shape:</b> the earlier design redirected the {@code grow()} call inside
 * {@code BambooWorldGen.generate} and wrapped it in try/catch, but sponge-mixin 0.8.7 requires a
 * {@code @Redirect} receiver to be the <i>exact</i> declared owner of the target invoke
 * ({@code BlockBamboo}), and both {@code IGrowable} and {@code Block} receivers were rejected at
 * apply time (InvalidInjectionException, confirmed live 2026-07-23). Since {@code BlockBamboo} is
 * not a compile dependency, we instead redirect the crash site itself: the
 * {@code IBlockState.getValue} call in {@code func_176474_b}. Its receiver is the vanilla
 * {@link IBlockState}, which satisfies the exact-owner rule with no foreign types. When the state
 * lacks the queried property (the racy air block), we return {@code Boolean.TRUE} so grow() treats
 * the stalk as mature and returns instead of crashing; otherwise the real value is passed through,
 * so normal bamboo growth is unchanged. Gated on {@code futureMcCompat.guardBambooWorldgenRace}.
 */
@Mixin(targets = "thedarkcolour.futuremc.block.villagepillage.BlockBamboo", remap = false)
public abstract class MixinFutureMcBambooWorldgen {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Redirect(
            method = "func_176474_b(Lnet/minecraft/world/World;Ljava/util/Random;"
                    + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/state/IBlockState;"
                            + "func_177229_b(Lnet/minecraft/block/properties/IProperty;)"
                            + "Ljava/lang/Comparable;"),
            remap = false)
    private Comparable insanetweaks$safeMature(IBlockState state, IProperty property) {
        if (ModConfig.futureMcCompat.guardBambooWorldgenRace
                && !state.getPropertyKeys().contains(property)) {
            // OTG-populate race: the block above the stalk was replaced with air, whose state has
            // no MATURE property. Return TRUE so grow() treats the stalk as mature and returns,
            // instead of crashing on getValue(MATURE). See class javadoc.
            return Boolean.TRUE;
        }
        return state.getValue(property);
    }
}
