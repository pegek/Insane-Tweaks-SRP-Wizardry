package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.tombtweaks.util.PerkTuningHelper;

import net.minecraft.entity.player.EntityPlayer;
import ovh.corail.tombstone.api.capability.Perk;
import ovh.corail.tombstone.helper.EntityHelper;

/**
 * Scribe: how much one Book of Disenchantment strips off an item.
 *
 * <p>Tombstone computes {@code maxExtracted = perkLevel + 2} and then pulls that many enchantments
 * into individual enchanted books, removing each from the item. The constant is what makes the book
 * strong before the perk is trained at all, and the perk adds a flat one per level on top — so both
 * halves need their own knob.
 *
 * <p>Both anchors were confirmed unique across the whole 238-instruction body of {@code setEnchant}
 * on 4.7.6: exactly one {@code iconst_2} and exactly one {@code getPerkLevelWithBonus} call.
 * Tombstone's Tetra branch (which returns {@code Integer.MAX_VALUE} regardless of the perk) is
 * untouched and unreachable without Tetra installed.
 */
@Mixin(targets = "ovh.corail.tombstone.item.ItemBookOfDisenchantment", remap = false)
public abstract class MixinTombstoneBookOfDisenchantment {

    @ModifyConstant(method = "setEnchant", constant = @Constant(intValue = 2), remap = false)
    private int tombtweaks$scribeBase(int original) {
        return PerkTuningHelper.scribeBaseEnchants(original);
    }

    @Redirect(method = "setEnchant",
            at = @At(value = "INVOKE",
                    target = "Lovh/corail/tombstone/helper/EntityHelper;getPerkLevelWithBonus(Lnet/minecraft/entity/player/EntityPlayer;Lovh/corail/tombstone/api/capability/Perk;)I"),
            remap = false)
    private int tombtweaks$scribePerLevel(EntityPlayer player, Perk perk) {
        return PerkTuningHelper.scribeScaledLevel(EntityHelper.getPerkLevelWithBonus(player, perk));
    }
}
