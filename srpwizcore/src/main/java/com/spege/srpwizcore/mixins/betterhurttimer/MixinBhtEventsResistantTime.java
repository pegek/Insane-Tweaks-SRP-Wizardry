package com.spege.srpwizcore.mixins.betterhurttimer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Stops WorseHurtTimer reading a <em>player's</em> {@code maxHurtResistantTime}, replacing it
 * with a fixed base from the config.
 *
 * <p>Without this, the Cross Necklace would be counted twice: Bountiful Baubles writes 36 to the
 * field (on equip, and again on every incoming attack from its own
 * {@code EventHandler.onDamage}), so the bare-handed-attacker branch of
 * {@code Events.getHurtTime} would produce 36 x 1.8 = 64 ticks while every other path produced
 * 20 x 1.8 = 36.
 *
 * <p><b>Players only.</b> A scan of all 269 jars in the instance found exactly two writers of
 * this field: Bountiful Baubles, and BabyMobs — whose {@code EntityBabyWitherSkeleton} sets its
 * own field to 50 in its constructor, buying that mob roughly 48 ticks of melee cooldown as a
 * victim instead of 19. A blanket override would silently nerf it. Bountiful Baubles is the only
 * writer that touches a player's field, so restricting the override to players removes the
 * double count and leaves every mob's self-declared value intact.
 *
 * <p>The eleven mods that only <em>read</em> the field — SoManyEnchantments most of all, with
 * eleven reads across eight enchantments — are untouched. This mixin changes what WorseHurtTimer
 * consumes, not what the field holds.
 */
@Mixin(targets = "arekkuusu.betterhurttimer.common.Events", remap = false)
public class MixinBhtEventsResistantTime {

    @Inject(
            method = "getHurtResistantTime(Lnet/minecraft/entity/Entity;)D",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void srpwizcore$deterministicPlayerBase(Entity entity,
            CallbackInfoReturnable<Double> cir) {
        if (!SrpWizCoreConfig.whtCompat.enabled) {
            return;
        }
        if (!(entity instanceof EntityPlayer)) {
            return;
        }
        cir.setReturnValue(Double.valueOf(SrpWizCoreConfig.whtCompat.baseIFrameTicks));
    }
}
