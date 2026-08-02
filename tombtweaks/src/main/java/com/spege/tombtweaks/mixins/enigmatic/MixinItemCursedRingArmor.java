package com.spege.tombtweaks.mixins.enigmatic;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.tombtweaks.util.CurseReliefHelper;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Softens the third Ring of the Seven Curses penalty — the armour debuff — for holders of the
 * "Relief for the Damned" perk.
 *
 * <p>Unlike the other two curses this one is not an event at all: {@code ItemCursedRing} builds
 * two operation-2 {@code AttributeModifier}s (on {@code generic.armor} and
 * {@code generic.armorToughness}) worth {@code -armorDebuff} each, and {@code onWornTick}
 * re-applies them <b>every single server tick</b>. There is nothing to cancel and no window in
 * which to overwrite them — anything we wrote would be replaced a tick later.
 *
 * <p>Redirecting the constant as EL reads it sidesteps that entirely, and the per-tick
 * re-application turns into a feature: buying or losing a perk level takes effect within one
 * tick without any bookkeeping on our side.
 *
 * <p>{@code createAttributeMap} reads the field twice, once per attribute, and the redirect
 * binds to both on purpose — the same relief should apply to armour and toughness alike.
 * Verified with {@code javap -p -c} against enigmaticlegacy-legacy-2.6.0.
 */
@Mixin(targets = "keletu.enigmaticlegacy.item.ItemCursedRing", remap = false)
public abstract class MixinItemCursedRingArmor {

    @Redirect(
            method = "createAttributeMap",
            at = @At(value = "FIELD",
                    target = "Lkeletu/enigmaticlegacy/EnigmaticConfigs;armorDebuff:F",
                    opcode = Opcodes.GETSTATIC),
            remap = false)
    private float tombtweaks$softenArmorDebuff(EntityPlayer player) {
        return CurseReliefHelper.softenDebuff(
                keletu.enigmaticlegacy.EnigmaticConfigs.armorDebuff, player);
    }
}
