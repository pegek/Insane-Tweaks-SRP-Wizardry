package com.spege.enchanteraser.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.enchanteraser.config.EnchantEraserConfig;
import com.spege.enchanteraser.util.EraserState;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;

/**
 * The backstop: {@code ItemStack.addEnchantment} is the standard way a mod puts an enchantment on an
 * item, so refusing an erased one here closes every third-party roller that uses the public API —
 * including ones nobody has looked at yet.
 *
 * <p>This exists because the alternative does not scale. Each roller patched individually needs its own
 * free anchor (InsaneTweaks and RLTweaker already occupy the obvious ones), its own mod gate, and its
 * own re-verification on every version bump — and Chance Cubes alone applies enchantments from four
 * different classes, two of them anonymous inner classes whose {@code $21}-style names are one refactor
 * away from silently no-opping. One injection on the method they all end up calling covers the lot.
 *
 * <p>🚨 It is a <b>backstop, not a replacement</b> for the caller-side mixins. Two things never reach
 * it: an enchanted <em>book</em>, which is built through {@code ItemEnchantedBook.addEnchantment} and
 * writes {@code StoredEnchantments} instead, and anything that writes the {@code ench} NBT by hand.
 * Books are exactly what the librarian trade, the {@code enchant_randomly} loot function and the JEI
 * list produce, which is why those three keep their own mixins. Do not delete a caller-side mixin
 * because this one exists.
 *
 * <p>Every vanilla caller — {@code EnchantmentHelper.addRandomEnchantment},
 * {@code ContainerEnchantment}, the {@code enchant_randomly} loot function — is already filtered
 * upstream through {@code getEnchantmentDatas}, so in a vanilla game this fires only for
 * {@code /enchant}. That is deliberate and is called out in the config comment: an enchantment the pack
 * declared unobtainable should not be handed out by the command either, and an admin who wants one
 * anyway can turn the flag off for as long as it takes.
 *
 * <p>Cancelling is the right failure mode rather than substituting another enchantment. Every other
 * choke point in this mod removes and moves on — the librarian loses an offer, the table lists fewer
 * options, the loot book comes out plain — and inventing a replacement would be a bigger behavioural
 * change than the removal. The one exception is Infernal Mobs, where the mod's own contract forbids a
 * null result.
 */
@Mixin(ItemStack.class)
public class MixinItemStackAddEnchantment {

    @Inject(method = { "addEnchantment", "func_77966_a" }, at = @At("HEAD"), cancellable = true,
            remap = false)
    private void enchanteraser$refuseErasedEnchantment(Enchantment enchantment, int level,
            CallbackInfo ci) {
        if (!EnchantEraserConfig.blockDirectEnchantCalls || EraserState.isEmpty()) {
            return;
        }
        if (EraserState.isDisabled(enchantment)) {
            EraserState.logOnce(enchantment, "ItemStack.addEnchantment");
            ci.cancel();
        }
    }
}
