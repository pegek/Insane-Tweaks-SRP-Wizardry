package com.spege.srpwizmixins.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.entity.projectile.EntityMeteor;
import com.spege.srpwizmixins.util.MeteorPlacement;

/**
 * Tells the player where the meteor landed.
 *
 * <p>SRP gives no notice at all. There is a screen shake within 400 blocks and a sound event,
 * {@code srparasites:meteor.impact} - which the mod registers but ships no {@code sounds.json} entry
 * for, so it is silent. An infestation anchor appears somewhere over the horizon and nothing says
 * where, which matters because everything the anchor governs - where parasites may spawn, how far
 * that reaches, how fast it grows - is invisible from the outside.
 *
 * <p>Injected at the head of {@code onImpact} rather than alongside the placement code, for three
 * reasons: the entity's position is by then the real impact point rather than the aiming point, the
 * message arrives when the outbreak actually exists rather than the several seconds of flight
 * earlier, and it works whether or not the placement guard is switched on.
 *
 * <p>Guarded on {@code getRoot()}. A meteor spawns non-root copies of itself as it flies - that is
 * the smoke trail - and each of those runs {@code onImpact} too. Only the root one creates an
 * anchor, so only the root one is worth announcing.
 *
 * <p>Gated on {@code srpCompat.meteorAnnounceInChat} and {@code srpCompat.meteorWaypoint}, both
 * checked inside {@link MeteorPlacement#announce}; with both off this is a virtual call and a
 * boolean test.
 */
@Mixin(value = EntityMeteor.class, remap = false)
public abstract class MixinSrpMeteorAnnounce {

    @Inject(method = "onImpact", at = @At("HEAD"), remap = false)
    private void srpwizmixins$announceImpact(CallbackInfo ci) {
        EntityMeteor self = (EntityMeteor) (Object) this;
        if (self.world.isRemote || !self.getRoot()) {
            return;
        }
        MeteorPlacement.announce(self.world, self.getPosition());
    }
}
