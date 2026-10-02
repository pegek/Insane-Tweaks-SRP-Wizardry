package com.spege.ebreduxaddon.gametest;

import com.binaris.wizardry.api.content.spell.internal.PlayerCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.GraspState;
import com.spege.ebreduxaddon.feature.ModSpells;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.spege.ebreduxaddon.gametest.CleanseGameTests.EMPTY;
import static com.spege.ebreduxaddon.gametest.CleanseGameTests.casterAt;

@GameTestHolder(EbreduxAddon.MODID)
@PrefixGameTestTemplate(false)
public final class GraspGameTests {

    private GraspGameTests() {
    }

    private static PlayerCastContext tick(GameTestHelper helper, Player caster, int castingTicks) {
        return new PlayerCastContext(helper.getLevel(), caster, InteractionHand.MAIN_HAND, castingTicks, new SpellModifiers());
    }

    /** Pillager przed rzucajacym; ticki 1..9 nie zadaja obrazen, wiec test chwytu ich nie zaburza. */
    private static Pillager target(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 2, 2);
    }

    @GameTest(template = EMPTY)
    public static void graspRootsBothAndEndCastReleases(GameTestHelper helper) {
        Pillager pillager = target(helper);
        Player caster = casterAt(helper, 1.5, 2, 0.2, 0f, 0f);
        PlayerCastContext ctx = tick(helper, caster, 1);
        helper.assertTrue(ModSpells.GRASP.get().cast(ctx), "Grasp did not cast");
        helper.assertTrue(GraspState.isRooted(pillager), "target not rooted");
        helper.assertTrue(GraspState.isRooted(caster), "caster not rooted");
        ModSpells.GRASP.get().endCast(ctx);
        helper.assertFalse(GraspState.isRooted(pillager), "target still rooted after endCast");
        helper.assertFalse(GraspState.isRooted(caster), "caster still rooted after endCast");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void holdSurvivesLookingAway(GameTestHelper helper) {
        Pillager pillager = target(helper);
        Player caster = casterAt(helper, 1.5, 2, 0.2, 0f, 0f);
        ModSpells.GRASP.get().cast(tick(helper, caster, 1));
        caster.setYRot(180f);
        caster.setYHeadRot(180f);
        ModSpells.GRASP.get().cast(tick(helper, caster, 2));
        GraspState.Hold hold = GraspState.get(caster);
        helper.assertTrue(hold != null && hold.target() == pillager, "hold lost when the caster looked away");
        ModSpells.GRASP.get().endCast(tick(helper, caster, 3));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void graspDamagesHealthyTarget(GameTestHelper helper) {
        Pillager pillager = target(helper);
        Player caster = casterAt(helper, 1.5, 2, 0.2, 0f, 0f);
        float before = pillager.getHealth();
        ModSpells.GRASP.get().cast(tick(helper, caster, 0));
        helper.assertTrue(pillager.isAlive(), "healthy target was executed");
        helper.assertTrue(pillager.getHealth() < before, "no damage on tick 0");
        // Straznik na blad z podloga areny: obrazenia maja byc z zaklecia, nie z duszenia w bloku.
        helper.assertTrue(pillager.getLastDamageSource() != null
                && !"inWall".equals(pillager.getLastDamageSource().getMsgId()), "damage came from suffocation, not Grasp");
        ModSpells.GRASP.get().endCast(tick(helper, caster, 1));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void graspExecutesWeakenedMob(GameTestHelper helper) {
        Pillager pillager = target(helper);
        pillager.setHealth(pillager.getMaxHealth() * 0.15f);
        Player caster = casterAt(helper, 1.5, 2, 0.2, 0f, 0f);
        ModSpells.GRASP.get().cast(tick(helper, caster, 0));
        helper.assertTrue(pillager.isDeadOrDying(), "weakened target survived, health " + pillager.getHealth());
        helper.assertTrue(GraspState.get(caster) == null, "hold kept after the execute");
        helper.assertFalse(GraspState.isRooted(caster), "caster left rooted after the execute");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void abandonedHoldIsSwept(GameTestHelper helper) {
        Pillager pillager = target(helper);
        Player caster = casterAt(helper, 1.5, 2, 0.2, 0f, 0f);
        ModSpells.GRASP.get().cast(tick(helper, caster, 1));
        helper.assertTrue(GraspState.isRooted(pillager), "target not rooted");
        // Brak endCast i kolejnych tickow zaklecia: sweeper ma zwolnic chwyt sam.
        helper.succeedWhen(() -> {
            helper.assertFalse(GraspState.isRooted(pillager), "abandoned hold still roots the target");
            helper.assertTrue(GraspState.get(caster) == null, "abandoned hold still in the state map");
        });
    }
}
