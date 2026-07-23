package com.spege.insanetweaks.mixins.srp;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.util.config.SRPConfig;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.SrpMobCapHelper;

import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

/**
 * P3 (mobcapmulti) - per-dimension parasite mob-cap multiplier.
 *
 * <p>SRP's spawn caps are global. Every cap read inside
 * {@code SRPSpawning$DimensionHandler.onSpawn} (the spawn gate + "SOO MANY PARASITES" cull) is
 * {@link Redirect}ed and scaled by the multiplier configured for the event's dimension. A dimension
 * with no entry gets multiplier 1.0, so untouched worlds behave exactly like vanilla SRP; e.g.
 * {@code 111=0.75} lowers every Lost-Cities parasite cap by 25%.
 *
 * <p>Scaled caps: {@code worldSpawningMobCap} (cull floor / effective population ceiling),
 * {@code worldMobCapPlusPlayer} (per-player-scaled total), and the {@code worldGnatCap /
 * worldWaterCap / worldAirCap} subtype caps. Each redirect captures the enclosing method's
 * {@code CheckSpawn} argument to resolve the dimension. Gated on
 * {@link ModConfig#srpCompat}.enablePerDimMobCap. {@code SRPConfig} is SRP's own class so the field
 * targets use {@code remap = false}.
 */
@Mixin(targets = "com.dhanantry.scapeandrunparasites.init.SRPSpawning$DimensionHandler", remap = false)
public abstract class MixinSrpMobCapMulti {

    @Redirect(method = "onSpawn", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                    target = "Lcom/dhanantry/scapeandrunparasites/util/config/SRPConfig;worldSpawningMobCap:I"))
    private static int insanetweaks$scaleSpawningCap(LivingSpawnEvent.CheckSpawn event) {
        return insanetweaks$scale(SRPConfig.worldSpawningMobCap, event);
    }

    @Redirect(method = "onSpawn", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                    target = "Lcom/dhanantry/scapeandrunparasites/util/config/SRPConfig;worldMobCapPlusPlayer:I"))
    private static int insanetweaks$scaleMobCapPlusPlayer(LivingSpawnEvent.CheckSpawn event) {
        return insanetweaks$scale(SRPConfig.worldMobCapPlusPlayer, event);
    }

    @Redirect(method = "onSpawn", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                    target = "Lcom/dhanantry/scapeandrunparasites/util/config/SRPConfig;worldGnatCap:I"))
    private static int insanetweaks$scaleGnatCap(LivingSpawnEvent.CheckSpawn event) {
        return insanetweaks$scale(SRPConfig.worldGnatCap, event);
    }

    @Redirect(method = "onSpawn", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                    target = "Lcom/dhanantry/scapeandrunparasites/util/config/SRPConfig;worldWaterCap:I"))
    private static int insanetweaks$scaleWaterCap(LivingSpawnEvent.CheckSpawn event) {
        return insanetweaks$scale(SRPConfig.worldWaterCap, event);
    }

    @Redirect(method = "onSpawn", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                    target = "Lcom/dhanantry/scapeandrunparasites/util/config/SRPConfig;worldAirCap:I"))
    private static int insanetweaks$scaleAirCap(LivingSpawnEvent.CheckSpawn event) {
        return insanetweaks$scale(SRPConfig.worldAirCap, event);
    }

    private static int insanetweaks$scale(int base, LivingSpawnEvent.CheckSpawn event) {
        if (!ModConfig.srpCompat.enablePerDimMobCap || event == null) {
            return base;
        }
        World world = event.getWorld();
        if (world == null) {
            return base;
        }
        return SrpMobCapHelper.scaleCap(base, world.provider.getDimension());
    }
}
