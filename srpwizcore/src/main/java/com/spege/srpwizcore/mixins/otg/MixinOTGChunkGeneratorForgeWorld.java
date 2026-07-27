package com.spege.srpwizcore.mixins.otg;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.pg85.otg.forge.generator.OTGChunkGenerator;
import com.pg85.otg.forge.world.ForgeWorld;
import com.spege.srpwizcore.util.ForgeWorldAccessor;

/**
 * Exposes {@code OTGChunkGenerator}'s private {@code world} field through the shared
 * {@link ForgeWorldAccessor} duck, so a mixin holding only an {@code IChunkGenerator} can reach
 * the dimension's {@code WorldConfig} (used by {@code MixinCqrStructureHelper}).
 *
 * <p>The duck method keeps its historical {@code insanetweaks$} name — the existing OTG mixins
 * in this package call it, and renaming it here would mean touching them for no gain.
 */
@Mixin(value = OTGChunkGenerator.class, remap = false)
public class MixinOTGChunkGeneratorForgeWorld implements ForgeWorldAccessor {

    @Shadow(remap = false)
    private ForgeWorld world;

    @Override
    public Object insanetweaks$getForgeWorld() {
        return this.world;
    }
}
