package com.spege.insanetweaks.mixins;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.entity.living.ISummonedCreature;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Fixes an interaction between the Enigmatic Legacy "Cursed Ring" and
 * Electroblob's Wizardry summoned creatures.
 *
 * The Cursed Ring iterates nearby EntityLiving mobs each tick and calls
 * setAttackTarget(player) on any mob that has no active target. Since
 * EntitySummonedCreature does not override isOnSameTeam(), the ring's
 * guard check ("if neutral.isOnSameTeam(player) continue;") always returns
 * false for our minions — causing them to be re-targeted against their own
 * owner every tick.
 *
 * This Mixin overrides isOnSameTeam() on EntitySummonedCreature so that:
 *   1. The minion reports being "on the same team" as its caster.
 *   2. The minion reports being "on the same team" as anyone on the caster's team.
 *   3. Two minions with the same caster report being "on the same team" as each other.
 *
 * Controlled by ModConfig.tweaks.enableCursedRingFix (default: true).
 */
@Mixin(value = EntitySummonedCreature.class, remap = false)
public abstract class MixinEntitySummonedCreature extends EntityCreature {
    @Unique
    private static int INSANETWEAKS$TEAM_DEBUG_LOGS = 0;

    protected MixinEntitySummonedCreature(World worldIn) {
        super(worldIn);
    }

    /**
     * Dev-name path used in deobfuscated environments.
     */
    public boolean isOnSameTeam(@Nullable Entity entityIn) {
        this.insanetweaks$debugEntry("isOnSameTeam", entityIn);
        return this.insanetweaks$checkSummonTeam(entityIn);
    }

    /**
     * SRG/runtime path used by reobfuscated 1.12.2 mod jars.
     */
    public boolean func_184191_r(@Nullable Entity entityIn) {
        this.insanetweaks$debugEntry("func_184191_r", entityIn);
        return this.insanetweaks$checkSummonTeam(entityIn);
    }

    @Unique
    private boolean insanetweaks$checkSummonTeam(@Nullable Entity entityIn) {
        // Guard against null first — super.isOnSameTeam() is @Nonnull and would NPE.
        if (entityIn == null) {
            this.insanetweaks$debugDecision("entityIn == null", null, false);
            return false;
        }

        // Respect the config toggle — if the fix is disabled, use the vanilla path.
        if (!ModConfig.tweaks.enableCursedRingFix) {
            boolean result = super.isOnSameTeam(entityIn);
            this.insanetweaks$debugDecision("config disabled -> super", entityIn, result);
            return result;
        }

        ISummonedCreature self = (ISummonedCreature) (Object) this;
        EntityLivingBase caster = self.getCaster();

        if (caster != null) {
            // Case 1: The queried entity IS the caster (direct owner check).
            if (entityIn == caster) {
                this.insanetweaks$debugDecision("entity is caster", entityIn, true);
                return true;
            }

            // Case 2: The queried entity is on the same Minecraft team as our caster.
            if (caster.isOnSameTeam(entityIn)) {
                this.insanetweaks$debugDecision("caster.isOnSameTeam(entity)", entityIn, true);
                return true;
            }

            // Case 3: The queried entity is another summoned creature with the same (or allied) caster.
            if (entityIn instanceof ISummonedCreature) {
                EntityLivingBase otherCaster = ((ISummonedCreature) entityIn).getCaster();

                if (otherCaster != null && (otherCaster == caster || caster.isOnSameTeam(otherCaster))) {
                    this.insanetweaks$debugDecision("other summon has same/allied caster", entityIn, true);
                    return true;
                }
            }
        }

        // Fallback to vanilla team logic (scoreboards, etc.).
        boolean result = super.isOnSameTeam(entityIn);
        this.insanetweaks$debugDecision("fallback -> super", entityIn, result);
        return result;
    }

    @Unique
    private void insanetweaks$debugEntry(String methodName, @Nullable Entity entityIn) {
        if (!ModConfig.client.displayDebugInfo || !(entityIn instanceof EntityPlayer)
                || INSANETWEAKS$TEAM_DEBUG_LOGS >= 40) {
            return;
        }

        INSANETWEAKS$TEAM_DEBUG_LOGS++;
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks][SummonTeamDebug] {} called for summon={} target={}",
                methodName, this.getClass().getName(), entityIn.getClass().getName());
    }

    @Unique
    private void insanetweaks$debugDecision(String reason, @Nullable Entity entityIn, boolean result) {
        if (!ModConfig.client.displayDebugInfo || !(entityIn instanceof EntityPlayer)
                || INSANETWEAKS$TEAM_DEBUG_LOGS >= 40) {
            return;
        }

        INSANETWEAKS$TEAM_DEBUG_LOGS++;
        ISummonedCreature self = (ISummonedCreature) (Object) this;
        EntityLivingBase caster = self.getCaster();
        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][SummonTeamDebug] reason='{}' result={} summon={} caster={} target={}",
                reason,
                result,
                this.getClass().getName(),
                caster == null ? "null" : caster.getClass().getName(),
                entityIn == null ? "null" : entityIn.getClass().getName());
    }
}
