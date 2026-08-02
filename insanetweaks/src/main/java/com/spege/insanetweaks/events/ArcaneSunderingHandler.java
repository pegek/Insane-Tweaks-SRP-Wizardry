package com.spege.insanetweaks.events;

import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.ArcaneSunderingCategory;
import com.spege.insanetweaks.util.AdvPropertyResolver;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Arcane Sundering: part of every melee hit is re-dealt as true damage, and part is converted from
 * physical to magic.
 *
 * <h3>Why this takes two events</h3>
 * Vanilla runs one reduction pipeline per hit, and both events sit inside it:
 * {@code LivingHurtEvent} fires <i>before</i> armour, Resistance and absorption are applied, and
 * {@code LivingDamageEvent} fires <i>after</i> all three, immediately before the health is written.
 * So the split is bookkeeping across the pair - subtract the two shares from the raw hit on the way
 * in, and add them back on the way out, where nothing further can reduce them.
 *
 * <h3>Why not simply deal a second attack</h3>
 * The obvious implementation - {@code attackEntityFrom} with a bypassing damage source - cannot
 * work from inside this pipeline. {@code EntityLivingBase.attackEntityFrom} sets
 * {@code hurtResistantTime} to the full window <b>before</b> calling {@code damageEntity}, so a
 * nested attack made while we are inside {@code damageEntity} always lands in the invulnerability
 * branch, where {@code amount <= lastDamage} makes it return false. A 10% follow-up is by
 * definition smaller than the hit that spawned it, so it would be discarded every single time,
 * silently.
 *
 * <h3>Carrying the split between the two events</h3>
 * The pending shares live on the <b>target's</b> entity data rather than in a static field. That is
 * what makes re-entrancy a non-issue: a handler running after ours can damage some other entity
 * mid-event, producing a nested hurt/damage pair, and because the carry is keyed by the entity it
 * belongs to, the nested pair cannot consume ours. The tags are stamped with the world tick and the
 * attacker's entity id and validated on the way out, so a carry orphaned by another mod cancelling
 * the hit between the two events can never be applied to an unrelated later hit; and they are
 * cleared on every {@code LivingDamageEvent} that sees them, so nothing accumulates.
 *
 * <h3>Known limits</h3>
 * The magic share is reduced by Resistance and by Protection, mirroring
 * {@code applyPotionDamageCalculations} for {@code DamageSource.MAGIC}, but it does not consume
 * absorption - absorption was already settled before {@code LivingDamageEvent}. It is also not a
 * real magic-typed attack, so a mod watching for incoming magic damage will not see it; the nested
 * attack that would make it visible is exactly the thing that cannot work, see above.
 */
public class ArcaneSunderingHandler {

    /** Damage owed to the target that must bypass every reduction. */
    private static final String TAG_TRUE = "insanetweaks_sunder_true";
    /** Damage owed to the target that must be reduced as magic, not as a physical blow. */
    private static final String TAG_MAGIC = "insanetweaks_sunder_magic";
    /** World tick the carry was written on; a carry is only valid within its own tick. */
    private static final String TAG_TICK = "insanetweaks_sunder_tick";
    /** Entity id of the attacker, so the carry cannot be applied to somebody else's hit. */
    private static final String TAG_ATTACKER = "insanetweaks_sunder_attacker";

