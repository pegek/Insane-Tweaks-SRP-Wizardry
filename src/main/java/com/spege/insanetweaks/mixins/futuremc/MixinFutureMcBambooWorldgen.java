package com.spege.insanetweaks.mixins.futuremc;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Guards FutureMC's bamboo worldgen against an OTG-populate race crash.
 *
 * <p>{@code BambooWorldGen.generate(World,Random,BlockPos)} places a bamboo block, then
 * calls {@code BlockBamboo.grow} (func_176474_b). grow() reads
 * {@code world.getBlockState(pos.up(numOfAboveBamboo)).getValue(MATURE)} assuming the block
 * above the stalk is bamboo, but during OTG chunk population it can already be air, throwing
 * {@code IllegalArgumentException: Cannot get property PropertyBool{mature} ...
 * block=minecraft:air} and crashing chunk gen (crash-2026-07-21_06.26.53). Note: the crash
 * is inside grow() on a re-fetched state, NOT on grow()'s state parameter (generate() proved
 * to always pass a live bamboo state), so guarding the parameter would not help - we wrap the
 * whole call. String-targeted (FutureMC is not a compile dependency); the redirected receiver
 * is typed as vanilla {@link Block} because Mixin's @Redirect verifier requires the exact
 * declared receiver type of the target invoke (BlockBamboo extends Block; a plain
 * {@code IGrowable} receiver was rejected at apply time with "unexpected argument type
 * net.minecraft.block.IGrowable ... expected ...BlockBamboo" - confirmed live 2026-07-23), so
 * we cast to {@link IGrowable} only for the {@code grow()} call itself. Gated on
 * {@code futureMcCompat.guardBambooWorldgenRace}.
 */
@Mixin(targets = "thedarkcolour.futuremc.world.gen.feature.BambooWorldGen", remap = false)
public abstract class MixinFutureMcBambooWorldgen {

    @Redirect(
            method = "generate(Lnet/minecraft/world/World;Ljava/util/Random;"
                    + "Lnet/minecraft/util/math/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lthedarkcolour/futuremc/block/villagepillage/BlockBamboo;"
                            + "func_176474_b(Lnet/minecraft/world/World;Ljava/util/Random;"
                            + "Lnet/minecraft/util/math/BlockPos;"
                            + "Lnet/minecraft/block/state/IBlockState;)V"),
            remap = false)
    private void insanetweaks$safeBambooGrow(Block self, World world, Random rand,
            BlockPos pos, IBlockState state) {
        IGrowable growable = (IGrowable) self;
        if (!ModConfig.futureMcCompat.guardBambooWorldgenRace) {
            growable.grow(world, rand, pos, state);
            return;
        }
        try {
            growable.grow(world, rand, pos, state);
        } catch (IllegalArgumentException e) {
            // OTG-populate worldgen race: block above the stalk was air by the time grow()
            // read getValue(MATURE). Skip this one bamboo instead of crashing chunk gen.
        }
    }
}
