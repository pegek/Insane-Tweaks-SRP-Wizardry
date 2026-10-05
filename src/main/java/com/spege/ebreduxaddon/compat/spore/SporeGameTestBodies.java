package com.spege.ebreduxaddon.compat.spore;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.EvolvedInfected.Scamper;
import com.binaris.wizardry.setup.registries.Elements;
import com.spege.ebreduxaddon.core.InfectedTiers;
import com.spege.ebreduxaddon.feature.ModSpells;
import com.spege.ebreduxaddon.platform.Config;
import com.spege.ebreduxaddon.feature.entity.SpineEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Ciala GameTestow zaleznych od Spore. Wolane z gametest.InfectedWizardGameTests dopiero po
 * SporeCompat.present() - sama klasa-holder nie moze nazywac typow Spore (weryfikator JVM
 * zaladowalby Infected i bez Spore test by padl z NoClassDefFoundError).
 */
public final class SporeGameTestBodies {

    private SporeGameTestBodies() {
    }

    private static InfectedWizardEntity spawnInfected(GameTestHelper helper, double x, double y, double z, boolean ai) {
        InfectedWizardEntity wizard = SporeContent.INFECTED_WIZARD.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        wizard.moveTo(at.x, at.y, at.z, 0f, 0f);
        wizard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(BlockPos.containing(at)),
                MobSpawnType.CONVERSION, null, null);
        wizard.setNoAi(!ai);
        helper.getLevel().addFreshEntity(wizard);
        return wizard;
    }

    public static void conversionTurnsReduxWizardInfected(GameTestHelper helper) {
        EntityType<?> wizardType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("ebwizardry", "wizard"));
        MobEffect mycelium = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("spore", "mycelium_ef"));
        helper.assertTrue(wizardType != null && mycelium != null, "Redux wizard or spore:mycelium_ef missing");
        Entity created = wizardType.create(helper.getLevel());
        helper.assertTrue(created instanceof Mob, "ebwizardry:wizard is not a mob");
        Mob wizard = (Mob) created;
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        wizard.moveTo(at.x, at.y, at.z, 0f, 0f);
        wizard.setNoAi(true);
        helper.getLevel().addFreshEntity(wizard);
        wizard.addEffect(new MobEffectInstance(mycelium, 600, 0));
        wizard.kill();
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6);
        helper.succeedWhen(() -> {
            List<InfectedWizardEntity> infected = helper.getLevel().getEntitiesOfClass(InfectedWizardEntity.class, box);
            helper.assertTrue(infected.size() == 1, "expected one infected wizard, got " + infected.size());
            helper.assertTrue(!infected.get(0).getSpells().isEmpty(), "converted wizard has no spells");
        });
    }

    public static void rollGivesElementTierSpellsAndWand(GameTestHelper helper) {
        InfectedWizardEntity wizard = spawnInfected(helper, 1.5, 2, 1.5, false);
        helper.assertTrue(wizard.getElement() != Elements.MAGIC && wizard.getElement() != Elements.HEALING,
                "forbidden element " + wizard.getElement().getLocation());
        helper.assertTrue(wizard.getSpells().contains(ModSpells.SPINE_VOLLEY.get()), "no Spine Volley");
        helper.assertTrue(wizard.getSpells().size() >= 2, "too few spells: " + wizard.getSpells());
        helper.assertTrue(wizard.getSpells().stream().allMatch(s -> s.canCastByEntity()), "a spell mobs cannot cast");
        helper.assertTrue(!wizard.getMainHandItem().isEmpty(), "no wand in hand");
        helper.succeed();
    }

    public static void hiveDoesNotTargetItAndItTargetsPillagers(GameTestHelper helper) {
        InfectedWizardEntity wizard = spawnInfected(helper, 1.5, 2, 1.5, false);
        EntityType<?> humanType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("spore", "inf_human"));
        Entity human = humanType.create(helper.getLevel());
        helper.assertTrue(human instanceof Infected, "spore:inf_human is not Infected");
        Pillager pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 0, 2, 0);
        // Predykat celow samego Spore, publiczne pole Infected.TARGET_SELECTOR.
        helper.assertFalse(((Infected) human).TARGET_SELECTOR.test(wizard), "Spore's infected would attack the infected wizard");
        helper.assertTrue(wizard.TARGET_SELECTOR.test(pillager), "infected wizard would not attack a pillager");
        helper.assertFalse(wizard.TARGET_SELECTOR.test((Infected) human), "infected wizard would attack the hive");
        helper.succeed();
    }

    /** Jak mycomancerCastsAtTarget: sam Spine Volley, bo losowy zestaw bywa bez zaklecia raniacego. */
    public static void infectedWizardCastsAtTarget(GameTestHelper helper) {
        Pillager pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 2, 9);
        InfectedWizardEntity wizard = spawnInfected(helper, 1.5, 2, 1.5, true);
        wizard.loadout().spells().clear();
        wizard.loadout().spells().add(ModSpells.SPINE_VOLLEY.get());
        wizard.setTarget(pillager);
        float start = pillager.getHealth();
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(30);
        helper.succeedWhen(() -> {
            wizard.setTarget(pillager);
            boolean spines = !helper.getLevel().getEntitiesOfClass(SpineEntity.class, box, s -> s.getOwner() == wizard).isEmpty();
            boolean hurt = pillager.getHealth() < start && pillager.getLastDamageSource() != null
                    && !"mob".equals(pillager.getLastDamageSource().getMsgId());
            helper.assertTrue(spines || hurt, "no spell reached the target yet, spells " + wizard.getSpells());
        });
    }

    /** /summon z NBT i spawner z danymi pomijaja finalizeSpawn - mag ma sie dolosowac sam. */
    public static void summonWithoutFinalizeSpawnStillRolls(GameTestHelper helper) {
        InfectedWizardEntity wizard = SporeContent.INFECTED_WIZARD.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        wizard.moveTo(at.x, at.y, at.z, 0f, 0f);
        wizard.setNoAi(true);
        helper.getLevel().addFreshEntity(wizard);
        helper.assertFalse(wizard.isRolled(), "rolled before the first tick");
        helper.succeedWhen(() -> {
            helper.assertTrue(wizard.isRolled(), "never rolled without finalizeSpawn");
            helper.assertTrue(!wizard.getSpells().isEmpty(), "no spells");
            helper.assertTrue(!wizard.getMainHandItem().isEmpty(), "no wand");
        });
    }

    public static void saveAndLoadKeepsTheRoll(GameTestHelper helper) {
        InfectedWizardEntity wizard = spawnInfected(helper, 1.5, 2, 1.5, false);
        CompoundTag tag = new CompoundTag();
        wizard.saveWithoutId(tag);
        InfectedWizardEntity copy = SporeContent.INFECTED_WIZARD.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.getElement() == wizard.getElement(), "element lost");
        helper.assertTrue(copy.getMaxTier() == wizard.getMaxTier(), "tier lost");
        helper.assertTrue(copy.getSpells().equals(wizard.getSpells()), "spells lost: " + copy.getSpells());
        helper.assertTrue(copy.getTextureIndex() == wizard.getTextureIndex(), "texture lost");
        helper.assertTrue(copy.isRolled(), "rolled flag lost - the copy would re-roll its spells");
        copy.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ 0.3: ewolucja i Mykomanta

    private static MycomancerEntity spawnMycomancer(GameTestHelper helper, double x, double y, double z, boolean ai) {
        MycomancerEntity mycomancer = SporeContent.MYCOMANCER.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        mycomancer.moveTo(at.x, at.y, at.z, 0f, 0f);
        mycomancer.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(BlockPos.containing(at)),
                MobSpawnType.MOB_SUMMONED, null, null);
        mycomancer.setNoAi(!ai);
        helper.getLevel().addFreshEntity(mycomancer);
        return mycomancer;
    }

    /**
     * Mag z punktami i licznikiem na progu Spore ewoluuje przy najblizszym pelnym 20. ticku. Wynik to
     * Mykomanta albo (w 10% domyslnego configu) Scamper - test przyjmuje oba, bo losuje Spore'owa regula.
     */
    public static void evolutionFiresOnSporesTimer(GameTestHelper helper) {
        InfectedWizardEntity wizard = spawnInfected(helper, 1.5, 2, 1.5, false);
        wizard.setEvoPoints(SConfig.SERVER.min_kills.get());
        wizard.setEvolution(SConfig.SERVER.evolution_age_human.get());
        // Ciasne pudelko wokol maga: obok biegna inne testy z Mykomantami.
        AABB box = wizard.getBoundingBox().inflate(1.0);
        helper.succeedWhen(() -> {
            helper.assertTrue(wizard.isRemoved(), "wizard did not evolve");
            boolean mycomancer = !helper.getLevel().getEntitiesOfClass(MycomancerEntity.class, box).isEmpty();
            boolean scamper = !helper.getLevel().getEntitiesOfClass(Scamper.class, box).isEmpty();
            helper.assertTrue(mycomancer || scamper, "nothing took the wizard's place");
        });
    }

    public static void evolutionKeepsLoadoutAndSporeState(GameTestHelper helper) {
        InfectedWizardEntity wizard = spawnInfected(helper, 1.5, 2, 1.5, false);
        wizard.setKills(3);
        wizard.setEvoPoints(2);
        wizard.setCustomName(Component.literal("Merlin"));
        List<?> before = List.copyOf(wizard.getSpells());
        MycomancerEntity next = SporeContent.MYCOMANCER.get().create(helper.getLevel());
        wizard.evolveInto(helper.getLevel(), next, true);

        helper.assertTrue(wizard.isRemoved(), "wizard still there");
        helper.assertTrue(next.isAddedToWorld(), "mycomancer not added");
        helper.assertTrue(next.getElement() == wizard.getElement(), "element changed");
        helper.assertTrue(next.getMaxTier() == InfectedTiers.MASTER, "not master: " + next.getMaxTier());
        helper.assertTrue(next.getSpells().containsAll(before), "lost the wizard's spells: " + next.getSpells());
        helper.assertTrue(next.getSpells().size() >= before.size(), "fewer spells than the wizard");
        helper.assertTrue(next.getKills() == 3 && next.getEvoPoints() == 2, "Spore kills/evo points not carried");
        helper.assertTrue(next.getCustomName() != null && "Merlin".equals(next.getCustomName().getString()), "name lost");
        helper.assertTrue(!next.getMainHandItem().isEmpty(), "no wand");
        helper.succeed();
    }

    public static void wardShieldsTheHiveOnly(GameTestHelper helper) {
        MycomancerEntity mycomancer = spawnMycomancer(helper, 1.5, 2, 1.5, false);
        MycomancerEntity other = spawnMycomancer(helper, 3.5, 2, 1.5, false);
        EntityType<?> humanType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("spore", "inf_human"));
        Infected human = (Infected) humanType.create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        human.moveTo(at.x, at.y, at.z, 0f, 0f);
        human.setNoAi(true);
        helper.getLevel().addFreshEntity(human);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 3);

        mycomancer.ward(helper.getLevel());

        helper.assertTrue(human.hasEffect(MobEffects.DAMAGE_RESISTANCE), "infected neighbour not shielded");
        helper.assertFalse(cow.hasEffect(MobEffects.DAMAGE_RESISTANCE), "cow shielded");
        helper.assertFalse(other.hasEffect(MobEffects.DAMAGE_RESISTANCE), "mycomancers shield each other");
        helper.assertFalse(mycomancer.hasEffect(MobEffects.DAMAGE_RESISTANCE), "shielded itself");
        helper.succeed();
    }

    public static void mycomancerSummonRollsAsMaster(GameTestHelper helper) {
        MycomancerEntity mycomancer = SporeContent.MYCOMANCER.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        mycomancer.moveTo(at.x, at.y, at.z, 0f, 0f);
        mycomancer.setNoAi(true);
        helper.getLevel().addFreshEntity(mycomancer);
        helper.succeedWhen(() -> {
            helper.assertTrue(mycomancer.isRolled(), "never rolled without finalizeSpawn");
            helper.assertTrue(mycomancer.getMaxTier() == InfectedTiers.MASTER, "not master");
            int own = (int) mycomancer.getSpells().stream().filter(sp -> sp != ModSpells.SPINE_VOLLEY.get()).count();
            helper.assertTrue(own >= 1 && own <= Config.INSTANCE.infectedSpellCount.get() + 1, "spell count " + own);
            helper.assertTrue(!mycomancer.getMainHandItem().isEmpty(), "no wand");
        });
    }

    public static void mycomancerSaveAndLoad(GameTestHelper helper) {
        MycomancerEntity mycomancer = spawnMycomancer(helper, 1.5, 2, 1.5, false);
        CompoundTag tag = new CompoundTag();
        mycomancer.saveWithoutId(tag);
        MycomancerEntity copy = SporeContent.MYCOMANCER.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.getElement() == mycomancer.getElement(), "element lost");
        helper.assertTrue(copy.getSpells().equals(mycomancer.getSpells()), "spells lost");
        helper.assertTrue(copy.getTextureIndex() == mycomancer.getTextureIndex(), "texture lost");
        helper.assertTrue(copy.isRolled(), "rolled flag lost");
        copy.discard();
        helper.succeed();
    }

    /**
     * Czy AttackSpellGoal Redux dziala na EvolvedInfected. Zestaw ustawiony na sam Spine Volley: losowy
     * zestaw mistrza bywa samymi buffami i przywolaniami (obrazenia od przywolanca to "mob"), co dawalo
     * czerwony test bez bledu w kodzie.
     */
    public static void mycomancerCastsAtTarget(GameTestHelper helper) {
        Pillager pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 2, 9);
        MycomancerEntity mycomancer = spawnMycomancer(helper, 1.5, 2, 1.5, true);
        mycomancer.loadout().spells().clear();
        mycomancer.loadout().spells().add(ModSpells.SPINE_VOLLEY.get());
        mycomancer.setTarget(pillager);
        float start = pillager.getHealth();
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(30);
        helper.succeedWhen(() -> {
            mycomancer.setTarget(pillager);
            boolean spines = !helper.getLevel().getEntitiesOfClass(SpineEntity.class, box, sp -> sp.getOwner() == mycomancer).isEmpty();
            boolean hurt = pillager.getHealth() < start && pillager.getLastDamageSource() != null
                    && !"mob".equals(pillager.getLastDamageSource().getMsgId());
            helper.assertTrue(spines || hurt, "no spell reached the target yet, spells " + mycomancer.getSpells());
        });
    }

    public static void hiveAndMycomancerAreAllies(GameTestHelper helper) {
        MycomancerEntity mycomancer = spawnMycomancer(helper, 1.5, 2, 1.5, false);
        EntityType<?> humanType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("spore", "inf_human"));
        Infected human = (Infected) humanType.create(helper.getLevel());
        Pillager pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 0, 2, 0);
        helper.assertFalse(human.TARGET_SELECTOR.test(mycomancer), "Spore's infected would attack the mycomancer");
        helper.assertFalse(mycomancer.TARGET_SELECTOR.test(human), "mycomancer would attack the hive");
        helper.assertTrue(mycomancer.TARGET_SELECTOR.test(pillager), "mycomancer would not attack a pillager");
        human.discard();
        helper.succeed();
    }
}
