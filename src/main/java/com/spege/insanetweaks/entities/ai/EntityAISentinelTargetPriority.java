package com.spege.insanetweaks.entities.ai;

import java.util.List;

import com.google.common.base.Predicate;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySentinel;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.util.ResourceLocation;

/**
 * F2 (spec 2026-07-10): proactive target selection with priority classes. Replaces the
 * vanilla EntityAINearestAttackableTarget (pure nearest-first). Candidates are scored
 * (priorityIndex, distance) and the best is attacked:
 *   index 0..n-1 : first matching prefix in entities.sentinel.targetPriorityPrefixes
 *   index n      : undead (after all listed prefixes)
 *   index n+1    : everything else
 * Defensive stance is enforced upstream — the shared targetSelector predicate returns
 * false for every candidate while the stance is defensive.
 */
public class EntityAISentinelTargetPriority extends EntityAITarget {

    private final EntitySentinel sentinel;
    private final Predicate<EntityLivingBase> selector;
    private EntityLivingBase chosenTarget;

    public EntityAISentinelTargetPriority(EntitySentinel sentinel, Predicate<EntityLivingBase> selector) {
        super(sentinel, false, true);
        this.sentinel = sentinel;
        this.selector = selector;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        double range = this.getTargetDistance();
        List<EntityLivingBase> candidates = this.sentinel.world.getEntitiesWithinAABB(
                EntityLivingBase.class,
                this.sentinel.getEntityBoundingBox().grow(range, 4.0D, range),
                candidate -> candidate != null && this.selector.apply(candidate)
                        && this.isSuitableTarget(candidate, false));
        if (candidates.isEmpty()) {
            return false;
        }

        EntityLivingBase best = null;
        double bestScore = Double.MAX_VALUE;
        for (EntityLivingBase candidate : candidates) {
            // Priority class dominates; distance breaks ties within a class.
            double score = priorityIndex(candidate) * 1.0E7D + this.sentinel.getDistanceSq(candidate);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        this.chosenTarget = best;
        return best != null;
    }

    @Override
    public void startExecuting() {
        this.sentinel.setAttackTarget(this.chosenTarget);
        super.startExecuting();
    }

    private static int priorityIndex(EntityLivingBase entity) {
        String[] prefixes = ModConfig.entities.sentinel.targetPriorityPrefixes;
        ResourceLocation key = EntityList.getKey(entity);
        if (key != null) {
            String name = key.toString();
            for (int i = 0; i < prefixes.length; i++) {
                if (prefixes[i] != null && !prefixes[i].isEmpty() && name.startsWith(prefixes[i])) {
                    return i;
                }
            }
        }
        return entity.isEntityUndead() ? prefixes.length : prefixes.length + 1;
    }
}
