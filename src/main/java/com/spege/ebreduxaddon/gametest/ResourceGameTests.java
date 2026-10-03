package com.spege.ebreduxaddon.gametest;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

import static com.spege.ebreduxaddon.gametest.CleanseGameTests.EMPTY;

@GameTestHolder(EbreduxAddon.MODID)
@PrefixGameTestTemplate(false)
public final class ResourceGameTests {

    private ResourceGameTests() {
    }

    /** Przepis, ktory nie sparsuje sie (np. zla nazwa przedmiotu Redux), znika po cichu - stad test. */
    @GameTest(template = EMPTY)
    public static void recipesLoadWithTheirResults(GameTestHelper helper) {
        String[][] expected = {
                {"symbiotic_wand", "symbiotic_wand"},
                {"grafted_helmet", "grafted_helmet"},
                {"grafted_chestplate", "grafted_chestplate"},
                {"grafted_leggings", "grafted_leggings"},
                {"grafted_boots", "grafted_boots"}};
        for (String[] row : expected) {
            Optional<? extends Recipe<?>> recipe = helper.getLevel().getRecipeManager().byKey(EbreduxAddon.id(row[0]));
            helper.assertTrue(recipe.isPresent(), "recipe missing: " + row[0]);
            ResourceLocation result = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    recipe.get().getResultItem(helper.getLevel().registryAccess()).getItem());
            helper.assertTrue(result.equals(EbreduxAddon.id(row[1])), row[0] + " makes " + result);
        }
        helper.assertTrue(ModItems.SENTIENT_WAND.get() != null, "items not registered");
        helper.succeed();
    }
}
