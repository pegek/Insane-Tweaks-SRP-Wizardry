package com.spege.manacore.mixins.tab;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.manacore.compat.tab.TabManaAccess;

import net.minecraft.entity.EntityLivingBase;
import xzeroair.trinkets.capabilities.CapabilityEntityBase;
import xzeroair.trinkets.capabilities.magic.MagicStats;

/**
 * Thin dispatcher onto {@link TabManaAccess}. Every method here does the same three things: read
 * the capability's owner, ask {@link TabManaAccess#handles} whether ManaCore should answer for
 * this player, and if so cancel the original call with our own result. All the actual policy -
 * unit conversion, which of our API methods to call - lives in {@link TabManaAccess}, not here,
 * so that a future Trinkets and Baubles update only ever costs an edit to that one file.
 *
 * <p>{@code @Inject}, not {@code @Overwrite}: with {@code manacore.tab.enabled} off, or for a
 * non-player owner (TaB's magic capability is not player-exclusive), {@link TabManaAccess#handles}
 * returns {@code false}, nothing is cancelled, and TaB's original implementation runs completely
 * untouched.
 */
@Mixin(value = MagicStats.class, remap = false)
public abstract class MixinMagicStats {

    /**
     * {@code getEntity()} is declared as {@code CapabilityEntityBase<T, E>.getEntity(): E}, where
     * {@code MagicStats} binds {@code E} to {@link EntityLivingBase}. A wildcard-bounded cast on
     * {@code (Object) this} captures that bound cleanly, so the call below returns
     * {@code EntityLivingBase} directly - no further cast needed, no unchecked-cast warning.
     */
    private EntityLivingBase manacore$owner() {
        return ((CapabilityEntityBase<?, ? extends EntityLivingBase>) (Object) this).getEntity();
    }

    @Inject(method = "getMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$getMana(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            cir.setReturnValue(Float.valueOf(TabManaAccess.getMana(owner)));
        }
    }

    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$setMana(float value, CallbackInfo ci) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            TabManaAccess.setMana(owner, value);
            ci.cancel();
        }
    }

    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$addMana(float value, CallbackInfo ci) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            TabManaAccess.addMana(owner, value);
            ci.cancel();
        }
    }

    @Inject(method = "spendMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$spendMana(float value, CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            cir.setReturnValue(Boolean.valueOf(TabManaAccess.spendMana(owner, value)));
        }
    }

    @Inject(method = "getMaxMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$getMaxMana(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            cir.setReturnValue(Float.valueOf(TabManaAccess.getMaxMana(owner)));
        }
    }

    @Inject(method = "refillMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$refillMana(CallbackInfo ci) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            TabManaAccess.refillMana(owner);
            ci.cancel();
        }
    }

    @Inject(method = "needMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$needMana(CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            cir.setReturnValue(Boolean.valueOf(TabManaAccess.needMana(owner)));
        }
    }

    /**
     * Stops Trinkets and Baubles from running its own per-tick mana upkeep once we own the pool.
     *
     * <p>This one is not about presentation, it fixes a real double-count. TaB's {@code onUpdate}
     * regenerates mana by calling {@code addMana(...)}, and every one of those calls is redirected
     * by the injections above into <em>our</em> pool - so leaving it running would give the player
     * two regeneration sources stacked on top of each other: ManaCore's own tick handler plus this
     * one, both filling the same pool. It also calls {@code refillMana} and {@code setMana}, which
     * land in our pool for the same reason.
     *
     * <p>Cancelling the whole method is safe because this capability is TaB's mana subsystem and
     * nothing else: once ManaCore is the source of truth, its update loop has no work left that is
     * still meaningful. TaB's own mana HUD is switched off through TaB's config rather than here.
     */
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$onUpdate(CallbackInfo ci) {
        if (TabManaAccess.handles(manacore$owner())) {
            ci.cancel();
        }
    }
}
