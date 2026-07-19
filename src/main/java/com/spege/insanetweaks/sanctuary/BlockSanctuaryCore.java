package com.spege.insanetweaks.sanctuary;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

public class BlockSanctuaryCore extends Block {
    public BlockSanctuaryCore() {
        super(Material.ROCK);
        setHardness(4.0F);
        setResistance(2000.0F); // blast-resistant like a beacon base
        setSoundType(SoundType.STONE);
        setLightLevel(0.5F);
        // registry name + creative tab set in ModBlocks
    }
}
