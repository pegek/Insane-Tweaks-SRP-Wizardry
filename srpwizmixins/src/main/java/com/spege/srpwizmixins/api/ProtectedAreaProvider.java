package com.spege.srpwizmixins.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lets another mod veto positions that the parasite meteor must not be aimed at.
 *
 * <p>Register an implementation with {@link MeteorProtection#register(ProtectedAreaProvider)} and it
 * is asked about every candidate impact point while
 * {@code srpCompat.meteorPlacementGuard} is picking one. Answering {@code true} sends the search
 * somewhere else; the meteor is never cancelled because of it.
 *
 * <p>Deliberately expressed in nothing but vanilla types, so a mod can depend on this without either
 * side knowing anything about the other's classes. The direction is one-way: content mods reach in
 * here, this mod never reaches back.
 *
 * <p>Two things to be careful about, because of where this is called from:
 * <ul>
 *   <li>It runs on the server thread while a world tick is in progress, and the position is usually
 *       hundreds of blocks away and not loaded. <b>Do not touch the world through it</b> - no
 *       {@code getBlockState}, no {@code getHeight}, no tile-entity lookups - or you will generate
 *       chunks in the middle of a tick. Answer from saved data or from an in-memory index.</li>
 *   <li>It is called up to a few dozen times per meteor, and a meteor is a rare event, so cost per
 *       call barely matters - but a provider that throws would take the meteor down with it, so
 *       exceptions are caught and the provider is treated as "not protected" for that call.</li>
 * </ul>
 */
public interface ProtectedAreaProvider {

    /**
     * @param world the world the meteor is about to fall in, never null, never remote
     * @param pos   candidate impact position; only X and Z are meaningful, Y is the player's
     * @return true when the meteor must not land here
     */
    boolean isProtected(World world, BlockPos pos);
}
