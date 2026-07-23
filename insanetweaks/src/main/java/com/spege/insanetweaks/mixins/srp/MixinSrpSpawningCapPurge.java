package com.spege.insanetweaks.mixins.srp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * Fix A - stop SRP's over-cap parasite purge from deleting beckons/nexuses.
 *
 * <p>When the parasite population exceeds {@code SRPConfig.worldSpawningMobCap}, SRP's custom
 * spawner runs a "SOO MANY PARASITES" cull inside
 * {@code SRPSpawning$DimensionHandler.onSpawn} (a {@code LivingSpawnEvent.CheckSpawn} handler):
 * it iterates nearby parasites and calls {@code setDead} on each until the count drops back under
 * the cap - <b>blindly</b>. The diagnostic build confirmed this is exactly what deleted the
 * summoned Beckon SIV ({@code via=... SRPSpawning$DimensionHandler.onSpawn <- ...findChunksForSpawningOrigin},
 * canDespawn=false). This path bypasses the Forge {@code AllowDespawn} hook, which is why the
 * Groovy city_rules shield never caught it.
 *
 * <p>We {@link Redirect} the {@code setDead} call in that cull and skip it for: (a) beckons/nexuses,
 * identified by SRP registry-name path ({@code beckon}/{@code venkrol}/{@code nexus}), always; and
 * (b) any parasite within {@code capPurgeProtectRadius} blocks of a player, so the cull never deletes
 * entities the player can see fighting. Parasites far from every player are still culled, so the mob
 * cap is still enforced out of sight. Gated on
 * {@link ModConfig#srpCompat}.protectNonDespawnableFromCapPurge.
 *
 * <p>Note: this mixin is deliberately self-contained and does NOT add any interface/method to
 * {@code EntityParasiteBase} - doing so invalidates the whole parasite subclass hierarchy at load
 * time (crash 2026-07-19: EntityPDerived/EntityHeblu marked invalid). Protection is decided purely
 * from the entity's registry name via {@link EntityList}. The {@code setDead}/{@code onSpawn}
 * targets use production (SRG) names with {@code remap = false} to match the shipped SRP jar.
 */
@Mixin(targets = "com.dhanantry.scapeandrunparasites.init.SRPSpawning$DimensionHandler", remap = false)
public abstract class MixinSrpSpawningCapPurge {

    @Redirect(
            method = "onSpawn",
            at = @At(value = "INVOKE",
                    target = "Lcom/dhanantry/scapeandrunparasites/entity/ai/misc/EntityParasiteBase;func_70106_y()V"),
            remap = false)
    private static void insanetweaks$shieldBeckonsFromCapPurge(EntityParasiteBase parasite) {
        if (ModConfig.srpCompat.protectNonDespawnableFromCapPurge) {
            // Beckons/nexuses get a wider protection radius than ordinary parasites, but beyond it
            // even they can be culled so they don't accumulate forever far from any player.
            int radius = insanetweaks$isBeckonOrNexus(parasite)
                    ? ModConfig.srpCompat.beckonCapPurgeRadius
                    : ModConfig.srpCompat.capPurgeProtectRadius;
            if (radius > 0 && insanetweaks$nearAnyPlayer(parasite, radius)) {
                return;
            }
        }
        parasite.setDead();
    }

    /** True if any player in the parasite's world is within {@code radius} blocks. */
    private static boolean insanetweaks$nearAnyPlayer(EntityParasiteBase parasite, int radius) {
        World world = parasite.world;
        if (world == null || world.playerEntities.isEmpty()) {
            return false;
        }
        double r2 = (double) radius * (double) radius;
        for (int i = 0; i < world.playerEntities.size(); i++) {
            EntityPlayer player = world.playerEntities.get(i);
            double dx = player.posX - parasite.posX;
            double dy = player.posY - parasite.posY;
            double dz = player.posZ - parasite.posZ;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    /** Beckon SI..SV plus any deterrent/nexus (Venkrol) subclass, matched by registry path. */
    private static boolean insanetweaks$isBeckonOrNexus(EntityParasiteBase parasite) {
        ResourceLocation id = EntityList.getKey(parasite);
        if (id == null || !"srparasites".equals(id.getResourceDomain())) {
            return false;
        }
        String path = id.getResourcePath();
        return path.contains("beckon") || path.contains("venkrol") || path.contains("nexus");
    }
}
