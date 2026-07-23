package com.spege.insanetweaks.spells;

import java.util.function.Function;

import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;

import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.SpellMinion;
import electroblob.wizardry.util.BlockUtils;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Shared spawn loop for InsaneTweaks SRP summon spells.
 *
 * <p>Owns the logic every summon spell used to copy-paste: floor-space search
 * (with the flying variant), caster wiring, COTH-immunity safety, lifetime and
 * health scaling. Per-spell differences go through three hooks:
 * <ul>
 *   <li>{@link #getSummonCount(SpellModifiers)} — how many minions to spawn;</li>
 *   <li>{@link #appliesAttackDamageModifier()} — whether potency also scales
 *       the minion's melee attack-damage attribute;</li>
 *   <li>{@link #customizeMinion(EntitySummonedCreature, World, SpellModifiers)}
 *       — per-spell minion configuration, called before the attribute
 *       modifiers are applied (same order the old copies used).</li>
 * </ul>
 */
@SuppressWarnings("null")
public abstract class AbstractSrpSummonSpell<T extends EntitySummonedCreature> extends SpellMinion<T> {

    public AbstractSrpSummonSpell(String modID, String name, Function<World, T> minionFactory) {
        super(modID, name, minionFactory);
    }

    @Override
    protected boolean spawnMinions(World world, EntityLivingBase caster, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        int totalCount = this.getSummonCount(modifiers);

        for (int i = 0; i < totalCount; i++) {
            int summonRadius = this.getProperty(SUMMON_RADIUS).intValue();
            BlockPos pos = BlockUtils.findNearbyFloorSpace(caster, summonRadius, summonRadius * 2);

            if (this.flying) {
                if (pos != null) {
                    pos = pos.up(2);
                } else {
                    pos = caster.getPosition().add(world.rand.nextInt(summonRadius * 2) - summonRadius, 2,
                            world.rand.nextInt(summonRadius * 2) - summonRadius);
                }
            } else if (pos == null) {
                return false;
            }

            T minion = this.createMinion(world, caster, modifiers);
            minion.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            minion.setCaster(caster);
            SummonInfectionSafetyHelper.onSummonServerTick(minion);
            minion.setLifetime((int) (this.getProperty(MINION_LIFETIME).floatValue()
                    * modifiers.get(WizardryItems.duration_upgrade)));

            this.customizeMinion(minion, world, modifiers);

            if (this.appliesAttackDamageModifier()) {
                IAttributeInstance damage = minion.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
                if (damage != null) {
                    damage.applyModifier(new AttributeModifier(POTENCY_ATTRIBUTE_MODIFIER,
                            modifiers.get(POTENCY_ATTRIBUTE_MODIFIER) - 1.0F, 2));
                }
            }

            IAttributeInstance health = minion.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (health != null) {
                health.applyModifier(new AttributeModifier(HEALTH_MODIFIER, modifiers.get(HEALTH_MODIFIER) - 1.0F, 2));
                minion.setHealth(minion.getMaxHealth());
            }

            this.addMinionExtras(minion, pos, caster, modifiers, i);
            world.spawnEntity(minion);
        }

        return true;
    }

    /**
     * Number of minions to spawn. Default: the MINION_COUNT spell property
     * plus the flat wand-upgrade bonus.
     */
    protected int getSummonCount(SpellModifiers modifiers) {
        return this.getProperty(MINION_COUNT).intValue() + getFlatMinionCountBonus(modifiers);
    }

    /**
     * Whether the potency modifier is also applied to the minion's melee
     * attack-damage attribute. Default false; ranged/caster minions scale
     * their damage through {@link #customizeMinion} instead.
     */
    protected boolean appliesAttackDamageModifier() {
        return false;
    }

    /**
     * Per-spell minion configuration (potency multipliers, loadouts, ...).
     * Runs after caster/lifetime wiring and before attribute modifiers.
     */
    protected void customizeMinion(T minion, World world, SpellModifiers modifiers) {
        // default: no per-spell configuration
    }

    protected static int getFlatMinionCountBonus(SpellModifiers modifiers) {
        return Math.max(0, Math.round(modifiers.get(MINION_COUNT)) - 1);
    }
}
