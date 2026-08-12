package com.spege.commandsuggest.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import com.spege.commandsuggest.core.ArgType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Wartosci, ktore klient zna sam - bez ani jednego pakietu.
 *
 * <p>Listy z rejestrow sa cache'owane po pierwszym uzyciu: rejestry sa zamrozone w trakcie jednej
 * sesji na serwerze, a przeliczanie kilkunastu tysiecy {@code ResourceLocation.toString()} przy
 * kazdym wcisnietym klawiszu byloby marnotrawstwem.
 *
 * <p>🚨 "Zamrozone" znaczy "w trakcie jednej sesji" — NIE "na zawsze w tej instancji gry".
 * Forge przemapowuje rejestry przy kazdym {@code ClientConnectedToServerEvent} (inny modset na
 * kolejnym serwerze = inne itemy/bloki/encje pod tymi samymi ID), wiec {@link #invalidate()} musi
 * byc wolane z tego samego miejsca, ktore juz nasluchuje connect/disconnect —
 * {@code ClientTreeCache.Events}. Bez tego gracz, ktory przeszedl z serwera A na serwer B, dostaje
 * podpowiedzi itemow z A (ktorych B moze nie miec) i nie dostaje niczego unikalnego dla B, dopoki
 * nie zrestartuje gry — cichy, dlugozyjacy bug, bo nic nie rzuca wyjatku, popup po prostu klamie.
 * Wywolanie jest bezpieczne niezaleznie od tego, czy remap rejestrow zdarzy sie przed czy po
 * evencie connect: {@link #resolve} jest wolane wylacznie z {@code ChatScreenHandler}, co wymaga
 * otwartego {@code GuiChat}, co wymaga bycia juz w pelni w swiecie — a remap zawsze konczy sie
 * przed tym momentem (jest czescia procedury logowania, nie postepuje rownolegle z gra).
 */
@SideOnly(Side.CLIENT)
public final class LocalValueSource {

    /** Sufit na jedno zapytanie. Popup i tak scrolluje, a lista itemow ma pieciocyfrowy rozmiar. */
    private static final int LIMIT = 200;

    private static List<String> items;
    private static List<String> blocks;
    private static List<String> entities;

    private LocalValueSource() {
    }

    /** Kasuje cache rejestrow. Wolane z {@code ClientTreeCache.Events} przy connect i disconnect. */
    public static void invalidate() {
        items = null;
        blocks = null;
        entities = null;
    }

    public static List<String> resolve(ArgType type, String prefix) {
        if (type == null) {
            return Collections.emptyList();
        }
        switch (type) {
            case PLAYER:
                return filter(players(), prefix);
            case ITEM:
                if (items == null) {
                    items = names(ForgeRegistries.ITEMS.getKeys());
                }
                return filter(items, prefix);
            case BLOCK:
                if (blocks == null) {
                    blocks = names(ForgeRegistries.BLOCKS.getKeys());
                }
                return filter(blocks, prefix);
            case ENTITY:
                if (entities == null) {
                    entities = names(ForgeRegistries.ENTITIES.getKeys());
                }
                return filter(entities, prefix);
            case DIMENSION:
                return filter(dimensions(), prefix);
            case BLOCKPOS:
                return lookedAtBlock();
            default:
                return Collections.emptyList();
        }
    }

    private static List<String> players() {
        NetHandlerPlayClient conn = Minecraft.getMinecraft().getConnection();
        if (conn == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (NetworkPlayerInfo info : conn.getPlayerInfoMap()) {
            out.add(info.getGameProfile().getName());
        }
        Collections.sort(out);
        return out;
    }

    private static List<String> dimensions() {
        List<String> out = new ArrayList<String>();
        for (Integer id : DimensionManager.getIDs()) {
            out.add(String.valueOf(id));
        }
        return out;
    }

    private static List<String> lookedAtBlock() {
        RayTraceResult hit = Minecraft.getMinecraft().objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
                hit.getBlockPos().getX() + " " + hit.getBlockPos().getY() + " " + hit.getBlockPos().getZ());
    }

    private static List<String> names(Iterable<ResourceLocation> keys) {
        TreeSet<String> sorted = new TreeSet<String>();
        for (ResourceLocation rl : keys) {
            sorted.add(rl.toString());
        }
        return Collections.unmodifiableList(new ArrayList<String>(sorted));
    }

    /**
     * Dopasowanie jak w wanilii: prefiks bez dwukropka trafia takze w sama sciezke, wiec
     * {@code stone} znajduje {@code minecraft:stone}, a {@code minecraft:st} tez dziala.
     */
    private static List<String> filter(List<String> source, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        boolean pathToo = p.indexOf(':') < 0;
        List<String> out = new ArrayList<String>();
        for (String s : source) {
            String low = s.toLowerCase(Locale.ROOT);
            boolean hit = low.startsWith(p);
            if (!hit && pathToo) {
                int colon = low.indexOf(':');
                hit = colon >= 0 && low.startsWith(p, colon + 1);
            }
            if (hit) {
                out.add(s);
                if (out.size() >= LIMIT) {
                    break;
                }
            }
        }
        return out;
    }
}
