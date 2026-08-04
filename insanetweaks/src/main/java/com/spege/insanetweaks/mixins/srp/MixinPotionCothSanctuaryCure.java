package com.spege.insanetweaks.mixins.srp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.potion.PotionCOTH;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.sanctuary.SanctuaryDebug;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

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
 * <h3>The tag is permanent, deliberately</h3>
 * A cured animal keeps {@code srpcothimmunity=0} after it wanders out, so a herd walked through a
 * sanctuary once is immune for good. That was the chosen behaviour ("sanctuary = hospital") over
 * freezing the infection only while sheltered; the alternative needed a polling handler to strip the
 * tag on exit, i.e. exactly the per-tick cost this design avoids.
 *
 * <p>This does not resurrect anything already converted - by then the animal is a parasite and the
 * dome's purge fire owns it. Restoring a finished conversion is the Hourglass of Restoration's job.
 */
@Mixin(value = PotionCOTH.class, remap = false)
public abstract class MixinPotionCothSanctuaryCure {

    private static final String COTH_IMMUNITY_KEY = "srpcothimmunity";

    /**
     * Dual selector: {@code performEffect} is a vanilla {@code Potion} method SRP overrides, so the
     * name is MCP in dev and SRG in the packaged runtime. Exactly one of the two matches per
     * environment, which is why {@code require = 0} rather than 1.
     */
    @Inject(method = { "performEffect", "func_76394_a" }, at = @At("HEAD"), remap = false, require = 0)
    private void insanetweaks$cureCothInSanctuary(EntityLivingBase entity, int amplifier, CallbackInfo ci) {
        if (!ModConfig.sanctuary.cureCothInZone) {
            return;
        }
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }
        NBTTagCompound data = entity.getEntityData();
        // Already immune - leave early so this cannot re-log once per tick for every cured animal
        // standing in the dome.
        if (data.hasKey(COTH_IMMUNITY_KEY) && data.getInteger(COTH_IMMUNITY_KEY) == 0) {
            return;
        }
        if (!SanctuaryRegionHelper.isProtected(entity.world, entity.getPosition())) {
            return;
        }
        data.setInteger(COTH_IMMUNITY_KEY, 0);
        SanctuaryDebug.log("coth-cured", entity.getName() + " @(" + ((int) Math.floor(entity.posX)) + ","
                + ((int) Math.floor(entity.posY)) + "," + ((int) Math.floor(entity.posZ)) + ")");
    }
}
