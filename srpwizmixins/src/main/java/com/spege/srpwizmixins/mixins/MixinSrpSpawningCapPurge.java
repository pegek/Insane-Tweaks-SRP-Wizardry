package com.spege.srpwizmixins.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.entity.Entity;
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
 * {@code srpCompat.protectNonDespawnableFromCapPurge}.
 *
 * <p>Note: this mixin is deliberately self-contained and does NOT add any interface/method to
 * {@code EntityParasiteBase} - doing so invalidates the whole parasite subclass hierarchy at load
 * time (crash 2026-07-19: EntityPDerived/EntityHeblu marked invalid). Protection is decided purely
 * from the entity's registry name via {@link EntityList}. The {@code setDead}/{@code onSpawn}
 * targets use production (SRG) names with {@code remap = false} to match the shipped SRP jar.
 *
 * <h2>Two injectors, one call site</h2>
 *
 * <p>SRP 1.10.8 rewrote this cull (its changelog: "Anti-lag 'Mass parasite removal' favors removing
 * smaller parasites first"), and in doing so the {@code setDead} call changed owner:
 *
 * <pre>
 * 1.10.7:  invokevirtual com/dhanantry/.../EntityParasiteBase.func_70106_y:()V
 * 1.10.8:  invokevirtual net/minecraft/entity/Entity.func_70106_y:()V
 * </pre>
 *
 * <p>Mixin matches the owner in an {@code @At} target literally, so one descriptor cannot cover
 * both. Hence two injectors, each {@code require = 0}: exactly one binds on any given SRP build and
 * the other quietly finds nothing. Without that, running the wrong pair would abort start-up.
 *
 * <p>⚠️ The cost of {@code require = 0} is that if a future SRP moves this call again, <b>both</b>
 * injectors will find nothing and the protection will disappear silently instead of failing loudly.
 * If beckons start vanishing mid-fight again, check this call site first.
 */
@Mixin(targets = "com.dhanantry.scapeandrunparasites.init.SRPSpawning$DimensionHandler", remap = false)
public abstract class MixinSrpSpawningCapPurge {

    /** SRP 1.10.7 and earlier: the cull calls {@code setDead} through {@code EntityParasiteBase}. */
    @Redirect(
            method = "onSpawn",
            at = @At(value = "INVOKE",
                    target = "Lcom/dhanantry/scapeandrunparasites/entity/ai/misc/EntityParasiteBase;"
                            + "func_70106_y()V"),
            require = 0,
            remap = false)
    private static void srpwizmixins$shieldFromCapPurgeLegacy(EntityParasiteBase parasite) {
        srpwizmixins$cullUnlessProtected(parasite);
    }

    /** SRP 1.10.8 and later: the same call, now made through {@code Entity}. */
    @Redirect(
            method = "onSpawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;func_70106_y()V"),
            require = 0,
            remap = false)
    private static void srpwizmixins$shieldFromCapPurge(Entity entity) {
        srpwizmixins$cullUnlessProtected(entity);
    }

    private static void srpwizmixins$cullUnlessProtected(Entity entity) {
        if (SrpWizMixinsConfig.srpCompat.protectNonDespawnableFromCapPurge) {
            // Beckons/nexuses get a wider protection radius than ordinary parasites, but beyond it
            // even they can be culled so they don't accumulate forever far from any player.
            int radius = srpwizmixins$isBeckonOrNexus(entity)
                    ? SrpWizMixinsConfig.srpCompat.beckonCapPurgeRadius
                    : SrpWizMixinsConfig.srpCompat.capPurgeProtectRadius;
            if (radius > 0 && srpwizmixins$nearAnyPlayer(entity, radius)) {
                return;
            }
        }
        entity.setDead();
    }

    /** True if any player in the entity's world is within {@code radius} blocks. */
    private static boolean srpwizmixins$nearAnyPlayer(Entity entity, int radius) {
        World world = entity.world;
        if (world == null || world.playerEntities.isEmpty()) {
            return false;
        }
        double r2 = (double) radius * (double) radius;
        for (int i = 0; i < world.playerEntities.size(); i++) {
            EntityPlayer player = world.playerEntities.get(i);
            double dx = player.posX - entity.posX;
            double dy = player.posY - entity.posY;
            double dz = player.posZ - entity.posZ;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    /** Beckon SI..SV plus any deterrent/nexus (Venkrol) subclass, matched by registry path. */
    private static boolean srpwizmixins$isBeckonOrNexus(Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        if (id == null || !"srparasites".equals(id.getResourceDomain())) {
            return false;
        }
        String path = id.getResourcePath();
        return path.contains("beckon") || path.contains("venkrol") || path.contains("nexus");
    }
}
