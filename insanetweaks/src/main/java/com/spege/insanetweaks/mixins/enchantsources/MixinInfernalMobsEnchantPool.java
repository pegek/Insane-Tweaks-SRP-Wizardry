package com.spege.insanetweaks.mixins.enchantsources;

import java.util.Iterator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.EnchantSourceGuard;

import net.minecraft.util.registry.RegistryNamespaced;

/**
 * Quest-gate, third-party source: Infernal Mobs elite drops — the "mob drop loot" case.
 *
 * <p>{@code InfernalMobsCore.getRandomEnchantment(Random)} lazily builds a private
 * {@code ArrayList<Enchantment> enchantmentList} straight off {@code Enchantment.REGISTRY.iterator()}
 * the first time it is called, then indexes it with its own RNG. It consults neither
 * {@code isTreasureEnchantment()} nor {@code canApplyAtEnchantingTable()}, and it never touches
 * {@code EnchantmentHelper}, so none of the vanilla choke points see it. That list feeds
 * {@code dropRandomEnchantedItems}, i.e. the gear an infernal mob drops on death.
 *
 * <p>Filtering the iterator at build time is the right anchor precisely <b>because</b> the list is
 * cached: one filtered build and every later pick from that cache is clean, with zero per-drop cost.
 *
 * <p>NOTE on the cache: the list is built once and kept for the session, so this must run on the
 * first call. It does — a redirect is part of the method body, not something that can arrive late.
 * The only way to get a stale unfiltered list would be to toggle
 * {@code enchantments.blockNaturalDiscovery} on <i>after</i> Infernal Mobs had already built its
 * cache; that flag is documented as read-live everywhere else, so it is called out here as the one
 * place where a mid-session toggle needs a restart to take effect.
 *
 * <p>Gated on {@code InfernalMobs} by {@code LateMixinBooter} — note the modid's unusual capitals,
 * which {@code Loader.isModLoaded} matches exactly.
 */
@Mixin(targets = "atomicstryker.infernalmobs.common.InfernalMobsCore", remap = false)
public class MixinInfernalMobsEnchantPool {

    @Redirect(method = "getRandomEnchantment",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/registry/RegistryNamespaced;iterator()Ljava/util/Iterator;"),
            remap = false)
    private Iterator<?> insanetweaks$filterQuestGatedPool(RegistryNamespaced<?, ?> registry) {
        return EnchantSourceGuard.filteredIterator(registry, "Infernal Mobs drop");
    }
}
