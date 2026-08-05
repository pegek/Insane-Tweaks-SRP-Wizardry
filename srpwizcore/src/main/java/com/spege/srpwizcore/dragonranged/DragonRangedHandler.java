package com.spege.srpwizcore.dragonranged;

import com.github.alexthe666.iceandfire.entity.projectile.EntityDragonArrow;
import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Attaches a dragon element to every projectile fired from an elemental dragon weapon, and
 * applies it on impact.
 *
 * <p>The element is decided when the projectile spawns rather than when it lands, which buys two
 * things: the shooter may swap the item in hand mid-flight, and mobs firing such a weapon (CQR
 * dungeon archers) are covered by exactly the same code as the player.
 */
public class DragonRangedHandler {

    /** Persisted on the projectile's Forge NBT. 0 / absent = no element. */
    private static final String TAG = "srpwizcore_dragon_elem";

    /**
     * Spartan Fire's bolt entity, resolved by name: srpwizcore has no compile dependency on
     * Spartan Fire, and adding one would drag in its Spartan Weaponry supertype chain for what
     * amounts to a single instanceof.
     */
    private static final Class<?> DRAGON_BOLT =
            resolveClass("com.chaosbuffalo.spartanfire.entity.EntityDragonBolt");

    private static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityArrow)) {
            return;
        }
        // Ice and Fire and Spartan Fire already put an element on their own projectiles; tagging
        // one again would apply the effect twice.
        if (entity instanceof EntityDragonArrow) {
            return;
        }
        if (DRAGON_BOLT != null && DRAGON_BOLT.isInstance(entity)) {
            return;
        }
        Entity shooter = ((EntityArrow) entity).shootingEntity;
        if (!(shooter instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase living = (EntityLivingBase) shooter;
        DragonElement element = DragonWeaponRegistry.forStack(living.getHeldItemMainhand());
        if (element == null) {
            element = DragonWeaponRegistry.forStack(living.getHeldItemOffhand());
        }
        if (element == null) {
            return;
        }
        entity.getEntityData().setByte(TAG, element.id());
        if (SrpWizCoreConfig.dragonRanged.debugLogging) {
            SrpWizCore.LOGGER.info("[srpwizcore] dragon ranged: tagged {} as {}, shooter {}",
                    entity.getClass().getSimpleName(), element, living.getName());
        }
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent.Arrow event) {
        EntityArrow arrow = event.getArrow();
        if (arrow.world.isRemote) {
            return;
        }
        DragonElement element = DragonElement.byId(arrow.getEntityData().getByte(TAG));
        if (element == null) {
            return;
        }
        RayTraceResult hit = event.getRayTraceResult();
        if (hit == null || !(hit.entityHit instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) hit.entityHit;
        element.applyOnHit(target, arrow.shootingEntity);
        if (arrow.world instanceof WorldServer) {
            element.spawnImpactParticles((WorldServer) arrow.world, target.posX,
                    target.posY + target.height * 0.5D, target.posZ);
        }
        if (SrpWizCoreConfig.dragonRanged.debugLogging) {
            SrpWizCore.LOGGER.info("[srpwizcore] dragon ranged: {} applied to {}",
                    element, target.getName());
        }
    }
}
