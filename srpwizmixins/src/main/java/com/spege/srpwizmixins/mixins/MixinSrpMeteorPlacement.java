package com.spege.srpwizmixins.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.util.handlers.SRPEventHandlerBus;
import com.spege.srpwizmixins.util.MeteorPlacement;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Fix - the parasite meteor is aimed at a player and cannot be moved out of range of their base.
 *
 * <p>{@code SRPEventHandlerBus.spawningMet} runs once the five meteor gates have been passed and
 * picks its impact point like this:
 *
 * <pre>
 * for (EntityPlayer p : world.playerEntities)
 *     if (world.canSeeSky(p.getPosition())) {                 // first player under open sky
 *         ParasiteSummon.spawnMeteor(p.getPosition(),
 *                 rand.nextInt(SRPConfigWorld.meteorRadius),
 *                 SRPConfigWorld.meteorMinRadius, world);
 *         break;
 *     }
 * // nobody under sky: the same call for the first player in the list
 * </pre>
 *
 * <p>So the centre is always a player, and the offset is bounded by two config values that Forge
 * clamps to 120 and 110 - a maximum of 229 blocks, on each horizontal axis independently. The crash
 * carves terrain, spawns parasites, damages everything within {@code Meteor Damage} blocks and
 * plants an infestation anchor. Landing that on someone's base is entirely normal behaviour.
 *
 * <p>Raising the numbers does not help. {@code ParasiteSummon.spawnMeteor(BlockPos, int, int,
 * World)} passes them to a variant that clamps them again against the very same config fields
 * before use, so anything we hand it is brought back inside SRP's range.
 *
 * <p>We therefore {@link Redirect} the call rather than adjusting its arguments, and
 * {@link MeteorPlacement#place} picks the position itself and spawns the meteor through the
 * six-integer variant, which takes the impact point outright and clamps nothing. With the fix
 * switched off it forwards to the original call and SRP behaves exactly as before.
 *
 * <p>{@code require = 2} because there are two call sites and they are the two halves of the same
 * decision - the under-sky one and the fallback. Injecting into only one would leave a meteor that
 * behaves itself most of the time and does not the rest, which is worse than not applying at all.
 */
@Mixin(value = SRPEventHandlerBus.class, remap = false)
public abstract class MixinSrpMeteorPlacement {

    @Redirect(
            method = "spawningMet",
            at = @At(value = "INVOKE",
                    target = "Lcom/dhanantry/scapeandrunparasites/util/spawn/ParasiteSummon;"
                            + "spawnMeteor(Lnet/minecraft/util/math/BlockPos;IILnet/minecraft/world/World;)V"),
            require = 2,
            remap = false)
    private void srpwizmixins$guardMeteorPlacement(BlockPos center, int radiusRand, int minRadius,
            World world) {
        MeteorPlacement.place(center, radiusRand, minRadius, world);
    }
}
