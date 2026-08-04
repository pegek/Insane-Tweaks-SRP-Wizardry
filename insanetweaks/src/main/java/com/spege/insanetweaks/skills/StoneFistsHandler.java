package com.spege.insanetweaks.skills;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Stone Fists ({@code compatskills:stone_fists}) — Mining tree.
 *
 * With an empty main hand, the player breaks blocks as if they were holding the
 * dedicated stone-tier tool for that block, and blocks drop only when a stone tool
 * would have been allowed to harvest them.
 *
 * Implemented purely with Forge events, no mixins:
 *
 * <ul>
 *   <li>{@link PlayerEvent.BreakSpeed} supplies the speed half. Bare-hand base destroy
 *       speed is always {@code 1.0F} and {@code EntityPlayer.getDigSpeed} applies
 *       haste / mining fatigue / submerged / airborne modifiers <i>multiplicatively after</i>
 *       that base, so scaling the event speed by the emulated tool's destroy speed is
 *       arithmetically identical to actually holding the tool.</li>
 *   <li>{@link PlayerEvent.HarvestCheck} supplies the drop half. With an empty main hand
 *       {@code ForgeHooks.canHarvestBlock} short-circuits to {@code player.canHarvestBlock(state)},
 *       which is exactly where this event is fired — so it is the authoritative gate.
 *       Flipping it also moves {@code Block.getPlayerRelativeBlockHardness} from its
 *       "wrong tool" /100 divisor to the /30 divisor real tools get.</li>
 * </ul>
 *
 * Both events also fire client-side (the client predicts break progress) and Reskillable
 * syncs PlayerData to the client, so no packets and no desync.
 *
 * Modded blocks work without special-casing: every decision below is delegated to the
 * block's own {@code getHarvestTool} / {@code getHarvestLevel} and to the real vanilla
 * tool items, rather than to any hardcoded block list.
 */
public class StoneFistsHandler {


    /**
     * The tools a Stone Fists player emulates.
     *
     * <p>Pickaxe / shovel / axe are stone tier (harvest level 1). The sword is deliberately
     * <b>wooden</b>: in 1.12.2 {@code ItemSword.getDestroySpeed} returns a flat 15.0F for
     * cobweb and 1.5F for plant-ish materials regardless of tool material, so the tier costs
     * nothing mechanically and keeps cobweb breaking at wood tier by intent.
     *
     * <p>Built in the constructor rather than lazily: these events fire on both the client
     * and the server thread, and a lazy static would be an unsafely-published array. The
     * handler is constructed in {@code init}, long after {@link Items} is bootstrapped.
     *
     * <p>These stacks are shared and never mutated — only queried.
     */
    private final ItemStack[] emulatedTools;

    public StoneFistsHandler() {
        this.emulatedTools = new ItemStack[] {
            new ItemStack(Items.STONE_PICKAXE),
            new ItemStack(Items.STONE_SHOVEL),
            new ItemStack(Items.STONE_AXE),
            new ItemStack(Items.WOODEN_SWORD)
        };
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.isCanceled()) return;

        EntityPlayer player = event.getEntityPlayer();
        if (!isBareHanded(player)) return;

        IBlockState state = event.getState();
        if (state == null) return;

        float speed = bestDestroySpeed(state);
        if (speed <= 1.0F) return;

        // Multiply the current value rather than the original, so this composes with any
        // other handler that already adjusted the speed instead of silently overwriting it.
        event.setNewSpeed(event.getNewSpeed() * speed);
    }

    @SubscribeEvent
    public void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest()) return;

        EntityPlayer player = event.getEntityPlayer();
        if (!isBareHanded(player)) return;

        IBlockState state = event.getTargetBlock();
        if (state == null) return;

        if (anyToolCanHarvest(player, state)) {
            event.setCanHarvest(true);
        }
    }

    // -------------------------------------------------------------------------
    // Gating
    // -------------------------------------------------------------------------

    /**
     * Cheapest check first: both events fire every tick while mining, and the empty-hand
     * test rejects the common case (player holding a tool) before any registry lookup.
     */
    private static boolean isBareHanded(EntityPlayer player) {
        if (player == null) return false;
        if (!player.getHeldItemMainhand().isEmpty()) return false;
        return TraitHandle.STONE_FISTS.has(player);
    }

    // -------------------------------------------------------------------------
    // Stone tool emulation
    // -------------------------------------------------------------------------

    /** Destroy speed of whichever emulated tool is best against this block. */
    private float bestDestroySpeed(IBlockState state) {
        float best = 1.0F;
        for (ItemStack tool : this.emulatedTools) {
            float speed = tool.getDestroySpeed(state);
            if (speed > best) best = speed;
        }
        return best;
    }

    /** True when at least one emulated tool would have been allowed to harvest this block. */
    private boolean anyToolCanHarvest(EntityPlayer player, IBlockState state) {
        for (ItemStack tool : this.emulatedTools) {
            if (canHarvestWith(player, state, tool)) return true;
        }
        return false;
    }

    /**
     * Faithful replica of the tail of {@code ForgeHooks.canHarvestBlock}, evaluated against
     * an emulated tool instead of the (empty) held stack. Verified against the 1.12.2
     * Forge sources (ForgeHooks.java:216).
     */
    private static boolean canHarvestWith(EntityPlayer player, IBlockState state, ItemStack tool) {
        Block block = state.getBlock();
        String toolClass = block.getHarvestTool(state);

        // Block declares no tool class — fall back to the material check the vanilla
        // inventory path uses, which is what Forge does here too.
        if (toolClass == null) return tool.canHarvestBlock(state);

        int toolLevel = tool.getItem().getHarvestLevel(tool, toolClass, player, state);
        if (toolLevel < 0) return tool.canHarvestBlock(state);

        return toolLevel >= block.getHarvestLevel(state);
    }
}
