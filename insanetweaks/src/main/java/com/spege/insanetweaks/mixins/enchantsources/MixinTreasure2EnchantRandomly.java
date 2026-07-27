package com.spege.insanetweaks.mixins.enchantsources;

import java.util.Iterator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.EnchantSourceGuard;

import net.minecraft.util.registry.RegistryNamespaced;

/**
 * Quest-gate, third-party source: GottschCore / Treasure2 chest loot.
 *
 * <p>GottschCore ships its <b>own copy</b> of the {@code enchant_randomly} loot function —
 * {@code com.someguyssoftware.gottschcore.loot.functions.EnchantRandomly}, a separate class in a
 * separate package with its own {@code LootContext}. Our mixin on the vanilla class does not see it,
 * so Treasure2 chests were a live hole: verified with {@code javap} that its {@code apply} is a
 * near-instruction-for-instruction clone of vanilla's, right down to
 * {@code Enchantment.REGISTRY.iterator()} → {@code canApply} → {@code list.add} →
 * {@code list.get(rand.nextInt(size))}.
 *
 * <p>Because it is a clone, the fix is the same one line: filter the registry iterator that feeds the
 * candidate loop. See {@code com.spege.insanetweaks.mixins.enchant.MixinEnchantRandomly} for the full
 * reasoning on why the iterator is the right anchor rather than the {@code List.add}.
 *
 * <p>Mod target, so {@code remap = false} throughout and the names are the mod's own (never SRG).
 * Gated on {@code gottschcore} by {@code LateMixinBooter}.
 */
@Mixin(targets = "com.someguyssoftware.gottschcore.loot.functions.EnchantRandomly", remap = false)
public class MixinTreasure2EnchantRandomly {

    @Redirect(method = "apply",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/registry/RegistryNamespaced;iterator()Ljava/util/Iterator;"),
            remap = false)
    private Iterator<?> insanetweaks$filterQuestGatedCandidates(RegistryNamespaced<?, ?> registry) {
        return EnchantSourceGuard.filteredIterator(registry, "Treasure2 loot");
    }
}
