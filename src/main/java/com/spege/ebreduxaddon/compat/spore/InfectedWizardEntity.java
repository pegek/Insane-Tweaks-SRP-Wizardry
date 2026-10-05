package com.spege.ebreduxaddon.compat.spore;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Core.Sentities;
import com.Harbinger.Spore.ExtremelySusThings.SporeSavedData;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.EvolvedInfected.Scamper;
import com.Harbinger.Spore.Sentities.EvolvingInfected;
import com.Harbinger.Spore.Sentities.Variants.ScamperVariants;
import com.binaris.wizardry.api.content.entity.living.ISpellCaster;
import com.binaris.wizardry.api.content.spell.Element;
import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.content.entity.goal.AttackSpellGoal;
import com.binaris.wizardry.core.platform.Services;
import com.binaris.wizardry.setup.registries.Spells;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.core.EvolutionPick;
import com.spege.ebreduxaddon.core.InfectedTiers;
import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Zarazony mag: mag Redux przejety przez Spore (spec 0.2). Dziedziczy po Infected, wiec jest czlonkiem
 * roju (Spore nie atakuje swoich po klasie), ma glod, linkowanie z Hivemindem i cele roju za darmo.
 * Od siebie dokladamy rzucanie zaklec Redux przez jego AttackSpellGoal.
 *
 * <p>Powstaje z konwersji Spore (data/ebreduxaddon/spore_mob_conversion), ktora wola finalizeSpawn
 * z MobSpawnType.CONVERSION - tam losujemy zywiol, tier i zaklecia.
 *
 * <p>Od 0.3 to {@link EvolvingInfected}: zbiera punkty ewolucji jak kazdy podstawowy zarazony Spore
 * i po czasie z configu Spore ewoluuje, domyslnie w Mykomante (spec 0.3).
 */
public class InfectedWizardEntity extends Infected implements ISpellCaster, EvolvingInfected {

