package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.tombtweaks.util.GraveKeySoulboundPolicy;

import net.minecraft.item.ItemStack;
import ovh.corail.tombstone.helper.EntityHelper;

/**
 * Grave Keys Survive Death: lets a carried grave key lose its soulbound status at death.
 *
 * <p>{@code storeSoulboundsOnBody} walks the dropped items and moves every one for which
 * {@code EntityHelper.hasSoulbound} answers true out of the drops - keys into {@code tb_key_stacks},
 * the rest into {@code tb_soulbound_stacks} - so they come back on respawn. {@code hasSoulbound}
 * returns true for any grave key, enchanted or not. Answering false here leaves the key in the
 * drops, and it goes into the new grave like any other item.
 *
 * <p>4.8.0 calls {@code hasSoulbound} exactly once in the whole mod, from this method, so this
 * redirect is the entire decision and touches nothing else (tooltips do not go through it). The
 * key to the grave being created is handed out separately, by {@code handleLoot}, and is
 * unaffected.
 */
@Mixin(targets = "ovh.corail.tombstone.helper.DeathHandler", remap = false)
public abstract class MixinTombstoneSoulboundKeys {

    @Redirect(method = "storeSoulboundsOnBody",
            at = @At(value = "INVOKE",
                    target = "Lovh/corail/tombstone/helper/EntityHelper;hasSoulbound(Lnet/minecraft/item/ItemStack;)Z",
                    remap = false),
            remap = false)
    private boolean tombtweaks$graveKeySoulbound(ItemStack stack) {
        return GraveKeySoulboundPolicy.keepOnDeath(stack, EntityHelper.hasSoulbound(stack));
    }
}
