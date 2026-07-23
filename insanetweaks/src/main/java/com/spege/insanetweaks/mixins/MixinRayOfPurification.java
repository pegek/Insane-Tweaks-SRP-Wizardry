package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.RayBeckonPurge;
import com.spege.insanetweaks.util.SrpPurificationHelper;

import electroblob.wizardry.spell.RayOfPurification;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Ray of Purification vs SRP Beckons (spec 2).
 *
 * <p>EBW's own behaviour (RADIANT damage every 10 use-ticks + blindness) is left completely intact.
 * This injects at HEAD (non-cancellable) to ADD one unavoidable absolute-damage application the
 * FIRST time each Beckon is struck during a single continuous cast — see {@link RayBeckonPurge} for
 * the once-per-cast tracking and the SRP damage-cap bypass.
 *
 * <p>{@code remap = false}: {@code onEntityHit} is EBW's own (fg.deobf) method name, not an
 * SRG-mapped MC method — same convention as {@code MixinSpell} / {@code MixinSpellMinion}. The
 * name-only match resolves uniquely under {@code defaultRequire = 1} in late.json.
 */
@Mixin(value = RayOfPurification.class, remap = false)
public abstract class MixinRayOfPurification {

    @Inject(method = "onEntityHit", at = @At("HEAD"), remap = false)
    private void insanetweaks$purgeBeckonOnFirstHit(World world, Entity target, Vec3d hit,
            EntityLivingBase caster, Vec3d origin, int ticksInUse, SpellModifiers modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        // Module gate (spec 6): behave exactly like vanilla EBW when the spell bridge is off.
        if (!ModConfig.modules.enableSpells) {
            return;
        }
        // Server-authoritative only; a caster is required for the damage source + kill credit.
        if (world.isRemote || caster == null) {
            return;
        }
        if (!(target instanceof EntityLivingBase) || !SrpPurificationHelper.isBeckon(target)) {
            return;
        }
        // First hit on THIS Beckon during THIS cast?
        if (!RayBeckonPurge.shouldProc(caster, target, ticksInUse)) {
            return;
        }
        float potency = modifiers.get(SpellModifiers.POTENCY);
        RayBeckonPurge.applyPurge(world, caster, (EntityLivingBase) target, potency);
    }
}
