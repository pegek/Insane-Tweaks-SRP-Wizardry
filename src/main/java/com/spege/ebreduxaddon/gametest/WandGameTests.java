package com.spege.ebreduxaddon.gametest;

import com.binaris.wizardry.api.content.event.SpellCastEvent;
import com.binaris.wizardry.api.content.item.IManaItem;
import com.binaris.wizardry.api.content.spell.internal.PlayerCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.api.content.util.CastItemDataHelper;
import com.binaris.wizardry.api.content.util.RegistryUtils;
import com.binaris.wizardry.core.event.WizardryEventBus;
import com.binaris.wizardry.setup.registries.EBItems;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.binaris.wizardry.content.item.WandItem;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.ModItems;
import com.spege.ebreduxaddon.feature.ModSpells;
import com.spege.ebreduxaddon.feature.WandBonusHandler;
import com.spege.ebreduxaddon.feature.WandEvolution;
import com.spege.ebreduxaddon.feature.item.SentientWandItem;
import com.spege.ebreduxaddon.feature.item.SymbioticWandItem;
import com.spege.ebreduxaddon.platform.WandProgress;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static com.spege.ebreduxaddon.gametest.CleanseGameTests.EMPTY;
import static com.spege.ebreduxaddon.gametest.CleanseGameTests.casterAt;

/** Zaklada domyslny config: evolveAt 6000, Symbiotic -15% / +25%, Sentient 0.8 / 1.3 / 1.1. */
@GameTestHolder(EbreduxAddon.MODID)
@PrefixGameTestTemplate(false)
public final class WandGameTests {

    private WandGameTests() {
    }

    private static boolean near(float a, double b) {
        return Math.abs(a - b) < 1.0E-4;
    }

    @GameTest(template = EMPTY)
    public static void symbioticBonusScalesAndAppliesOnce(GameTestHelper helper) {
        ItemStack wand = new ItemStack(ModItems.SYMBIOTIC_WAND.get());
        WandProgress.set(wand, 3000);
        SpellModifiers modifiers = new SpellModifiers();
        helper.assertTrue(WandBonusHandler.applyBonus(wand, modifiers), "bonus not applied");
        helper.assertTrue(near(modifiers.getFactor(SpellModifiers.COST), 0.925), "cost " + modifiers.getFactor(SpellModifiers.COST));
        helper.assertTrue(near(modifiers.getFactor(SpellModifiers.DURATION), 1.125), "duration " + modifiers.getFactor(SpellModifiers.DURATION));
        // Drugi Pre na tym samym obiekcie (kolejny tick kanalu) nie moze mnozyc ponownie.
        helper.assertFalse(WandBonusHandler.applyBonus(wand, modifiers), "bonus applied twice");
        helper.assertTrue(near(modifiers.getFactor(SpellModifiers.COST), 0.925), "cost compounded: " + modifiers.getFactor(SpellModifiers.COST));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sentientBonusIsFlat(GameTestHelper helper) {
        SpellModifiers modifiers = new SpellModifiers();
        WandBonusHandler.applyBonus(new ItemStack(ModItems.SENTIENT_WAND.get()), modifiers);
        helper.assertTrue(near(modifiers.getFactor(SpellModifiers.COST), 0.8), "cost " + modifiers.getFactor(SpellModifiers.COST));
        helper.assertTrue(near(modifiers.getFactor(SpellModifiers.DURATION), 1.3), "duration");
        helper.assertTrue(near(modifiers.getFactor(SpellModifiers.POTENCY), 1.1), "potency");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void reduxWandsAreUntouched(GameTestHelper helper) {
        ItemStack redux = new ItemStack(RegistryUtils.getWand(SpellTiers.ADVANCED, Elements.MAGIC));
        helper.assertTrue(redux.getItem() instanceof WandItem, "Redux advanced wand not found");
        SpellModifiers modifiers = new SpellModifiers();
        helper.assertFalse(WandBonusHandler.applyBonus(redux, modifiers), "bonus applied to a Redux wand");
        helper.assertTrue(modifiers.getFactor(SpellModifiers.COST) == 1.0f, "Redux wand cost changed");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void preAndPostRunThroughReduxBus(GameTestHelper helper) {
        // FakePlayer, nie mock z GameTestHelper: wlasne listenery Redux na tej szynie rzutuja
        // rzucajacego na ServerPlayer.
        Player player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString("0e6b2b0e-6c1a-4c5e-9a43-2a4f4c6d7e81"), "[ebreduxaddon-test]"));
        ItemStack wand = new ItemStack(ModItems.SYMBIOTIC_WAND.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        PlayerCastContext ctx = new PlayerCastContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, 0, new SpellModifiers());
        WizardryEventBus.fireEvent(new SpellCastEvent.Pre(SpellCastEvent.Sources.WAND, ModSpells.CLEANSE.get(), ctx));
        // Postep 0: mnozniki 1.0, ale znacznik musi juz siedziec w modyfikatorach.
        helper.assertFalse(WandBonusHandler.applyBonus(wand, ctx.modifiers()), "Pre listener did not run on the Redux bus");

        PlayerCastContext post = new PlayerCastContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, 0, new SpellModifiers());
        WizardryEventBus.fireEvent(new SpellCastEvent.Post(SpellCastEvent.Sources.WAND, ModSpells.CLEANSE.get(), post));
        long points = WandProgress.get(player.getMainHandItem());
        helper.assertTrue(points == ModSpells.CLEANSE.get().getCost(), "points after one Cleanse: " + points);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void evolutionKeepsSpellsManaAndProgress(GameTestHelper helper) {
        Player player = casterAt(helper, 1.5, 2, 1.5, 0f, 0f);
        ItemStack wand = new ItemStack(ModItems.SYMBIOTIC_WAND.get());
        CastItemDataHelper.setSpells(wand, List.of(ModSpells.CLEANSE.get(), ModSpells.SPINE_VOLLEY.get()));
        ((IManaItem) wand.getItem()).setMana(wand, 100);
        WandProgress.set(wand, 5999);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        helper.assertFalse(WandEvolution.tryEvolve(player, InteractionHand.MAIN_HAND), "evolved below the threshold");

        WandProgress.set(player.getMainHandItem(), 6000);
        helper.assertTrue(WandEvolution.tryEvolve(player, InteractionHand.MAIN_HAND), "did not evolve at the threshold");
        ItemStack evolved = player.getMainHandItem();
        helper.assertTrue(evolved.getItem() instanceof SentientWandItem, "not a Sentient Wand: " + evolved.getItem());
        helper.assertTrue(CastItemDataHelper.getSpells(evolved).contains(ModSpells.SPINE_VOLLEY.get()), "spells lost");
        int mana = ((IManaItem) evolved.getItem()).getMana(evolved);
        helper.assertTrue(mana == 100, "mana not carried over: " + mana);
        helper.assertTrue(WandProgress.get(evolved) == 6000, "progress lost");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void arcaneTomeCannotReplaceTheWand(GameTestHelper helper) {
        ItemStack wand = new ItemStack(ModItems.SYMBIOTIC_WAND.get());
        ItemStack tome = new ItemStack(EBItems.MASTER_ARCANE_TOME.get());
        ItemStack result = ((WandItem) wand.getItem()).applyUpgrade(null, wand, tome);
        helper.assertTrue(result.getItem() instanceof SymbioticWandItem, "tome turned the wand into " + result.getItem());
        helper.assertTrue(tome.getCount() == 1, "tome was consumed");
        helper.succeed();
    }
}
