package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Vetoes the two Imbuement Altar results that Abomination breaks: an armour set that does not exist,
 * and a crystal <em>block</em> variant that has no model and no way back.
 *
 * <p>Both are {@code @Inject}s at {@code HEAD} of the same {@code getImbuementResult}. They are
 * separate handlers rather than one because they share nothing but the injection point - different
 * inputs, different failure, different key. Injectors compose, and {@code defaultRequire: 1} is
 * per-injector, so each is independently verified to bind.
 *
 * <h2>Branch 1 - armour ({@code insanetweaks$vetoUnregisteredArmourImbuement})</h2>
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
 * <p>Keyed on "the lookup produced no usable armour", not on the element's identity, so it stops firing
 * by itself the day a matching armour set is registered under the {@code ebwizardry} namespace.
 *
 * <h2>Branch 2 - crystal block ({@code insanetweaks$vetoUnrenderableCrystalBlockImbuement})</h2>
 *
 * <p>The crystal branch accepts {@code magic_crystal} <em>and</em>
 * {@code Item.getItemFromBlock(crystal_block)}, and returns the same item at the target element's
 * ordinal. For the crystal <b>item</b> that is entirely fine and deliberately left alone: meta 8 has a
 * model ({@code assets/ebwizardry/models/item/crystal_abomination.json}) and a lang key, both shipped by
 * this mod, and {@code ItemCrystal.getModelName} builds its path from a live {@code Element.values()}.
 *
 * <p>The <b>block</b> is not fine, and the reason is EBW's state mapper, not its blockstate property.
 * Note carefully: {@code BlockCrystal.ELEMENT} is a {@code PropertyEnum.create(..., Element.class)}
 * built after we have already appended Abomination, so the property genuinely has nine values and
 * {@code getStateFromMeta(8)} resolves fine - and {@code MixinBlockCrystalElements} narrows only
 * {@code getSubBlocks}, never the property (its own javadoc says so, and it must stay that way or a
 * blockstate round trip would land on the wrong element). What actually breaks is rendering:
 * {@code WizardryModels} registers
 * {@code new StateMap.Builder().withName(ELEMENT).withSuffix("_crystal_block")}, so meta 8 asks for
 * {@code ebwizardry:abomination_crystal_block} - one of eight per-element blockstate files, and the
 * ninth does not exist. On top of that the reverse recipe is eight hardcoded JSONs
 * ({@code crystal_block_to_crystals_*}), each naming a fixed {@code data} value, so there is no
 * abomination one either. A player feeding in nine crystals' worth of block plus four dust would get
 * back an unrenderable, unrecoverable brick. Hiding it from JEI ({@code MixinJeiImbuementAltarElements})
 * never stopped the altar itself.
 *
 * <p>🚨 Keyed on {@link NativeElements}, not on Abomination's identity. {@code NativeElements} is the
 * single knob both crystal-block mixins already use, so all three move together: the day the block is
 * un-hidden - by shipping {@code abomination_crystal_block} assets and a reverse recipe, then dropping
 * Abomination from that exclusion - this guard opens by itself with no edit here. Same self-disabling
 * property as the armour check above.
 *
 * <p>The metadata-0 test mirrors EBW's own branch condition exactly ({@code input.getMetadata() == 0}),
 * so this can only ever veto an input EBW would really have converted.
 *
 * <h2>Both branches pre-empt {@code ImbuementActivateEvent}</h2>
 *
 * <p>The HEAD cancels fire before EBW's own {@code @Cancelable ImbuementActivateEvent} is posted, so a
 * third-party mod that wanted to answer these exact combinations itself - by setting
 * {@code event.result} - never gets the chance. This is a deliberate trade, not an oversight: every
 * later injection point is worse. Landing after {@code EventBus.post} but before its {@code IFEQ} would
 * skip {@code return event.result} for a listener that *did* cancel, which is a worse bug than the one
 * being fixed. Landing after the whole {@code if (world != null)} block has no stable instruction to
 * anchor on, only a brittle bytecode offset. And inside either branch there is no stack-empty point at
 * all - the crashing cast happens mid-expression. The loss is confined to the combinations that are
 * broken today; for every element with registered armour, and every element the crystal block can
 * render, these guards return early before touching the event, so the event still fires normally.
 *
 * <p><b>Shares its target method with a twin.</b> {@code MixinTileEntityImbuementAltar} (config
 * {@code mixins.insanetweaks.compat.json}) is a third HEAD-cancellable {@code @Inject} into this same
 * {@code getImbuementResult}, cancelling only for {@code ebwizardry:master_*wand} and
 * {@code insanetweaks:corrupted_fruit} inputs. All three conditions are disjoint today, so which one
 * runs first does not matter - but that ordering is not actually controlled (both mixins sit at the
 * default priority 1000, and Mixin's tie-break is "whichever config was applied later ends up first"),
 * so widening any of them needs a re-check against the others before assuming they stay disjoint.
 */
@Mixin(value = TileEntityImbuementAltar.class, remap = false)
public abstract class MixinImbuementAltarGuard {

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

        Element target = insanetweaks$uniformElement(receptacleElements);
        if (target == null) {
            return;
        }

        Item result = ItemWizardArmour.getArmour(target, armour.armourClass, armour.armorType);
        if (result instanceof IManaStoringItem) {
            // A real armour piece exists for this element - let EBW build it normally.
            return;
        }

        cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "getImbuementResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void insanetweaks$vetoUnrenderableCrystalBlockImbuement(
            ItemStack input, Element[] receptacleElements, boolean fullLootGen,
            World world, EntityPlayer lastUser, CallbackInfoReturnable<ItemStack> cir) {

        // The BLOCK only. The crystal item is a genuine, renderable result - see the class javadoc.
        if (input.getItem() != Item.getItemFromBlock(WizardryBlocks.crystal_block)) {
            return;
        }

        // Mirrors EBW's own branch condition: it converts the plain (MAGIC, meta 0) form and nothing
        // else, so an already-imbued block is not ours to veto.
        if (input.getMetadata() != 0) {
            return;
        }

        Element target = insanetweaks$uniformElement(receptacleElements);
        if (target == null) {
            return;
        }

        for (Element renderable : NativeElements.values()) {
            if (renderable == target) {
                // A blockstate variant and a reverse recipe exist for this element.
                return;
            }
        }

        cir.setReturnValue(ItemStack.EMPTY);
    }

    /**
     * EBW's {@code Arrays.stream(receptacleElements).distinct().count() == 1 && receptacleElements[0]
     * != null}, without the stream: the single element every receptacle agrees on, or null if they
     * disagree, if any is null, or if there are none.
     *
     * <p>A private static helper on a mixin is merged into the target, which is fine - it allocates
     * nothing and holds no state, so there is no {@code <clinit>} to merge. It carries the
     * {@code insanetweaks$} prefix for the same reason the handlers do: the merged method lands in
     * {@code TileEntityImbuementAltar}'s own namespace, and a bare name could collide with an EBW
     * member added in a later version.
     */
    private static Element insanetweaks$uniformElement(Element[] receptacleElements) {
        if (receptacleElements.length == 0 || receptacleElements[0] == null) {
            return null;
        }
        Element target = receptacleElements[0];
        for (Element el : receptacleElements) {
            if (el != target) {
                return null;
            }
        }
        return target;
    }
}
