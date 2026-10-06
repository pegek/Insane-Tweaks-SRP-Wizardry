package com.spege.insanetweaks.entities.magic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityCanra;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.ai.EntityAIWardAllies;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Arcane Conjurer: SRP's primitive summoner, whose native summon skill (id 1, the vomit summon) is
 * replaced by Spectral Swarm: short-lived, tagged real SRP mobs that drop nothing. Ward Allies
 * shields hurt parasites with Absorption. The native ranged attack stays.
 *
 * <p>Skill protocol (EntityAISkill, bytecode-verified): {@code doSpecialSkill(id)} is called every
 * tick until {@code getFinished(id)} is true, then {@code setFinished(id, false)}. With the feature
 * switched off both calls go straight to SRP's own summon.
 */
public class EntityMagicSummoner extends EntityCanra implements IMagicParasite {

    private static final DataParameter<Byte> ARCANE_STATE = EntityDataManager.createKey(EntityMagicSummoner.class,
            DataSerializers.BYTE);
    private static final byte SKILL_SUMMON = 1;
    private static final int TELEGRAPH_TICKS = 15;
    /** The parasite status SRP's own summon holds while casting. */
    private static final int STATUS_CASTING = 10;
    private static final Set<String> WARNED_IDS = new HashSet<String>();

    /** Transient: who this Conjurer summoned. A reload empties it; the spectrals still expire. */
    private final List<Entity> spectrals = new ArrayList<Entity>();
    private int swarmTicks;
    private boolean swarmFinished;
    private long nextSwarmTime;

    public EntityMagicSummoner(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ARCANE_STATE, Byte.valueOf(STATE_IDLE));
    }

    @Override
    protected void initEntityAI() {
        super.initEntityAI();
        this.tasks.addTask(5, new EntityAIWardAllies(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.MAX_HEALTH, c.conjurerHealthMultiplier);
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.ATTACK_DAMAGE, c.conjurerDamageMultiplier);
    }

    @Override
    public byte getArcaneState() {
        return this.dataManager.get(ARCANE_STATE).byteValue();
    }

    @Override
    public void setArcaneState(byte state) {
        this.dataManager.set(ARCANE_STATE, Byte.valueOf(state));
    }

    @Override
    public void doSpecialSkill(byte id) {
        if (id != SKILL_SUMMON || !MagicParasiteTraits.enabled()) {
            super.doSpecialSkill(id);
            return;
        }
        castSwarm();
    }

    @Override
    public boolean getFinished(byte id) {
        if (id == SKILL_SUMMON && MagicParasiteTraits.enabled()) {
            return this.swarmFinished;
        }
        return super.getFinished(id);
    }

    @Override
    public void setFinished(byte id, boolean finished) {
        if (id == SKILL_SUMMON && MagicParasiteTraits.enabled()) {
            this.swarmFinished = finished;
            return;
        }
        super.setFinished(id, finished);
    }

    private void castSwarm() {
        if (this.world.isRemote) {
            return;
        }
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        long now = this.world.getTotalWorldTime();
        if (this.swarmTicks == 0) {
            pruneSpectrals();
            if (now < this.nextSwarmTime || this.spectrals.size() >= c.spectralLimit || this.getAttackTarget() == null) {
                this.swarmFinished = true;
                return;
            }
            this.setParasiteStatus(STATUS_CASTING);
            this.getNavigator().clearPath();
            this.setArcaneState(STATE_TELEGRAPH);
            MagicParasiteTraits.sound(this, SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON, 1.2F);
        }
        this.swarmTicks++;
        if (this.swarmTicks % 3 == 0) {
            MagicParasiteTraits.telegraph(this);
        }
        if (this.swarmTicks < TELEGRAPH_TICKS) {
            return;
        }
        summonSpectrals(now);
        this.swarmTicks = 0;
        this.swarmFinished = true;
        this.setParasiteStatus(0);
        this.setArcaneState(STATE_IDLE);
        this.nextSwarmTime = now + c.spectralCooldownSeconds * 20L;
    }

    private void summonSpectrals(long now) {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        String[] pool = c.spectralPool;
        if (pool.length == 0) {
            return;
        }
        int count = SpectralBudget.toSummon(c.spectralMin, c.spectralMax, this.rand.nextDouble(),
                this.spectrals.size(), c.spectralLimit);
        EntityLivingBase target = this.getAttackTarget();
        BlockPos center = new BlockPos(this);
        for (int i = 0; i < count; i++) {
            BlockPos pos = MagicParasiteTraits.findStandable(this.world, center, this.rand, 2, 4);
            if (pos == null) {
                continue;
            }
            String id = pool[this.rand.nextInt(pool.length)];
            Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), this.world);
            if (!(entity instanceof EntityLiving)) {
                if (WARNED_IDS.add(id)) {
                    InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Arcane Conjurer: '{}' is not a living entity id "
                            + "- skipped. Check 'Conjurer: Spectral Pool'.", id);
                }
                continue;
            }
            EntityLiving mob = (EntityLiving) entity;
            mob.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    this.rand.nextFloat() * 360.0F, 0.0F);
            mob.onInitialSpawn(this.world.getDifficultyForLocation(pos), null);
            SpectralTags.mark(mob, now + c.spectralLifetimeSeconds * 20L);
            if (!this.world.spawnEntity(mob)) {
                continue;
            }
            this.spectrals.add(mob);
            MagicParasiteTraits.burst(this.world, mob.posX, mob.posY + mob.height * 0.5D, mob.posZ, 12, 0.3D);
            if (target != null) {
                if (mob instanceof EntityParasiteBase) {
                    ((EntityParasiteBase) mob).setAttackTarget(target, 60);
                } else {
                    mob.setAttackTarget(target);
                }
            }
        }
    }

    private void pruneSpectrals() {
        Iterator<Entity> it = this.spectrals.iterator();
        while (it.hasNext()) {
            if (!it.next().isEntityAlive()) {
                it.remove();
            }
        }
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (!this.world.isRemote) {
            for (Entity spectral : this.spectrals) {
                if (spectral.isEntityAlive()) {
                    MagicParasiteTraits.dispel(spectral);
                }
            }
            this.spectrals.clear();
        }
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (this.world.isRemote) {
            MagicParasiteTraits.clientAura(this, getArcaneState() != STATE_IDLE);
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return super.attackEntityFrom(source, MagicParasiteTraits.scaleIncoming(source, amount));
    }
}