    private static final EntityDataAccessor<String> CONTINUOUS_SPELL =
            SynchedEntityData.defineId(InfectedWizardEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SPELL_COUNTER =
            SynchedEntityData.defineId(InfectedWizardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TEXTURE_INDEX =
            SynchedEntityData.defineId(InfectedWizardEntity.class, EntityDataSerializers.INT);

    public static final int TEXTURE_COUNT = 6;

    /** Nieznane id z listy ewolucji, juz zalogowane - jedno ostrzezenie na id, nie co ewolucje. */
    private static final Set<String> WARNED = new HashSet<>();

    private final WizardLoadout loadout = new WizardLoadout();

    public InfectedWizardEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        double health = SConfig.SERVER.global_health.get();
        double damage = SConfig.SERVER.global_damage.get();
        double armor = SConfig.SERVER.global_armor.get();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0 * health)
                .add(Attributes.ATTACK_DAMAGE, 4.0 * damage)
                .add(Attributes.ARMOR, 4.0 * armor)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        // Wzorzec InfectedHuman: wlasne cele, potem super - Infected dokleja cele roju i namierzanie.
        this.goalSelector.addGoal(2, new AttackSpellGoal<>(this, 0.6, 14.0f, 30, 50));
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

    // ------------------------------------------------------------------ spawn

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty,
                                        @NotNull MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        this.entityData.set(TEXTURE_INDEX, this.random.nextInt(TEXTURE_COUNT));
        if (!loadout.rolled()) {
            roll(level.getLevel());
        }
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    /**
     * 🚨 /summon z NBT (i spawner z danymi encji) NIE wola finalizeSpawn, wiec taki mag nie mialby
     * zaklec ani rozdzki. Losujemy przy pierwszym ticku serwera, jesli nic jeszcze nie losowalo.
     */
    @Override
    public void tick() {
        if (!loadout.rolled() && this.level() instanceof ServerLevel server) {
            this.entityData.set(TEXTURE_INDEX, this.random.nextInt(TEXTURE_COUNT));
            roll(server);
        }
        super.tick();
        if (!this.level().isClientSide && this.isAlive() && Config.INSTANCE.infectedEvolutionEnabled.get()) {
            // Lista idzie do naszego Evolve; ScamperVariants.VILLAGER jak u wiedzmy Spore (ludzka sylwetka).
            this.tickEvolution(this, Config.INSTANCE.infectedEvolutions.get(), ScamperVariants.VILLAGER);
        }
    }

    /** Losuje zywiol, tier i zaklecia. Publiczne dla GameTestow. */
    public void roll(ServerLevel level) {
        int tier = InfectedTiers.maxTier(hiveminds(level), protoThreshold(), this.random.nextDouble());
        loadout.roll(this.random, tier, Config.INSTANCE.infectedSpellCount.get(),
                Config.INSTANCE.infectedAlwaysSpineVolley.get());
        loadout.equipWand(this, Config.INSTANCE.infectedWandDropChance.get().floatValue());
    }

    private static int hiveminds(ServerLevel level) {
        try {
            return SporeSavedData.getDataLocation(level).getAmountOfHiveminds();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int protoThreshold() {
        try {
            return SConfig.SERVER.proto_spawn_world_mod.get();
        } catch (RuntimeException e) {
            return 3;
        }
    }

    // ------------------------------------------------------------------ ewolucja

    /**
     * Zamiast domyslnego Evolve Spore: ta sama regula (z szansa typ z listy, inaczej Scamper) i to
     * samo przeniesienie stanu, ale Mykomanta dostaje zywiol i zaklecia maga PRZED finalizeSpawn,
     * zeby nie losowala ich od nowa. Domyslne Evolve wywala sie tez na nieznanym id z listy.
     */
    @Override
    public void Evolve(Infected self, List<? extends String> ids, ScamperVariants variant) {
        if (!(this.level() instanceof ServerLevel server) || this.isRemoved()) {
            return;
        }
        List<EntityType<?>> types = resolveEvolutions(ids);
        int pick = EvolutionPick.choose(this.random.nextDouble(), Config.INSTANCE.infectedEvolveChance.get(),
                types.size(), this.random.nextDouble());
        Entity next;
        if (pick == EvolutionPick.SCAMPER) {
            Scamper scamper = new Scamper(Sentities.SCAMPER.get(), server);
            scamper.setVariant(variant);
            next = scamper;
        } else {
            next = types.get(pick).create(server);
        }
        if (next == null) {
            return;
        }
        evolveInto(server, next, pick != EvolutionPick.SCAMPER);
    }

    /** Przeniesienie stanu jak w EvolvingInfected.Evolve (Spore 2.2.0j). Publiczne dla GameTestow. */
    public void evolveInto(ServerLevel server, Entity next, boolean finalize) {
        next.moveTo(this.getX(), this.getY() + 0.5, this.getZ(), this.getYRot(), this.getXRot());
        next.setCustomName(this.getCustomName());
        if (next instanceof LivingEntity living) {
            for (MobEffectInstance effect : this.getActiveEffects()) {
                living.addEffect(new MobEffectInstance(effect));
            }
        }
        if (next instanceof Infected infected) {
            infected.setKills(this.getKills());
            infected.setEvoPoints(this.getEvoPoints());
            infected.setSearchPos(this.getSearchPos());
            infected.setLinked(this.getLinked());
        }
        if (next instanceof MycomancerEntity mycomancer) {
            mycomancer.inheritFrom(this);
        }
        if (finalize && next instanceof Infected infected) {
            infected.finalizeSpawn(server, server.getCurrentDifficultyAt(this.blockPosition()),
                    MobSpawnType.CONVERSION, null, null);
        }
        server.addFreshEntity(next);
        this.discard();
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 0.5, this.getZ(),
                2, 0.0, 0.0, 0.0, 1.0);
    }

    /**
     * Typy z listy ewolucji; nieznane id pomija z jednym ostrzezeniem. Wolane tez na starcie serwera
     * (SporeContent), zeby literowka w configu byla w logu startu, a nie przy pierwszej ewolucji.
     */
    static List<EntityType<?>> resolveEvolutions(List<? extends String> ids) {
        List<EntityType<?>> types = new ArrayList<>();
        for (String id : ids) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            EntityType<?> type = key == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(key);
            // getValue zwraca domyslny typ (pig) dla nieznanego klucza, stad containsKey.
            if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(key)) {
                if (WARNED.add(id)) {
                    EbreduxAddon.LOGGER.warn("[EbreduxAddon] config infectedWizard.evolutions: unknown entity '{}', skipped", id);
                }
                continue;
            }
            types.add(type);
        }
        return types;
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

    /** Lup idzie z naszej tabeli lupu, nie z listy configu Spore. */
    @Override
    public List<? extends String> getDropList() {
        return List.of();
    }

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
