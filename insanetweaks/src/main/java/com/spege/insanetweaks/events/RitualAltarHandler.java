package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.ritual.RitualAltarManager;

import electroblob.wizardry.registry.WizardryBlocks;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Event surface of the 3x3 Imbuement Altar ritual. Everything it does is bookkeeping - the actual
 * detection and playback live in {@link RitualAltarManager}.
 *
 * <p>Three events can change what the manager would see, and all three are player actions: putting an
 * item on an altar or taking it off (right-click), adding an altar to the field (place), removing one
 * (break). Nothing else can reach the altar's inventory, because EB's tile entity implements no
 * capability - so this is the complete set, not a sample of it.
 *
 * <p>The same three events on a receptacle matter too: lighting one, emptying it, hanging or knocking
 * it off changes whether the structure may run.
 *
 * <p>Each handler filters on the block being an imbuement altar or a receptacle BEFORE marking
 * anything. That filter is the whole reason this is cheap: without it every right-click anywhere in
 * the world would push a position into the dirty set.
 */
public class RitualAltarHandler {

    /**
     * Marked at LOWEST so the insertion has the best chance of already having happened - though the
     * manager works either way, because it examines the grid on the following world tick rather than
     * inside this event.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        mark(event.getWorld(), event.getPos());
    }

    /**
     * EntityPlaceEvent rather than the deprecated PlaceEvent, and not only to silence the warning:
     * PlaceEvent extends it, so this one listener also covers MultiPlaceEvent and any non-player
     * placement (a dispenser, another mod's builder) - all of which can complete a structure just as
     * well as a player can.
     */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        markState(event.getWorld(), event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        mark(event.getWorld(), event.getPos());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        if (!ModConfig.tweaks.enableAltarRituals) {
            return;
        }
        RitualAltarManager.tick(event.world);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        RitualAltarManager.onWorldUnload(event.getWorld());
    }

    private static void mark(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null) {
            return;
        }
        markState(world, pos, world.getBlockState(pos));
    }

    /**
     * An altar marks itself. A receptacle marks the altar it hangs on instead: it sits two blocks out
     * from the centre, outside the manager's one-block scan, while the side's middle altar it is
     * attached to is inside it.
     */
    private static void markState(World world, BlockPos pos, IBlockState state) {
        if (world == null || world.isRemote) {
            return;
        }
        if (state.getBlock() == WizardryBlocks.imbuement_altar) {
            RitualAltarManager.markDirty(world, pos);
        } else if (state.getBlock() == WizardryBlocks.receptacle) {
            RitualAltarManager.markDirty(world, pos.offset(state.getValue(BlockTorch.FACING).getOpposite()));
        }
    }
}
