package com.spege.enchanteraser.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.enchanteraser.config.EnchantEraserConfig;
import com.spege.enchanteraser.util.EraserState;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Opt-in, off by default: delete erased enchantments from items that already carry them, as those items
 * are read back in.
 *
 * <p>Target is the deserialization constructor {@code ItemStack(NBTTagCompound)} — the single funnel
 * every stored stack comes through. Chunk load brings chest and tile-entity contents; {@code player.dat}
 * brings inventories and ender chests; item entities, Tombstone graves and every stack in a network
 * packet arrive the same way. Forge's {@code deserializeNBT} delegates here too. Nothing else in the
 * game reconstructs a stack from NBT, and the paths that build one from scratch —
 * {@code ItemStack.copy()}, {@code ItemEnchantedBook.getSubItems} for the JEI/creative list — do not
 * come through this constructor and are therefore untouched.
 *
 * <p>It is idempotent and needs no dirty tracking: the object handed back to the caller is already
 * clean, so whatever writes it next serialises the cleaned form. An item that is never loaded is never
 * touched, which is the honest limit of this approach — a chest in an unloaded chunk keeps its NBT
 * until someone walks there.
 *
 * <p>🚨 This is the one thing in the mod that destroys data, and it cannot be undone by turning the flag
 * back off. Removing an entry from {@code Disabled Enchantments} restores nothing on items already
 * cleaned. That is why it defaults to off and why the config comment says so twice.
 *
 * <p>An enchanted book whose only enchantments were erased ends up blank. The loot mixin swaps such a
 * book for a plain one, but that is not possible here: the stack's item is set before this injection
 * runs and cannot be changed from inside its own constructor. A blank enchanted book is harmless and
 * reads clearly enough as "there was something here".
 *
 * <p>Performance: the constructor is genuinely hot — it runs for every stack in every loading chunk and
 * every network packet. Both guards in front of the strip are allocation-free. The config flag is a
 * static boolean read; {@link EraserState#mayCarryEnchantments} reads the two NBT keys directly rather
 * than calling {@code getEnchantmentTagList}, which allocates an empty list when the tag is absent —
 * the overwhelmingly common case here.
 */
@Mixin(ItemStack.class)
public class MixinItemStackLoadErase {

    /**
     * {@code @At("RETURN")} rather than {@code HEAD}: a constructor's fields are not populated until
     * after the super call, and Mixin forbids injecting ahead of it. Class names inside a descriptor are
     * not SRG-renamed, so this selector matches in the dev environment and the obfuscated runtime alike.
     */
    @Inject(method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"), remap = false)
    private void enchanteraser$stripErasedOnLoad(NBTTagCompound compound, CallbackInfo ci) {
        if (!EnchantEraserConfig.stripOnItemLoad || EraserState.isEmpty()) {
            return;
        }
        ItemStack self = (ItemStack) (Object) this;
        if (!EraserState.mayCarryEnchantments(self)) {
            return;
        }
        EraserState.stripFromStack(self, "item load");
    }
}
