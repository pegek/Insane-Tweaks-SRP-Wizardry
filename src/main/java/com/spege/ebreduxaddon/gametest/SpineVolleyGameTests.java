package com.spege.ebreduxaddon.gametest;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.ModEntities;
import com.spege.ebreduxaddon.feature.ModSpells;
import com.spege.ebreduxaddon.feature.entity.SpineEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.spege.ebreduxaddon.gametest.CleanseGameTests.EMPTY;
import static com.spege.ebreduxaddon.gametest.CleanseGameTests.casterAt;
import static com.spege.ebreduxaddon.gametest.CleanseGameTests.context;

@GameTestHolder(EbreduxAddon.MODID)
@PrefixGameTestTemplate(false)
public final class SpineVolleyGameTests {

    private SpineVolleyGameTests() {
    }

    /** Tylko kolce tego rzucajacego: testy z jednej partii stoja obok siebie i strzelaja naraz. */
    private static List<SpineEntity> spines(GameTestHelper helper, Player caster) {
        AABB box = new AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(40);
        return helper.getLevel().getEntitiesOfClass(SpineEntity.class, box, s -> s.getOwner() == caster);
    }

    @GameTest(template = EMPTY)
    public static void volleyFiresAFanOfFive(GameTestHelper helper) {
        Player caster = casterAt(helper, 1.5, 2, 1.5, 0f, 0f);
        helper.assertTrue(ModSpells.SPINE_VOLLEY.get().cast(context(helper, caster)), "Spine Volley did not cast");
        List<SpineEntity> spines = spines(helper, caster);
        helper.assertTrue(spines.size() == 5, "expected 5 spines, got " + spines.size());
        long distinctHeadings = spines.stream().map(s -> Math.round(s.getDeltaMovement().x * 100)).distinct().count();
        helper.assertTrue(distinctHeadings == 5, "spines are not fanned out, distinct x headings: " + distinctHeadings);
        spines.forEach(s -> s.discard());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyFourthVolleyCarriesOnePuddle(GameTestHelper helper) {
        Player caster = casterAt(helper, 1.5, 2, 1.5, 0f, 0f);
        StringBuilder pattern = new StringBuilder();
        for (int cast = 0; cast < 8; cast++) {
            ModSpells.SPINE_VOLLEY.get().cast(context(helper, caster));
            List<SpineEntity> spines = spines(helper, caster);
            long puddles = spines.stream().filter(SpineEntity::isPuddle).count();
            helper.assertTrue(puddles <= 1, "more than one puddle spine in a volley: " + puddles);
            pattern.append(puddles == 1 ? 'X' : '.');
            spines.forEach(s -> s.discard());
        }
        helper.assertTrue(pattern.toString().equals("...X...X"), "puddle pattern was " + pattern);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void spinePoisonsWhatItHits(GameTestHelper helper) {
        // Pillager. Wczesniejsza wersja z wiesniakiem byla niestabilna, ale nie przez Redux: wiesniak
        // jest trafiany i zatruwany (sprawdzone osobno). Testy partii stoja obok siebie i sie zaburzaja.
        Pillager target = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 2, 2);
        SpineEntity spine = new SpineEntity(helper.getLevel());
        Player caster = casterAt(helper, 1.5, 2, 0.2, 0f, 0f);
        spine.setOwner(caster);
        // Strzal z bliska, poziomo, w srodek celu.
        spine.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 3.0, 0.6)));
        spine.shoot(0, 0, 1, 1.5f, 0f);
        helper.getLevel().addFreshEntity(spine);
        helper.succeedWhen(() -> helper.assertTrue(target.hasEffect(MobEffects.POISON), "target not poisoned yet"));
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void puddleSpineLeavesAPoisonCloud(GameTestHelper helper) {
        SpineEntity spine = new SpineEntity(helper.getLevel());
        spine.setPuddle(true);
        spine.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 3.5, 1.5)));
        spine.shoot(0, -1, 0, 1.0f, 0f);
        helper.getLevel().addFreshEntity(spine);
        AABB box = new AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(6);
        helper.succeedWhen(() -> {
            List<AreaEffectCloud> clouds = helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, box);
            helper.assertTrue(clouds.size() == 1, "expected exactly one poison cloud, got " + clouds.size());
        });
    }

    @GameTest(template = EMPTY)
    public static void spineTypeIsRegistered(GameTestHelper helper) {
        helper.assertTrue(ModEntities.SPINE.get().create(helper.getLevel()) instanceof SpineEntity, "spine entity type broken");
        helper.succeed();
    }
}
