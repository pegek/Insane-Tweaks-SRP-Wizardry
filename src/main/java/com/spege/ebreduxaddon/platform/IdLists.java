package com.spege.ebreduxaddon.platform;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.core.PairList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Listy identyfikatorow z configu rozwiazane w rejestrach Forge, z cache.
 *
 * <p>Zasada logowania: id moda, ktorego nie ma (np. spore:* bez Spore), pomijamy po cichu, bo to
 * normalny stan. Id z zaladowanego moda, ktorego rejestr nie zna, to literowka w configu albo
 * zmiana po stronie tego moda: jedno ostrzezenie zbiorcze na liste, przy kazdym przeliczeniu.
 *
 * <p>Cache jest kluczowany samym obiektem listy z configu. Forge podmienia go przy przeladowaniu
 * pliku, wiec zmiana configu w trakcie gry przelicza liste sama.
 */
public final class IdLists {

    private static volatile CachedEffects effects = new CachedEffects(null, Collections.emptySet());
    private static volatile CachedBlocks blocks = new CachedBlocks(null, Collections.emptyMap());

    private IdLists() {
    }

    private record CachedEffects(List<? extends String> source, Set<MobEffect> resolved) {
    }

    private record CachedBlocks(List<? extends String> source, Map<Block, Block> resolved) {
    }

    /** Efekty zdejmowane przez Cleansing ponad wszystkie HARMFUL. Puste, gdy most do Spore wylaczony. */
    public static Set<MobEffect> cleansedEffects() {
        if (!Config.INSTANCE.sporeEnabled.get()) {
            return Collections.emptySet();
        }
        List<? extends String> source = Config.INSTANCE.sporeCleansedEffects.get();
        CachedEffects cached = effects;
        if (cached.source() != source) {
            cached = new CachedEffects(source, resolveEffects(source));
            effects = cached;
        }
        return cached.resolved();
    }

    /** Blok -&gt; czysty odpowiednik dla Purifying Pulse. Puste, gdy most do Spore wylaczony. */
    public static Map<Block, Block> purifiedBlocks() {
        if (!Config.INSTANCE.sporeEnabled.get()) {
            return Collections.emptyMap();
        }
        List<? extends String> source = Config.INSTANCE.sporePurifiedBlocks.get();
        CachedBlocks cached = blocks;
        if (cached.source() != source) {
            cached = new CachedBlocks(source, resolveBlocks(source));
            blocks = cached;
        }
        return cached.resolved();
    }

    /** Przelicza wszystko od razu - wolane po starcie serwera, zeby ostrzezenia byly w logu startu. */
    public static void warmUp() {
        cleansedEffects();
        purifiedBlocks();
    }

    private static Set<MobEffect> resolveEffects(List<? extends String> ids) {
        Set<MobEffect> out = new HashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : ids) {
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (id == null) {
                unknown.add(raw);
                continue;
            }
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect != null) {
                out.add(effect);
            } else if (modPresent(id)) {
                unknown.add(raw);
            }
        }
        warn("spore.cleansedEffects", "unknown effect ids", unknown);
        return Collections.unmodifiableSet(out);
    }

    private static Map<Block, Block> resolveBlocks(List<? extends String> entries) {
        PairList parsed = PairList.parse(entries);
        Map<Block, Block> out = new IdentityHashMap<>();
        List<String> unknown = new ArrayList<>(parsed.rejected());
        for (Map.Entry<String, String> pair : parsed.pairs().entrySet()) {
            ResourceLocation from = new ResourceLocation(pair.getKey());
            ResourceLocation to = new ResourceLocation(pair.getValue());
            Block source = block(from);
            Block target = block(to);
            if (source != null && target != null) {
                out.put(source, target);
            } else if ((source == null && modPresent(from)) || (target == null && modPresent(to))) {
                unknown.add(pair.getKey() + "|" + pair.getValue());
            }
        }
        warn("spore.purifiedBlocks", "unreadable or unknown entries", unknown);
        return Collections.unmodifiableMap(out);
    }

    /** Rejestr blokow zwraca powietrze dla nieznanego id, wiec powietrze jako cel sprawdzamy jawnie. */
    private static Block block(ResourceLocation id) {
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || (block == Blocks.AIR && !id.equals(ForgeRegistries.BLOCKS.getKey(Blocks.AIR)))) {
            return null;
        }
        return block;
    }

    private static boolean modPresent(ResourceLocation id) {
        String ns = id.getNamespace();
        return "minecraft".equals(ns) || ModList.get().isLoaded(ns);
    }

    private static void warn(String key, String what, List<String> entries) {
        if (!entries.isEmpty()) {
            EbreduxAddon.LOGGER.warn("[EbreduxAddon] config {}: skipping {} {}", key, what, entries);
        }
    }
}
