package com.spege.manacore.mixins.nd;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.manacore.compat.nd.LeechlinkBridge;

import electroblob.wizardry.item.IManaStoringItem;
import net.minecraft.item.ItemStack;

/**
 * Routes the Leechlink Amulet's mana return into the owner's pool - see {@link LeechlinkBridge}.
 *
 * <p>Both {@code rechargeMana} calls in {@code stealMana} are the Leechlink return (one per hand
 * the owner might hold a mana item in), so the redirect deliberately has no ordinal. The victim's
 * drain in the same method is a {@code consumeMana} call and is not touched. Targeted by name:
 * there is no Necromancer's Delight jar on the compile classpath, and none is needed, because
 * every type in the redirected call is EBW's or vanilla's.
 */
@Mixin(targets = "com.windanesz.necromancersdelight.entity.living.EntityManaLeechMinion", remap = false)
public abstract class MixinEntityManaLeechMinion {

    @Redirect(method = "stealMana", at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/item/IManaStoringItem;rechargeMana(Lnet/minecraft/item/ItemStack;I)V"),
            remap = false)
    private void manacore$returnStolenManaToPool(IManaStoringItem item, ItemStack stack, int amount) {
        LeechlinkBridge.returnStolenMana(this, item, stack, amount);
    }
}
