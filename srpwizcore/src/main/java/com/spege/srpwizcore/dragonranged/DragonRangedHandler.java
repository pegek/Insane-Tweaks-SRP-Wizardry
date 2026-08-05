package com.spege.srpwizcore.dragonranged;

import java.lang.reflect.Method;

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
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
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

    /** Key of the element byte on the projectile's Forge NBT. 0 / absent = no element. */
    private static final String NBT_KEY_ELEMENT = "srpwizcore_dragon_elem";

    private static final String DRAGON_BOLT_CLASS =
            "com.chaosbuffalo.spartanfire.entity.EntityDragonBolt";

    /**
     * Spartan Fire's bolt entity, resolved by name: srpwizcore has no compile dependency on
     * Spartan Fire, and adding one would drag in its Spartan Weaponry supertype chain for what
     * amounts to a single instanceof.
     */
    private static final Class<?> DRAGON_BOLT = resolveClass(DRAGON_BOLT_CLASS);

    /** Resolved without running the foreign class's {@code <clinit>} during our own class init. */
    private static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name, false, DragonRangedHandler.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** {@code EntityDragonBolt.getType()}, resolved reflectively for the same reason as the class. */
    private static final Method DRAGON_BOLT_GET_TYPE = resolveGetType(DRAGON_BOLT);

    private static Method resolveGetType(Class<?> owner) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod("getType");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Ice and Fire and Spartan Fire put an element on their own projectiles from the ammunition
     * item or from the WEAPON MATERIAL — and their weapon-material branch knows dragonbone only.
     * So a dragon projectile that came out as {@code DEFAULT} was fired by a dragonsteel weapon
     * and still needs us; one carrying a real type is already handled and must not be tagged
     * again, or the effect lands twice.
     *
     * <p>Both failure paths below answer "yes, already handled". When the type cannot be read we
     * would rather lose an effect than apply one twice: a missing effect looks like a weak shot,
     * a doubled one looks like a damage bug.
     */
    private static boolean carriesOwnElement(Entity entity) {
        if (entity instanceof EntityDragonArrow) {
            return ((EntityDragonArrow) entity).getType() != EntityDragonArrow.Type.DEFAULT;
        }
        if (DRAGON_BOLT != null && DRAGON_BOLT.isInstance(entity)) {
            if (DRAGON_BOLT_GET_TYPE == null) {
                return true;
            }
            try {
                return DRAGON_BOLT_GET_TYPE.invoke(entity) != EntityDragonArrow.Type.DEFAULT;
            } catch (Exception e) {
                return true;
            }
        }
        return false;
    }

    public DragonRangedHandler() {
        if (DRAGON_BOLT == null && Loader.isModLoaded("spartanfire")) {
            SrpWizCore.LOGGER.warn("[srpwizcore] dragon ranged: Spartan Fire is loaded but {} did not"
                    + " resolve - its dragon bolts will receive the element twice", DRAGON_BOLT_CLASS);
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        try {
            if (event.getWorld().isRemote) {
                return;
            }
            Entity entity = event.getEntity();
            if (!(entity instanceof EntityArrow)) {
                return;
            }
            if (carriesOwnElement(entity)) {
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
            entity.getEntityData().setByte(NBT_KEY_ELEMENT, element.nbtId());
            if (SrpWizCoreConfig.dragonRanged.debugLogging) {
                SrpWizCore.LOGGER.info("[srpwizcore] dragon ranged: tagged {} as {}, shooter {}",
                        entity.getClass().getSimpleName(), element, living.getName());
            }
        } catch (Throwable t) {
            // Experimental mechanic must never take a spawn down with it.
            SrpWizCore.LOGGER.error("[srpwizcore] dragon ranged tagging failed: {}", t.toString());
        }
    }

    /**
     * LOWEST so that anything cancelling the impact (a deflect/shield mod registered after us)
     * has already had its say — a cancelled arrow deals no damage and must not apply an element
     * either. An event cancelled BEFORE us never arrives at all ({@code receiveCanceled} is false).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onProjectileImpact(ProjectileImpactEvent.Arrow event) {
        try {
            EntityArrow arrow = event.getArrow();
            if (arrow.world.isRemote) {
                return;
            }
            // Before getEntityData(): the event also fires for block impacts, and that call
            // lazily ALLOCATES the compound and Forge then persists it as ForgeData — every
            // arrow landing in dirt would carry an empty tag around forever.
            RayTraceResult hit = event.getRayTraceResult();
            if (hit == null || !(hit.entityHit instanceof EntityLivingBase)) {
                return;
            }
            DragonElement element =
                    DragonElement.byNbtId(arrow.getEntityData().getByte(NBT_KEY_ELEMENT));
            if (element == null) {
                return;
            }
            EntityLivingBase target = (EntityLivingBase) hit.entityHit;
            // shootingEntity is NOT persisted by EntityArrow.writeEntityToNBT while our element
            // tag is, so an arrow that survived a chunk unload comes back shooterless. Chain
            // lightning builds an EntityDamageSourceIndirect out of it and null there is a crash
            // in getDeathMessage; the arrow itself is the honest stand-in.
            Entity shooter = arrow.shootingEntity != null ? arrow.shootingEntity : arrow;
            element.applyOnHit(target, shooter);
            if (arrow.world instanceof WorldServer) {
                element.spawnImpactParticles((WorldServer) arrow.world, target.posX,
                        target.posY + target.height * 0.5D, target.posZ);
            }
            if (SrpWizCoreConfig.dragonRanged.debugLogging) {
                SrpWizCore.LOGGER.info("[srpwizcore] dragon ranged: {} applied to {}",
                        element, target.getName());
            }
        } catch (Throwable t) {
            // Three third-party APIs behind one projectile hook: never take the tick down.
            SrpWizCore.LOGGER.error("[srpwizcore] dragon ranged impact failed: {}", t.toString());
        }
    }
}
