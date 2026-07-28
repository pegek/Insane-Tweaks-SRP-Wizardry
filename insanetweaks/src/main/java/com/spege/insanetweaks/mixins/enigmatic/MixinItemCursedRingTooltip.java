package com.spege.insanetweaks.mixins.enigmatic;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.CurseReliefHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Keeps the Cursed Ring's own tooltip honest once "Relief for the Damned" is reducing its
 * penalties.
 *
 * <p>Enigmatic Legacy builds all three curse lines by reading the very constants the sibling
 * mixins reduce, so without this the ring keeps advertising the unmitigated numbers — "30%"
 * armour loss while actually applying 19%, and so on.
 *
 * <p>Verified with {@code javap -p -c} against enigmaticlegacy-legacy-2.6.0: inside
 * {@code func_77624_a} the reads are {@code painMultiplier} at 22 and 78, {@code armorDebuff}
 * at 143 and {@code monsterDamageDebuff} at 207. None of the redirects carries an
 * {@code ordinal}, so each binds to every read of its field in the method — see the note on
 * {@link #insanetweaks$displayEffectivePainMultiplier} for why that is deliberate rather than
 * merely tolerable.
 *
 * <p>Client-only, and listed in the mixin config's {@code client} section: the target method is
 * {@code Item.addInformation}, which is {@code @SideOnly(Side.CLIENT)}, and the handlers read
 * {@code Minecraft.getMinecraft().player}. {@code require = 0} throughout because a cosmetic
 * line is never worth failing a load over, and these are the injections here whose anchors sit
 * in a tooltip string EL may reasonably reformat.
 */
@Mixin(targets = "keletu.enigmaticlegacy.item.ItemCursedRing", remap = false)
public abstract class MixinItemCursedRingTooltip {

    /**
     * Curse 1's line. The first of the two reads is EL's {@code painMultiplier == 2.0} test,
     * which picks between the fixed "You receive double damage" string and the
     * {@code _alt} one that prints a percentage. Binding there too is the point: at relief the
     * effective multiplier is no longer 2.0, so the tooltip must fall to the percentage branch
     * instead of claiming "double" while the player takes 1.64x.
     */
    @Redirect(
            method = { "addInformation", "func_77624_a" },
            at = @At(value = "FIELD",
                    target = "Lkeletu/enigmaticlegacy/EnigmaticConfigs;painMultiplier:F",
                    opcode = Opcodes.GETSTATIC),
            remap = false,
            require = 0)
    private float insanetweaks$displayEffectivePainMultiplier() {
        float base = keletu.enigmaticlegacy.EnigmaticConfigs.painMultiplier;
        EntityPlayer player = Minecraft.getMinecraft().player;
        return player == null ? base : CurseReliefHelper.softenMultiplier(base, player);
    }

    /** Curse 3's line, mirroring {@link MixinItemCursedRingArmor} on the attribute side. */
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

    /** Curse 4's line, mirroring the damage-dealt redirect in {@code MixinEnigmaticEventsCurses}. */
    @Redirect(
            method = { "addInformation", "func_77624_a" },
            at = @At(value = "FIELD",
                    target = "Lkeletu/enigmaticlegacy/EnigmaticConfigs;monsterDamageDebuff:F",
                    opcode = Opcodes.GETSTATIC),
            remap = false,
            require = 0)
    private float insanetweaks$displayEffectiveMonsterDamageDebuff() {
        float base = keletu.enigmaticlegacy.EnigmaticConfigs.monsterDamageDebuff;
        EntityPlayer player = Minecraft.getMinecraft().player;
        return player == null ? base : CurseReliefHelper.softenDebuff(base, player);
    }
}
