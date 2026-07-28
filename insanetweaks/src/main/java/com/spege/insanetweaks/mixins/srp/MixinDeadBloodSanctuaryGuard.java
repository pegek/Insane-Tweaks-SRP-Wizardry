package com.spege.insanetweaks.mixins.srp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.block.BlockFluid;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;

/**
 * Sanctuary Dome - make SRP's dead blood ({@code srparasites:deadblood}) inert inside a protected
 * region.
 *
 * <p>Worth doing even though {@code SanctuaryCleanseHelper.drainDeadBlood} removes the fluid
 * outright, because the fluid's damage is otherwise unanswerable. Verified with
 * {@code javap -p -c} (2026-07-28): {@code BlockFluid.attackEntityAsMobMinimum} never calls
 * {@code attackEntityFrom}. It writes {@code func_70606_j} (setHealth) directly after a
 * {@code CombatTracker.trackDamage} bookkeeping call, so armour, {@code hurtResistantTime},
 * {@code isEntityInvulnerable}, {@code LivingAttackEvent} and {@code LivingHurtEvent} are all
 * bypassed. Nothing in the pack can reduce it, and the drain needs a few passes on a big pool.
 *
 * <h3>Why three redirects instead of one HEAD cancel</h3>
 * The hook is <b>not</b> {@code onEntityWalk} - {@code BlockFluid} does not override
 * {@code func_176216_a} at all. Everything lives in
 * {@code func_180634_a} = {@code Block.onEntityCollidedWithBlock(World, BlockPos, IBlockState,
 * Entity)}, which does four separable things (bytecode offsets from the same javap run):
 * <ol>
 *   <li>{@code 9-26} parasite in the fluid -&gt; {@code heal(1.0F)}, then returns</li>
 *   <li>{@code 27-79} movement drag ({@code motionX/Z *= 0.85}, {@code motionY *= 0.92} when
 *       falling) and {@code fallDistance = 0}</li>
 *   <li>{@code 82-108} {@code attackEntityAsMobMinimum(target, 0.1F)}, server side only</li>
 *   <li>{@code 126-239} {@code SRPPotions.CORRO_E} (100t) and {@code VIRA_E} (200t, amp 1)</li>
 * </ol>
 * Cancelling at HEAD would take (2) with it. Losing {@code fallDistance = 0} means a player who
 * drops into dead blood inside their own sanctuary takes the full fall damage the fluid was
 * breaking - the dome would actively hurt them. So (1), (3) and (4) are redirected away
 * individually and the physics is left exactly as SRP wrote it, identical on both sides.
 *
 * <p>Position comes from the entity rather than the injected {@code BlockPos}: the sanctuary test
 * is a 2-D cylinder, so being within one block of the fluid cannot change the answer, and this
 * keeps all three handlers to a single shared shape.
 *
 * <p>Gated on {@code sanctuary.neutralizeDeadBlood}, which is <b>read live</b> - this config has no
 * {@code IMixinConfigPlugin}, so the flag gates the handler bodies, never whether the mixin applies.
 */
@Mixin(value = BlockFluid.class, remap = false)
public abstract class MixinDeadBloodSanctuaryGuard {

    /** Shared gate: is this entity standing in dead blood inside an active sanctuary? */
    private static boolean insanetweaks$shielded(EntityLivingBase target) {
        return target != null
                && ModConfig.sanctuary.neutralizeDeadBlood
                && SanctuaryRegionHelper.isProtected(target.world, target.getPosition());
    }

    /**
     * Offset 23 - dead blood heals any {@code EntityParasiteBase} standing in it. Inside the dome
     * that directly fights Purge Fire, so a parasite could camp a pool and out-heal the burn.
     */
    @Redirect(method = "func_180634_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;func_70691_i(F)V"),
            remap = false)
    private void insanetweaks$noParasiteHealInSanctuary(EntityLivingBase target, float amount) {
        if (!insanetweaks$shielded(target)) {
            target.heal(amount);
        }
    }

    /** Offset 105 - the unmitigable direct-setHealth damage described above. */
    @Redirect(method = "func_180634_a",
            at = @At(value = "INVOKE",
                    target = "Lcom/dhanantry/scapeandrunparasites/block/BlockFluid;"
                            + "attackEntityAsMobMinimum(Lnet/minecraft/entity/EntityLivingBase;F)Z"),
            remap = false)
    private boolean insanetweaks$noDeadBloodDamageInSanctuary(BlockFluid self, EntityLivingBase target,
            float amount) {
        if (insanetweaks$shielded(target)) {
            return false;
        }
        return self.attackEntityAsMobMinimum(target, amount);
    }

    /**
     * Offsets 192 and 236 - Corrosive and Viral. The redirect binds to <b>both</b> on purpose; they
     * are the same affliction pair and there is no reason to shield one and not the other.
     */
    @Redirect(method = "func_180634_a",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;"
                            + "func_70690_d(Lnet/minecraft/potion/PotionEffect;)V"),
            remap = false)
    private void insanetweaks$noDeadBloodEffectsInSanctuary(EntityLivingBase target, PotionEffect effect) {
        if (!insanetweaks$shielded(target)) {
            target.addPotionEffect(effect);
        }
    }
}
