package com.spege.enchanteraser.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.enchanteraser.util.EraserState;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.item.ItemStack;

/**
 * Choke point 2 of 4: the anvil will not apply an erased enchantment.
 *
 * <p>Target: {@code ContainerRepair.updateRepairOutput()} — {@code func_82848_d}, which contains
 * <b>exactly one</b> {@code Enchantment.canApply} invoke (confirmed with {@code javap -p -c},
 * attributed to its enclosing method). That call decides whether an enchantment coming off the
 * <em>right</em> input is allowed onto the output; the output map is seeded from the <em>left</em>
 * input before the loop runs, so returning false here blocks the transfer and leaves an already
 * enchanted weapon completely untouched. Renaming and repairing existing gear keep working — that is
 * the deliberate "block application, never destroy what a player already owns" boundary. Removing an
 * erased enchantment from existing NBT is a separate opt-in feature for a later version.
 *
 * <p>Redirecting the call rather than overriding {@code Enchantment.canApply} is the whole point: the
 * method is virtual and mod enchantments override it (SoManyEnchantments' {@code EnchantmentSplitshot}
 * does), so an injection into the base class would never run for them. A redirect binds to the call
 * instruction and therefore fires whichever subclass is on the other end.
 *
 * <p>🚨 {@code ContainerRepair} is a crowded target and {@code priority = 1500} is load-bearing.
 * <b>noexpensive</b> ({@code NE-1.12.2-Forge-1.1.1.jar}, modid {@code noexpensive}) {@code @Overwrite}s
 * {@code func_82848_d} outright at the default priority 1000, and Mixin refuses to inject into a method
 * another mixin merged at a priority greater than or equal to your own — which is exactly why
 * UniversalTweaks' two {@code UTContainerRepairMixin}s fail on this class in the DEv 1.2 pack, and why
 * this one failed at priority 1000 too. Sitting above noexpensive makes this apply after the overwrite
 * and inject into <em>its</em> body, which is the body that actually runs. Verified with {@code javap}:
 * noexpensive's replacement still contains exactly one {@code Enchantment.func_92089_a} invoke, so the
 * anchor survives the overwrite. SoManyEnchantments also mixes this class but only injects into
 * {@code onCraftMatrixChanged} and declares no {@code @Overwrite}, so it does not contend.
 *
 * <p>If a future noexpensive drops that invoke, the fallback is Forge's {@code AnvilUpdateEvent} —
 * cancel the whole combine when the right-hand stack carries an erased enchantment. That is coarser
 * (it would also refuse the allowed enchantments on the same book) but collision-proof.
 *
 * <p>🚨 {@code @At.target} syntax is Mixin's {@code MemberInfo}, {@code Lowner;name(args)ret} —
 * <b>no colon</b> before the descriptor. Writing the javap form
 * {@code Lowner;name:(args)ret} throws {@code InvalidMemberDescriptorException: Invalid name: name:}
 * at injection-point construction, which surfaces as a mixin-apply failure at runtime rather than a
 * compile error.
 *
 * <p>🚨 There is no refmap in this repo ({@code -proc:none}) and {@code @At.target} takes a single
 * member name, so the SRG name is hardcoded and this one mixin does <b>not</b> match under
 * {@code ./gradlew runClient}, where the method is still called {@code canApply}. Under
 * {@code "required": false} it no-ops silently there. That is accepted: testing happens with the built
 * jar in the DEv 1.2 instance, against the obfuscated runtime. Everything else in this mod matches in
 * both environments.
 */
@Mixin(value = ContainerRepair.class, priority = 1500)
public class MixinContainerRepairErase {

    @Redirect(method = { "updateRepairOutput", "func_82848_d" },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/Enchantment;func_92089_a(Lnet/minecraft/item/ItemStack;)Z"),
            remap = false)
    private boolean enchanteraser$refuseErasedOnAnvil(Enchantment enchantment, ItemStack stack) {
        if (EraserState.isDisabled(enchantment)) {
            EraserState.logOnce(enchantment, "anvil");
            return false;
        }
        return enchantment.canApply(stack);
    }
}