    /**
     * Takes the two shares out of the raw hit.
     *
     * <p>{@code LOWEST} so the split is measured against the final physical number, after every
     * other mod's multipliers and flat reductions have had their say. Splitting an intermediate
     * value would make the percentages mean something different depending on what else is installed.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target == null || target.world == null || target.world.isRemote) return;

        ArcaneSunderingCategory cfg = ModConfig.arcaneSundering;
        if (!cfg.enabled || (cfg.trueDamagePercent <= 0 && cfg.magicDamagePercent <= 0)) return;

        float amount = event.getAmount();
        if (amount <= 0.0F) return;

        DamageSource source = event.getSource();
        if (!isDirectPhysicalBlow(source, target)) return;

        EntityLivingBase attacker = (EntityLivingBase) source.getTrueSource();
        ItemStack weapon = attacker.getHeldItem(EnumHand.MAIN_HAND);
        if (weapon.isEmpty()
                || !AdvPropertyResolver.has(weapon, AdvPropertyRegistry.ARCANE_SUNDERING)) {
            return;
        }

        float trueShare = amount * (cfg.trueDamagePercent / 100.0F);
        float magicShare = amount * (cfg.magicDamagePercent / 100.0F);
        float physical = amount - trueShare - magicShare;

        // The config ranges (45 + 45) put this out of reach, and it stays as a guard because the
        // failure is silent and total: damageEntity returns early on a non-positive amount, so
        // LivingDamageEvent would never fire and the whole hit - shares included - would vanish.
        if (physical <= 0.0F) return;

        event.setAmount(physical);

        NBTTagCompound data = target.getEntityData();
        data.setFloat(TAG_TRUE, trueShare);
        data.setFloat(TAG_MAGIC, magicShare);
        data.setLong(TAG_TICK, target.world.getTotalWorldTime());
        data.setInteger(TAG_ATTACKER, attacker.getEntityId());
    }

    /**
     * Adds the two shares back once every reduction has been applied.
     *
     * <p>{@code LOWEST} for the mirror-image reason: anything that reduces damage after us would
     * otherwise get to reduce the true share, which is the one thing it must not be able to do.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target == null || target.world == null || target.world.isRemote) return;

        NBTTagCompound data = target.getEntityData();
        if (!data.hasKey(TAG_TICK)) return;

        long tick = data.getLong(TAG_TICK);
        int attackerId = data.getInteger(TAG_ATTACKER);
        float trueShare = data.getFloat(TAG_TRUE);
        float magicShare = data.getFloat(TAG_MAGIC);

        // Cleared before the validation, not after: a carry that fails validation is a carry that
        // was orphaned, and leaving it would only give it another chance to attach to a hit it has
        // nothing to do with.
        data.removeTag(TAG_TRUE);
        data.removeTag(TAG_MAGIC);
        data.removeTag(TAG_TICK);
        data.removeTag(TAG_ATTACKER);

        if (tick != target.world.getTotalWorldTime()) return;

        DamageSource source = event.getSource();
        Entity attacker = source == null ? null : source.getTrueSource();
        if (attacker == null || attacker.getEntityId() != attackerId) return;

        float bonus = trueShare + reduceAsMagic(target, magicShare);
        if (bonus > 0.0F) {
            event.setAmount(event.getAmount() + bonus);
        }
    }

    /**
     * A direct melee blow from one living entity to another, and nothing else.
     *
     * <p>{@code getImmediateSource() == getTrueSource()} is what separates a swung weapon from an
     * arrow or a spell the attacker cast: for those two the immediate source is the projectile.
     * Sources that already bypass the pipeline we are splitting are refused outright - there is no
     * physical portion to convert in a magic or absolute hit.
     */
    private static boolean isDirectPhysicalBlow(DamageSource source, EntityLivingBase target) {
        if (source == null) return false;
        if (source.isMagicDamage() || source.isDamageAbsolute() || source.isUnblockable()
                || source.isProjectile() || source.isExplosion() || source.isFireDamage()) {
            return false;
        }
        Entity attacker = source.getTrueSource();
        if (!(attacker instanceof EntityLivingBase) || attacker == target) return false;
        return source.getImmediateSource() == attacker;
    }

    /**
     * Reduces the magic share the way vanilla would reduce {@code DamageSource.MAGIC}.
     *
     * <p>A line-for-line mirror of {@code EntityLivingBase.applyPotionDamageCalculations} for a
     * magic source - Resistance, then the armour's enchantment modifier - minus the armour-point
     * step, which magic damage skips. Replicated rather than called because the vanilla method is
     * protected, and because we are converting a number, not delivering a second attack.
     */
    private static float reduceAsMagic(EntityLivingBase target, float magic) {
        if (magic <= 0.0F) return 0.0F;

        PotionEffect resistance = target.getActivePotionEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            int level = (resistance.getAmplifier() + 1) * 5;
            // Clamped, unlike vanilla: Resistance V and above make the inverse negative, and a
            // negative share would heal the target instead of merely failing to hurt it.
            magic = Math.max(magic * (25 - level) / 25.0F, 0.0F);
            if (magic <= 0.0F) return 0.0F;
        }

        int enchantModifier = EnchantmentHelper.getEnchantmentModifierDamage(
                target.getArmorInventoryList(), DamageSource.MAGIC);
        if (enchantModifier > 0) {
            magic = CombatRules.getDamageAfterMagicAbsorb(magic, enchantModifier);
        }
        return Math.max(magic, 0.0F);
    }
}
