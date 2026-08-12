package com.spege.commandsuggest.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.core.ArgType;
import com.spege.commandsuggest.core.CmdArg;
import com.spege.commandsuggest.core.CmdNode;
import com.spege.commandsuggest.core.CommandIndex;
import com.spege.commandsuggest.core.CommandTree;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

/**
 * Rejestr {@code ICommand} plus opisy JSON daja {@link CommandIndex} dla jednego gracza.
 *
 * <p>Opis z {@link DescriptorLoader} daje pelne typy; komenda bez opisu dostaje wezel generyczny
 * {@code literal + greedy} z {@code getUsage()} jako linia usage, a wartosci bierze potem klient
 * waniliowym {@code CPacketTabComplete}. Dzieki temu 268 modow dziala od pierwszego dnia, tylko
 * bez kolorowania typow.
 *
 * <p>🚨 {@code ICommandManager.getCommands()} zawiera KAZDY alias jako osobny klucz wskazujacy na
 * ten sam obiekt. Grupujemy wiec po tozsamosci obiektu ({@link IdentityHashMap}), nazwa kanoniczna
 * jest z {@code getName()}, a reszta kluczy to aliasy.
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    public static CommandIndex build(MinecraftServer server, ICommandSender sender) {
        Map<String, ICommand> registry = server.getCommandManager().getCommands();

        // obiekt komendy -> wszystkie klucze, pod ktorymi siedzi
        Map<ICommand, List<String>> keys = new IdentityHashMap<ICommand, List<String>>();
        for (Map.Entry<String, ICommand> e : registry.entrySet()) {
            List<String> list = keys.get(e.getValue());
            if (list == null) {
                list = new ArrayList<String>(2);
                keys.put(e.getValue(), list);
            }
            list.add(e.getKey());
        }

        Map<String, CommandTree> descriptors = DescriptorLoader.get();
        Map<String, ICommand> canonical = new HashMap<String, ICommand>();
        List<CommandTree> trees = new ArrayList<CommandTree>(keys.size());

        for (Map.Entry<ICommand, List<String>> e : keys.entrySet()) {
            ICommand cmd = e.getKey();
            String name = cmd.getName();
            // 🚨 CommandTree rzuca IllegalArgumentException na nazwie null (guard z Task 4, zeby
            // null nie dolecial do TreeSet w allNames i nie wybuchl w petli rysujacej). W paczce
            // z 268 modami jeden mod z zepsutym getName() nie ma prawa zabrac podpowiedzi calej
            // reszcie, wiec taka komende pomijamy z ostrzezeniem zamiast przerywac budowe.
            if (name == null) {
                CommandSuggest.LOGGER.warn("Komenda {} zwrocila null z getName() - pomijam.",
                        cmd.getClass().getName());
                continue;
            }
            // 🚨 Dwie ROZNE komendy (rozny obiekt, wiec dwa osobne klucze w tym IdentityHashMap)
            // moga zglosic ten sam getName() - w zywym rejestrze druga rejestracja pod tym samym
            // stringiem po prostu podmienia wpis mapy pod tamtym kluczem, wiec pierwsza komenda
            // zostaje osiagalna juz tylko przez swoj alias (jesli jakis ma). CommandIndex.prune
            // dostaje predykat Predicate<String>, wiec i tak moze rozstrzygac uprawnienia tylko
            // PO NAZWIE, nie po tozsamosci komendy - gdybysmy pozwolili 'canonical' nadpisac sie
            // druga komenda, prune sprawdzalby uprawnienia DRUGIEJ komendy takze dla drzewa
            // PIERWSZEJ (bo obie maja ta sama nazwe, ten sam klucz w 'canonical'). Zamiast tej
            // dwuznacznosci: pierwsza komenda pod danym imieniem wygrywa, druga i kolejne sa
            // pomijane z ostrzezeniem - deterministyczne (w ramach jednego przebiegu) i bez
            // przypadkowego sprawdzania cudzych uprawnien.
            if (canonical.containsKey(name)) {
                CommandSuggest.LOGGER.warn(
                        "Komendy {} i {} zglaszaja ta sama nazwe '{}' - druga jest pomijana "
                        + "(prune sprawdza uprawnienia po nazwie, nie po tozsamosci obiektu).",
                        canonical.get(name).getClass().getName(), cmd.getClass().getName(), name);
                continue;
            }

            List<String> aliases = new ArrayList<String>();
            for (String k : e.getValue()) {
                if (!k.equals(name)) {
                    aliases.add(k);
                }
            }
            canonical.put(name, cmd);

            CommandTree described = descriptors.get(name);
            CmdNode root = described != null ? described.getRoot() : genericRoot(cmd, sender);
            trees.add(new CommandTree(name, aliases, root));
        }

        CommandIndex full = new CommandIndex(trees);
        final MinecraftServer srv = server;
        final ICommandSender who = sender;
        final Map<String, ICommand> lookup = canonical;
        return full.prune(name -> {
            ICommand cmd = lookup.get(name);
            if (cmd == null) {
                return false;
            }
            try {
                return cmd.checkPermission(srv, who);
            } catch (RuntimeException ex) {
                // cudzy kod; komenda, ktora wywala sie na sprawdzeniu uprawnien, nie trafia do drzewa
                CommandSuggest.LOGGER.warn("checkPermission komendy '{}' rzucilo {} - pomijam.",
                        name, ex.getClass().getSimpleName());
                return false;
            }
        });
    }

    /**
     * Wezel dla komendy bez opisu. {@code getUsage} zwraca zwykle KLUCZ tlumaczenia, nie gotowy
     * tekst — klient przepuszcza go przez {@code I18n.format}, ktore nieznany klucz oddaje bez zmian,
     * wiec obie postacie sa obsluzone.
     */
    private static CmdNode genericRoot(ICommand cmd, ICommandSender sender) {
        String usage;
        try {
            usage = cmd.getUsage(sender);
        } catch (RuntimeException ex) {
            usage = null;
        }
        CmdArg tail = new CmdArg("args", ArgType.GREEDY, null, null, null);
        return new CmdNode(null, Collections.singletonList(tail), null, true, usage);
    }
}
