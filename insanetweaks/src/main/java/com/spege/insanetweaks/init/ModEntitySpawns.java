package com.spege.insanetweaks.init;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.EntitySimBattlemage;
import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Puts sim_wizard and sim_battlemage on the MONSTER spawn lists. Called once from postInit, when
 * every mod's biomes are registered.
 *
 * <p>The list entry is only a pre-filter: whether a picked entry actually spawns is decided by
 * {@code SimWizardNaturalSpawnHandler} (phase, infested ground, exclusion radius). Going through
 * the vanilla spawner rather than a spawner of our own is what lets the Sanctuary veto, SRP's
 * parasite cap, srpwizcore's SpawnEngine and InControl all apply without a line of code here.
 *
 * <p>Group size 1-1: an assimilated mage walks alone.
 */
public final class ModEntitySpawns {

    private ModEntitySpawns() {
    }

    public static void register() {
        EntitiesCategory.NaturalSpawn cfg = ModConfig.entities.assimilatedWizard.naturalSpawn;
        if (!ModConfig.entities.assimilatedWizard.spawning.enabled || !cfg.enabled) {
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sim wizard natural spawn is off.");
            return;
        }

        Set<String> excluded = new HashSet<String>();
        for (String name : cfg.excludedBiomeTypes) {
            if (name != null && !name.trim().isEmpty()) {
                excluded.add(name.trim().toUpperCase(Locale.ROOT));
            }
        }

        // Matched against the types biomes actually carry rather than looked up by name:
        // BiomeDictionary.Type.getType(name) CREATES a type that does not exist yet.
        Set<String> seen = new HashSet<String>();
        List<Biome> allowed = new ArrayList<Biome>();
        for (Biome biome : ForgeRegistries.BIOMES) {
            boolean skip = false;
            for (BiomeDictionary.Type type : BiomeDictionary.getTypes(biome)) {
                String name = type.getName().toUpperCase(Locale.ROOT);
                seen.add(name);
                if (excluded.contains(name)) {
                    skip = true;
                }
            }
            if (!skip) {
                allowed.add(biome);
            }
        }
        for (String name : excluded) {
            if (!seen.contains(name)) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Sim wizard spawn: no biome carries the type '{}'"
                        + " listed in 'Excluded Biome Types' - typo, or a type from a mod that is not installed.",
                        name);
            }
        }

        Biome[] biomes = allowed.toArray(new Biome[0]);
        if (cfg.weight > 0) {
            EntityRegistry.addSpawn(EntitySimWizard.class, cfg.weight, 1, 1, EnumCreatureType.MONSTER, biomes);
        }
        if (cfg.battlemageWeight > 0) {
            EntityRegistry.addSpawn(EntitySimBattlemage.class, cfg.battlemageWeight, 1, 1,
                    EnumCreatureType.MONSTER, biomes);
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sim wizard natural spawn: weight {} / battlemage {} in {} of {}"
                + " biomes.", Integer.valueOf(cfg.weight), Integer.valueOf(cfg.battlemageWeight),
                Integer.valueOf(biomes.length),
                Integer.valueOf(ForgeRegistries.BIOMES.getValuesCollection().size()));
    }
}
