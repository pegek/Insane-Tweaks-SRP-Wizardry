package com.spege.insanetweaks.entities.ai;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySentinel;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;

/**
 * F1 (spec 2026-07-10): the Sentinel's single combat task, replacing ASC's
 * EntityAIBattlemageMelee + EntityAIBattlemageSpellcasting (which cast too rarely and
 * were outside our control). Battlemage variant of the sim-wizard v4 state machine:
 *
 *   distance > MELEE_RANGE : casting stance — approach to CAST_RANGE, cast on cadence
 *   distance <= MELEE_RANGE: melee — navigate + swing with attackEntityAsMob
 *
 * Shield use stays in ASC's EntityAIBlockWithShield (task 2, separate mutex — works today).
 * No telegraph — telegraph exists to give the PLAYER a dodge window against an enemy
 * caster; the Sentinel is an ally, a wind-up would only nerf it (deliberate deviation
 * recorded in the plan).
 */
public class EntityAISentinelCombat extends EntityAIBase {

    private static final double MELEE_RANGE = 6.0D;
    private static final double MELEE_REACH_SQ = 2.5D * 2.5D;
    private static final double CAST_RANGE = 14.0D;
    private static final int MELEE_SWING_COOLDOWN = 20;
    private static final int BASE_CAST_COOLDOWN = 60;
    private static final int MAX_SPELL_COOLDOWN_BONUS = 80;
    private static final int FAILED_CAST_COOLDOWN = 10;
    private static final int REPATH_INTERVAL = 10;
    private static final double MOVE_SPEED = 1.0D;
    private static final float LOW_HP_FRACTION = 0.35F;

    private final EntitySentinel sentinel;
    private long nextCastReadyTime;
    private int meleeSwingTimer;
    private int repathTimer;

    public EntityAISentinelCombat(EntitySentinel sentinel) {
        this.sentinel = sentinel;
        this.setMutexBits(3);
    }

    private void logDiag(String message) {
        if (!ModConfig.client.enableSentinelDebugLogs) return;
        com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][Sentinel#{}] {}", this.sentinel.getEntityId(), message);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = this.sentinel.getAttackTarget();
        return target != null && target.isEntityAlive();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void resetTask() {
        this.sentinel.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = this.sentinel.getAttackTarget();
        if (target == null) return;

        this.sentinel.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        if (this.meleeSwingTimer > 0) this.meleeSwingTimer--;

        double distance = this.sentinel.getDistance(target);

        if (distance <= MELEE_RANGE) {
            tickMelee(target, distance);
        } else {
            tickCasting(target, distance);
        }
    }

    private void tickMelee(EntityLivingBase target, double distance) {
        if (distance * distance > MELEE_REACH_SQ) {
            repathTo(target);
            return;
        }
        this.sentinel.getNavigator().clearPath();
        if (this.meleeSwingTimer <= 0) {
            this.sentinel.swingArm(EnumHand.MAIN_HAND);
            this.sentinel.attackEntityAsMob(target);
            this.meleeSwingTimer = MELEE_SWING_COOLDOWN;
            logDiag("melee swing at " + target.getName());
        }
    }

    private void tickCasting(EntityLivingBase target, double distance) {
        if (distance > CAST_RANGE) {
            repathTo(target);
        } else {
            this.sentinel.getNavigator().clearPath();
        }

        if (this.sentinel.world.getTotalWorldTime() < this.nextCastReadyTime) return;
        if (distance > CAST_RANGE) return;

        Spell spell = pickSpell();
        logDiag("pickSpell -> " + (spell == null ? "null" : String.valueOf(spell.getRegistryName()))
                + " (dist " + String.format("%.1f", distance) + ")");
        if (spell == null || spell == Spells.none) {
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }

        // Heal casts target the sentinel itself; everything else targets the enemy.
        EntityLivingBase castTarget = isHealSpell(spell) ? this.sentinel : target;

        SpellModifiers modifiers = this.sentinel.getModifiers();
        if (MinecraftForge.EVENT_BUS.post(
                new SpellCastEvent.Pre(SpellCastEvent.Source.NPC, spell, this.sentinel, modifiers))) {
            // Some OTHER mod's listener vetoed this NPC cast — log which spell so pack
            // conflicts (tier gates, suppression artefacts, class-spell locks) are visible.
            logDiag("cast VETOED by another mod's Pre listener (" + spell.getRegistryName() + ")");
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }

        boolean cast = spell.cast(this.sentinel.world, this.sentinel, EnumHand.MAIN_HAND, 0, castTarget, modifiers);
        logDiag("cast " + spell.getRegistryName() + " -> " + cast);
        if (!cast) {
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }
        MinecraftForge.EVENT_BUS.post(
                new SpellCastEvent.Post(SpellCastEvent.Source.NPC, spell, this.sentinel, modifiers));
        this.sentinel.swingArm(EnumHand.MAIN_HAND);
        setCooldown(BASE_CAST_COOLDOWN + Math.min(spell.getCooldown() / 2, MAX_SPELL_COOLDOWN_BONUS));
    }

    private void repathTo(EntityLivingBase target) {
        if (--this.repathTimer > 0) return;
        this.repathTimer = REPATH_INTERVAL;
        this.sentinel.getNavigator().tryMoveToEntityLiving(target, MOVE_SPEED);
    }

    private void setCooldown(int ticks) {
        this.nextCastReadyTime = this.sentinel.world.getTotalWorldTime() + Math.max(0, ticks);
    }

    /**
     * Low HP -> heal/summon self-care; otherwise a random pick among offensive pool
     * entries (heal and summons excluded). The sentinel's pool is randomized per spawn
     * (ADVANCED/MASTER tier), so name-keyed distance bands would not generalize — the
     * low-HP branch plus offensive filtering is the part that matters.
     */
    private Spell pickSpell() {
        List<Spell> pool = this.sentinel.getSpells();
        if (pool.isEmpty()) return null;

        if (this.sentinel.getHealth() / this.sentinel.getMaxHealth() <= LOW_HP_FRACTION) {
            List<Spell> selfCare = new ArrayList<Spell>(3);
            for (Spell s : pool) {
                if (s == null || s.getRegistryName() == null) continue;
                String path = s.getRegistryName().getResourcePath();
                if (path.equals("heal") || path.startsWith("summon_")) selfCare.add(s);
            }
            if (!selfCare.isEmpty()) {
                return selfCare.get(this.sentinel.getRNG().nextInt(selfCare.size()));
            }
        }

        List<Spell> offensive = new ArrayList<Spell>(pool.size());
        for (Spell s : pool) {
            if (s == null || s == Spells.none || s.getRegistryName() == null) continue;
            String path = s.getRegistryName().getResourcePath();
            if (path.equals("heal") || path.startsWith("summon_")) continue;
            offensive.add(s);
        }
        if (offensive.isEmpty()) {
            return pool.get(this.sentinel.getRNG().nextInt(pool.size()));
        }
        return offensive.get(this.sentinel.getRNG().nextInt(offensive.size()));
    }

    private static boolean isHealSpell(Spell spell) {
        return spell != null && spell.getRegistryName() != null
                && "heal".equalsIgnoreCase(spell.getRegistryName().getResourcePath());
    }
}
