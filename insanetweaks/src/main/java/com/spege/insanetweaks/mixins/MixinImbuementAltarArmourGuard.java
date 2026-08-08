package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Vetoes an Imbuement Altar armour result before EBW can cast an unregistered item to
 * {@code IManaStoringItem}.
 *
 * <p>{@code getImbuementResult}'s armour branch looks up {@code ItemWizardArmour.getArmour(element,
 * armourClass, armorType)} and builds an {@code ItemStack} straight from whatever it returns, with no
 * check for a miss. For an element with no armour registered under that name - Abomination today, any
 * future element with no armour set tomorrow - the very next line casts {@code result.getItem()} to
 * {@code IManaStoringItem}: a {@code ClassCastException} one line before the {@code isEmpty} check that
 * looks like it would have caught this. Do not "simplify" the guard below to
 * {@code getArmour(...) == null} - {@code Item.REGISTRY} is a plain {@code RegistryNamespaced} that
 * would return null on a miss, but Forge substitutes a wrapper whose default key resolves to
 * {@code minecraft:air}, so what a miss actually yields is not settled by one declaration. The
 * {@code instanceof IManaStoringItem} check below is correct either way, because it is exactly the
 * question the crashing cast asks. This is not JEI-only - the altar's tile entity calls this method
 * itself whenever its contents change.
 *
 * <p>Guarded on "the lookup produced no usable armour", not on the element's identity, so this stops
 * firing by itself the day a matching armour set is registered under the {@code ebwizardry} namespace -
 * no edit needed here or in JEI.
 *
 * <p><b>Pre-empts {@code ImbuementActivateEvent}.</b> The HEAD cancel below fires before EBW's own
 * {@code @Cancelable ImbuementActivateEvent} is posted, so a third-party mod that wanted to answer this
 * exact combination itself - by setting {@code event.result} - never gets the chance. This is a
 * deliberate trade, not an oversight: every later injection point is worse. Landing after
 * {@code EventBus.post} but before its {@code IFEQ} would skip {@code return event.result} for a
 * listener that *did* cancel, which is a worse bug than the one being fixed. Landing after the whole
 * {@code if (world != null)} block has no stable instruction to anchor on, only a brittle bytecode
 * offset. And inside the armour branch itself there is no stack-empty point at all - the cast happens
 * mid-expression. The loss is confined to the one combination that crashes today; for every element
 * with registered armour this guard returns early before touching the event, so the event still fires
 * normally.
 *
 * <p><b>Shares its target method with a twin.</b> {@code MixinTileEntityImbuementAltar} (config
 * {@code mixins.insanetweaks.compat.json}) is a second HEAD-cancellable {@code @Inject} into this same
 * {@code getImbuementResult}, cancelling only for {@code ebwizardry:master_*wand} and
 * {@code insanetweaks:corrupted_fruit} inputs. The two conditions are disjoint today, so which one runs
 * first does not matter - but that ordering is not actually controlled (both mixins sit at the default
 * priority 1000, and Mixin's tie-break is "whichever config was applied later ends up first"), so
 * widening either condition needs a re-check against the other before assuming they stay disjoint.
 */
@Mixin(value = TileEntityImbuementAltar.class, remap = false)
public abstract class MixinImbuementAltarArmourGuard {

    @Inject(method = "getImbuementResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void insanetweaks$vetoUnregisteredArmourImbuement(
            ItemStack input, Element[] receptacleElements, boolean fullLootGen,
            World world, EntityPlayer lastUser, CallbackInfoReturnable<ItemStack> cir) {

        if (!(input.getItem() instanceof ItemWizardArmour)) {
            return;
        }
        ItemWizardArmour armour = (ItemWizardArmour) input.getItem();

        // EBW's own check: only elementless armour is imbuable in the first place.
        if (armour.element != null) {
            return;
        }

        if (receptacleElements.length == 0 || receptacleElements[0] == null) {
            return;
        }
        Element target = receptacleElements[0];
        for (Element el : receptacleElements) {
            if (el != target) {
                return;
            }
        }

        Item result = ItemWizardArmour.getArmour(target, armour.armourClass, armour.armorType);
        if (result instanceof IManaStoringItem) {
            // A real armour piece exists for this element - let EBW build it normally.
            return;
        }

        cir.setReturnValue(ItemStack.EMPTY);
    }
}
