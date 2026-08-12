package com.spege.commandsuggest.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.core.CommandTree;
import com.spege.commandsuggest.core.JsonTreeReader;

/**
 * Opisy komend z dwoch zrodel: wbudowane w jarze i nadpisujace je pliki z
 * {@code config/commandsuggest/commands/}. Klucz to pole {@code command} z pliku.
 *
 * <p>Bledny plik nie zatrzymuje ladowania — leci WARN z nazwa pliku i lecimy dalej. To sa dane
 * pisane recznie i jedna literowka nie ma prawa zabrac podpowiedzi calej reszcie paczki.
 */
public final class DescriptorLoader {

    private static final String BUILTIN_DIR = "/assets/commandsuggest/commands/";

    private static File userDir;
    private static Map<String, CommandTree> descriptors = Collections.emptyMap();

    private DescriptorLoader() {
    }

    /** Wolane z preInit. Tworzy katalog uzytkownika, zeby bylo gdzie wrzucic wlasny opis. */
    public static void init(File configDir) {
        userDir = new File(new File(configDir, CommandSuggest.MODID), "commands");
        if (!userDir.exists() && !userDir.mkdirs()) {
            CommandSuggest.LOGGER.warn("Nie udalo sie utworzyc {}", userDir.getAbsolutePath());
        }
    }

    public static Map<String, CommandTree> get() {
        return descriptors;
    }

    /** Przeladowuje oba zrodla. Wolane przy starcie serwera i z {@code /commandsuggest reload}. */
    public static int reload() {
        Map<String, CommandTree> loaded = new HashMap<String, CommandTree>();
        int builtin = loadBuiltin(loaded);
        int user = loadUser(loaded);
        descriptors = Collections.unmodifiableMap(loaded);
        CommandSuggest.LOGGER.info("Wczytano opisy komend: {} wbudowanych, {} z config/, razem {}.",
                Integer.valueOf(builtin), Integer.valueOf(user), Integer.valueOf(loaded.size()));
        return loaded.size();
    }

    private static int loadBuiltin(Map<String, CommandTree> into) {
        int n = 0;
        InputStream indexStream = DescriptorLoader.class.getResourceAsStream(BUILTIN_DIR + "index.json");
        if (indexStream == null) {
            CommandSuggest.LOGGER.warn("Brak {}index.json w jarze - zero wbudowanych opisow.", BUILTIN_DIR);
            return 0;
        }
        try {
            JsonElement el = new JsonParser().parse(readAll(indexStream));
            JsonArray names = el.getAsJsonArray();
            for (JsonElement nameEl : names) {
                String fileName = nameEl.getAsString();
                InputStream in = DescriptorLoader.class.getResourceAsStream(BUILTIN_DIR + fileName);
                if (in == null) {
                    CommandSuggest.LOGGER.warn("index.json wymienia {}, ktorego nie ma w jarze.", fileName);
                    continue;
                }
                try {
                    CommandTree t = JsonTreeReader.read(readAll(in));
                    into.put(t.getName(), t);
                    n++;
                } catch (RuntimeException e) {
                    CommandSuggest.LOGGER.warn("Wbudowany opis {} jest bledny: {}", fileName, e.getMessage());
                } finally {
                    closeQuietly(in);
                }
            }
        } catch (IOException e) {
            CommandSuggest.LOGGER.warn("Nie udalo sie odczytac index.json: {}", e.getMessage());
        } catch (RuntimeException e) {
            CommandSuggest.LOGGER.warn("index.json jest bledny: {}", e.getMessage());
        } finally {
            closeQuietly(indexStream);
        }
        return n;
    }

    private static int loadUser(Map<String, CommandTree> into) {
        if (userDir == null || !userDir.isDirectory()) {
            return 0;
        }
        File[] files = userDir.listFiles();
        if (files == null) {
            return 0;
        }
        int n = 0;
        for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                continue;
            }
            InputStream in = null;
            try {
                in = new FileInputStream(f);
                CommandTree t = JsonTreeReader.read(readAll(in));
                // config/ nadpisuje wbudowane - to jest cala pointa tego katalogu
                into.put(t.getName(), t);
                n++;
            } catch (IOException e) {
                CommandSuggest.LOGGER.warn("Nie udalo sie odczytac {}: {}", f.getName(), e.getMessage());
            } catch (RuntimeException e) {
                CommandSuggest.LOGGER.warn("Opis {} jest bledny: {}", f.getName(), e.getMessage());
            } finally {
                closeQuietly(in);
            }
        }
        return n;
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // nic sensownego nie da sie tu zrobic
            }
        }
    }
}
