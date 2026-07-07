package com.spege.insanetweaks.entities;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.dhanantry.scapeandrunparasites.entity.ai.EntityAIAttackMeleeStatus;
import com.dhanantry.scapeandrunparasites.entity.ai.EntityAIGetFollowers;
import com.dhanantry.scapeandrunparasites.entity.ai.EntityAISwimmingDiving;
import com.dhanantry.scapeandrunparasites.entity.ai.EntityAIWaterLeapAtTargetStatus;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityAICircleGroup;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.entity.monster.infected.EntityInfHuman;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.ai.EntityAISimWizardCast;

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
 *  - {@link #initEntityAI()} is OWN composition (does NOT call super.initEntityAI). The SRP
 *    melee task is replaced by {@link EntityAISimWizardCast} as the primary attack task and
 *    demoted melee acts as a fallback when no spell can be cast.
 *  - Cast/heal pipeline runs through real EBW NPC path: {@code spell.cast(World, EntityLiving,
 *    EnumHand, int, EntityLivingBase, SpellModifiers)}.
 *  - All balance values (HP/armor/speed multipliers, cooldowns, decision range, spell
 *    modifiers) are read from {@code ModConfig.entities.assimilatedWizard}. Hardcoded constants
 *    act as safety fallbacks if the config returns degenerate values.
 */
@SuppressWarnings({"null", "deprecation"})
public class EntitySimWizard extends EntityInfHuman implements ISpellCaster {

    private static final byte STATUS_CAST_BURST = 61;
    private static final byte STATUS_HEAL_BURST = 62;
    private static final byte STATUS_CAST_TELEGRAPH = 63;

    private static final DataParameter<String> CONTINUOUS_SPELL = EntityDataManager.createKey(EntitySimWizard.class,
            DataSerializers.STRING);
    private static final DataParameter<Integer> SPELL_COUNTER = EntityDataManager.createKey(EntitySimWizard.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> CAST_ANIMATION_TICKS = EntityDataManager.createKey(
            EntitySimWizard.class, DataSerializers.VARINT);

    private final List<Spell> spells = new ArrayList<Spell>(8);
    private int selfHealCooldown = 50;
    /** Phase scaling factor applied on spawn. 1.0 = no bonus. Used by getModifiers(). */
    private float cachedPhaseBonus = 1.0F;

    public EntitySimWizard(World worldIn) {
        super(worldIn);
        this.experienceValue = 0;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(CONTINUOUS_SPELL, "ebwizardry:none");
        this.dataManager.register(SPELL_COUNTER, 0);
        this.dataManager.register(CAST_ANIMATION_TICKS, 0);
    }

    /**
     * Own AI composition - does NOT call super.initEntityAI(). The SRP target tasks
     * {@code EntityAINearestAttackableTargetStatus<EntityPlayer/EntityLiving>} are added
     * by {@link com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected}'s
     * constructor (priority 4) and are automatically inherited - sim_wizard already attacks
     * everything non-parasite via that path.
     *
     * Tasks layout (matches {@link EntityInfHuman} but replaces the melee primary with
     * the dedicated cast task and widens the CircleGroup predicate to include sim_wizard
     * itself, so a sim_wizard treats other parasites as group-mates and forms with them).
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

        this.tasks.addTask(0, new EntityAISwimmingDiving(this, 0.08));
        this.tasks.addTask(1, new EntityAIOpenDoor(this, true));
        this.tasks.addTask(2, new EntityAIWaterLeapAtTargetStatus(this, 0.7F, 1.5D, 3, 20, 0));

        this.tasks.addTask(3, new EntityAISimWizardCast(this));

        // v3.2: caster movement discipline. Kite (priority 4, mutex 1) owns combat movement -
        // retreat when crowded, approach when too far, otherwise stand and cast. It yields
        // only when the target is within 3 blocks, which is the ONLY window in which the
        // SRP melee task below (priority 5, conflicting mutex) can run - so the wizard claws
        // exclusively when cornered instead of sprinting into fist range between casts.
        this.tasks.addTask(4, new com.spege.insanetweaks.entities.ai.EntityAISimWizardKite(this, 1.0D));
        this.tasks.addTask(5, new EntityAIAttackMeleeStatus(this, 1.2D, false, 0.0D));

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
        this.ensureSpellPool();
        this.equipVisualWand();
        this.applyPhaseScaling();
        this.selfHealCooldown = 50;
        return data;
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
    private void applyPhaseScaling() {
        com.spege.insanetweaks.config.categories.EntitiesCategory.Spawning cfg = ModConfig.entities.assimilatedWizard.spawning;
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
            int clampedPhase = Math.min(rawPhase, cfg.phaseScalingMaxPhase);
            if (clampedPhase <= 0) {
                return;
            }
            double bonus = 1.0D + clampedPhase * cfg.phaseScalingPerPhase;
            this.multiplyBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, bonus);
            this.multiplyBaseAttribute(SharedMonsterAttributes.ARMOR, bonus);
            this.setHealth(this.getMaxHealth()); // top up so the new max is visible immediately
            this.cachedPhaseBonus = (float) bonus;
        } catch (Exception ex) {
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks][SimWizard] Phase scaling failed: {}", ex.getMessage());
        }
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

        if (this.spells.isEmpty()) {
            // Config produced nothing usable - built-in fallback keeps the wizard functional.
            this.spells.add(Spells.magic_missile);
            this.spells.add(Spells.ice_shard);
            this.spells.add(Spells.force_orb);
            this.spells.add(Spells.spark_bomb);
            this.spells.add(Spells.heal);
        }
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
    private void ensureVisualWand() {
        ItemStack mainHand = this.getHeldItemMainhand();
        if (mainHand.isEmpty() || mainHand.getItem() != WizardryItems.master_necromancy_wand) {
            this.equipVisualWand();
        }
    }

    private void equipVisualWand() {
        // v3.2: master NECROMANCY wand - its dark palette matches the violet parasite-mage
        // identity better than the plain master wand. Still purely visual (player-only
        // calculateModifiers is never invoked by the NPC cast pipeline).
        ItemStack wand = new ItemStack(WizardryItems.master_necromancy_wand);
        // Stamp the visible spell list so wand tooltip / JEI inspection matches the AI pool.
        ArrayList<Spell> visibleSpells = new ArrayList<Spell>(this.spells);
        if (!visibleSpells.isEmpty()) {
            WandHelper.setSpells(wand, visibleSpells.toArray(new Spell[visibleSpells.size()]));
        }
        this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, wand);
        this.setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);
    }

    /** Server-side hook called by {@link EntityAISimWizardCast} after a successful cast. */
    public void signalCastBurst(int animationTicks) {
        this.setCastAnimationTicks(animationTicks);
        this.world.setEntityState(this, STATUS_CAST_BURST);
    }

    /**
     * Server-side hook called by {@link EntityAISimWizardCast} at the START of charge-up
     * (before the actual spell.cast). Triggers the telegraph vocalization on the client side
     * and a brief halo particle burst so the player has a tell to dodge.
     */
    public void signalCastTelegraph(int animationTicks) {
        this.setCastAnimationTicks(animationTicks);
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
        for (int i = 0; i < 2; i++) {
            boolean purple = (this.ticksExisted + i) % 2 == 0;
            float r = purple ? 0.45F : 0.78F;
            float g = purple ? 0.08F : 0.05F;
            float b = purple ? 0.55F : 0.10F;
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC)
                    .pos(this.posX + (this.rand.nextDouble() - 0.5D) * 0.7D,
                            this.posY + 1.1D + this.rand.nextDouble() * 0.7D,
                            this.posZ + (this.rand.nextDouble() - 0.5D) * 0.7D)
                    .clr(r, g, b)
                    .spawn(this.world);
        }
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
            this.spawnBurstParticles(0.78F, 0.06F, 0.32F, 12);
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
        // Phase bonus also folds into spell potency so late-game sim_wizards hit harder.
        float potency = (float) ModConfig.entities.assimilatedWizard.combat.potencyMultiplier * this.cachedPhaseBonus;
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
     * Cast intensity 0.0-1.0 used by {@code RenderSimWizard} for the cast pulse.
     * Replaces the legacy {@code getSelfeFlashIntensity} usage in the renderer (that one
     * tracks SRP infected melt progress, not casting).
     */
    public float getCastFlashIntensity(float partialTickTime) {
        int ticks = this.getCastAnimationTicks();
        if (ticks <= 0) {
            return 0.0F;
        }
        // 14 is the value passed in by EntityAISimWizardCast.
        float normalized = ticks / 14.0F;
        if (normalized > 1.0F) {
            normalized = 1.0F;
        }
        if (normalized < 0.0F) {
            normalized = 0.0F;
        }
        return normalized;
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        return super.attackEntityAsMob(entityIn);
    }

    /**
     * v3.1: SRP faction membership. Every parasite is an ally - vanilla
     * {@code EntityAITarget.isSuitableTarget} consults the task owner's
     * {@code isOnSameTeam}, so this stops OUR targeting tasks from ever picking a
     * parasite, and helps any third-party mod that respects team checks. (The reverse
     * direction - other parasites targeting us - is handled by SimWizardFactionHandler
     * cancelling the friendly-fire damage that would set their revenge targets.)
     */
    @Override
    public boolean isOnSameTeam(@Nonnull Entity other) {
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
