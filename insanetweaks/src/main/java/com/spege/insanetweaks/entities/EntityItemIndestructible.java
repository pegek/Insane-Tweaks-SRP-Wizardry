package com.spege.insanetweaks.entities;

import javax.annotation.Nonnull;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Hardened item entity for legendary Living/Sentient gear drops.
 *
 * This entity explicitly ignores the environmental damage sources that still
 * proved unreliable on vanilla EntityItem in practice.
 */
public class EntityItemIndestructible extends EntityItem {

    public EntityItemIndestructible(World world) {
        super(world);
        this.isImmuneToFire = true;
    }

    public EntityItemIndestructible(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.isImmuneToFire = true;
    }

    public EntityItemIndestructible(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack);
        this.isImmuneToFire = true;
    }

    @Override
    public boolean isImmuneToExplosions() {
        return true;
    }

    @Override
    @SuppressWarnings("null")
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        if (source == DamageSource.IN_FIRE
                || source == DamageSource.ON_FIRE
                || source == DamageSource.LAVA
                || source == DamageSource.CACTUS
                || source.isExplosion()) {
            return false;
        }

        return super.attackEntityFrom(source, amount);
    }
}
