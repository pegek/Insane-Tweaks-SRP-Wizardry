package com.spege.insanetweaks.mixins.srp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.enchant.MmmmHandler;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

/**
 * Makes Mmmm-enchanted food immune to SRP's food contamination.
 *
 * <h3>What SRP does</h3>
 * {@code EntityParasiteBase.attackEntityAsMobFood(Entity, boolean, int amount, double chance)} runs
 * at the tail of {@code attackEntityAsMob}. Past the {@code rand.nextDouble() &lt; chance} roll and a
 * non-creative-player check, it walks the 36 main-inventory slots in order, takes the <b>first</b>
 * stack whose item is an {@link ItemFood}, shrinks it by {@code rand.nextInt(amount) + 1}, and
 * spawns that many {@code SRPItems.infected_drop} on the ground with no pickup delay - which is why
 * it reads in play as the food being swapped for Infected Flesh. If main inventory yields nothing it
 * repeats the whole thing for the offhand, then returns. Rates come from
 * {@code SRParasites.cfg}: "Version &lt;X&gt; Food Item Chance" / "Food Item Amount", per tier.
 *
 * <h3>Why the anchor is {@code getItem}</h3>
 * Verified with {@code javap -p -c} on SRParasites-1.10.7: within that method
 * {@code ItemStack.func_77973_b()} is invoked exactly twice - offset 100 for the main-inventory
 * scan and 214 for the offhand - and in both places the returned {@link Item} is consumed
 * <i>solely</i> by the following {@code instanceof ItemFood}. Nothing else reads it, so handing back
 * {@link Items#AIR} for a protected stack is inert beyond making that one test fail. No
 * {@code ordinal} is wanted: binding both covers inventory and offhand alike.
 *
 * <p>The effect is that a protected stack becomes <b>invisible to the scan</b> rather than the
 * attack being cancelled - the loop simply continues to the next slot, so unenchanted food still
 * rots as SRP intends and only the enchanted stack is spared. Cancelling at HEAD would have shielded
 * a player's entire larder off the back of one enchanted apple.
 *
 * <p>Targets and {@code @At} strings use runtime SRG names with {@code remap = false}, per the
 * house rule; {@code attackEntityAsMobFood} is SRP's own name and needs no alias. Handler-body calls
 * use MCP names, which reobf maps. As with every SRG-anchored injection here, the {@code getItem}
 * anchor will not match under {@code runClient} against the deobfuscated SRP jar - it is written for
 * the obfuscated runtime the pack actually runs on.
 */
@Mixin(value = EntityParasiteBase.class, remap = false)
public abstract class MixinParasiteFoodContamination {

    @Redirect(
            method = "attackEntityAsMobFood",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;func_77973_b()Lnet/minecraft/item/Item;"),
            remap = false)
    private Item insanetweaks$hideProtectedFoodFromContamination(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemFood && MmmmHandler.protectsAgainstParasiteContamination(stack)) {
            return Items.AIR;
        }
        return item;
    }
}
