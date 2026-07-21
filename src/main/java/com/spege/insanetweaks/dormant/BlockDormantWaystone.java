package com.spege.insanetweaks.dormant;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/**
 * The "dormant waystone" marker block for the Dormant Eye underneath-access feature.
 *
 * <p>Deliberately stateless: no {@link net.minecraft.tileentity.TileEntity}, no interaction
 * handling. All player-facing logic (teleport, locator, tooltip, gate, placing the return
 * anchor in dim 150) lives in GroovyScript on the pack side. This block is only a physical
 * marker that GS keys its right-click interaction off, and that the native
 * {@link com.spege.insanetweaks.api.DormantWaystoneRegistry} tracks for persistence.
 *
 * <p>The same block instance is used both in the Overworld (placed naturally by
 * {@link DormantWaystoneWorldGen}) and in dim 150 (placed by GS via {@code world.setBlockState}).
 * Full cube (normal collision/hitbox) so it can be right-clicked. Drops itself on break
 * (vanilla default) and does not disappear from a stray block update.
 */
public class BlockDormantWaystone extends Block {

    public BlockDormantWaystone() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(30.0F);
        setSoundType(SoundType.STONE);
    }
}
