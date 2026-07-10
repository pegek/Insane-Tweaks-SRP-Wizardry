package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallChestHelper;
import com.spege.insanetweaks.entities.ThrallMode;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.IShearable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI task: HUSBANDRY mode (spec 2026-07-10). Anchored at HOME. Each cycle builds a job
 * queue over farm animals in the working radius, walks to each target and acts:
 *   1. SHEAR every ready IShearable adult (requires shears in a home chest; durability is consumed).
 *   2. CULL adults above the per-species population cap (direct damage — NO combat AI;
 *      this is the single user-approved exception to the "never aggressive" invariant).
 *   3. BREED pairs below the cap (requires 2 matching feed items in a home chest per pair).
 * Then deposits its bag into home chests. Never touches babies, in-love animals or owned pets.
 */
@SuppressWarnings("null")
public class ThrallAIHusbandry extends EntityAIBase {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallHusbandry");

    private static final int CHEST_VRANGE = 4;
    private static final double ACT_RANGE_SQ = 2.5D * 2.5D;
    private static final double WALK_SPEED = 0.7D;
    private static final int JOB_TIMEOUT_TICKS = 200;
    private static final int MAX_JOBS_PER_CYCLE = 8;
    private static final float CULL_DAMAGE = 1000.0F;

    private enum JobType { SHEAR, CULL, BREED }

    private static final class Job {
        final JobType type;
        final EntityAnimal target;
        /** Second animal of a BREED pair; null otherwise. */
        @Nullable final EntityAnimal partner;

        Job(JobType type, EntityAnimal target, @Nullable EntityAnimal partner) {
            this.type = type;
            this.target = target;
            this.partner = partner;
        }
    }

    private final EntityThrallMinion thrall;
    private final Deque<Job> jobs = new ArrayDeque<Job>();
    private long nextCycleTime;
    private int jobTicks;

    public ThrallAIHusbandry(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
    }

