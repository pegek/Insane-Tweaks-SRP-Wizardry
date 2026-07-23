package com.spege.srpwizmixins.mixins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/**
 * Milestone 0 diagnostic (read-only): which channel actually removes a beckon/nexus?
 *
 * <p>In SRP 1.10.7 an {@link EntityParasiteBase} can leave the world through several
 * paths that all funnel into {@code setDead} ({@code func_70106_y}):
 * <ul>
 *   <li>idle despawn - {@code func_70623_bb} (fires the Forge {@code AllowDespawn} hook,
 *       which the Groovy city_rules shield already DENYs);</li>
 *   <li>internal transform/upgrade - {@code ParasiteEventEntity.spawnNext} calls
 *       {@code old.func_70106_y()} directly, BYPASSING the Forge hook (shield-invisible);</li>
 *   <li>ordinary death.</li>
 * </ul>
 * We inject at HEAD of setDead, filter to beckons/nexuses, and log the dimension, position,
 * age, canD flag and the SRP call frames so the live log tells us exactly which path killed
 * the summoned Beckon SIV. Purely observational - never cancels, never changes state.
 * Gated on {@link ModConfig#srpCompat}.debugLogging (read live).
 */
@Mixin(value = EntityParasiteBase.class, remap = false)
public abstract class MixinSrpDespawnDiag {

    private static final Logger LOGGER = LogManager.getLogger("insanetweaks");

    @Shadow(remap = false)
    public abstract boolean func_70692_ba();

    @Inject(method = {"setDead", "func_70106_y"}, at = @At("HEAD"), remap = false)
    private void insanetweaks$logBeckonRemoval(CallbackInfo ci) {
        if (!SrpWizMixinsConfig.srpCompat.debugLogging) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (self.world == null || self.world.isRemote) {
            return;
        }
        if (!insanetweaks$isBeckonOrNexus(self)) {
            return;
        }
        ResourceLocation id = EntityList.getKey(self);
        BlockPos pos = self.getPosition();
        LOGGER.info(
                "[InsaneTweaks] SRP-diag despawn: entity={} dim={} pos=[{},{},{}] age={} canDespawn={} via={}",
                id, self.dimension, pos.getX(), pos.getY(), pos.getZ(), self.ticksExisted,
                func_70692_ba(), insanetweaks$srpCallers());
    }

    /** Beckon SI..SV plus any deterrent/nexus (Venkrol) subclass, matched by registry path. */
    private static boolean insanetweaks$isBeckonOrNexus(Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        if (id == null || !"srparasites".equals(id.getResourceDomain())) {
            return false;
        }
        String path = id.getResourcePath();
        return path.contains("beckon") || path.contains("venkrol") || path.contains("nexus");
    }

    /** Compact trace of the SRP (com.dhanantry) frames that led into setDead. */
    private static String insanetweaks$srpCallers() {
        StackTraceElement[] frames = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (StackTraceElement frame : frames) {
            if (!frame.getClassName().startsWith("com.dhanantry")) {
                continue;
            }
            if (frame.getMethodName().equals("func_70106_y") || frame.getMethodName().equals("setDead")) {
                continue;
            }
            if (shown > 0) {
                sb.append(" <- ");
            }
            String cls = frame.getClassName();
            sb.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(frame.getMethodName());
            if (++shown >= 4) {
                break;
            }
        }
        return shown == 0 ? "(no SRP frame)" : sb.toString();
    }
}
