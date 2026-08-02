package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.tombtweaks.effects.EffectPoolEntry;
import com.spege.tombtweaks.effects.EffectPoolId;
import com.spege.tombtweaks.effects.EffectPoolRegistry;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import ovh.corail.tombstone.helper.EffectHelper;
import ovh.corail.tombstone.helper.Helper;
import ovh.corail.tombstone.helper.NBTStackHelper;

/**
 * Restricts the Magic Scroll's rolled effect to our whitelist.
 *
 * <p>The Magic Scroll needs its own injection because it never touches
 * {@code EffectHelper.getRandomEffect}: it walks {@code ForgeRegistries.POTIONS.getEntries()}
 * itself, drops Tombstone's own namespace, filters on {@code isAllowedEffect} and writes the
 * winner straight into the {@code magic_effect} NBT tag. Without this, scrolls would keep rolling
 * out of the whole registry even with the whitelist on.
 *
 * <p>The NBT written here is byte-for-byte what Tombstone writes (verified against
 * {@code javap -c}), so {@code getMagicEffect} and the tooltip read it unchanged.
 */
@Mixin(targets = { "ovh.corail.tombstone.item.ItemMagicScroll" }, remap = false)
public abstract class MixinTombstoneMagicScroll {

    @Inject(method = "setRandomMagicEffect(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void tombtweaks$whitelistMagicEffect(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (!EffectPoolRegistry.isActive()) {
            return;
        }

        // Same guard Tombstone opens with: the stack has to actually be this scroll.
        Object self = this;
        if (stack.getItem() != self) {
            return;
        }

        EffectPoolEntry entry = EffectPoolRegistry.pick(EffectPoolId.MAGIC_SCROLL, true);
        if (entry == null) {
            return;
        }

        ResourceLocation key = ForgeRegistries.POTIONS.getKey(entry.potion);
        if (key == null) {
            return;
        }

        int amplifier = entry.clampAmplifier(
                EffectHelper.RANDOM_EFFECT_LEVEL.apply(Helper.RANDOM).intValue());

        NBTTagCompound effectTag = new NBTTagCompound();
        effectTag.setString("id", key.toString());
        effectTag.setByte("amplifier", (byte) amplifier);
        NBTStackHelper.getOrCreateTag(stack).setTag("magic_effect", effectTag);

        cir.setReturnValue(stack);
    }
}
