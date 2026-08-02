package com.spege.insanetweaks.entities;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.dhanantry.scapeandrunparasites.entity.ai.EntityAIGetFollowers;
import com.dhanantry.scapeandrunparasites.entity.ai.EntityAISwimmingDiving;
import com.dhanantry.scapeandrunparasites.entity.ai.EntityAIWaterLeapAtTargetStatus;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityAICircleGroup;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.entity.monster.infected.EntityInfHuman;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.ai.EntityAISimWizardCombat;

import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.ParticleBuilder;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIOpenDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

/**
 * Assimilated wizard parasite. Spawned by {@code SrpWizardryAssimilationHelper} when an
 * {@code ebwizardry:wizard} or {@code ebwizardry:evil_wizard} is converted by SRP infection.
 *
 * Identity rules (v2):
 *  - Full-fledged SRP parasite. NO {@code EPEL_E} protection. Attacks everything non-parasite.
 *  - Inherits {@link EntityInfHuman} for SRP collective integration (CircleGroup formation,
 *    GetFollowers attraction, parasiteIDRegister, save data spawn count).
 *  - {@link #initEntityAI()} is OWN composition (does NOT call super.initEntityAI). v4: ONE
 *    combat task ({@link com.spege.insanetweaks.entities.ai.EntityAISimWizardCombat}) owns both
 *    movement and casting; there is NO melee — the wizard is a pure caster.
 *  - Cast/heal pipeline runs through real EBW NPC path: {@code spell.cast(World, EntityLiving,
 *    EnumHand, int, EntityLivingBase, SpellModifiers)}.
 *  - All balance values (HP/armor/speed multipliers, cooldowns, decision range, spell
 *    modifiers) are read from {@code ModConfig.entities.assimilatedWizard}. Hardcoded constants
 *    act as safety fallbacks if the config returns degenerate values.
 */
@SuppressWarnings({"null", "deprecation"})
public class EntitySimWizard extends EntityInfHuman implements ISpellCaster {

    /** Per-tier particle accent, indexed by {@link SimWizardTier} ordinal. */
    private static final float[][] TIER_ACCENT = {
            { 0.35F, 0.10F, 0.60F },
            { 0.45F, 0.08F, 0.55F },
            { 0.72F, 0.05F, 0.62F }
    };

    private static final byte STATUS_CAST_BURST = 61;
    private static final byte STATUS_HEAL_BURST = 62;
    private static final byte STATUS_CAST_TELEGRAPH = 63;

