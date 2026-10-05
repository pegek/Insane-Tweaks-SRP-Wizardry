package com.spege.ebreduxaddon.compat.spore;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Sentities.BaseEntities.EvolvedInfected;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.binaris.wizardry.api.content.entity.living.ISpellCaster;
import com.binaris.wizardry.api.content.spell.Element;
import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.content.entity.goal.AttackSpellGoal;
import com.binaris.wizardry.core.platform.Services;
import com.binaris.wizardry.setup.registries.Spells;
import com.spege.ebreduxaddon.core.InfectedTiers;
import com.spege.ebreduxaddon.core.WardRules;
import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mykomanta: ewoluowany zarazony mag (spec 0.3). Dziedziczy po EvolvedInfected Spore (limit obrazen,
 * brak glodu, odpornosc na zimno, despawn tylko bez linku). Zawsze tier master, zaklecia przejete od
 * maga plus jedno, a w walce co jakis czas oslania zarazonych wokol siebie (Resistance).
 *
 * <p>Nie ewoluuje dalej: brak EvolvingInfected, wiec hyper-ewolucja Spore jej nie dotyczy.
 */
public class MycomancerEntity extends EvolvedInfected implements ISpellCaster {

    private static final EntityDataAccessor<String> CONTINUOUS_SPELL =
            SynchedEntityData.defineId(MycomancerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SPELL_COUNTER =
            SynchedEntityData.defineId(MycomancerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TEXTURE_INDEX =
            SynchedEntityData.defineId(MycomancerEntity.class, EntityDataSerializers.INT);

    public static final int TEXTURE_COUNT = 6;
    public static final float SCALE = 1.15f;

    private final WizardLoadout loadout = new WizardLoadout();

    public MycomancerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        double health = SConfig.SERVER.global_health.get();
        double damage = SConfig.SERVER.global_damage.get();
        double armor = SConfig.SERVER.global_armor.get();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0 * health)
                .add(Attributes.ATTACK_DAMAGE, 6.0 * damage)
                .add(Attributes.ARMOR, 8.0 * armor)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new AttackSpellGoal<>(this, 0.6, 16.0f, 20, 40));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        super.registerGoals();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CONTINUOUS_SPELL, Spells.NONE.getLocation().toString());
        this.entityData.define(SPELL_COUNTER, 0);
        this.entityData.define(TEXTURE_INDEX, 0);
    }

    // ------------------------------------------------------------------ powstanie

    /** Ewolucja z zarazonego maga: jego zywiol i zaklecia plus jedno, tier master. Przed finalizeSpawn. */
    void inheritFrom(InfectedWizardEntity wizard) {
        loadout.inherit(wizard.loadout(), this.random, InfectedTiers.MASTER, Config.INSTANCE.infectedSpellCount.get() + 1);
        this.entityData.set(TEXTURE_INDEX, wizard.getTextureIndex());
        equipWand();
    }

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty,
                                        @NotNull MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        if (!loadout.rolled()) {
            roll();
        }
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    /** /summon z NBT i spawner z danymi pomijaja finalizeSpawn - jak u maga, losowanie przy pierwszym ticku. */
    @Override
    public void tick() {
        if (!loadout.rolled() && !this.level().isClientSide) {
            roll();
        }
        super.tick();
        if (this.level() instanceof ServerLevel server && this.isAlive()
                && WardRules.due(this.tickCount, Config.INSTANCE.mycomancerWardInterval.get(), this.getTarget() != null)) {
            ward(server);
        }
    }

    /** Mykomanta spoza ewolucji (jajko, /summon): losuje sama, od razu jako master. Publiczne dla GameTestow. */
    public void roll() {
        this.entityData.set(TEXTURE_INDEX, this.random.nextInt(TEXTURE_COUNT));
        loadout.roll(this.random, InfectedTiers.MASTER, Config.INSTANCE.infectedSpellCount.get() + 1,
                Config.INSTANCE.infectedAlwaysSpineVolley.get());
        equipWand();
    }

    private void equipWand() {
        float chance = (float) Math.min(1.0, Config.INSTANCE.infectedWandDropChance.get() * 2.0);
        loadout.equipWand(this, chance);
    }

    // ------------------------------------------------------------------ oslona grzybni

    /** Daje Resistance zarazonym w promieniu. Zwraca liczbe oslonietych. Publiczne dla GameTestow. */
    public int ward(ServerLevel server) {
        double radius = Config.INSTANCE.mycomancerWardRadius.get();
        int duration = Config.INSTANCE.mycomancerWardDuration.get();
        int amplifier = Config.INSTANCE.mycomancerWardAmplifier.get();
        List<Infected> nearby = server.getEntitiesOfClass(Infected.class, this.getBoundingBox().inflate(radius),
                e -> WardRules.receives(e == this, true, e instanceof MycomancerEntity, e.isAlive())
                        && e.distanceToSqr(this) <= radius * radius);
        for (Infected infected : nearby) {
            infected.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, amplifier), this);
        }
        ring(server, radius);
        server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.HOSTILE, 1.0f, 0.8f);
        return nearby.size();
    }

    private void ring(ServerLevel server, double radius) {
        int points = 32;
        double r = Math.min(radius, 6.0);
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2 / points);
            server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, this.getX() + Math.cos(a) * r, this.getY() + 0.2,
                    this.getZ() + Math.sin(a) * r, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    // ------------------------------------------------------------------ ISpellCaster

    @Override
    public @NotNull List<Spell> getSpells() {
        return loadout.spells();
    }

    @Override
    public @NotNull Spell getContinuousSpell() {
        Spell spell = Services.REGISTRY_UTIL.getSpell(ResourceLocation.tryParse(this.entityData.get(CONTINUOUS_SPELL)));
        return spell == null ? Spells.NONE : spell;
    }

    @Override
    public void setContinuousSpell(Spell spell) {
        this.entityData.set(CONTINUOUS_SPELL, (spell == null ? Spells.NONE : spell).getLocation().toString());
    }

    @Override
    public int getSpellCounter() {
        return this.entityData.get(SPELL_COUNTER);
    }

    @Override
    public void setSpellCounter(int count) {
        this.entityData.set(SPELL_COUNTER, count);
    }

    WizardLoadout loadout() {
        return loadout;
    }

    public Element getElement() {
        return loadout.element();
    }

    public int getMaxTier() {
        return loadout.maxTier();
    }

    public boolean isRolled() {
        return loadout.rolled();
    }

    public int getTextureIndex() {
        return Math.floorMod(this.entityData.get(TEXTURE_INDEX), TEXTURE_COUNT);
    }

    // ------------------------------------------------------------------ Spore

    @Override
    public List<? extends String> getDropList() {
        return List.of();
    }

    /** Domyslny ORIGIN Spore (konwersja nadpisuje go zmarlym mobem): pierwotna istota, mag Redux. */
    @Override
    public String origin() {
        return "ebwizardry:wizard";
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        loadout.save(tag);
        tag.putInt("ebreduxaddon:texture", getTextureIndex());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadout.load(tag);
        this.entityData.set(TEXTURE_INDEX, tag.getInt("ebreduxaddon:texture"));
    }
}