    private static boolean debugLogs() {
        return ModConfig.client.enableThrallDebugLogs;
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() != ThrallMode.HUSBANDRY) return false;
        if (!ModConfig.thrall.husbandry.enableHusbandryMode) return false;
        return thrall.getHomePoint() != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        jobs.clear();
        nextCycleTime = 0;
        thrall.getNavigator().clearPath();
        thrall.setStatusText("Tending...");
    }

    @Override
    public void resetTask() {
        jobs.clear();
        thrall.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        if (jobs.isEmpty()) {
            long now = thrall.world.getTotalWorldTime();
            if (now < nextCycleTime) return;
            nextCycleTime = now + (long) ModConfig.thrall.husbandry.husbandryIntervalSeconds * 20L;
            buildJobQueue(home);
            depositBag(home);
            if (jobs.isEmpty()) {
                thrall.setStatusText("Standing by...");
            }
            return;
        }

        Job job = jobs.peekFirst();
        if (!isJobStillValid(job)) {
            jobs.pollFirst();
            jobTicks = 0;
            return;
        }

        jobTicks++;
        if (jobTicks > JOB_TIMEOUT_TICKS) {
            if (debugLogs()) LOG.info("[Thrall#{}] Husbandry job {} timed out", thrall.getEntityId(), job.type);
            jobs.pollFirst();
            jobTicks = 0;
            return;
        }

        if (thrall.getDistanceSq(job.target) > ACT_RANGE_SQ) {
            thrall.getNavigator().tryMoveToEntityLiving(job.target, WALK_SPEED);
            thrall.getLookHelper().setLookPositionWithEntity(job.target, 30.0F, 30.0F);
            return;
        }

        thrall.getNavigator().clearPath();
        performJob(job, home);
        jobs.pollFirst();
        jobTicks = 0;

        if (jobs.isEmpty()) {
            depositBag(home);
            thrall.setStatusText("Standing by...");
        }
    }

    // ------------------------------------------------------------------
    // Queue building
    // ------------------------------------------------------------------

    private void buildJobQueue(BlockPos home) {
        int radius = ModConfig.thrall.husbandry.husbandryRadius;
        int cap = ModConfig.thrall.husbandry.husbandryPopulationCap;

        AxisAlignedBB box = new AxisAlignedBB(home).grow(radius, 8, radius);
        List<EntityAnimal> animals = thrall.world.getEntitiesWithinAABB(EntityAnimal.class, box,
                a -> a != null && a.isEntityAlive() && !isOwnedPet(a));

        // Group ADULTS by species for cap/breeding logic.
        Map<Class<?>, List<EntityAnimal>> adultsBySpecies = new HashMap<Class<?>, List<EntityAnimal>>();
        for (EntityAnimal a : animals) {
            if (a.isChild()) continue;
            List<EntityAnimal> list = adultsBySpecies.get(a.getClass());
            if (list == null) {
                list = new ArrayList<EntityAnimal>();
                adultsBySpecies.put(a.getClass(), list);
            }
            list.add(a);
        }

        int queued = 0;

        // 1. SHEAR — every ready shearable adult (independent of the cap).
        for (EntityAnimal a : animals) {
            if (queued >= MAX_JOBS_PER_CYCLE) break;
            if (a.isChild() || !(a instanceof IShearable)) continue;
            ItemStack probe = new ItemStack(Items.SHEARS);
            if (((IShearable) a).isShearable(probe, thrall.world, new BlockPos(a))) {
                jobs.addLast(new Job(JobType.SHEAR, a, null));
                queued++;
            }
        }

        // 2. CULL — excess adults per species (never in-love; babies/pets excluded above).
        for (Map.Entry<Class<?>, List<EntityAnimal>> e : adultsBySpecies.entrySet()) {
            int excess = e.getValue().size() - cap;
            for (EntityAnimal a : e.getValue()) {
                if (queued >= MAX_JOBS_PER_CYCLE || excess <= 0) break;
                if (a.isInLove()) continue;
                jobs.addLast(new Job(JobType.CULL, a, null));
                queued++;
                excess--;
            }
        }

        // 3. BREED — pair up eligible adults while below the cap.
        for (Map.Entry<Class<?>, List<EntityAnimal>> e : adultsBySpecies.entrySet()) {
            if (queued >= MAX_JOBS_PER_CYCLE) break;
            List<EntityAnimal> eligible = new ArrayList<EntityAnimal>();
            for (EntityAnimal a : e.getValue()) {
                if (a.getGrowingAge() == 0 && !a.isInLove()) eligible.add(a);
            }
            int room = cap - e.getValue().size();
            for (int i = 0; i + 1 < eligible.size() && room > 0 && queued < MAX_JOBS_PER_CYCLE; i += 2) {
                jobs.addLast(new Job(JobType.BREED, eligible.get(i), eligible.get(i + 1)));
                queued++;
                room--;
            }
        }

        if (debugLogs() && !jobs.isEmpty()) {
            LOG.info("[Thrall#{}] Husbandry queued {} jobs", thrall.getEntityId(), jobs.size());
        }
    }

    private static boolean isOwnedPet(EntityAnimal a) {
        return a instanceof EntityTameable && ((EntityTameable) a).getOwnerId() != null;
    }

    private boolean isJobStillValid(Job job) {
        if (job.target == null || !job.target.isEntityAlive()) return false;
        if (job.type == JobType.BREED) {
            return job.partner != null && job.partner.isEntityAlive()
                    && job.target.getGrowingAge() == 0 && !job.target.isInLove()
                    && job.partner.getGrowingAge() == 0 && !job.partner.isInLove();
        }
        if (job.type == JobType.CULL) {
            return !job.target.isChild() && !job.target.isInLove();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Job execution
    // ------------------------------------------------------------------

    private void performJob(Job job, BlockPos home) {
        switch (job.type) {
            case SHEAR: performShear(job.target, home); break;
            case CULL:  performCull(job.target);        break;
            case BREED: performBreed(job, home);        break;
        }
    }

    private void performShear(EntityAnimal animal, BlockPos home) {
        ChestSlotRef shears = findChestStack(home, new StackPredicate() {
            @Override public boolean test(ItemStack s) { return s.getItem() == Items.SHEARS; }
        });
        if (shears == null) {
            thrall.setStatusText("No shears");
            return;
        }
        IShearable shearable = (IShearable) animal;
        BlockPos pos = new BlockPos(animal);
        if (!shearable.isShearable(shears.stack, thrall.world, pos)) return;

        List<ItemStack> drops = shearable.onSheared(shears.stack, thrall.world, pos, 0);
        for (ItemStack drop : drops) {
            ItemStack working = drop.copy();
            thrall.getThrallInventory().addItemStackToInventory(working);
            if (!working.isEmpty()) {
                animal.entityDropItem(working, 0.5F);
            }
        }
        shears.stack.damageItem(1, thrall);
        if (shears.stack.isEmpty()) {
            shears.chest.setInventorySlotContents(shears.slot, ItemStack.EMPTY);
        }
        shears.chest.markDirty();
        thrall.setStatusText("Shearing...");
    }

    private void performCull(EntityAnimal animal) {
        thrall.swingArm(EnumHand.MAIN_HAND);
        animal.attackEntityFrom(DamageSource.causeMobDamage(thrall), CULL_DAMAGE);
        thrall.setStatusText("Culling...");
        // Drops land inside the thrall's passive pickup range (it is standing next to
        // the animal) and are banked during the end-of-queue deposit.
    }

    private void performBreed(Job job, BlockPos home) {
        final EntityAnimal first = job.target;
        ChestSlotRef feed = findChestStack(home, new StackPredicate() {
            @Override public boolean test(ItemStack s) { return first.isBreedingItem(s); }
        });
        if (feed == null || feed.stack.getCount() < 2) {
            thrall.setStatusText("No feed");
            return;
        }
        job.target.setInLove(null);
        job.partner.setInLove(null);
        feed.stack.shrink(2);
        if (feed.stack.isEmpty()) {
            feed.chest.setInventorySlotContents(feed.slot, ItemStack.EMPTY);
        }
        feed.chest.markDirty();
        thrall.setStatusText("Breeding...");
    }

    private void depositBag(BlockPos home) {
        if (thrall.getThrallInventory().containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home,
                    ModConfig.thrall.porter.porterChestScanRange, CHEST_VRANGE, false);
        }
    }

    // ------------------------------------------------------------------
    // Chest access
    // ------------------------------------------------------------------

    /** Local single-method predicate — avoids mixing guava/java.util.function here. */
    private interface StackPredicate { boolean test(ItemStack stack); }

    private static final class ChestSlotRef {
        final IInventory chest;
        final int slot;
        final ItemStack stack;
        ChestSlotRef(IInventory chest, int slot, ItemStack stack) {
            this.chest = chest; this.slot = slot; this.stack = stack;
        }
    }

    @Nullable
    private ChestSlotRef findChestStack(BlockPos home, StackPredicate predicate) {
        List<IInventory> chests = ThrallChestHelper.findNearbyInventories(thrall.world, home,
                ModConfig.thrall.porter.porterChestScanRange, CHEST_VRANGE);
        for (IInventory chest : chests) {
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack s = chest.getStackInSlot(i);
                if (!s.isEmpty() && predicate.test(s)) {
                    return new ChestSlotRef(chest, i, s);
                }
            }
        }
        return null;
    }
}
