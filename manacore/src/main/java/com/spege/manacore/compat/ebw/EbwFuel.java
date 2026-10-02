package com.spege.manacore.compat.ebw;

import java.util.ArrayList;
import java.util.List;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.compat.BaublesAccess;
import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.registry.WizardryItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Reserve sources that pay the part of a spell's cost the mana pool cannot: the mana-storing
 * artefacts listed in {@code ebw.fuelArtefacts} (Ancient Spellcraft's Rings of Mana and Majestic
 * Mana Charm by default), then hunger while EBW's Demonic Seal is worn.
 *
 * <p>Upstream, both fired when the WAND could not pay (bytecode, EBW 4.3.19 / ASC 1.8.3):
 * <ul>
 *   <li>ASC {@code ASEventHandler.onSpellCastPreEvent}: wand mana &lt;= cost &rarr; the first
 *   equipped ring-type {@code ItemManaArtefact} holding the WHOLE cost moves it into the wand,
 *   plus 50 more for a continuous spell.</li>
 *   <li>EBW {@code ItemArtefact.onSpellCastPreEvent}: wand mana &lt; cost, not continuous, not
 *   creative &rarr; costs {@code baseCost / 5} hunger (integer division, so anything under 5
 *   mana was free) and sets the spell's cost modifier to 0.</li>
 * </ul>
 * Wand mana no longer moves, so both read a number frozen at whatever the wand last held - and a
 * freshly crafted wand holds 0 ({@code ItemWand.onCreated}), which made the Demonic Seal pay
 * every one-shot cast with hunger alone. {@code MixinItemArtefact} and {@code MixinASEventHandler}
 * blind those two gates; this class replaces them, driven from the pool's own gate in
 * {@link EbwSpellCostHandler}.
 *
 * <p>Differences from upstream, all deliberate: only the missing difference is paid, not the
 * whole cost; several reserves can combine and hunger tops up the rest; hunger rounds up, so
 * nothing is free; reserve mana converts at {@code ebw.fuelArtefactManaPerPoolMana}, because the
 * artefacts keep capacities sized for wand mana.
 *
 * <p>Nothing is drained unless the whole difference can be covered, so a cast that still fails
 * costs no reserve. On the client the same arithmetic runs without side effects, so the client's
 * copy of the gate agrees with the server's instead of cancelling a cast the server allows.
 */
public final class EbwFuel {

    /** Absorbs floating-point error, so pool + covered difference reliably passes {@code hasMana}. */
    private static final double EPSILON = 1.0E-6D;

    private EbwFuel() {
    }

    /**
     * Covers {@code deficit} pool mana from reserves.
     *
     * @param commit drain the reserves and credit the pool; {@code false} only answers whether it
     *               could be done. Callers pass {@code !world.isRemote}.
     * @return whether the whole deficit is (or would be) covered.
     */
    public static boolean cover(EntityPlayer player, double deficit, boolean continuous, boolean commit) {
        if (!ManaCoreConfig.ebw.fuelEnabled || player == null || !(deficit > 0.0D)) {
            return false;
        }

        double ratio = ManaCoreConfig.ebw.fuelArtefactManaPerPoolMana;
        int unitsWanted = (int) Math.ceil(deficit * ratio - EPSILON);
        List<ItemStack> reserves = new ArrayList<ItemStack>();
        List<Integer> draws = new ArrayList<Integer>();
        int unitsFound = 0;

        List<ItemStack> equipped = BaublesAccess.equippedStacks(player);
        for (String id : ManaCoreConfig.ebw.fuelArtefacts) {
            for (ItemStack stack : equipped) {
                if (unitsFound >= unitsWanted) {
                    break;
                }
                if (!isReserve(player, stack, id)) {
                    continue;
                }
                int available = ((IManaStoringItem) stack.getItem()).getMana(stack);
                int draw = Math.min(available, unitsWanted - unitsFound);
                if (draw > 0) {
                    reserves.add(stack);
                    draws.add(Integer.valueOf(draw));
                    unitsFound += draw;
                }
            }
        }

        double remaining = Math.max(0.0D, deficit - unitsFound / ratio);
        int hunger = 0;
        if (remaining > EPSILON) {
            if (continuous || player.capabilities.isCreativeMode
                    || !ItemArtefact.isArtefactActive(player, WizardryItems.charm_hunger_casting)) {
                return false;
            }
            hunger = (int) Math.ceil(remaining / ManaCoreConfig.ebw.hungerManaPerPoint - EPSILON);
            if (player.getFoodStats().getFoodLevel() < hunger) {
                return false;
            }
        }

        if (commit) {
            for (int i = 0; i < reserves.size(); i++) {
                ItemStack stack = reserves.get(i);
                IManaStoringItem store = (IManaStoringItem) stack.getItem();
                store.setMana(stack, store.getMana(stack) - draws.get(i).intValue());
            }
            if (hunger > 0) {
                player.getFoodStats().addStats(-hunger, 0.0F);
            }
            ManaAPI.add(player, deficit + EPSILON);
        }
        return true;
    }

    private static boolean isReserve(EntityPlayer player, ItemStack stack, String id) {
        Item item = stack.getItem();
        if (!(item instanceof IManaStoringItem)) {
            return false;
        }
        ResourceLocation name = item.getRegistryName();
        if (name == null || !name.toString().equals(id)) {
            return false;
        }
        // Honours EBW's own per-artefact disable list, exactly as the upstream trigger did.
        return ItemArtefact.isArtefactActive(player, item);
    }

    /**
     * What the blinded upstream gates see as the held wand's mana: more than any spell costs, so
     * their "wand ran dry" branch never runs.
     *
     * <p>Keyed on {@code ebw.enabled}, deliberately NOT on {@code ebw.fuelEnabled}: with the pool
     * paying, wand mana is frozen whether or not our replacement is on, so letting the upstream
     * gates read it again would bring back the free-casting Demonic Seal. Only when ManaCore stops
     * handling EBW altogether does the wand pay again, and its gates become truthful again.
     */
    public static int upstreamGateMana(IManaStoringItem item, ItemStack stack) {
        if (!ManaCoreConfig.ebw.enabled) {
            return item.getMana(stack);
        }
        return Integer.MAX_VALUE;
    }
}
