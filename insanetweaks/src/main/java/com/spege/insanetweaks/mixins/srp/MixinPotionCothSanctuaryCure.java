package com.spege.insanetweaks.mixins.srp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.dhanantry.scapeandrunparasites.potion.PotionCOTH;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;
import com.spege.insanetweaks.sanctuary.SanctuaryDebug;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;

/**
 * Sanctuary cures an in-progress SRP infection (Call of the Hive) instead of only stopping new ones.
 *
 * <h3>Why write the flag rather than cancel the tick</h3>
 * {@code javap -p -c} on SRP 1.10.7 (2026-08-05) shows {@code PotionCOTH.func_76394_a} opens with,
 * after the side and player checks:
 *
 * <pre>
 * if (data.hasKey("srpcothimmunity") &amp;&amp; data.getInteger("srpcothimmunity") == 0) {
 *     entity.removePotionEffect(SRPPotions.COTH_E);
 *     return;
 * }
 * </pre>
 *
 * So setting the tag to 0 at HEAD makes SRP strip its own effect on that very call and bail - we use
 * SRP's contract and let SRP do the curing, rather than guessing at what "cured" means. The two other
 * uses of the tag in that class agree on the semantics: the transform branch only calls
 * {@code ParasiteEventEntity.convertEntity} when the value is NON-zero, and the progression branch
 * increments it. Hence: <b>0 = immune, &gt;0 = victim mid-conversion, the value is a counter.</b>
 *
 * <p>🚨 Absence of the key is NOT immunity - every read is guarded by {@code hasKey} first. Writing
 * the 0 is therefore a real marker SRP respects, not a no-op that happens to match the default.
 *
 * <h3>Cost</h3>
 * Nothing except when an entity actually has the COTH effect ticking, which is the whole reason this
 * is a mixin on the effect rather than a handler sweeping entities inside the dome every tick.
 *
 * <h3>Why NOT the permanent {@code srpcothimmunity=0} tag</h3>
 * 🚨 The first version of this mixin wrote that tag, which is how SRP's own immunity works, and it
 * cured correctly - but the tag is permanent and applies to <b>players too</b>. The 2026-08-05 test
 * run logged {@code coth-cured: Issuthh}: one step inside a dome had made the player immune to Call
 * of the Hive for the rest of the save, which quietly deletes a core SRP threat. That is not a
 * sanctuary feature, that is a bug.
 *
 * <p>So this uses the pack's existing idiom instead - the one the Restoration Hourglass has always
 * used in {@code SrpOriginSnapshotHelper.applyRestorationProtection}: clear the effect, then apply
 * {@code SRPPotions.EPEL_E}, which blocks re-assimilation and <b>expires on its own</b>. Nothing
 * persistent is written, so walking out of the dome ends the protection a few seconds later.
 *
 * <p>A mob that stays inside simply loops: re-infected, cured on the effect's first tick, protected
 * again. Net effect is continuous protection while sheltered, with zero persistent state.
 *
 * <p>{@code SummonInfectionSafetyHelper} pairs EPEL_E with the permanent tag anyway, noting that
 * "EPEL_E is sometimes bypassed by SRP". That backstop is right for our own summons and unnecessary
 * here: {@code MixinParasiteEventEntity} refuses all four conversion entry points inside a dome, so
 * a bypassed EPEL_E still has nowhere to go.
 *
 * <h3>Modifying the potion map from inside a potion tick</h3>
 * Both the clear and the EPEL_E application mutate {@code activePotionsMap} while
 * {@code EntityLivingBase.updatePotionEffects} is iterating it. That is safe here only because
 * vanilla wraps that loop in {@code catch (ConcurrentModificationException) { ; }} - the entity's
 * remaining effects skip one tick and nothing throws. SRP's own immunity branch removes COTH_E from
 * exactly the same place, so this is its established behaviour, not a new hazard.
 *
 * <p>This does not resurrect anything already converted - by then the animal is a parasite and the
 * dome's purge fire owns it. Restoring a finished conversion is the Hourglass of Restoration's job.
 */
@Mixin(value = PotionCOTH.class, remap = false)
public abstract class MixinPotionCothSanctuaryCure {

    /** Refresh EPEL_E when it has less than this left, so we are not re-applying it every tick. */
    private static final int EPEL_REFRESH_THRESHOLD_TICKS = 100;

    /**
     * Dual selector: {@code performEffect} is a vanilla {@code Potion} method SRP overrides, so the
     * name is MCP in dev and SRG in the packaged runtime. Exactly one of the two matches per
     * environment, which is why {@code require = 0} rather than 1.
     */
    @Inject(method = { "performEffect", "func_76394_a" }, at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void insanetweaks$cureCothInSanctuary(EntityLivingBase entity, int amplifier, CallbackInfo ci) {
        if (!ModConfig.sanctuary.cureCothInZone) {
            return;
        }
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }
        if (!SanctuaryRegionHelper.isProtected(entity.world, entity.getPosition())) {
            return;
        }

        PotionEffect repel = entity.getActivePotionEffect(SRPPotions.EPEL_E);
        if (repel == null || repel.getDuration() < EPEL_REFRESH_THRESHOLD_TICKS) {
            entity.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E,
                    ModConfig.sanctuary.cothImmunityTicks, 0, false, false));
        }
        SummonInfectionSafetyHelper.clearCoth(entity);

        // Cancel rather than fall through: SRP's own "immune" branch is keyed on the NBT tag we
        // deliberately do not write, so without this the rest of performEffect would keep running
        // the conversion logic on an entity we just cured.
        ci.cancel();

        SanctuaryDebug.log("coth-cured", entity.getName() + " @(" + ((int) Math.floor(entity.posX)) + ","
                + ((int) Math.floor(entity.posY)) + "," + ((int) Math.floor(entity.posZ)) + ")");
    }
}
