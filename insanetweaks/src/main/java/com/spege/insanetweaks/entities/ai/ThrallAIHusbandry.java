package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallChestHelper;
import com.spege.insanetweaks.entities.ThrallMode;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.init.Items;
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
 * AI task: HUSBANDRY mode (spec 2026-07-10, reworked 2026-07-16). Anchored at the WORK
 * SITE (where the command was issued; falls back to HOME on legacy saves). Each cycle it
 * builds a job queue over farm animals in the working radius, walks to each target and acts:
 *   1. SHEAR every ready IShearable adult (shears carried in the bag).
 *   2. CULL one adult per cull interval while a species is ABOVE the population cap
 *      (periodic slaughter — the single user-approved exception to "never aggressive").
 *   3. BREED pairs while the species is at or below the cap (herd overshoots to cap+1,
 *      feeding the periodic cull; feed carried in the bag).
 * Supplies (shears/feed) are fetched from HOME by a teleport provisioning trip at cycle
 * start, which also deposits the bag; a FULL bag mid-cycle is covered by the entity-level
 * auto-return shuttle. Never touches babies, in-love animals or owned pets.
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
    /** Feed withdrawn per breed-species on a provisioning trip (covers a few pairs). */
    private static final int FEED_PER_SPECIES = 8;

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
    /** World time before which no periodic cull may happen (spec 2026-07-16). */
    private long nextCullTime;
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
        return thrall.getWorkAnchor() != null;
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
        BlockPos anchor = thrall.getWorkAnchor();
        if (anchor == null) return;

        if (jobs.isEmpty()) {
            long now = thrall.world.getTotalWorldTime();
            if (now < nextCycleTime) return;
            nextCycleTime = now + (long) ModConfig.thrall.husbandry.husbandryIntervalSeconds * 20L;
            buildJobQueue(anchor);
            provisionAtDepot(anchor);
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
        performJob(job);
        jobs.pollFirst();
        jobTicks = 0;

        if (jobs.isEmpty()) {
            thrall.setStatusText("Standing by...");
        }
    }

    // ------------------------------------------------------------------
    // Queue building
    // ------------------------------------------------------------------

    private void buildJobQueue(BlockPos anchor) {
        int radius = ModConfig.thrall.husbandry.husbandryRadius;
        int cap = ModConfig.thrall.husbandry.husbandryPopulationCap;

        AxisAlignedBB box = new AxisAlignedBB(anchor).grow(radius, 8, radius);
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

        // 2. PERIODIC CULL — at most ONE adult per cull interval, only while a species
        //    is ABOVE the cap (spec 2026-07-16: steady meat trickle, not instant purges).
        long now = thrall.world.getTotalWorldTime();
        if (now >= nextCullTime && queued < MAX_JOBS_PER_CYCLE) {
            outer:
            for (Map.Entry<Class<?>, List<EntityAnimal>> e : adultsBySpecies.entrySet()) {
                if (e.getValue().size() <= cap) continue;
                for (EntityAnimal a : e.getValue()) {
                    if (a.isInLove()) continue;
                    jobs.addLast(new Job(JobType.CULL, a, null));
                    queued++;
                    if (debugLogs()) LOG.info("[Thrall#{}] Husbandry periodic cull queued: {} ({} > cap {})",
                            thrall.getEntityId(), a.getName(), e.getValue().size(), cap);
                    break outer;
                }
            }
        }

        // 3. BREED — pair up eligible adults while the species is at or below the cap
        //    (the herd overshoots to cap+1, which the periodic cull then harvests).
        for (Map.Entry<Class<?>, List<EntityAnimal>> e : adultsBySpecies.entrySet()) {
            if (queued >= MAX_JOBS_PER_CYCLE) break;
            if (e.getValue().size() > cap) continue;
            List<EntityAnimal> eligible = new ArrayList<EntityAnimal>();
            for (EntityAnimal a : e.getValue()) {
                if (a.getGrowingAge() == 0 && !a.isInLove()) eligible.add(a);
            }
            int room = cap + 1 - e.getValue().size();
            for (int i = 0; i + 1 < eligible.size() && room > 0 && queued < MAX_JOBS_PER_CYCLE; i += 2) {
                jobs.addLast(new Job(JobType.BREED, eligible.get(i), eligible.get(i + 1)));
                queued++;
                room--;
            }
        }

        if (debugLogs() && !jobs.isEmpty()) {
            LOG.info("[Thrall#{}] Husbandry queued {} jobs at site {}", thrall.getEntityId(), jobs.size(), anchor);
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
            // Re-check ownership at execution time — the player may tame the animal
            // during the walk to it, and owned pets must never be culled (invariant).
            return !job.target.isChild() && !job.target.isInLove() && !isOwnedPet(job.target);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Depot provisioning (spec 2026-07-16: teleport shuttle, no remote chest magic)
    // ------------------------------------------------------------------

    /**
     * One teleport round-trip to HOME at cycle start when the queued jobs need supplies
     * the bag lacks: deposits the whole bag (wool, drops, stale supplies), withdraws
     * shears (1) and feed per queued breed species, then returns to the work site.
     * No HOME set -> works with whatever is already in the bag.
     */
    private void provisionAtDepot(BlockPos anchor) {
        BlockPos home = thrall.getHomePoint();
        if (home == null || jobs.isEmpty()) return;

        boolean needShears = false;
        List<EntityAnimal> feedSpecies = new ArrayList<EntityAnimal>();
        for (Job job : jobs) {
            if (job.type == JobType.SHEAR && countInBag(SHEARS_MATCHER) == 0) {
                needShears = true;
            } else if (job.type == JobType.BREED) {
                final EntityAnimal rep = job.target;
                boolean covered = false;
                for (EntityAnimal seen : feedSpecies) {
                    if (seen.getClass() == rep.getClass()) { covered = true; break; }
                }
                if (!covered && countInBag(new ThrallChestHelper.StackMatcher() {
                    @Override public boolean matches(ItemStack s) { return rep.isBreedingItem(s); }
                }) < 2) {
                    feedSpecies.add(rep);
                }
            }
        }
        if (!needShears && feedSpecies.isEmpty()) return;

        int range = ModConfig.thrall.porter.porterChestScanRange;
        BlockPos returnPos = new BlockPos(thrall);

        thrall.setStatusText("Restocking...");
        thrall.teleportWithEffects(home);
        ThrallChestHelper.smartDeposit(thrall, home, range, CHEST_VRANGE, false);

        int got = 0;
        if (needShears) {
            got += ThrallChestHelper.withdrawFromChests(thrall, home, range, CHEST_VRANGE, SHEARS_MATCHER, 1);
        }
        for (final EntityAnimal rep : feedSpecies) {
            got += ThrallChestHelper.withdrawFromChests(thrall, home, range, CHEST_VRANGE,
                    new ThrallChestHelper.StackMatcher() {
                        @Override public boolean matches(ItemStack s) { return rep.isBreedingItem(s); }
                    }, FEED_PER_SPECIES);
        }

        thrall.teleportWithEffects(returnPos);
        if (debugLogs()) LOG.info("[Thrall#{}] Husbandry depot trip: deposited bag, withdrew {} supply item(s)",
                thrall.getEntityId(), got);
    }

    private static final ThrallChestHelper.StackMatcher SHEARS_MATCHER = new ThrallChestHelper.StackMatcher() {
        @Override public boolean matches(ItemStack s) { return s.getItem() == Items.SHEARS; }
    };

    // ------------------------------------------------------------------
    // Job execution
    // ------------------------------------------------------------------

    private void performJob(Job job) {
        switch (job.type) {
            case SHEAR: performShear(job.target); break;
            case CULL:  performCull(job.target);  break;
            case BREED: performBreed(job);        break;
        }
    }

    private void performShear(EntityAnimal animal) {
        ItemStack shears = findBagStack(SHEARS_MATCHER);
        if (shears == null) {
            thrall.setStatusText("No shears");
            return;
        }
        IShearable shearable = (IShearable) animal;
        BlockPos pos = new BlockPos(animal);
        if (!shearable.isShearable(shears, thrall.world, pos)) return;

        List<ItemStack> drops = shearable.onSheared(shears, thrall.world, pos, 0);
        for (ItemStack drop : drops) {
            ItemStack working = drop.copy();
            thrall.getThrallInventory().addItemStackToInventory(working);
            if (!working.isEmpty()) {
                animal.entityDropItem(working, 0.5F);
            }
        }
        shears.damageItem(1, thrall);
        clearEmptyBagSlots();
        thrall.setStatusText("Shearing...");
    }

    private void performCull(EntityAnimal animal) {
        thrall.swingArm(EnumHand.MAIN_HAND);
        animal.attackEntityFrom(DamageSource.causeMobDamage(thrall), CULL_DAMAGE);
        this.nextCullTime = thrall.world.getTotalWorldTime()
                + (long) ModConfig.thrall.husbandry.husbandryCullIntervalSeconds * 20L;
        thrall.setStatusText("Culling...");
        if (debugLogs()) LOG.info("[Thrall#{}] Husbandry culled {}; next cull in {} s",
                thrall.getEntityId(), animal.getName(), ModConfig.thrall.husbandry.husbandryCullIntervalSeconds);
        // Drops land inside the thrall's passive pickup range (it is standing next to
        // the animal) and are banked on the next depot trip or full-bag auto-return.
    }

    private void performBreed(Job job) {
        final EntityAnimal first = job.target;
        ThrallChestHelper.StackMatcher feedMatcher = new ThrallChestHelper.StackMatcher() {
            @Override public boolean matches(ItemStack s) { return first.isBreedingItem(s); }
        };
        if (countInBag(feedMatcher) < 2) {
            thrall.setStatusText("No feed");
            return;
        }
        job.target.setInLove(null);
        job.partner.setInLove(null);
        consumeFromBag(feedMatcher, 2);
        thrall.setStatusText("Breeding...");
    }

    // ------------------------------------------------------------------
    // Bag access (supplies live in the thrall inventory between depot trips)
    // ------------------------------------------------------------------

    private int countInBag(ThrallChestHelper.StackMatcher matcher) {
        int count = 0;
        for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
            ItemStack s = thrall.getThrallInventory().getStackInSlot(i);
            if (!s.isEmpty() && matcher.matches(s)) count += s.getCount();
        }
        return count;
    }

    @Nullable
    private ItemStack findBagStack(ThrallChestHelper.StackMatcher matcher) {
        for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
            ItemStack s = thrall.getThrallInventory().getStackInSlot(i);
            if (!s.isEmpty() && matcher.matches(s)) return s;
        }
        return null;
    }

    private void consumeFromBag(ThrallChestHelper.StackMatcher matcher, int amount) {
        for (int i = 0; i < thrall.getThrallInventory().getSizeInventory() && amount > 0; i++) {
            ItemStack s = thrall.getThrallInventory().getStackInSlot(i);
            if (s.isEmpty() || !matcher.matches(s)) continue;
            int take = Math.min(amount, s.getCount());
            s.shrink(take);
            amount -= take;
            if (s.isEmpty()) {
                thrall.getThrallInventory().setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }
    }

    /** Clears bag slots whose stacks were emptied in place (e.g. shears breaking). */
    private void clearEmptyBagSlots() {
        for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
            ItemStack s = thrall.getThrallInventory().getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() <= 0) {
                thrall.getThrallInventory().setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }
    }
}
