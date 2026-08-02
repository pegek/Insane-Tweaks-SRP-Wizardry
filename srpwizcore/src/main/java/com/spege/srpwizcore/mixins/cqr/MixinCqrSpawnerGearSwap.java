package com.spege.srpwizcore.mixins.cqr;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.srpwizcore.util.CqrGearSwapHandler;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Gear-swap hook on the CQR dungeon spawn path. {@code TileEntitySpawner.spawnEntityFromNBT}
 * (plain CQR method, unobfuscated) is where every dungeon inhabitant enters the world —
 * worldgen packs the entity NBT into the spawner, activation deserializes and spawns it,
 * passengers recurse through the same method (each gets its own RETURN). Injecting here
 * instead of listening to {@code EntityJoinWorldEvent} (the Groovy prototype) means zero
 * cost for every other entity in the game, and bosses are excluded by construction — they
 * spawn via the separate {@code TileEntityBoss}.
 *
 * <p>Logic and tables live in {@link CqrGearSwapHandler} (null-tolerant: the method returns
 * null for empty NBT). CQR is targeted by string — no compile dependency.
 */
@Mixin(targets = "team.cqr.cqrepoured.tileentity.TileEntitySpawner", remap = false)
public class MixinCqrSpawnerGearSwap {

    @Inject(
            method = "spawnEntityFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/entity/Entity;",
            at = @At("RETURN"))
    private void srpwizcore$swapDungeonGear(NBTTagCompound nbt,
            CallbackInfoReturnable<Entity> cir) {
        CqrGearSwapHandler.process(cir.getReturnValue());
    }
}
