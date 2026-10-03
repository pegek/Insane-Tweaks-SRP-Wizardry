package com.spege.ebreduxaddon.gametest;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.ModSpells;
import com.spege.ebreduxaddon.feature.entity.PurifyingWaveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.spege.ebreduxaddon.gametest.CleanseGameTests.EMPTY;
import static com.spege.ebreduxaddon.gametest.CleanseGameTests.casterAt;
import static com.spege.ebreduxaddon.gametest.CleanseGameTests.context;

@GameTestHolder(EbreduxAddon.MODID)
@PrefixGameTestTemplate(false)
public final class PurifyingPulseGameTests {

    private PurifyingPulseGameTests() {
    }

    private static PurifyingWaveEntity wave(GameTestHelper helper, LivingEntity owner, float radius, float heal, float damage, float knockback) {
        PurifyingWaveEntity wave = new PurifyingWaveEntity(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        wave.setPos(at.x, at.y, at.z);
        wave.configure(owner, radius, 4, heal, damage, knockback);
        helper.getLevel().addFreshEntity(wave);
        return wave;
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void pulseCleansMyceliumAndLeavesOtherBlocks(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.MYCELIUM);
        helper.setBlock(0, 1, 0, Blocks.MYCELIUM);
        helper.setBlock(2, 1, 2, Blocks.COARSE_DIRT);
        wave(helper, null, 3, 0, 0, 0);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, 1, 1, 1);
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, 0, 1, 0);
            helper.assertBlockPresent(Blocks.COARSE_DIRT, 2, 1, 2);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void pulseHealsOwnerPushesEnemiesLeavesOthers(GameTestHelper helper) {
        // Wlasciciel musi byc w swiecie (fala szuka go po UUID).
        Villager owner = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 1, 2, 1);
        owner.setHealth(5f);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 0, 2, 1);
        pig.setHealth(3f);
        Pillager pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 2, 2, 1);
        double startX = pillager.getX();
        double pigX = pig.getX();
        wave(helper, owner, 3, 4, 0, 0.8f);
        helper.succeedWhen(() -> {
            helper.assertTrue(owner.getHealth() > 5f, "owner not healed, health " + owner.getHealth());
            helper.assertTrue(pillager.getX() > startX + 0.2, "enemy not pushed away, dx " + (pillager.getX() - startX));
            helper.assertTrue(pillager.getHealth() == pillager.getMaxHealth(), "non-fungal enemy was damaged");
            helper.assertTrue(pig.getHealth() == 3f, "neutral mob was healed or hurt: " + pig.getHealth());
            helper.assertTrue(Math.abs(pig.getX() - pigX) < 0.05, "neutral mob was pushed");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void pulseBurnsFungalMobs(GameTestHelper helper) {
        List<EntityType<?>> tagged = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.getTag(PurifyingWaveEntity.FUNGUS_ENTITIES)
                .ifPresent(tag -> tag.forEach(holder -> tagged.add(holder.value())));
        boolean spore = net.minecraftforge.fml.ModList.get().isLoaded("spore");
        helper.assertTrue(spore == !tagged.isEmpty(), "spore loaded=" + spore + " but tag size=" + tagged.size());
        if (tagged.isEmpty()) {
            // Bez Spore tag jest pusty - to tez jest wynik, ktorego oczekujemy.
            helper.succeed();
            return;
        }
        ResourceLocation preferred = new ResourceLocation("spore", "inf_human");
        EntityType<?> type = tagged.stream()
                .filter(t -> preferred.equals(BuiltInRegistries.ENTITY_TYPE.getKey(t)))
                .findFirst().orElse(tagged.get(0));
        Villager owner = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 1, 2, 1);
        Entity created = type.create(helper.getLevel());
        helper.assertTrue(created instanceof Mob, "tagged type is not a mob: " + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        Mob mob = (Mob) created;
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        mob.moveTo(at.x, at.y, at.z, 0f, 0f);
        mob.setNoAi(true);
        helper.getLevel().addFreshEntity(mob);
        float start = mob.getHealth();
        wave(helper, owner, 3, 0, 8, 0);
        helper.succeedWhen(() -> helper.assertTrue(mob.getHealth() < start || !mob.isAlive(),
                "fungal mob " + BuiltInRegistries.ENTITY_TYPE.getKey(type) + " took no damage"));
    }

    @GameTest(template = EMPTY)
    public static void spellNeedsAPointToStrike(GameTestHelper helper) {
        // Tuz pod limitem budowania: arena GameTestow stoi w zwyklym terenie, wiec "wysoko nad nia"
        // potrafilo wypasc w kamieniu (y = 61). Nad max build height nie ma juz zadnego bloku.
        int skyY = helper.getLevel().getMaxBuildHeight() - 3 - helper.absolutePos(BlockPos.ZERO).getY();
        Player skyward = casterAt(helper, 1.5, skyY, 1.5, 0f, -90f);
        helper.assertFalse(ModSpells.PURIFYING_PULSE.get().cast(context(helper, skyward)), "cast at the sky should fail");
        Player downward = casterAt(helper, 1.5, 2, 1.5, 0f, 90f);
        helper.assertTrue(ModSpells.PURIFYING_PULSE.get().cast(context(helper, downward)), "cast at the floor failed");
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(4);
        int waves = helper.getLevel().getEntitiesOfClass(PurifyingWaveEntity.class, box).size();
        helper.assertTrue(waves >= 1, "no wave spawned");
        helper.succeed();
    }
}
