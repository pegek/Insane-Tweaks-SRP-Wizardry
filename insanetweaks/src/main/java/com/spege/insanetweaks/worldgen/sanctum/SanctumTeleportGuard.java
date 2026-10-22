package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.spege.insanetweaks.anchor.AnchorLeash;
import com.spege.insanetweaks.config.ModConfig;

import electroblob.wizardry.event.SpellCastEvent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Keeps teleport spells cast by a sanctum's guardians inside the sanctum (spec 15.8, option b).
 * EBW's Banish, Blink and Phase Step move an entity with setPositionAndUpdate - no event to veto
 * afterwards - and Banish is the sims' close-range answer, so the cast is allowed: the caster and
 * its target are remembered, and whichever ends up outside the sanctum's box within a few ticks is
 * put back where it stood. A teleport that lands inside the sanctum stays.
 */
public class SanctumTeleportGuard {

    /** EBW teleports land inside the cast, so the end of the same world tick already sees them. */
    private static final int WATCH_TICKS = 1;
    /** Radius around the caster within which anyone could be the one a teleport spell moves. */
    private static final double SPELL_REACH = 32.0D;
    private static final List<Pending> PENDING = new ArrayList<Pending>();

    private static final class Pending {
        final int dimension;
        final long sanctumId;
        final int entityId;
        final double x;
        final double y;
        final double z;
        final long expires;

        Pending(Entity entity, long sanctumId, long expires) {
            this.dimension = entity.dimension;
            this.sanctumId = sanctumId;
            this.entityId = entity.getEntityId();
            this.x = entity.posX;
            this.y = entity.posY;
            this.z = entity.posZ;
            this.expires = expires;
        }
    }

    @SubscribeEvent
    public void onCast(SpellCastEvent.Pre event) {
        EntityLivingBase caster = event.getCaster();
        if (caster == null || caster.world.isRemote || !(caster instanceof EntityLiving)
                || !caster.getEntityData().hasKey(AnchorLeash.SANCTUM) || event.getSpell() == null
                || event.getSpell().getRegistryName() == null || !guarded(event.getSpell().getRegistryName().toString())) {
            return;
        }
        long id = caster.getEntityData().getLong(AnchorLeash.SANCTUM);
        SanctumWorldData data = SanctumWorldData.get(caster.world);
        int[] box = data.box(id);
        if (box == null || data.isCleared(id)) {
            return;
        }
        long expires = caster.world.getTotalWorldTime() + WATCH_TICKS;
        // Banish is a ray: it hits the first living thing on its line - another player, a thrall, a
        // pet - not necessarily the attack target. So everyone inside the sanctum near the caster
        // is remembered, the caster included.
        List<EntityLivingBase> near = caster.world.getEntitiesWithinAABB(EntityLivingBase.class,
                caster.getEntityBoundingBox().grow(SPELL_REACH));
        synchronized (PENDING) {
            for (EntityLivingBase e : near) {
                if (inside(box, e.posX, e.posY, e.posZ)) {
                    PENDING.add(new Pending(e, id, expires));
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        World world = event.world;
        int dim = world.provider.getDimension();
        long now = world.getTotalWorldTime();
        synchronized (PENDING) {
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                if (p.dimension != dim) {
                    continue;
                }
                Entity entity = world.getEntityByID(p.entityId);
                int[] box = SanctumWorldData.get(world).box(p.sanctumId);
                if (entity != null && entity.isEntityAlive() && box != null
                        && !inside(box, entity.posX, entity.posY, entity.posZ)) {
                    entity.setPositionAndUpdate(p.x, p.y, p.z);
                    it.remove();
                } else if (now > p.expires) {
                    it.remove();
                }
            }
        }
    }

    private static boolean guarded(String id) {
        for (String s : ModConfig.arcaneSanctum.sanctumGuardedSpells) {
            if (s.trim().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** box = minX, minZ, maxX, maxZ, hullMinY, hullMaxY. */
    private static boolean inside(int[] box, double x, double y, double z) {
        return x >= box[0] && x < box[2] + 1 && z >= box[1] && z < box[3] + 1 && y >= box[4] && y < box[5] + 1;
    }
}
