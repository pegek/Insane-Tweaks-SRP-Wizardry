package com.spege.insanetweaks.entities.ai;

import java.util.ArrayList;
import java.util.List;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySimWizard;

import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Heals wounded parasite allies around the wizard. The "parasite collective looks after its own"
 * behaviour, and the one piece of the combat-reaction package that legitimately belongs in its own
 * AI task rather than inside {@link EntityAISimWizardCombat}.
 *
 * <h3>Why mutex 0</h3>
 * In 1.12.2 {@code EntityAITasks} only lets a LOWER-numbered task interrupt a running one whose
 * mutex bits overlap. The combat task sits at priority 3 with mutex 3 (movement + look) and is
 * active whenever a valid target exists, so anything registered at priority 4 or 5 that claimed
 * either bit would simply never run. This task therefore claims NOTHING: it does not path, does not
 * turn the head, and casts a spell centred on the caster. Movement stays exclusively the combat
 * task's business, which is what keeps the v3.x "two tasks fighting over the navigator" failure
 * mode from coming back.
 *
 * <p>Because the two tasks can now be "running" in the same tick, the thing that stops them both
 * casting is the shared gate on the entity ({@code isCastGateReady} / {@code isCastCommitted}),
 * not the mutex.
 *
 * <h3>Why group_heal and not heal</h3>
 * EBW's {@code SpellBuff} NPC overload calls {@code applyEffects(caster, modifiers)} and discards
 * the target entirely, so {@code ebwizardry:heal} can only ever heal the wizard itself. Healing
 * anyone else needs an {@link SpellRole#ALLY_HEAL} spell - a {@code SpellAreaEffect} such as
 * {@code ebwizardry:group_heal}, which resolves its allies through EBW's Ally Designation System.
 * That in turn consults {@code isOnSameTeam}, which {@code EntitySimWizard} already answers true
 * for every {@code EntityParasiteBase}.
 */
public class EntityAISimWizardSupport extends EntityAIBase {

    private final EntitySimWizard wizard;

    /**
     * Absolute world time before the next support cast. A timestamp rather than a countdown: this
     * task's {@code shouldExecute} is polled on the AI tick rate, so a decremented counter would
     * run at a third of the intended speed (the v3.1 lesson).
     */
    private long nextSupportReadyTime;

    public EntityAISimWizardSupport(EntitySimWizard wizard) {
        this.wizard = wizard;
        this.setMutexBits(0);
    }

    private static com.spege.insanetweaks.config.categories.EntitiesCategory.Tuning tuning() {
        return ModConfig.entities.assimilatedWizard.tuning;
    }

    @Override
    public boolean shouldExecute() {
        // The enable flag is read HERE rather than gating registration, so toggling it takes
        // effect without a world restart - the task list is built once per entity construction.
        if (!tuning().enableAllySupport) {
            return false;
        }
        if (this.wizard.world.getTotalWorldTime() < this.nextSupportReadyTime) {
            return false;
        }
        if (!this.wizard.isCastGateReady() || this.wizard.isCastCommitted()) {
            return false;
        }
        return countWoundedAllies() >= tuning().supportMinWoundedAllies;
    }

    /** One-shot: everything happens in {@link #startExecuting()}. */
    @Override
    public boolean shouldContinueExecuting() {
        return false;
    }

    @Override
    public void startExecuting() {
        List<Spell> pool = this.wizard.getSpells();
        List<Spell> candidates = new ArrayList<Spell>(pool.size());
        SpellRoleResolver.collect(pool, SpellRole.ALLY_HEAL, candidates);
        if (candidates.isEmpty()) {
            // No ally-heal in the pool at all. Back off for a full cooldown rather than rescanning
            // the area every poll for something that cannot be there.
            this.nextSupportReadyTime = this.wizard.world.getTotalWorldTime() + tuning().supportCooldownTicks;
            return;
        }

        Spell spell = candidates.get(this.wizard.getRNG().nextInt(candidates.size()));

        // The area effect is centred on the caster, so the wizard is both caster and target.
        SimWizardCastPipeline.Outcome outcome =
                SimWizardCastPipeline.fire(this.wizard, spell, this.wizard, this.wizard.getModifiers());

        long now = this.wizard.world.getTotalWorldTime();
        if (outcome == SimWizardCastPipeline.Outcome.CAST) {
            this.wizard.signalCastBurst(tuning().castAnimationTicks);
            // Consume the shared gate too, so the combat task does not immediately cast on top.
            this.wizard.setCastGate(tuning().castAnimationTicks);
            this.nextSupportReadyTime = now + tuning().supportCooldownTicks;
            if (ModConfig.client.enableSimWizardDebugLogs) {
                InsaneTweaksMod.LOGGER.info("[InsaneTweaks][SimWizard#{}] ally support -> {}",
                        this.wizard.getEntityId(), spell.getRegistryName());
            }
        } else {
            // Vetoed or refused - retry sooner than a full support cooldown, but not next tick.
            this.nextSupportReadyTime = now + tuning().eventBlockCooldownTicks;
        }
    }

    /**
     * Counts nearby parasites below the wounded threshold. Excludes the wizard itself - its own
     * health is the combat task's low-HP branch, not this one's business.
     */
    private int countWoundedAllies() {
        double radius = tuning().supportRadius;
        float threshold = tuning().supportAllyHealthPercent / 100.0F;

        AxisAlignedBB box = this.wizard.getEntityBoundingBox().grow(radius);
        List<EntityParasiteBase> nearby =
                this.wizard.world.getEntitiesWithinAABB(EntityParasiteBase.class, box);

        int wounded = 0;
        for (EntityParasiteBase ally : nearby) {
            if (ally == this.wizard || !ally.isEntityAlive()) {
                continue;
            }
            float max = ally.getMaxHealth();
            if (max > 0.0F && ally.getHealth() / max <= threshold) {
                wounded++;
            }
        }
        return wounded;
    }
}
