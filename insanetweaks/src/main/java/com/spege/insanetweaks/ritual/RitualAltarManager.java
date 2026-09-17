package com.spege.insanetweaks.ritual;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.api.RitualRecipe;
import com.spege.insanetweaks.api.RitualRegistry;
import com.spege.insanetweaks.config.ModConfig;

import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.registry.WizardrySounds;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Runs the 3x3 Imbuement Altar ritual: finds the structure, matches a {@link RitualRecipe} against
 * the nine altars' contents, plays it out over time and puts the result on the centre altar.
 *
 * <p><b>Server-side only, and entirely transient.</b> A ritual in flight is not saved. If the server
 * stops mid-ritual the ingredients are still sitting on the altars, so the structure re-detects and
 * the ritual simply restarts - which is a better outcome than a {@code WorldSavedData} that has to
 * be kept consistent with nine tile entities it does not own.
 *
 * <p>🚨 <b>Nothing here polls the world.</b> The obvious implementation - sweep
 * {@code world.loadedTileEntityList} every N ticks looking for altars - is O(all tile entities in
 * the dimension) forever, in a pack where that number runs to thousands, on the server thread. Instead
 * the three events that can change an altar's contents or the structure's shape (right-click, place,
 * break) mark a position dirty, and only those positions are examined, on the next tick. Hoppers and
 * pipes cannot reach the altar at all: EB's tile entity exposes no {@code IItemHandler}, so
 * right-click is genuinely the only insertion path.
 *
 * <p>The dirty position is expanded to its nine possible centres rather than treated as one: touching
 * a corner altar changes the grid of every structure that corner belongs to.
 */
public final class RitualAltarManager {

    /** How often the swirl particles are emitted while a ritual runs. */
    private static final int PARTICLE_INTERVAL = 4;

    /** Per-dimension running rituals, keyed by the centre altar. */
    private static final Map<Integer, Map<BlockPos, ActiveRitual>> ACTIVE = new HashMap<>();

    /** Per-dimension positions whose neighbourhood needs re-examining next tick. */
    private static final Map<Integer, Set<BlockPos>> DIRTY = new HashMap<>();

    private RitualAltarManager() {
    }

