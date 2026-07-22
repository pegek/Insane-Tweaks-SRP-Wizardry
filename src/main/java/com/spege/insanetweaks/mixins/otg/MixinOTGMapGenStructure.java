package com.spege.insanetweaks.mixins.otg;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.pg85.otg.forge.world.ForgeWorld;
import com.spege.insanetweaks.util.ForgeWorldAccessor;

/**
 * Accessor mixin on {@code OTGMapGenStructure} — the abstract superclass of all OTG structure
 * generators. Exposes the package-private {@code forgeWorld} field via the
 * {@link ForgeWorldAccessor} duck interface.
 *
 * <p>This is needed because {@code @Shadow} only resolves fields declared in the <b>target</b>
 * class itself, not inherited fields. {@code forgeWorld} is declared in
 * {@code OTGMapGenStructure}, so the {@code @Shadow} must live here. Subclass mixins
 * ({@code MixinOTGNetherFortressGen}, {@code MixinOTGMineshaftGen}) obtain the value by
 * casting {@code this} to {@code ForgeWorldAccessor}.
 *
 * <p>Confirmed via {@code javap -p} on {@code OpenTerrainGenerator-1.12.2-v9.7.jar}:
 * {@code OTGMapGenStructure} declares {@code ForgeWorld forgeWorld} (package-private).
 */
@Mixin(targets = "com.pg85.otg.forge.generator.structure.OTGMapGenStructure", remap = false)
public abstract class MixinOTGMapGenStructure implements ForgeWorldAccessor {

    @Shadow(remap = false)
    ForgeWorld forgeWorld;

    @Override
    public Object insanetweaks$getForgeWorld() {
        return this.forgeWorld;
    }
}
