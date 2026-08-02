package com.spege.srpwizcore.mixins.cqrspartan;

import org.spongepowered.asm.mixin.Mixin;

import com.oblivioussp.spartanweaponry.init.SoundRegistry;
import com.oblivioussp.spartanweaponry.item.ItemCrossbow;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.util.CqrCrossbowShootHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

import team.cqr.cqrepoured.item.IRangedWeapon;

/**
 * Makes Spartan Weaponry crossbows work in CQR mobs' hands by implementing CQR's
 * {@code IRangedWeapon} on {@code ItemCrossbow}. CQR's {@code EntityAIAttackRanged} (task 13,
 * present unconditionally in every {@code AbstractEntityCQR}) gates on
 * {@code item instanceof ItemBow || item instanceof IRangedWeapon} — with this interface
 * grafted on, the existing AI detects the crossbow, handles charge animation
 * ({@code getChargeTicks} &gt; 0 → {@code setActiveHand}), cooldown and range itself, and
 * calls {@link #shoot} for the actual projectile. Without it a crossbow-holding mob just
 * stands there: Spartan's own fire path is hard-gated on {@code EntityPlayer}.
 *
 * <p>{@code ItemCrossbow} is a leaf class (only anonymous inner classes), so the
 * no-interfaces-on-eagerly-loaded-hierarchies rule does not apply. Queued only when both
 * {@code cqrepoured} and {@code spartanweaponry} are loaded (see {@code SrpWizCoreLateBooter}).
 * No static fields — state and logic live in {@link CqrCrossbowShootHelper}.
 */
@Mixin(value = ItemCrossbow.class, remap = false)
public abstract class MixinSwItemCrossbow implements IRangedWeapon {

    @Override
    public void shoot(World world, EntityLivingBase shooter, Entity target, EnumHand hand) {
        CqrCrossbowShootHelper.shoot(world, shooter, target, (ItemCrossbow) (Object) this);
    }

    @Override
    public SoundEvent getShootSound() {
        // Played by CQR's AI right after shoot(); Spartan's own fire sound.
        return SoundRegistry.CROSSBOW_FIRE;
    }

    @Override
    public double getRange() {
        // Same as CQR's bow branch.
        return 32.0D;
    }

    @Override
    public int getCooldown() {
        return SrpWizCoreConfig.cqrIntegration.crossbowCooldown;
    }

    @Override
    public int getChargeTicks() {
        // HARD CAP (bugfix 1.8.1): an UNLOADED crossbow's getMaxItemUseDuration is its load
        // time — ConfigHandler.crossbowTicksToLoad (20, hardcoded) + harvestLevel*1. When the
        // use counter runs out, onItemUseFinish (a no-op for mobs) RESETS the hand, so CQR's
        // charge check (getItemInUseMaxCount() >= chargeTicks) can never pass for any value
        // >= the load time — the mob pumps the crossbow forever and never fires. Cap two
        // ticks under the base load time; the material modifier only ever makes it longer.
        int cap = com.oblivioussp.spartanweaponry.util.ConfigHandler.crossbowTicksToLoad - 2;
        int cfg = SrpWizCoreConfig.cqrIntegration.crossbowChargeTicks;
        return Math.max(0, Math.min(cfg, cap));
    }
}
