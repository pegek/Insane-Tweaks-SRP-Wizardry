package com.spege.ebreduxaddon.gametest;

import com.binaris.wizardry.api.content.spell.internal.PlayerCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.ModEffects;
import com.spege.ebreduxaddon.feature.ModSpells;
import com.spege.ebreduxaddon.platform.IdLists;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Testy w swiecie, bez klienta. Wlaczane flaga -Dforge.enableGameTest=true, uruchamiane /test runall
 * (tools/server-check.sh robi oba). Bez flagi Forge tej klasy w ogole nie laduje.
 *
 * <p>Struktura to pusta 3x3x3 skopiowana z Redux (GPL-3.0, jak addon).
 */
@GameTestHolder(EbreduxAddon.MODID)
@PrefixGameTestTemplate(false)
public final class CleanseGameTests {

    /** data/ebreduxaddon/structures/empty_3x3x3.nbt. Forge zawsze dokleja namespace moda, wiec
     *  cudzej struktury (ebwizardry:...) uzyc sie nie da. */
    static final String EMPTY = "empty_3x3x3";

    private CleanseGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void cleansingStripsHarmfulAndKeepsBeneficial(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400));
        zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400));
        zombie.addEffect(new MobEffectInstance(ModEffects.CLEANSING.get(), 100));
        helper.succeedWhen(() -> {
            helper.assertFalse(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowness survived Cleansing");
            helper.assertTrue(zombie.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Cleansing removed a beneficial effect");
        });
    }

    @GameTest(template = EMPTY)
    public static void cleanseHitsTargetInFront(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 2);
        Player caster = casterAt(helper, 1.5, 1, 0.2, 0f, 0f);
        boolean cast = ModSpells.CLEANSE.get().cast(context(helper, caster));
        helper.assertTrue(cast, "Cleanse did not cast");
        helper.assertTrue(zombie.hasEffect(ModEffects.CLEANSING.get()), "target in front got no Cleansing");
        helper.assertFalse(caster.hasEffect(ModEffects.CLEANSING.get()), "caster cleansed although the ray hit a target");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cleanseFallsBackToCasterOnMiss(GameTestHelper helper) {
        // Patrzy w niebo: nic w zasiegu, wiec oczyszcza siebie.
        Player caster = casterAt(helper, 1.5, 1, 1.5, 0f, -90f);
        boolean cast = ModSpells.CLEANSE.get().cast(context(helper, caster));
        helper.assertTrue(cast, "Cleanse did not cast on a miss");
        MobEffectInstance effect = caster.getEffect(ModEffects.CLEANSING.get());
        helper.assertTrue(effect != null, "caster got no Cleansing on a miss");
        helper.assertTrue(effect.getDuration() == 200, "duration should come from cleanse.json (200), was " + effect.getDuration());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sporeEffectIdsResolveOnlyWithSpore(GameTestHelper helper) {
        int resolved = IdLists.cleansedEffects().size();
        if (ModList.get().isLoaded("spore")) {
            helper.assertTrue(resolved == 8, "with Spore all 8 default effect ids should resolve, got " + resolved);
        } else {
            helper.assertTrue(resolved == 0, "without Spore no effect id should resolve, got " + resolved);
        }
        helper.succeed();
    }

    static Player casterAt(GameTestHelper helper, double x, double y, double z, float yaw, float pitch) {
        Player player = helper.makeMockPlayer();
        Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
        player.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        player.setYHeadRot(yaw);
        return player;
    }

    static PlayerCastContext context(GameTestHelper helper, Player caster) {
        return new PlayerCastContext(helper.getLevel(), caster, InteractionHand.MAIN_HAND, 0, new SpellModifiers());
    }
}
