package com.spege.insanetweaks.integration.patchouli;

import java.util.List;

import com.spege.insanetweaks.api.RitualRecipe;
import com.spege.insanetweaks.api.RitualRegistry;
import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.registry.WizardryItems;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariableProvider;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * Fills the Codex's {@code insanetweaks:ritual} page template from {@link RitualRegistry}, so a ritual
 * page can never disagree with the ritual the altar actually runs.
 *
 * <p>The page names its ritual by id ({@code "ritual": "insanetweaks:living_wand"}). Keys this
 * processor answers:
 * <ul>
 * <li>{@code s0}..{@code s8} - the nine cells, unrotated, row-major from the north-west corner;
 *     {@code ""} for a cell that must stay empty (the template guards on it);</li>
 * <li>{@code rN}, {@code rE}, {@code rS}, {@code rW} - the receptacles: N shows Abomination dust (one
 *     must burn it), the other three every element's dust;</li>
 * <li>{@code result} and {@code name} - the product and its display name.</li>
 * </ul>
 *
 * <p>🚨 Patchouli is optional. This class is only ever loaded by Patchouli itself, by the name written
 * in the template JSON; nothing else in the mod may reference it, or a pack without Patchouli fails to
 * link the referencing class.
 */
public class RitualPageProcessor implements IComponentProcessor {

    private RitualRecipe recipe;

    @Override
    public void setup(IVariableProvider<String> variables) {
        String id = variables.has("ritual") ? variables.get("ritual") : null;
        List<RitualRecipe> recipes = RitualRegistry.getRecipes();
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).getId().toString().equals(id)) {
                this.recipe = recipes.get(i);
                return;
            }
        }
        this.recipe = null; // unknown id or a switched-off ritual: every key answers ""
    }

    @Override
    public String process(String key) {
        if (this.recipe == null) {
            return "";
        }
        if (key.length() == 2 && key.charAt(0) == 's' && Character.isDigit(key.charAt(1))) {
            Ingredient cell = this.recipe.getIngredient(key.charAt(1) - '0');
            return cell.getMatchingStacks().length == 0 ? "" : PatchouliAPI.instance.serializeIngredient(cell);
        }
        if ("rN".equals(key)) {
            return abominationDust();
        }
        if ("rE".equals(key) || "rS".equals(key) || "rW".equals(key)) {
            return anyElementDust();
        }
        if ("result".equals(key)) {
            return PatchouliAPI.instance.serializeItemStack(this.recipe.getResult());
        }
        if ("name".equals(key)) {
            return this.recipe.getResult().getDisplayName();
        }
        return "";
    }

    private static String abominationDust() {
        if (!ModElements.EXTENDED || ModElements.ABOMINATION == null) {
            return "";
        }
        return PatchouliAPI.instance.serializeItemStack(
                new ItemStack(WizardryItems.spectral_dust, 1, ModElements.ABOMINATION.ordinal()));
    }

    /** One dust per element except MAGIC, which has none. */
    private static String anyElementDust() {
        Element[] elements = Element.values();
        ItemStack[] dusts = new ItemStack[elements.length - 1];
        int n = 0;
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] != Element.MAGIC) {
                dusts[n++] = new ItemStack(WizardryItems.spectral_dust, 1, elements[i].ordinal());
            }
        }
        return PatchouliAPI.instance.serializeIngredient(Ingredient.fromStacks(dusts));
    }
}
