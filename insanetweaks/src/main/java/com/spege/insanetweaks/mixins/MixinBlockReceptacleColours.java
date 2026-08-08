package com.spege.insanetweaks.mixins;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.block.BlockReceptacle;
import electroblob.wizardry.constants.Element;

/**
 * Puts Abomination into {@code BlockReceptacle.PARTICLE_COLOURS} at the only moment that table can
 * still be written to.
 *
 * <p>The field looks writable - it is declared as a plain {@code java.util.Map} - but
 * {@code <clinit>} ends with {@code PARTICLE_COLOURS = Maps.immutableEnumMap(map)}, and Guava's
 * {@code ImmutableEnumMap.put} throws unconditionally. A {@code put} call site therefore compiles
 * without a warning and crashes the game. Redirecting the wrap itself is the only route.
 *
 * <p>Worth the injection because five client-side sites read this table and dereference the result
 * unchecked - {@code randomDisplayTick}, {@code TileEntityImbuementAltar},
 * {@code EntityRemnant.onUpdate}, {@code RenderImbuementAltar} and {@code RenderDonationPerks} -
 * and one redirect covers all five.
 */
@Mixin(value = BlockReceptacle.class, remap = false)
public abstract class MixinBlockReceptacleColours {

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                     target = "Lcom/google/common/collect/Maps;immutableEnumMap(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;"))
    private static ImmutableMap<Element, int[]> insanetweaks$addAbominationColour(Map<Element, int[]> colours) {
        ModElements.addReceptacleColour(colours);
        return Maps.immutableEnumMap(colours);
    }
}