    private static final DataParameter<String> CONTINUOUS_SPELL = EntityDataManager.createKey(EntitySimWizard.class,
            DataSerializers.STRING);
    private static final DataParameter<Integer> SPELL_COUNTER = EntityDataManager.createKey(EntitySimWizard.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> CAST_ANIMATION_TICKS = EntityDataManager.createKey(
            EntitySimWizard.class, DataSerializers.VARINT);
    /**
     * Length of the animation window that {@link #CAST_ANIMATION_TICKS} is counting down from.
     * Used purely to normalise {@link #getCastFlashIntensity(float)} without the renderer having
     * to know the AI's window length. Previously that divisor was a hardcoded {@code 14.0F} that
     * silently had to stay in sync with EntityAISimWizardCombat.
     *
     * <p>🚨 DataParameter ids are assigned in registration order. APPEND new parameters at the END
     * of {@link #entityInit()} only - inserting one before an existing registration shifts every
     * later id and desyncs clients running an older build.
     */
    private static final DataParameter<Integer> CAST_ANIMATION_MAX = EntityDataManager.createKey(
            EntitySimWizard.class, DataSerializers.VARINT);
    /** Power tier ordinal; synced because the renderer tints glow, focus and particles by it. */
    private static final DataParameter<Integer> TIER = EntityDataManager.createKey(
            EntitySimWizard.class, DataSerializers.VARINT);

    private final List<Spell> spells = new ArrayList<Spell>(8);
    private int selfHealCooldown = 50;

    /**
     * Shared cast gate. Lives on the ENTITY rather than in one AI task because more than one task
     * can now want to cast (combat and ally support): the gate is what guarantees they never both
     * fire in the same tick, since their mutex bits deliberately do not overlap.
     *
     * <p>An absolute {@code getTotalWorldTime()} timestamp, never a countdown - a counter ticked
     * from {@code shouldExecute} would run at the AI poll rate rather than the tick rate, which is
     * the v3.1 "every cooldown was silently tripled" bug. Not persisted: a stale absolute world
     * time means nothing after a reload, and a surviving cast cooldown has no value.
     */
    private long nextCastReadyTime;

    /** True while a telegraph or channel is in flight, so no other task may start a cast. */
    private boolean castCommitted;
    /** Phase scaling factor computed on spawn. 1.0 = no bonus. Feeds health/armor only. */
    private float cachedPhaseBonus = 1.0F;
    /** SRP evolution phase seen at spawn, clamped to the configured maximum. Feeds the tier roll. */
    private int cachedSrpPhase;
    /** Attribute base values before any scaling, so re-scaling never compounds. Negative = unset. */
    private double pristineMaxHealth = -1.0D;
    private double pristineArmor = -1.0D;

    /** Per-tier loot tables. Registered in {@code InsaneTweaksMod.preInit}. */
    public static final net.minecraft.util.ResourceLocation LOOT_NOVICE =
            new net.minecraft.util.ResourceLocation(InsaneTweaksMod.MODID, "entities/sim_wizard");
    public static final net.minecraft.util.ResourceLocation LOOT_ADEPT =
            new net.minecraft.util.ResourceLocation(InsaneTweaksMod.MODID, "entities/sim_wizard_adept");
    public static final net.minecraft.util.ResourceLocation LOOT_MASTER =
            new net.minecraft.util.ResourceLocation(InsaneTweaksMod.MODID, "entities/sim_wizard_master");

    public EntitySimWizard(World worldIn) {
        super(worldIn);
        this.experienceValue = ModConfig.entities.assimilatedWizard.spawning.experienceValue;
    }

    /**
     * Per-tier loot. {@code getLootTable} is an instance method consulted at death, when the tier
     * is already known, so no drop-loop code and no NBT loot condition is needed - 1.12.2 loot
     * tables cannot read entity NBT anyway.
     */
    @Override
    protected net.minecraft.util.ResourceLocation getLootTable() {
        if (!ModConfig.entities.assimilatedWizard.spawning.enableLootTable) {
            return super.getLootTable();
        }
        switch (this.getTier()) {
            case MASTER:
                return LOOT_MASTER;
            case ADEPT:
                return LOOT_ADEPT;
            default:
                return LOOT_NOVICE;
        }
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(CONTINUOUS_SPELL, "ebwizardry:none");
        this.dataManager.register(SPELL_COUNTER, 0);
        this.dataManager.register(CAST_ANIMATION_TICKS, 0);
        // APPEND-ONLY below this line - see the CAST_ANIMATION_MAX javadoc.
        this.dataManager.register(CAST_ANIMATION_MAX, 0);
        this.dataManager.register(TIER, SimWizardTier.NOVICE.ordinal());
    }

    /**
     * Own AI composition - does NOT call super.initEntityAI(). The SRP target tasks
     * {@code EntityAINearestAttackableTargetStatus<EntityPlayer/EntityLiving>} inherited
     * from {@link com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected}'s
     * constructor CANNOT be relied on: their shouldExecute bails when
     * {@code getParasiteStatus() != 0} AND rolls rand.nextInt(10) != 0 (bytecode-verified
     * 2026-07-16) - the long-lived "wizard rarely fights" root cause. v4.1 therefore adds
     * an OWN un-gated proactive target task at priority 2.
     *
     * Tasks layout (matches {@link EntityInfHuman} but replaces the melee primary with
     * the dedicated cast task and widens the CircleGroup predicate to include sim_wizard
     * itself, so a sim_wizard treats other parasites as group-mates and forms with them).
     *
     * <p>Those inherited SRP target tasks are deliberately LEFT IN PLACE rather than stripped.
     * Removing them would mean reflectively walking {@code targetTasks.taskEntries} and
     * class-matching SRP internals - fragile across SRP versions and liable to remove our own
     * task by accident. They only ever SET an attack target, never clear one, so at worst they
     * are inert; the un-gated task at priority 2 below is what actually drives acquisition.
     */
    @Override
    protected void initEntityAI() {
        // v3.1: retaliation must never point at a fellow parasite. Even with the
        // SimWizardFactionHandler cancelling parasite<->wizard damage, a stray edge case
        // (indirect sources, other mods' damage events) must not start a parasite civil war.
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true, new Class[0]) {
            @Override
            protected boolean isSuitableTarget(@javax.annotation.Nullable EntityLivingBase target,
                    boolean includeInvincibles) {
                if (target instanceof EntityParasiteBase) {
                    return false;
                }
                return super.isSuitableTarget(target, includeInvincibles);
            }
        });

        // v4.1: OWN proactive targeting, NOT gated by SRP parasite status (the inherited
        // Status tasks return false whenever getParasiteStatus() != 0 and pass only 1 in 10
        // polls even at status 0). chance=5 keeps acquisition snappy without per-tick scans.
        this.targetTasks.addTask(2, new net.minecraft.entity.ai.EntityAINearestAttackableTarget<EntityLivingBase>(
                this, EntityLivingBase.class, 5, true, false,
                e -> EntityAISimWizardCombat.isValidSpellTarget(e, this)));

        this.tasks.addTask(0, new EntityAISwimmingDiving(this, 0.08));
        this.tasks.addTask(1, new EntityAIOpenDoor(this, true));
        this.tasks.addTask(2, new EntityAIWaterLeapAtTargetStatus(this, 0.7F, 1.5D, 3, 20, 0));

        // v4: single combat task owns movement AND casting (mutex 3). No melee — the
        // wizard is a pure caster; banish handles close quarters (spec 2026-07-10).
        this.tasks.addTask(3, new EntityAISimWizardCombat(this));

        // Ally support at priority 5 with mutex 0 - see EntityAISimWizardSupport for why nothing
        // that moves or looks can live at priority 4/5 alongside the combat task. Registered
        // unconditionally and gated inside shouldExecute, matching how InsaneTweaksMod.init
        // registers handlers, so the toggle needs no world restart.
        this.tasks.addTask(5, new com.spege.insanetweaks.entities.ai.EntityAISimWizardSupport(this));

        this.tasks.addTask(6, new EntityAICircleGroup((EntityCreature) this, 1.15D, 8, 4.0D, 10.0D, 16,
                e -> e instanceof EntityParasiteBase));

