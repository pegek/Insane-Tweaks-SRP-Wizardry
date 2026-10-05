package com.spege.ebreduxaddon.gametest;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.compat.spore.SporeCompat;
import com.spege.ebreduxaddon.compat.spore.SporeGameTestBodies;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static com.spege.ebreduxaddon.gametest.CleanseGameTests.EMPTY;

/**
 * Zarazony mag i Mykomanta. Bez Spore kazdy test sprawdza tylko, ze moba nie ma, i konczy sie sukcesem. Ciala ze
 * Spore sa w compat.spore.SporeGameTestBodies - ta klasa nie moze nazywac typow Spore.
 */
@GameTestHolder(EbreduxAddon.MODID)
@PrefixGameTestTemplate(false)
public final class InfectedWizardGameTests {

    private InfectedWizardGameTests() {
    }

    private static boolean skipWithoutSpore(GameTestHelper helper) {
        if (SporeCompat.present()) {
            return false;
        }
        helper.assertFalse(ForgeRegistries.ENTITY_TYPES.containsKey(EbreduxAddon.id("infected_wizard")),
                "infected_wizard registered without Spore");
        helper.assertFalse(ForgeRegistries.ENTITY_TYPES.containsKey(EbreduxAddon.id("mycomancer")),
                "mycomancer registered without Spore");
        helper.succeed();
        return true;
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void conversionTurnsReduxWizardInfected(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.conversionTurnsReduxWizardInfected(helper);
        }
    }

    @GameTest(template = EMPTY)
    public static void rollGivesElementTierSpellsAndWand(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.rollGivesElementTierSpellsAndWand(helper);
        }
    }

    @GameTest(template = EMPTY)
    public static void hiveDoesNotTargetItAndItTargetsPillagers(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.hiveDoesNotTargetItAndItTargetsPillagers(helper);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void infectedWizardCastsAtTarget(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.infectedWizardCastsAtTarget(helper);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void summonWithoutFinalizeSpawnStillRolls(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.summonWithoutFinalizeSpawnStillRolls(helper);
        }
    }

    @GameTest(template = EMPTY)
    public static void saveAndLoadKeepsTheRoll(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.saveAndLoadKeepsTheRoll(helper);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void evolutionFiresOnSporesTimer(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.evolutionFiresOnSporesTimer(helper);
        }
    }

    @GameTest(template = EMPTY)
    public static void evolutionKeepsLoadoutAndSporeState(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.evolutionKeepsLoadoutAndSporeState(helper);
        }
    }

    @GameTest(template = EMPTY)
    public static void wardShieldsTheHiveOnly(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.wardShieldsTheHiveOnly(helper);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void mycomancerSummonRollsAsMaster(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.mycomancerSummonRollsAsMaster(helper);
        }
    }

    @GameTest(template = EMPTY)
    public static void mycomancerSaveAndLoad(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.mycomancerSaveAndLoad(helper);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void mycomancerCastsAtTarget(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.mycomancerCastsAtTarget(helper);
        }
    }

    @GameTest(template = EMPTY)
    public static void hiveAndMycomancerAreAllies(GameTestHelper helper) {
        if (!skipWithoutSpore(helper)) {
            SporeGameTestBodies.hiveAndMycomancerAreAllies(helper);
        }
    }
}
