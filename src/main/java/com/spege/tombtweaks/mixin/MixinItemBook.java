package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Blokada uzycia ksiegi w trakcie cooldownu.
 *
 * <p>Siedzi na klasie bazowej wszystkich ksiag, wiec dziala niezaleznie od tego, skad
 * ktos ja zawola. remap = false: canEnchant to metoda Tombstone'a.
 */
@Mixin(ItemBook.class)
public class MixinItemBook {

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true, remap = false)
    private void tombtweaks$blockDuringCooldown(Level level, BlockPos pos, Player player,
                                                ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (level == null || level.isClientSide() || player == null) {
            return;
        }
        Item self = (Item) (Object) this;
        long remaining = BookCooldownService.remaining(player, self);
        if (remaining > 0L) {
            BookCooldownService.tellRemaining(player, remaining);
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
