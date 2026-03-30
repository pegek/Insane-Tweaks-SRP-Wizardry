package com.spege.insanetweaks.entities;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * Ported from Enigmatic Legacy (originally based on Thaumic Augmentation).
 * Makes the item indestructible when dropped by being immune to fire, lava, and explosions.
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
    @SuppressWarnings("null")
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        if (source == DamageSource.IN_FIRE ||
            source == DamageSource.ON_FIRE ||
            source == DamageSource.LAVA    ||
            source == DamageSource.CACTUS  ||
            source.isExplosion()) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }
}