    /**
     * Flags a position whose altar contents or block state just changed. Cheap and idempotent -
     * call it from any event that could matter without checking whether it does.
     */
    public static void markDirty(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null) {
            return;
        }
        // 🚨 This guard is not an optimisation. tick() is the only thing that drains DIRTY, and it
        // returns early when rituals are off - so without the same condition here, every right-click
        // on an altar would add a position to a set nothing ever empties, for the whole session.
        // The empty-registry half covers the other way in: rituals enabled but no recipe registered
        // (e.g. the Abomination element failed), where detection can never succeed anyway.
        if (!ModConfig.tweaks.enableAltarRituals || RitualRegistry.getRecipes().isEmpty()) {
            return;
        }
        Integer dim = Integer.valueOf(world.provider.getDimension());
        Set<BlockPos> set = DIRTY.get(dim);
        if (set == null) {
            set = new HashSet<>();
            DIRTY.put(dim, set);
        }
        set.add(pos.toImmutable());
    }

    /** Called once per server world tick. */
    public static void tick(World world) {
        if (world.isRemote || !(world instanceof WorldServer)) {
            return;
        }
        Integer dim = Integer.valueOf(world.provider.getDimension());

        Set<BlockPos> dirty = DIRTY.remove(dim);
        if (dirty != null) {
            for (BlockPos pos : dirty) {
                scanNeighbourhood(world, pos);
            }
        }

        Map<BlockPos, ActiveRitual> active = ACTIVE.get(dim);
        if (active == null || active.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, ActiveRitual>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ActiveRitual> entry = it.next();
            if (!tickRitual(world, entry.getKey(), entry.getValue())) {
                it.remove();
            }
        }
        if (active.isEmpty()) {
            ACTIVE.remove(dim);
        }
    }

    /** Drops all state for a dimension that is going away, so the maps cannot leak across a reload. */
    public static void onWorldUnload(World world) {
        if (world.isRemote) {
            return;
        }
        Integer dim = Integer.valueOf(world.provider.getDimension());
        ACTIVE.remove(dim);
        DIRTY.remove(dim);
    }

    // ------------------------------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------------------------------

    private static void scanNeighbourhood(World world, BlockPos touched) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                tryStart(world, touched.add(dx, 0, dz));
            }
        }
    }

    private static void tryStart(World world, BlockPos centre) {
        Integer dim = Integer.valueOf(world.provider.getDimension());
        Map<BlockPos, ActiveRitual> active = ACTIVE.get(dim);
        if (active != null && (active.containsKey(centre) || overlapsRunning(active, centre))) {
            // A larger field of altars has several valid centres; the one that started first owns
            // the ingredients, and its neighbours must not start a second ritual on the same blocks.
            return;
        }

        ItemStack[] grid = readGrid(world, centre);
        if (grid == null) {
            return;
        }
        RitualRegistry.Match match = RitualRegistry.findMatch(grid);
        if (match == null) {
            return;
        }

        if (active == null) {
            active = new HashMap<>();
            ACTIVE.put(dim, active);
        }
        active.put(centre.toImmutable(), new ActiveRitual(match.getRecipe(), match.getRotation()));

        world.playSound(null, centre, WizardrySounds.BLOCK_IMBUEMENT_ALTAR_IMBUE, SoundCategory.BLOCKS,
                1.0F, 0.8F);
    }

    private static boolean overlapsRunning(Map<BlockPos, ActiveRitual> active, BlockPos centre) {
        for (BlockPos running : active.keySet()) {
            if (running.getY() == centre.getY()
                    && Math.abs(running.getX() - centre.getX()) <= 2
                    && Math.abs(running.getZ() - centre.getZ()) <= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads the nine altars around a centre, row-major from the north-west corner.
     *
     * @return the grid, or null if any of the nine is not an imbuement altar
     */
    private static ItemStack[] readGrid(World world, BlockPos centre) {
        ItemStack[] grid = new ItemStack[RitualRecipe.GRID_SIZE];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos pos = centre.add(dx, 0, dz);
                // isBlockLoaded first: reading a tile entity in an unloaded chunk would drag the
                // chunk in from disk, on the tick thread, for a structure nobody is standing near.
                if (!world.isBlockLoaded(pos) || world.getBlockState(pos).getBlock() != WizardryBlocks.imbuement_altar) {
                    return null;
                }
                TileEntity te = world.getTileEntity(pos);
                if (!(te instanceof TileEntityImbuementAltar)) {
                    return null;
                }
                grid[(dz + 1) * 3 + (dx + 1)] = ((TileEntityImbuementAltar) te).getStack();
            }
        }
        return grid;
    }

    // ------------------------------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------------------------------

    /** @return false when the ritual is over (finished or aborted) and should be forgotten */
    private static boolean tickRitual(World world, BlockPos centre, ActiveRitual ritual) {
        ItemStack[] grid = readGrid(world, centre);
        if (grid == null) {
            abort(world, centre); // structure broken mid-ritual
            return false;
        }
        int rotation = ritual.recipe.match(grid);
        if (rotation < 0) {
            abort(world, centre); // an ingredient was taken back off an altar
            return false;
        }
        ritual.rotation = rotation;

        ritual.ticks++;

        if (ritual.ticks % PARTICLE_INTERVAL == 0) {
            spawnProgressParticles(world, centre, ritual);
        }

        if (ritual.ticks < ritual.recipe.getDurationTicks()) {
            return true;
        }

        complete(world, centre, ritual);
        return false;
    }

    /**
     * A ritual that stops without producing anything says so. Silence here would be indistinguishable
     * from "this arrangement was never a recipe", which is the one thing a player debugging their own
     * structure most needs to tell apart.
     */
    private static void abort(World world, BlockPos centre) {
        world.playSound(null, centre, WizardrySounds.BLOCK_IMBUEMENT_ALTAR_IMBUE, SoundCategory.BLOCKS,
                0.6F, 0.5F);
    }

    private static void complete(World world, BlockPos centre, ActiveRitual ritual) {
        // Consume first, THEN write the result. The centre altar is both an ingredient slot and the
        // output slot, so doing it the other way round would immediately eat what we just produced.
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int index = (dz + 1) * 3 + (dx + 1);
                if (!ritual.recipe.consumesAt(index, ritual.rotation)) {
                    continue;
                }
                TileEntity te = world.getTileEntity(centre.add(dx, 0, dz));
                if (!(te instanceof TileEntityImbuementAltar)) {
                    continue;
                }
                TileEntityImbuementAltar altar = (TileEntityImbuementAltar) te;
                ItemStack held = altar.getStack().copy();
                held.shrink(1);
                altar.setStack(held.isEmpty() ? ItemStack.EMPTY : held);
                altar.markDirty();
            }
        }

        TileEntity centreTe = world.getTileEntity(centre);
        if (centreTe instanceof TileEntityImbuementAltar) {
            TileEntityImbuementAltar altar = (TileEntityImbuementAltar) centreTe;
            altar.setStack(ritual.recipe.getResult());
            altar.markDirty();
        }

        world.playSound(null, centre, WizardrySounds.BLOCK_IMBUEMENT_ALTAR_IMBUE, SoundCategory.BLOCKS,
                1.0F, 1.2F);
        spawnCompletionParticles(world, centre);

        InsaneTweaksMod.LOGGER.debug("[InsaneTweaks] Ritual '{}' completed at {} in dim {}.",
                ritual.recipe.getId(), centre, Integer.valueOf(world.provider.getDimension()));
    }

    /**
     * Particles rise from each altar holding an ingredient and drift towards the centre, so the
     * animation reads as "the structure is feeding the middle" rather than "the middle is glowing".
     *
     * <p>Spawned through {@link WorldServer#spawnParticle}, which broadcasts to nearby clients on
     * its own - no packet of ours is involved, and nothing here is client-only.
     */
    private static void spawnProgressParticles(World world, BlockPos centre, ActiveRitual ritual) {
        WorldServer server = (WorldServer) world;
        float progress = (float) ritual.ticks / (float) ritual.recipe.getDurationTicks();

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int index = (dz + 1) * 3 + (dx + 1);
                if (index == RitualRecipe.CENTRE_INDEX || !ritual.recipe.consumesAt(index, ritual.rotation)) {
                    continue;
                }
                double x = centre.getX() + 0.5D + dx;
                double y = centre.getY() + 0.9D;
                double z = centre.getZ() + 0.5D + dz;
                server.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y, z, 2, 0.1D, 0.05D, 0.1D, 0.0D);
            }
        }

        // The centre brightens as the ritual runs, which is the only progress readout a player gets.
        server.spawnParticle(EnumParticleTypes.END_ROD, centre.getX() + 0.5D, centre.getY() + 0.9D,
                centre.getZ() + 0.5D, 1 + (int) (progress * 4.0F), 0.15D, 0.1D, 0.15D, 0.01D);
    }

    private static void spawnCompletionParticles(World world, BlockPos centre) {
        WorldServer server = (WorldServer) world;
        server.spawnParticle(EnumParticleTypes.END_ROD, centre.getX() + 0.5D, centre.getY() + 1.1D,
                centre.getZ() + 0.5D, 40, 0.35D, 0.35D, 0.35D, 0.05D);
        server.spawnParticle(EnumParticleTypes.SPELL_INSTANT, centre.getX() + 0.5D, centre.getY() + 1.0D,
                centre.getZ() + 0.5D, 25, 0.4D, 0.3D, 0.4D, 0.0D);
    }

    /** One ritual in flight. Mutable on purpose; it lives in a map and is ticked in place. */
    private static final class ActiveRitual {

        private final RitualRecipe recipe;
        private int rotation;
        private int ticks;

        ActiveRitual(RitualRecipe recipe, int rotation) {
            this.recipe = recipe;
            this.rotation = rotation;
        }
    }
}
