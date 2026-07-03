package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallMode;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;

/**
 * AI task: Walk to nearby EntityItem and collect it into the thrall's inventory.
 * Active in FOLLOW, WOODCUTTING, and MINESHAFT modes (not in STAY).
 * Uses mutex bit 1 only (movement) so it can coexist with look-based tasks.
 * Respects pickup cooldown from EntityThrallMinion.
 */
@SuppressWarnings("null")
public class ThrallAIGatherItems extends EntityAIBase {

    private static final int SCAN_RADIUS = 8;
    private static final int MIN_ITEM_AGE = 40; // ticks
    private static final int NAV_TIMEOUT_TICKS = 100; // abort if can't reach in 5 seconds

    private final EntityThrallMinion thrall;
    private EntityItem targetItem;
    private int navTimer;

    public ThrallAIGatherItems(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(1); // movement only — allows coexistence with look tasks
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() == ThrallMode.STAY) return false;
        if (thrall.getThrallInventory().isFull()) return false;

        this.targetItem = findNearestItem();
        return this.targetItem != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (thrall.getMode() == ThrallMode.STAY) return false;
        if (thrall.getThrallInventory().isFull()) return false;
        if (navTimer > NAV_TIMEOUT_TICKS) return false; // give up if stuck
        return targetItem != null && !targetItem.isDead;
    }

    @Override
    public void startExecuting() {
        navTimer = 0;
    }

    @Override
    public void resetTask() {
        targetItem = null;
        navTimer = 0;
    }

    @Override
    public void updateTask() {
        if (targetItem == null || targetItem.isDead) {
            resetTask();
            return;
        }

        navTimer++;

        // Navigate toward target item
        if (navTimer % 10 == 0) {
            thrall.getNavigator().tryMoveToXYZ(
                    targetItem.posX, targetItem.posY, targetItem.posZ, 0.8D);
        }

        thrall.getLookHelper().setLookPosition(
                targetItem.posX, targetItem.posY + 0.3, targetItem.posZ,
                10.0F, (float) thrall.getVerticalFaceSpeed());

        if (thrall.getDistanceSq(targetItem) < 2.5 * 2.5) {
            ItemStack stack = targetItem.getItem();
            if (!stack.isEmpty()) {
                thrall.getThrallInventory().addItemStackToInventory(stack);
                if (stack.isEmpty() || stack.getCount() <= 0) {
                    targetItem.setDead();
                }
            }
            targetItem = null;
        }
    }

    private EntityItem findNearestItem() {
        AxisAlignedBB bb = thrall.getEntityBoundingBox().grow(SCAN_RADIUS, 4, SCAN_RADIUS);
        List<EntityItem> items = thrall.world.getEntitiesWithinAABB(EntityItem.class, bb);

        EntityItem nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (EntityItem item : items) {
            if (item.isDead) continue;
            if (item.cannotPickup()) continue; // respects pickup delay
            if (item.getAge() < MIN_ITEM_AGE) continue; // too fresh
            if (item.getItem().isEmpty()) continue;

            double dist = thrall.getDistanceSq(item);
            if (dist < nearestDist) {
                nearest = item;
                nearestDist = dist;
            }
        }
        return nearest;
    }
}