        this.tasks.addTask(7, new EntityAIGetFollowers(this, 1, 16));
        this.tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();

        com.spege.insanetweaks.config.categories.EntitiesCategory.Spawning spawning = ModConfig.entities.assimilatedWizard.spawning;
        com.spege.insanetweaks.config.categories.EntitiesCategory.Combat combat = ModConfig.entities.assimilatedWizard.combat;
        this.multiplyBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, spawning.healthMultiplier);
        this.addBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, spawning.extraHealth);
        this.multiplyBaseAttribute(SharedMonsterAttributes.ARMOR, spawning.armorMultiplier);
        this.multiplyBaseAttribute(SharedMonsterAttributes.MOVEMENT_SPEED, spawning.speedMultiplier);
        this.setMinimumBaseAttribute(SharedMonsterAttributes.FOLLOW_RANGE, combat.minFollowRange);
    }

    private void multiplyBaseAttribute(IAttribute attribute, double multiplier) {
        IAttributeInstance instance = this.getEntityAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }

    private void addBaseAttribute(IAttribute attribute, double amount) {
        IAttributeInstance instance = this.getEntityAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() + amount);
        }
    }

    private void setMinimumBaseAttribute(IAttribute attribute, double minimum) {
        IAttributeInstance instance = this.getEntityAttribute(attribute);
        if (instance != null && instance.getBaseValue() < minimum) {
            instance.setBaseValue(minimum);
        }
    }

    @Override
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, IEntityLivingData livingdata) {
        IEntityLivingData data = super.onInitialSpawn(difficulty, livingdata);
        // Order matters: the SRP phase feeds the tier roll, the tier filters the spell pool, and
        // both the tier and the phase feed the attribute scaling.
        this.readSrpPhase();
        this.rollTier();
        this.ensureSpellPool();
        this.equipVisualWand();
        this.applyScaling();
        this.selfHealCooldown = 50;
        return data;
    }

    // ------------------------------------------------------------------------
    // Tier
    // ------------------------------------------------------------------------

    public SimWizardTier getTier() {
        return SimWizardTier.fromId(this.dataManager.get(TIER));
    }

    public void setTier(SimWizardTier tier) {
        this.dataManager.set(TIER, (tier == null ? SimWizardTier.NOVICE : tier).ordinal());
    }

    /**
     * Raises the tier to at least {@code floor}, never lowers it. Called by the assimilation
     * bridge AFTER {@code onInitialSpawn}, so it re-applies the attribute scaling.
     */
    public void setTierFloor(SimWizardTier floor) {
        if (floor == null || this.world == null || this.world.isRemote) {
            return;
        }
        if (floor.ordinal() <= this.getTier().ordinal()) {
            return;
        }
        this.setTier(floor);
        // The pool may be tier-filtered, so rebuild it before re-scaling.
        this.spells.clear();
        this.ensureSpellPool();
        this.equipVisualWand();
        this.applyScaling();
    }

    /**
     * Weighted roll, repeated once per {@code phase / tierPhaseRollDivisor} extra rolls, keeping
     * the best result. The evolution phase therefore decides how LIKELY a strong wizard is, not
     * how strong each tier is - which is what keeps phase and tier from multiplying into a
     * runaway (the v2.1 "way too strong" lesson).
     */
    private void rollTier() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        com.spege.insanetweaks.config.categories.EntitiesCategory.Tiers cfg =
                ModConfig.entities.assimilatedWizard.tiers;
        int rolls = 1 + this.cachedSrpPhase / Math.max(1, cfg.tierPhaseRollDivisor);

        SimWizardTier best = SimWizardTier.NOVICE;
        for (int i = 0; i < rolls; i++) {
            SimWizardTier rolled = weightedTierRoll(cfg.tierWeights);
            if (rolled.ordinal() > best.ordinal()) {
                best = rolled;
            }
        }
        this.setTier(best);
    }

    private SimWizardTier weightedTierRoll(int[] weights) {
        SimWizardTier[] tiers = SimWizardTier.values();
        int total = 0;
        for (int i = 0; i < tiers.length; i++) {
            total += Math.max(0, arrayAt(weights, i, 0));
        }
        if (total <= 0) {
            return SimWizardTier.NOVICE;
        }
        int roll = this.rand.nextInt(total);
        for (int i = 0; i < tiers.length; i++) {
            roll -= Math.max(0, arrayAt(weights, i, 0));
            if (roll < 0) {
                return tiers[i];
            }
        }
        return SimWizardTier.NOVICE;
    }

    private static int arrayAt(int[] array, int index, int fallback) {
        return array != null && index >= 0 && index < array.length ? array[index] : fallback;
    }

    private static double arrayAt(double[] array, int index, double fallback) {
        return array != null && index >= 0 && index < array.length ? array[index] : fallback;
    }

    /**
     * v3: scale stats with the current SRP evolution phase. Done in onInitialSpawn (not
     * applyEntityAttributes) because the world reference is required to read SRPSaveData and
     * world is not bound when applyEntityAttributes runs.
     *
     * Each phase above 0 multiplies HP/armor/spell potency by
     * (1.0 + phase * phaseScalingPerPhase), capped at maxPhase. This makes late-game
     * sim_wizards feel meaningfully heavier than early-game ones without needing a full
     * tier system.
     */
    private void readSrpPhase() {
        com.spege.insanetweaks.config.categories.EntitiesCategory.Spawning cfg = ModConfig.entities.assimilatedWizard.spawning;
        this.cachedSrpPhase = 0;
        this.cachedPhaseBonus = 1.0F;
        if (!cfg.enablePhaseScaling || this.world == null || this.world.isRemote) {
            return;
        }
        try {
            com.dhanantry.scapeandrunparasites.world.SRPSaveData data =
                    com.dhanantry.scapeandrunparasites.world.SRPSaveData.get(this.world, cfg.srpSaveDataId);
            if (data == null) {
                return;
            }
            int rawPhase = data.getEvolutionPhase(cfg.srpSaveDataId) & 0xFF;
            this.cachedSrpPhase = Math.max(0, Math.min(rawPhase, cfg.phaseScalingMaxPhase));
            this.cachedPhaseBonus = (float) (1.0D + this.cachedSrpPhase * cfg.phaseScalingPerPhase);
        } catch (Exception ex) {
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks][SimWizard] Phase lookup failed: {}", ex.getMessage());
        }
    }

    /**
     * Applies the combined tier and SRP-phase scaling to health and armor.
     *
     * <p>Idempotent by construction: the pristine base values are captured on the first call and
     * every later call recomputes from them, so {@link #setTierFloor(SimWizardTier)} can re-run
     * this without compounding.
     *
     * <p>Health is the ONE stat where tier and phase deliberately multiply - it is a
     * time-to-kill knob rather than a burst knob - and it is therefore the one with a hard
     * ceiling. Spell potency deliberately does NOT compound; see {@link #getModifiers()}.
     */
    private void applyScaling() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        com.spege.insanetweaks.config.categories.EntitiesCategory.Tiers cfg =
                ModConfig.entities.assimilatedWizard.tiers;
        int tier = this.getTier().ordinal();

        IAttributeInstance health = this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        IAttributeInstance armor = this.getEntityAttribute(SharedMonsterAttributes.ARMOR);

        if (health != null) {
            if (this.pristineMaxHealth < 0.0D) {
                this.pristineMaxHealth = health.getBaseValue();
            }
            double factor = Math.min(this.cachedPhaseBonus * arrayAt(cfg.tierHealthMultipliers, tier, 1.0D),
                    cfg.maxCombinedHealthMultiplier);
            health.setBaseValue(this.pristineMaxHealth * factor);
        }
        if (armor != null) {
            if (this.pristineArmor < 0.0D) {
                this.pristineArmor = armor.getBaseValue();
            }
            armor.setBaseValue(this.pristineArmor
                    * this.cachedPhaseBonus * arrayAt(cfg.tierArmorMultipliers, tier, 1.0D));
        }
        this.setHealth(this.getMaxHealth()); // top up so the new max is visible immediately
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (this.world.isRemote) {
            this.spawnCastingAuraParticles();
        }
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();

        if (this.world.isRemote) {
            return;
        }

        this.ensureSpellPool();
        this.ensureVisualWand();
        this.tickServerCastAnimation();
        this.tickPassiveSelfHeal();
    }

    private void tickServerCastAnimation() {
        int ticks = this.getCastAnimationTicks();
        if (ticks > 0) {
            this.setCastAnimationTicks(ticks - 1);
        }
    }

    /**
     * v3.3: the pool is built from {@code ModConfig.entities.assimilatedWizard.spells.spellPool} (registry names) -
     * repertoire changes are config edits, no recompilation. Unknown ids are skipped with a
     * warning; an empty result falls back to the built-in default so the wizard is never
     * spell-less. {@code summon_*} entries are additionally gated by includeAbominationSummons.
     *
     * Historical notes on deliberately EXCLUDED spells:
     *  - insanetweaks:summon_thrall: ThrallSlotManager requires EntityPlayerMP (utility
     *    companion, not a combat minion).
     *  - insanetweaks:parasite_shroud: applyShroud is player-only and the effect (hide the
     *    player from SRP) is semantically backwards for an entity that IS a parasite.
     */
    private void ensureSpellPool() {
        if (!this.spells.isEmpty()) {
            return;
        }

        for (String id : ModConfig.entities.assimilatedWizard.spells.spellPool) {
            if (id == null || id.trim().isEmpty()) {
                continue;
            }
            Spell s = Spell.get(id.trim());
            if (s == null || s == Spells.none) {
                InsaneTweaksMod.LOGGER.warn(
                        "[InsaneTweaks][SimWizard] Unknown spell id '{}' in simWizard.spellPool - skipped.", id);
                continue;
            }
            if (!ModConfig.entities.assimilatedWizard.spells.includeAbominationSummons && s.getRegistryName() != null
                    && s.getRegistryName().getResourcePath().startsWith("summon_")) {
                continue;
            }
            if (!this.spells.contains(s)) {
                this.spells.add(s);
            }
        }

        this.applyTierSpellFilter();

        if (this.spells.isEmpty()) {
            // Config produced nothing usable - built-in fallback keeps the wizard functional.
            this.spells.add(Spells.magic_missile);
            this.spells.add(Spells.ice_shard);
            this.spells.add(Spells.force_orb);
            this.spells.add(Spells.spark_bomb);
            this.spells.add(Spells.heal);
        }

        if (ModConfig.client.enableSimWizardDebugLogs) {
            // Log the RESOLVED ROLES, not just the ids. If role resolution ever degrades - e.g.
            // EBW spell properties were not loaded yet - every situational branch in pickSpell
            // silently collapses to a random pick with no error anywhere. A line reading
            // "magic_missile -> [UNKNOWN]" makes that failure obvious at a glance.
            StringBuilder sb = new StringBuilder();
            for (Spell s : this.spells) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getRegistryName()).append(' ')
                        .append(com.spege.insanetweaks.entities.ai.SpellRoleResolver.describe(s));
            }
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks][SimWizard#{}] spell pool built ({}): [{}]",
                    this.getEntityId(), this.spells.size(), sb);
        }
    }

    /**
     * Narrows the pool to this tier's whitelist, when one is configured. An empty filter (the
     * default for all three tiers) means no restriction, so the feature costs nothing until a
     * pack opts in. A filter that would empty the pool entirely is ignored - the built-in fallback
     * below is for a broken config, not for a deliberately narrow tier.
     */
    private void applyTierSpellFilter() {
        com.spege.insanetweaks.config.categories.EntitiesCategory.Tiers cfg =
                ModConfig.entities.assimilatedWizard.tiers;
        String[] filter;
        switch (this.getTier()) {
            case ADEPT:
                filter = cfg.adeptSpellFilter;
                break;
            case MASTER:
                filter = cfg.masterSpellFilter;
                break;
            default:
                filter = cfg.noviceSpellFilter;
                break;
        }
        if (filter == null || filter.length == 0 || this.spells.isEmpty()) {
            return;
        }

        List<Spell> allowed = new ArrayList<Spell>(this.spells.size());
        for (Spell spell : this.spells) {
            if (spell.getRegistryName() == null) {
                continue;
            }
            String id = spell.getRegistryName().toString();
            for (String entry : filter) {
                if (entry != null && id.equals(entry.trim())) {
                    allowed.add(spell);
                    break;
                }
            }
        }
        if (allowed.isEmpty()) {
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks][SimWizard] Tier {} spell filter matched nothing in the pool - ignoring it.",
                    this.getTier());
            return;
        }
        this.spells.clear();
        this.spells.addAll(allowed);
    }

    /**
     * Equip a vanilla EBW master wand purely as a visual prop so the player can read the
     * sim_wizard's role at a glance. v2.1 removed the custom {@code ModItems.LIVING_WAND}
     * because it carries a {@code +40%} potency bonus through {@link com.spege.insanetweaks.items.wand.BaseCustomWandItem#calculateModifiers}
     * that, combined with our local {@code getModifiers()}, produced double-buffed casts.
     *
     * Vanilla {@code WizardryItems.master_wand} is a {@code ItemWand} whose
     * {@code calculateModifiers(ItemStack, EntityPlayer, Spell)} signature is player-only -
     * the NPC cast pipeline ({@code spell.cast(World, EntityLiving, ...)}) never invokes it,
     * so this wand stays visual-only and does not double-stack with our local modifiers.
     *
     * Drop chance is forced to 0 so killing a sim_wizard does not flood the world with
     * master wands. Mana is intentionally not filled - NPC casts do not consume wand mana.
     */
    /**
     * The focus item this wizard should be holding, by tier. Purely cosmetic - the orbiting focus
     * layer renders whatever is in the main hand, so this is a free tier tell.
     *
     * <p>All three are NECROMANCY wands: the dark palette matches the violet parasite-mage
     * identity, and being an {@code ItemWand} its {@code calculateModifiers} is player-only, so it
     * can never leak a bonus into the NPC cast pipeline (the v2.1 double-buff lesson).
     */
    protected net.minecraft.item.Item tierWandItem() {
        if (!ModConfig.entities.assimilatedWizard.tiers.enableTierVisuals) {
            return WizardryItems.master_necromancy_wand;
        }
        switch (this.getTier()) {
            case NOVICE:
                return WizardryItems.novice_necromancy_wand;
            case ADEPT:
                return WizardryItems.apprentice_necromancy_wand;
            default:
                return WizardryItems.master_necromancy_wand;
        }
    }

    protected void ensureVisualWand() {
        // 🚨 Compare against the TIER'S wand, not a fixed one. Comparing against a single item
        // while equipping a different one would re-equip every tick - 20 equipment-sync packets a
        // second, per wizard.
        ItemStack mainHand = this.getHeldItemMainhand();
        if (mainHand.isEmpty() || mainHand.getItem() != this.tierWandItem()) {
            this.equipVisualWand();
        }
    }

    protected void equipVisualWand() {
        ItemStack wand = new ItemStack(this.tierWandItem());
        // Stamp the visible spell list so wand tooltip / JEI inspection matches the AI pool.
        ArrayList<Spell> visibleSpells = new ArrayList<Spell>(this.spells);
        if (!visibleSpells.isEmpty()) {
            WandHelper.setSpells(wand, visibleSpells.toArray(new Spell[visibleSpells.size()]));
        }
        this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, wand);
        this.setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);
    }

    // ------------------------------------------------------------------------
    // Shared cast gate (see the nextCastReadyTime javadoc)
    // ------------------------------------------------------------------------

    public boolean isCastGateReady() {
        return this.world.getTotalWorldTime() >= this.nextCastReadyTime;
    }

    /**
     * Pushes the gate out by {@code ticks}. NEVER shortens an existing gate - a task that failed
     * and wants a quick retry must not be able to cancel the long cooldown another cast just paid.
     */
    public void setCastGate(int ticks) {
        int scaled = (int) Math.round(Math.max(0, ticks) * this.castGateMultiplier());
        long candidate = this.world.getTotalWorldTime() + scaled;
        if (candidate > this.nextCastReadyTime) {
            this.nextCastReadyTime = candidate;
        }
    }

    /**
     * Optionally forces SRP's remains coin flip in our favour. OFF by default - the pack wants the
     * native 50/50, so this normally does nothing and the entity dies on stock SRP terms.
     *
     * <p>{@code EntityParasiteBase} starts with {@code madeRng == -1} and, the first time the
     * parasite takes damage, rolls {@code nextInt(2)}. {@code onDeathUpdate} then branches on it:
     * 0 runs {@code dyingBurst} - whose fuse reaches {@code selfExplode()} and therefore
     * {@code spawnGore()}, placing the gore block and the {@code EntityRemain} - while 1 falls
     * through to the plain {@code onDeathUpdateOG()} and leaves nothing behind. So a stock SRP
     * parasite leaves remains only half the time.
     *
     * <p>We rewrite ONLY the losing roll, after super has run. {@code madeRng} is
     * {@code protected}, so this needs neither reflection nor a mixin.
     *
     * <p>The roll is also what triggers SRP's status-40 client effect, and super only fires it when
     * the roll came up 0 - so when we flip a 1 we have to send it ourselves, otherwise the forced
     * remains would arrive without the burst that normally announces them.
     */
    @Override
    public boolean attackEntityFrom(@Nonnull net.minecraft.util.DamageSource source, float amount) {
        boolean unrolled = this.madeRng == -1;
        boolean result = super.attackEntityFrom(source, amount);
        if (!this.world.isRemote
                && ModConfig.entities.assimilatedWizard.spawning.guaranteedRemains
                && this.madeRng == 1) {
            this.madeRng = 0;
            if (unrolled) {
                this.world.setEntityState(this, (byte) 40);
            }
        }
        return result;
    }

    /**
     * Scales every cast cooldown this entity pays. 1.0 for a plain sim_wizard.
     *
     * <p>Applied here rather than at the call sites so that EVERY path - normal casts, failed-cast
     * retries, panic, ally support - is covered by construction. A subclass that casts less can
     * therefore not be defeated by some future task calling {@code setCastGate} directly.
     */
    protected float castGateMultiplier() {
        return 1.0F;
    }

    public boolean isCastCommitted() {
        return this.castCommitted;
    }

    public void setCastCommitted(boolean committed) {
        this.castCommitted = committed;
    }

    /** Server-side hook called by {@link EntityAISimWizardCombat} after a successful cast. */
    public void signalCastBurst(int animationTicks) {
        this.startCastAnimation(animationTicks);
        this.world.setEntityState(this, STATUS_CAST_BURST);
    }

    /**
     * Server-side hook called by {@link EntityAISimWizardCombat} at the START of charge-up
     * (before the actual spell.cast). Triggers the telegraph vocalization on the client side
     * and a brief halo particle burst so the player has a tell to dodge.
     */
    public void signalCastTelegraph(int animationTicks) {
        this.startCastAnimation(animationTicks);
        this.world.setEntityState(this, STATUS_CAST_TELEGRAPH);
        // Server-side sound so attentive players hear the wind-up regardless of particles.
        this.playSound(net.minecraft.init.SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON,
                0.7F, 0.5F + this.rand.nextFloat() * 0.15F);
    }

    private void tickPassiveSelfHeal() {
        if (this.isPotionActive(electroblob.wizardry.registry.WizardryPotions.arcane_jammer)) {
            return;
        }

        if (this.selfHealCooldown > 0) {
            this.selfHealCooldown--;
            return;
        }

        if (this.getHealth() < this.getMaxHealth() && this.getHealth() > 0.0F) {
            this.heal((float) ModConfig.entities.assimilatedWizard.combat.selfHealAmount);
            this.playSound(Spells.heal.getSounds()[0], 0.7F, this.rand.nextFloat() * 0.4F + 1.0F);
            this.world.setEntityState(this, STATUS_HEAL_BURST);
        }

        this.selfHealCooldown = this.getHealth() < 10.0F
                ? ModConfig.entities.assimilatedWizard.combat.selfHealCooldownLow
                : ModConfig.entities.assimilatedWizard.combat.selfHealCooldownNormal;
    }

    private void spawnCastingAuraParticles() {
        if (!this.isCastingSpellVisual() || this.ticksExisted % 4 != 0) {
            return;
        }

        // v2.4: Abomination palette - alternating blood-red and parasite-purple instead of
        // the flat blue. Reads as "infected mage" rather than vanilla EBW wizard.
        // The purple half shifts with the tier; the blood-red half is the constant that keeps all
        // tiers recognisably the same creature.
        float[] accent = this.tierAccent();
        for (int i = 0; i < 2; i++) {
            boolean purple = (this.ticksExisted + i) % 2 == 0;
            float r = purple ? accent[0] : 0.78F;
            float g = purple ? accent[1] : 0.05F;
            float b = purple ? accent[2] : 0.10F;
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC)
                    .pos(this.posX + (this.rand.nextDouble() - 0.5D) * 0.7D,
                            this.posY + 1.1D + this.rand.nextDouble() * 0.7D,
                            this.posZ + (this.rand.nextDouble() - 0.5D) * 0.7D)
                    .clr(r, g, b)
                    .spawn(this.world);
        }
    }

    /**
     * Per-tier accent colour, indexed NOVICE / ADEPT / MASTER. Client-safe: the tier arrives via
     * DataManager sync. Indexed defensively so adding a tier can never crash the render thread.
     */
    private float[] tierAccent() {
        if (!ModConfig.entities.assimilatedWizard.tiers.enableTierVisuals) {
            return TIER_ACCENT[SimWizardTier.ADEPT.ordinal()];
        }
        return TIER_ACCENT[Math.max(0, Math.min(this.getTier().ordinal(), TIER_ACCENT.length - 1))];
    }

    private void spawnBurstParticles(float red, float green, float blue, int count) {
        for (int i = 0; i < count; i++) {
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC)
                    .pos(this.posX + (this.rand.nextDouble() - 0.5D) * this.width,
                            this.posY + 0.6D + this.rand.nextDouble() * this.height,
                            this.posZ + (this.rand.nextDouble() - 0.5D) * this.width)
                    .clr(red, green, blue)
                    .spawn(this.world);
        }
    }

    /**
     * Ring of slow-spiraling particles around the wizard's hands during the cast telegraph.
     * Tells the player "something is being cast, dodge now" before the spell actually fires.
     */
    private void spawnTelegraphParticles() {
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0D) * Math.PI * 2.0D;
            double radius = 0.8D;
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC)
                    .pos(this.posX + Math.cos(angle) * radius,
                            this.posY + 1.2D,
                            this.posZ + Math.sin(angle) * radius)
                    .clr(0.85F, 0.10F, 0.30F)
                    .spawn(this.world);
        }
    }

    @Override
    public void handleStatusUpdate(byte id) {
        if (id == STATUS_CAST_BURST) {
            // Burst tinted Abomination red/purple matching the aura.
            // v3.0: no client-side setCastAnimationTicks here - the server is the single
            // writer of CAST_ANIMATION_TICKS (set in signalCastBurst, decremented in
            // tickServerCastAnimation) and the value reaches the client via DataManager
            // sync. The old double-write caused the intensity to stutter as sync packets
            // kept overwriting the client-local value.
            float[] accent = this.tierAccent();
            this.spawnBurstParticles(
                    (0.78F + accent[0]) * 0.5F, (0.06F + accent[1]) * 0.5F, (0.32F + accent[2]) * 0.5F, 12);
            return;
        }

        if (id == STATUS_HEAL_BURST) {
            // Heal stays sickly green - distinct from offensive burst.
            this.spawnBurstParticles(0.30F, 0.78F, 0.45F, 12);
            return;
        }

        if (id == STATUS_CAST_TELEGRAPH) {
            this.spawnTelegraphParticles();
            return;
        }

        super.handleStatusUpdate(id);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("WizardSelfHealCooldown", this.selfHealCooldown);
        compound.setInteger("WizardTier", this.getTier().ordinal());
        // v3.3: the spell pool is intentionally NOT persisted anymore. ModConfig.spellPool is
        // the single source of truth, so config edits apply to already-saved wizards after a
        // world reload. The legacy "WizardSpells" NBT tag on old entities is simply ignored.
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.selfHealCooldown = compound.hasKey("WizardSelfHealCooldown")
                ? compound.getInteger("WizardSelfHealCooldown")
                : 50;

        // 🚨 The tier MUST be restored before the pool is rebuilt: ensureSpellPool filters by tier,
        // so reading this afterwards would give every loaded wizard the NOVICE filter regardless of
        // what was saved. A save from before tiers existed has no key and correctly loads as NOVICE
        // - which is why the NOVICE multipliers are all exactly 1.0.
        if (compound.hasKey("WizardTier")) {
            this.setTier(SimWizardTier.fromId(compound.getInteger("WizardTier")));
        }

        this.spells.clear();
        this.ensureSpellPool();
    }

    @Override
    @Nonnull
    public List<Spell> getSpells() {
        this.ensureSpellPool();
        return this.spells;
    }

    @Override
    @Nonnull
    public SpellModifiers getModifiers() {
        SpellModifiers modifiers = new SpellModifiers();
        // Potency scales with the TIER only, never with the SRP phase as well. The phase already
        // makes high tiers likelier (see rollTier), so folding it in here too gave a phase-4 MASTER
        // 1.175 x 1.4 x 1.5 = 2.47x potency - v2.1 was reverted for far less. One factor caps at
        // 1.76x. cachedPhaseBonus is still used for health/armor, where the compounding is wanted
        // and clamped.
        float potency = (float) (ModConfig.entities.assimilatedWizard.combat.potencyMultiplier
                * arrayAt(ModConfig.entities.assimilatedWizard.tiers.tierPotencyMultipliers,
                        this.getTier().ordinal(), 1.0D));
        modifiers.set(SpellModifiers.POTENCY, potency, true);
        modifiers.set("range", (float) ModConfig.entities.assimilatedWizard.combat.rangeMultiplier, true);
        return modifiers;
    }

    public float getPhaseBonus() {
        return this.cachedPhaseBonus;
    }

    @Override
    @Nonnull
    public Spell getContinuousSpell() {
        Spell spell = Spell.get(this.dataManager.get(CONTINUOUS_SPELL));
        return spell == null ? Spells.none : spell;
    }

    @Override
    public void setContinuousSpell(Spell spell) {
        Spell resolved = spell == null ? Spells.none : spell;
        this.dataManager.set(CONTINUOUS_SPELL,
                resolved.getRegistryName() == null ? "ebwizardry:none" : resolved.getRegistryName().toString());
    }

    @Override
    public int getSpellCounter() {
        return this.dataManager.get(SPELL_COUNTER);
    }

    @Override
    public void setSpellCounter(int count) {
        this.dataManager.set(SPELL_COUNTER, count);
    }

    @Override
    public int getAimingError(EnumDifficulty difficulty) {
        switch (difficulty) {
            case EASY:
                return 7;
            case NORMAL:
                return 4;
            case HARD:
                return 1;
            default:
                return 7;
        }
    }

    public boolean isCastingSpellVisual() {
        return this.getContinuousSpell() != Spells.none || this.getCastAnimationTicks() > 0;
    }

    public int getCastAnimationTicks() {
        return this.dataManager.get(CAST_ANIMATION_TICKS);
    }

    private void setCastAnimationTicks(int ticks) {
        this.dataManager.set(CAST_ANIMATION_TICKS, Math.max(0, ticks));
    }

    /**
     * Opens a cast-animation window: sets the countdown AND records its length so the renderer
     * can normalise without knowing the AI's constant. Server-only - the client is a pure reader
     * of both parameters (v3.0 desync rule).
     */
    private void startCastAnimation(int animationTicks) {
        int clamped = Math.max(0, animationTicks);
        this.dataManager.set(CAST_ANIMATION_TICKS, clamped);
        this.dataManager.set(CAST_ANIMATION_MAX, clamped);
    }

    /**
     * Cast intensity 0.0-1.0 used by {@code RenderSimWizard} for the cast pulse.
     * Replaces the legacy {@code getSelfeFlashIntensity} usage in the renderer (that one
     * tracks SRP infected melt progress, not casting).
     */
    public float getCastFlashIntensity(float partialTickTime) {
        int ticks = this.getCastAnimationTicks();
        if (ticks <= 0) {
            return 0.0F;
        }
        // Normalised against the window length recorded when the animation started, so the
        // renderer no longer duplicates the AI's constant.
        float normalized = ticks / (float) Math.max(1, this.dataManager.get(CAST_ANIMATION_MAX));
        if (normalized > 1.0F) {
            normalized = 1.0F;
        }
        if (normalized < 0.0F) {
            normalized = 0.0F;
        }
        return normalized;
    }

    /**
     * v3.1: SRP faction membership. Every parasite is an ally - vanilla
     * {@code EntityAITarget.isSuitableTarget} consults the task owner's
     * {@code isOnSameTeam}, so this stops OUR targeting tasks from ever picking a
     * parasite, and helps any third-party mod that respects team checks. (The reverse
     * direction - other parasites targeting us - is handled by SimWizardFactionHandler
     * cancelling the friendly-fire damage that would set their revenge targets.)
     *
     * <p>v4.2 hardening: a sim_wizard must NEVER be a player's ally. We explicitly return
     * false for any {@link EntityPlayer} before the parasite check, so no scoreboard team, no
     * EBW Ally Designation System lookup (ADS consults isOnSameTeam), and no player-assimilation
     * / COTH state can ever flip a sim_wizard onto the player's side. The wizard stays hostile to
     * players regardless of how deeply the player is integrated into the hive - it is a rogue
     * parasite-mage, a threat even to a parasitic battlemage. (Player targeting itself is
     * un-gated by COTH in {@link EntityAISimWizardCombat#isValidSpellTarget}.)
     */
    @Override
    public boolean isOnSameTeam(@Nonnull Entity other) {
        if (other instanceof EntityPlayer) {
            return false;
        }
        if (other instanceof EntityParasiteBase) {
            return true;
        }
        return super.isOnSameTeam(other);
    }

    // ------------------------------------------------------------------------
    // v2.4: caster-flavored audio override. Default EntityInfHuman uses the
    // SRPSounds.INFECTEDHUMAN_* set (flat zombie-style growl). We layer in an
    // evoker/illager flavor with a low pitch shift so the wizard sounds like a
    // parasite-magic caster, not a generic infected. Returning null from a
    // get*Sound forces the parent class to skip its own playSound.
    // ------------------------------------------------------------------------

    @Override
    protected net.minecraft.util.SoundEvent getAmbientSound() {
        return net.minecraft.init.SoundEvents.ENTITY_EVOCATION_ILLAGER_AMBIENT;
    }

    @Override
    protected net.minecraft.util.SoundEvent getHurtSound(@Nonnull net.minecraft.util.DamageSource source) {
        return net.minecraft.init.SoundEvents.ENTITY_EVOCATION_ILLAGER_HURT;
    }

    @Override
    protected net.minecraft.util.SoundEvent getDeathSound() {
        return net.minecraft.init.SoundEvents.EVOCATION_ILLAGER_DEATH;
    }

    @Override
    protected float getSoundPitch() {
        // Pitched down (~0.55-0.75) for that "infected caster" feel.
        return 0.55F + this.rand.nextFloat() * 0.20F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.05F;
    }
}
