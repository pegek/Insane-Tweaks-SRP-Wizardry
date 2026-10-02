package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pilnuje jedynej wlasnosci, ktora czyni pakiet core wartym istnienia: braku typow obcych.
 * Skanuje skompilowane klasy, bo nazwy typow siedza w constant poolu jako zwykly tekst -
 * dzieki temu lapie tez uzycie w pelni kwalifikowana nazwa, bez importu.
 */
class CoreHasNoForeignTypesTest {

    private static final String[] FORBIDDEN = {
        "net/minecraft", "net/minecraftforge", "ovh/corail"
    };

    /** Zakazane nazwy typow, ktore constant pool danej klasy zawiera jako zwykly tekst. */
    private static List<String> foreignTypesIn(Path classFile) throws IOException {
        String bytes = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
        List<String> hits = new ArrayList<String>();
        for (String forbidden : FORBIDDEN) {
            if (bytes.contains(forbidden)) {
                hits.add(classFile.getFileName() + " -> " + forbidden);
            }
        }
        return hits;
    }

    @Test
    void coreClassesNameNoForeignTypes() throws IOException {
        Path root = Paths.get("build", "classes", "java", "main",
                              "com", "spege", "tombtweaks", "core");
        assertTrue(Files.isDirectory(root),
                   "brak skompilowanych klas core pod " + root.toAbsolutePath()
                   + " - uruchom test przez ./gradlew test, nie z IDE bez kompilacji");

        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(root)) {
            classFiles = files.filter(p -> p.toString().endsWith(".class"))
                              .collect(java.util.stream.Collectors.toList());
        }

        List<String> offenders = new ArrayList<String>();
        for (Path file : classFiles) {
            offenders.addAll(foreignTypesIn(file));
        }

        assertTrue(offenders.isEmpty(), "core nazywa typy obce: " + offenders);
    }

    @Test
    void theScanWouldActuallyCatchSomething() throws IOException {
        // Kontrola samego skanu: klasa moda (poza core) nazywa net/minecraftforge, wiec ten
        // sam skan MUSI cos na niej znalezc. Gdyby skan byl zepsuty, test powyzej przechodzilby
        // pusto.
        Path modClass = Paths.get("build", "classes", "java", "main",
                                  "com", "spege", "tombtweaks", "TombTweaks.class");
        assertTrue(Files.exists(modClass),
                   "brak " + modClass.toAbsolutePath() + " - kontrola skanu nie ma na czym dzialac");

        assertFalse(foreignTypesIn(modClass).isEmpty(),
                    "skan nie znalazl typow obcych w klasie, ktora ich na pewno uzywa");
    }
}
