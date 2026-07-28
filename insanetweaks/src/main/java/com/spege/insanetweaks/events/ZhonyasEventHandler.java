package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooM;
import com.dhanantry.scapeandrunparasites.entity.monster.crude.EntityInhooS;
import com.spege.insanetweaks.util.SrpOriginCaptureState;
import com.spege.insanetweaks.util.SrpOriginSnapshotHelper;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Diagnostics for the Hourglass of Restoration snapshot pipeline: reports whether a newly spawned
 * SRP parasite arrived carrying its pre-infection snapshot.
 *
 * <h3>What this class used to be, and why none of it worked</h3>
 * It used to do the capture itself, with no mixin: on the join of an Inhoo/Infected it scanned a
 * 1.5-block AABB for a live non-parasite entity and treated it as the original. Its javadoc stated
 * SRP's order as {@code spawnEntity(new)} then {@code removeEntity(old)}, so the original would
 * still be alive and findable.
 *
 * <p>The order is the exact opposite. {@code javap -p -c} on {@code ParasiteEventEntity}
 * (2026-07-28) shows {@code removeEntity} before {@code spawnEntity} on every branch of both
 * {@code convertEntity} (279-&gt;332, 607-&gt;667, 921-&gt;974) and {@code spawnInsider}
 * (236-&gt;242). {@code removeEntity} marks the entity dead, and the scan skipped dead candidates -
 * so it returned null every single time. Its fallback then called
 * {@code SrpOriginSnapshotHelper.popMostRecent()}, which read a map that nothing ever wrote to and
 * so returned null unconditionally. Both paths were dead; the handler could only ever log.
 *
 * <p>Capture now lives in {@code MixinParasiteEventEntity}, which stamps the snapshot onto exactly
 * the entity being spawned, before the spawn - so it is already present by the time this event
 * fires. What is left here is the check that this actually happened. The AABB scan is gone: it cost
 * an entity query on every parasite join, on a pack that spawns a great many of them, and it could
 * never have produced an answer.
 */
public class ZhonyasEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityInhooM || entity instanceof EntityInhooS
                || entity instanceof EntityPInfected)) {
            return;
        }

        NBTTagCompound data = entity.getEntityData();
        if (data.hasKey(SrpOriginSnapshotHelper.KEY_ORIGINAL_ID, 8)) {
            SrpOriginCaptureState.debug("{} joined carrying snapshot of '{}'",
                    entity.getClass().getSimpleName(),
                    data.getString(SrpOriginSnapshotHelper.KEY_ORIGINAL_ID));
            return;
        }

        // Not necessarily a fault: SRP also spawns these from worldgen, nests and beckons, where
        // there is no original mob to record. It only matters for a conversion, and that case is
        // reported loudly by SrpOriginCaptureState.clear().
        SrpOriginCaptureState.debug("{} joined with no origin snapshot - restoring it would fall back "
                + "to the host table or a hitbox-size guess", entity.getClass().getSimpleName());
    }
}
