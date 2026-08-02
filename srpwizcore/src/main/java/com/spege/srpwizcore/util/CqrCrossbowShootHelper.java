package com.spege.srpwizcore.util;

import com.oblivioussp.spartanweaponry.entity.projectile.EntityBolt;
import com.oblivioussp.spartanweaponry.item.ItemBolt;
import com.oblivioussp.spartanweaponry.item.ItemCrossbow;
import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

/**
 * Mob-side crossbow shot for CQR's ranged AI. Spartan's own use-path
 * ({@code onItemUseFinish}/{@code onPlayerStoppedUsing}) is hard-gated on
 * {@code EntityPlayer} (player inventory, CooldownTracker, camera look vector), so
 * {@code MixinSwItemCrossbow} implements CQR's {@code IRangedWeapon} and delegates here.
 *
 * <p>Ballistics replicate CQR's own bow branch byte for byte (recon 2026-08-01,
 * {@code notes/cqr_spartan_mixin_research_2026-08-01.md}): aim at target chest height,
 * drop compensation {@code distSq * 0.0045} — which CQR tuned for arrow speed 2.4, so it is
 * rescaled by {@code (2.4/speed)^2} for the much faster bolt — difficulty-based inaccuracy
 * (1/2/4 for HARD/NORMAL/other), shooter motion added to the projectile. Bolt speed and
 * Power/Punch/Flame handling replicate Spartan's {@code spawnProjectile}. Ammo is a default
 * {@code spartanweaponry:bolt} and is not consumed — parity with CQR's bow branch, which
 * also fires without ammo. Verified mob-safe: every EntityPlayer cast in EntityBolt's
 * hit/update path is instanceof-gated.
 */
public final class CqrCrossbowShootHelper {

    public static long shots;

    private static Item defaultBolt;
    private static boolean boltResolved;

    private CqrCrossbowShootHelper() {
    }

    public static void shoot(World world, EntityLivingBase shooter, Entity target,
            ItemCrossbow crossbow) {
        try {
            if (world == null || world.isRemote || shooter == null || target == null) {
                return;
            }
            Item boltItem = resolveDefaultBolt();
            if (!(boltItem instanceof ItemBolt)) {
                return;
            }
            ItemStack ammo = new ItemStack(boltItem);
            EntityBolt bolt = ((ItemBolt) boltItem).createBolt(world, ammo, shooter);
            bolt.setIsCritical(true);
            bolt.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;

            ItemStack crossbowStack = shooter.getHeldItemMainhand();
            int power = EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER, crossbowStack);
            if (power > 0) {
                bolt.setDamage(bolt.getDamage() + power * 0.5D + 0.5D);
            }
            int punch = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH, crossbowStack);
            if (punch > 0) {
                bolt.setKnockbackStrength(punch);
            }
            if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAME, crossbowStack) > 0) {
                bolt.setFire(100);
            }

            double dx = target.posX - shooter.posX;
            double dy = target.posY + target.height * 0.5D - bolt.posY;
            double dz = target.posZ - shooter.posZ;
            double distSq = dx * dx + dz * dz;
            float speed = crossbow.getBoltSpeed() * 3.0F;
            if (speed <= 0.0F) {
                speed = 2.4F;
            }
            double drop = distSq * 0.0045D * (2.4D / speed) * (2.4D / speed);
            bolt.shoot(dx, dy + drop, dz, speed, inaccuracy(world));
            bolt.motionX += shooter.motionX;
            bolt.motionZ += shooter.motionZ;
            if (!shooter.onGround) {
                bolt.motionY += shooter.motionY;
            }
            world.spawnEntity(bolt);
            shots++;
            if (SrpWizCoreConfig.cqrIntegration.debugLogging || shots == 1L) {
                SrpWizCore.LOGGER.info(
                        "[srpwizcore] cqr crossbow shot #{}: {} -> {} (speed={}, dist={})",
                        Long.valueOf(shots), shooter.getName(), target.getName(),
                        Float.valueOf(speed), Integer.valueOf((int) Math.sqrt(distSq)));
            }
        } catch (Throwable t) {
            SrpWizCore.LOGGER.error("[srpwizcore] cqr crossbow shot failed: {}", t.toString());
        }
    }

    private static float inaccuracy(World world) {
        EnumDifficulty diff = world.getDifficulty();
        if (diff == EnumDifficulty.HARD) {
            return 1.0F;
        }
        if (diff == EnumDifficulty.NORMAL) {
            return 2.0F;
        }
        return 4.0F;
    }

    private static Item resolveDefaultBolt() {
        if (!boltResolved) {
            defaultBolt = Item.getByNameOrId("spartanweaponry:bolt");
            boltResolved = true;
        }
        return defaultBolt;
    }
}
