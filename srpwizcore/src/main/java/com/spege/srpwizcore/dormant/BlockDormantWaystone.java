package com.spege.srpwizcore.dormant;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/**
 * The "dormant waystone" marker block for the configurable dormant-waystone travel system.
 *
 * <p>Deliberately stateless: no {@link net.minecraft.tileentity.TileEntity}, no interaction
 * handling of its own. All player-facing logic (teleport, locator, tooltip, gate, placing the
 * return anchor in the target dimension) lives natively in {@link DormantTeleportHandler} and
 * {@link DormantEyeHandler}. This block is only a physical marker that those handlers key their
 * right-click interaction off, and that {@link com.spege.srpwizcore.api.DormantWaystoneRegistry}
 * tracks for persistence.
 *
 * <p>The same block instance is used both on the surface side (placed naturally by
 * {@link DormantWaystoneWorldGen}) and on the target side (placed natively by
 * {@link DormantTeleportHandler} via {@code world.setBlockState}). Full cube (normal
 * collision/hitbox) so it can be right-clicked. Drops itself on break (vanilla default) and does
 * not disappear from a stray block update.
 */
public class BlockDormantWaystone extends Block {

    public BlockDormantWaystone() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(30.0F);
        setSoundType(SoundType.STONE);
    }
}
