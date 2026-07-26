package com.spege.insanetweaks.mixins.enchant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.EnchantSourceGuard;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.world.storage.loot.functions.EnchantRandomly;

/**
 * Quest-gating, choke point 2 of 3: keep InsaneTweaks enchantments out of generated chest loot.
 *
 * <p>Target: {@code EnchantRandomly.apply(ItemStack, Random, LootContext)} — {@code func_186553_a},
 * the {@code minecraft:enchant_randomly} loot function. This is the vector that neither
 * {@code isTreasureEnchantment()} nor {@code canApplyAtEnchantingTable()} can stop: verified in
 * bytecode, the candidate loop is just
 * {@code if (stack.getItem() == Items.BOOK || enchantment.canApply(stack)) list.add(enchantment);}
 * — no treasure check anywhere, and for a plain book {@code canApply} is short-circuited away
 * entirely. Vanilla uses it on books in {@code chests/simple_dungeon},
 * {@code chests/abandoned_mineshaft}, {@code gameplay/fishing/treasure} and friends, so without this
 * a dungeon chest would happily hand out a quest-gated enchantment.
 *
 * <p>We filter the <b>registry iterator</b> that feeds the candidate loop rather than the
 * {@code List.add} that consumes it, which would be the obvious target. Reason:
 * <b>RLTweaker's {@code patchEnchantments}</b> (enabled in the DEv 1.2 pack) ASM-<em>replaces</em>
 * that exact {@code List.add} with {@code HookEnchant.addEnchantmentRestricted} for its own
 * config-driven enchantment blacklist. Two transformers rewriting one instruction means whichever
 * runs second finds it gone — either our redirect reports "Scanned 0 target(s)" (silently, under
 * {@code required: false}) or RLTweaker logs "Couldn't find add in apply" and drops its own patch.
 * {@code RegistryNamespaced.iterator()} is untouched by RLTweaker, so both filters apply and compose.
 * Also nicer: {@code iterator} is an {@code Iterable} method with no SRG name, so unlike
 * {@link MixinVillagerEnchantedBookTrade} this one matches in {@code ./gradlew runClient} too.
 *
 * <p>Only the auto-discovery branch is touched. When a loot table lists enchantments <b>explicitly</b>
 * ({@code "enchantments": ["insanetweaks:swift_picking"]}) vanilla reads them from the function's own
 * field and never iterates the registry, so a pack author can still hand the enchantment out through
 * a loot table on purpose. That is intended.
 *
 * <p>A redirect rather than an inject because by RETURN the enchantment is already written into the
 * stack's NBT (and a book has already been swapped for an enchanted book) — there is nothing left to
 * veto at that point. When every candidate is filtered out vanilla logs its own "Couldn't find a
 * compatible enchantment" and returns the item unenchanted, a path it already handles.
 */
@Mixin(EnchantRandomly.class)
public class MixinEnchantRandomly {

    @Redirect(method = { "apply", "func_186553_a" },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/registry/RegistryNamespaced;iterator()Ljava/util/Iterator;"),
            remap = false)
    private Iterator<?> insanetweaks$filterQuestGatedCandidates(RegistryNamespaced<?, ?> registry) {
        if (!EnchantSourceGuard.isActive()) {
            return registry.iterator();
        }
        // One small list per loot roll; loot rolls are rare enough that this is free, and it keeps the
        // filtering out of vanilla's own control flow entirely.
        List<Object> allowed = new ArrayList<Object>();
        for (Object entry : registry) {
            if (entry instanceof Enchantment && EnchantSourceGuard.isNaturallyBlocked((Enchantment) entry)) {
                EnchantSourceGuard.logOnce((Enchantment) entry, "loot enchant_randomly");
                continue;
            }
            allowed.add(entry);
        }
        return allowed.iterator();
    }
}
