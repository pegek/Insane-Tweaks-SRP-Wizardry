package com.spege.srpwizcore.mixins.enigmatic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.util.CubeLocationSearch;

import keletu.enigmaticlegacy.item.ItemTheCube;
import keletu.enigmaticlegacy.item.ItemTheCube.CachedTeleportationLocation;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Server crash fix for the Non-Euclidean Cube: {@code StackOverflowError} in
 * {@code ItemTheCube.findBlockPos}, rethrown on the server thread as a {@code RuntimeException}
 * (crash 2026-08-02 21:24, {@code Ticking player}).
 *
 * <p><b>Root cause</b>, read off {@code javap -p -c -l} of the shipped
 * {@code enigmaticlegacy-legacy-2.6.0.jar} — the pack's exact build:
 *
 * <ul>
 *   <li>{@code findBlockPos(World, EntityPlayerMP, int)} picks a random point in a disc of radius
 *       100..10000 around the world origin, scans that column for ground, and on a miss
 *       <b>calls itself</b> (source lines 315 and 329). Its only exit is {@code depth > 10000}.
 *       No thread stack holds ten thousand frames, so the bail-out is unreachable and a run of
 *       misses ends in {@code StackOverflowError}.</li>
 *   <li>{@code generateCachedLocation} runs that search on {@code Executors.newCachedThreadPool()},
 *       i.e. off the server thread. {@code MixinChunkProviderServerThreadGuard} answers
 *       {@code provideChunk} there with an empty chunk rather than generating one — which is
 *       exactly its job, and which also means <b>every probe on that worker reads air</b>. Only
 *       already-loaded chunks are visible: a few hundred of the ~380 000 candidates in that disc.
 *       So the run of misses is not bad luck, it is the normal case.</li>
 *   <li>The lambda's {@code catch (Exception)} does not catch an {@code Error}. The
 *       {@code StackOverflowError} lands in the {@code FutureTask}, and {@code onWornTick} calls
 *       {@code Future.get()} on it every tick and wraps the {@code ExecutionException} in a
 *       {@code RuntimeException} (source line 206). That is the frame that kills the server.</li>
 * </ul>
 *
 * <p><b>What set it off.</b> The Cube's active ability teleported the player into The End at their
 * Overworld coordinates. From then on the cached location's dimension matched the player's own, so
 * {@code onWornTick} resubmitted the search every tick — now searching The End, where even real
 * terrain is mostly void.
 *
 * <p><b>The fix.</b> Three injections, in order of how much of the problem each removes:
 *
 * <ol>
 *   <li>{@code findBlockPos} is taken over at HEAD by {@link CubeLocationSearch#searchNow}, which
 *       iterates instead of recursing. This alone makes the crash impossible, and it covers the
 *       direct call from {@code triggerActiveAbility} as well.</li>
 *   <li>{@code generateCachedLocation} no longer reaches the executor: the search runs on the
 *       server thread, a metered number of terrain-generating probes per tick, publishing a
 *       completed {@code Future} when it settles. Generating terrain on the server thread is the
 *       legitimate way to do it, so the guard never sees this caller and the item works again.
 *       Returning nothing this tick is the whole loop: an absent (or same-dimension) cache entry
 *       is precisely what makes {@code onWornTick} call this method again next tick.</li>
 *   <li>{@code Future.get()} in {@code onWornTick} can no longer rethrow. With the two injections
 *       above the future is always a completed one, so this is belt and braces — but it is the
 *       only one of the three that keeps a future failure in this item from reaching the server
 *       tick at all.</li>
 * </ol>
 *
 * <p><b>Dimension discipline.</b> Enigmatic Legacy's fallback is the player's current position
 * <em>and current dimension</em>, and {@code onWornTick} regenerates whenever the cached dimension
 * equals the player's — so its own fallback re-runs the search every tick, forever. Every
 * destination produced here carries a dimension from the item's pool minus the player's current
 * one, so a finished search always settles.
 *
 * <p><b>Names.</b> Enigmatic Legacy is a mod, so nothing here is obfuscated and every selector is
 * {@code remap = false}; the vanilla calls inside {@link CubeLocationSearch} are remapped by
 * {@code reobfJar} as usual. The jar is on this project's classpath as {@code compileOnly} and not
 * deobfuscated on purpose: the only member touched across the boundary is
 * {@code CachedTeleportationLocation.<init>(IDDD)}, whose descriptor holds no Minecraft type, and
 * the two shadowed fields are a {@code List} and a {@code Map}. Baubles rides along on the
 * classpath only so javac can resolve {@code ItemSpellstoneBauble implements IBauble}.
 */
@Mixin(value = ItemTheCube.class, remap = false)
public abstract class MixinElItemTheCube {

    @Shadow
    @Final
    private List<Integer> worlds;

    @Shadow
    @Final
    private Map<EntityPlayerMP, Future<CachedTeleportationLocation>> locationCache;

    /**
     * Replaces the recursive search outright. Cancelling at HEAD means the original body never
     * runs, so it can never reach its own recursive call sites either.
     */
    @Inject(method = "findBlockPos", at = @At("HEAD"), cancellable = true, remap = false)
    private void srpwizcore$boundedSearch(World world, EntityPlayerMP player, int depth,
            CallbackInfoReturnable<CachedTeleportationLocation> cir) {
        if (!SrpWizCoreConfig.enigmaticCompat.fixCubeLocationSearch) {
            return;
        }
        cir.setReturnValue(srpwizcore$toLocation(
                CubeLocationSearch.searchNow(world, player, this.worlds)));
    }

    /**
     * Keeps the cached-destination search on the server thread and spreads it over ticks. The
     * cancel is what stops the submit to {@code Executors.newCachedThreadPool()}.
     */
    @Inject(method = "generateCachedLocation", at = @At("HEAD"), cancellable = true, remap = false)
    private void srpwizcore$searchOnServerThread(EntityPlayerMP player, CallbackInfo ci) {
        if (!SrpWizCoreConfig.enigmaticCompat.fixCubeLocationSearch) {
            return;
        }
        ci.cancel();
        CubeLocationSearch.Spot spot = CubeLocationSearch.stepBackground(player, this.worlds);
        if (spot != null) {
            this.locationCache.put(player,
                    CompletableFuture.completedFuture(srpwizcore$toLocation(spot)));
        }
    }

    /**
     * Last line of defence: a failed search must not become a server crash. Drops the poisoned
     * entry so the next tick starts a clean search, and answers with a destination in another
     * dimension so this tick's {@code onWornTick} does not immediately ask for another one.
     */
    @Redirect(method = "onWornTick",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Future;get()Ljava/lang/Object;"),
            remap = false)
    private Object srpwizcore$neverRethrowSearchFailure(Future<?> future, ItemStack stack,
            EntityLivingBase context) {
        try {
            return future.get();
        } catch (Throwable failure) {
            if (!(context instanceof EntityPlayerMP)) {
                // Unreachable: Enigmatic Legacy only reaches this call inside its own
                // instanceof EntityPlayerMP branch. Left as its original behaviour rather than
                // inventing one for a state that cannot occur.
                throw new RuntimeException(failure);
            }
            EntityPlayerMP player = (EntityPlayerMP) context;
            this.locationCache.remove(player);
            if (CubeLocationSearch.shouldReportFailure()) {
                SrpWizCore.LOGGER.warn(
                        "[srpwizcore] Non-Euclidean Cube destination search failed for {};"
                                + " substituting a safe destination instead of crashing the server.",
                        player.getName(), failure);
            }
            return srpwizcore$toLocation(CubeLocationSearch.emergency(player));
        }
    }

    private static CachedTeleportationLocation srpwizcore$toLocation(CubeLocationSearch.Spot spot) {
        return new CachedTeleportationLocation(spot.dimension, spot.x, spot.y, spot.z);
    }
}
