package com.spege.insanetweaks.mixins.enigmatic;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.CurseReliefHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Keeps the Cursed Ring's own tooltip honest once "Relief for the Damned" is reducing the
 * armour debuff.
 *
 * <p>Enigmatic Legacy renders that line as {@code Math.round(armorDebuff * 100) + "%"}, reading
 * the same constant {@link MixinItemCursedRingArmor} reduces. Without this the ring would keep
 * advertising "30%" while actually applying 19%.
 *
 * <p>Client-only, and listed in the mixin config's {@code client} section: the target method is
 * {@code Item.addInformation}, which is {@code @SideOnly(Side.CLIENT)}, and the handler reads
 * {@code Minecraft.getMinecraft().player}. {@code require = 0} because a cosmetic line is never
 * worth failing a load over, and this is the one injection here whose anchor is a tooltip
 * string that EL may reasonably reformat.
 */
@Mixin(targets = "keletu.enigmaticlegacy.item.ItemCursedRing", remap = false)
public abstract class MixinItemCursedRingTooltip {

    @Redirect(
            method = { "addInformation", "func_77624_a" },
            at = @At(value = "FIELD",
                    target = "Lkeletu/enigmaticlegacy/EnigmaticConfigs;armorDebuff:F",
                    opcode = Opcodes.GETSTATIC),
            remap = false,
            require = 0)
    private float insanetweaks$displayEffectiveArmorDebuff() {
        float base = keletu.enigmaticlegacy.EnigmaticConfigs.armorDebuff;
        EntityPlayer player = Minecraft.getMinecraft().player;
        return player == null ? base : CurseReliefHelper.softenDebuff(base, player);
    }
}
