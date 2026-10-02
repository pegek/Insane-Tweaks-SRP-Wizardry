package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.api.capability.ISoulConsumer;
import ovh.corail.tombstone.block.BlockDecorativeGrave;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Startuje cooldown dokladnie wtedy, gdy uzycie ksiegi sie powiodlo.
 *
 * <p>ISoulConsumer.setEnchant jest wolane z jednego miejsca w calym Tombstonie - stad
 * jeden redirect obejmuje wszystkie ksiegi.
 *
 * <p>Dwa poziomy remapowania: selektor method = "use" celuje w metode Minecrafta
 * (BlockBehaviour.use), wiec MUSI byc remapowany przez refmape - i to on jest powodem,
 * dla ktorego refmapy w tym projekcie sa wlaczone. Wewnetrzny @At celuje w czlonka
 * Tombstone'a i dostaje wlasne remap = false.
 *
 * <p>Receiver redirectu to ISoulConsumer, bo wywolanie to invokeinterface na tym
 * interfejsie - nie na konkretnej ksiedze. Ten sam receiver jest kluczem cooldownu:
 * to Item ksiegi, a stack moze byc juz pusty, bo ksiega zuzyla sie przy uzyciu.
 */
@Mixin(BlockDecorativeGrave.class)
public class MixinBlockDecorativeGrave {

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lovh/corail/tombstone/api/capability/ISoulConsumer;setEnchant("
                           + "Lnet/minecraft/world/level/Level;"
                           + "Lnet/minecraft/core/BlockPos;"
                           + "Lnet/minecraft/server/level/ServerPlayer;"
                           + "Lnet/minecraft/world/item/ItemStack;I)"
                           + "Lovh/corail/tombstone/api/capability/ISoulConsumer$ConsumeResult;",
                    remap = false))
    private ISoulConsumer.ConsumeResult tombtweaks$startCooldown(ISoulConsumer consumer,
                                                                 Level level, BlockPos pos,
                                                                 ServerPlayer player,
                                                                 ItemStack stack, int soulStrength) {
        ISoulConsumer.ConsumeResult result = consumer.setEnchant(level, pos, player, stack, soulStrength);
        // Tylko ItemBook: blokada siedzi wylacznie na ItemBook.canEnchant, wiec cooldown
        // innego ISoulConsumer (zwoje, tablice, receptakle) startowalby i niczego nie blokowal.
        if (result != null && result.result() == ISoulConsumer.ConsumeResult.Result.SUCCESS
                && consumer instanceof ItemBook book) {
            BookCooldownService.start(player, book);
        }
        return result;
    }
}
