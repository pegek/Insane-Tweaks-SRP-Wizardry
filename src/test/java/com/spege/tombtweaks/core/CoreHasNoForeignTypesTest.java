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
            String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            for (String forbidden : FORBIDDEN) {
                if (bytes.contains(forbidden)) {
                    offenders.add(file.getFileName() + " -> " + forbidden);
                }
            }
        }

        assertTrue(offenders.isEmpty(), "core nazywa typy obce: " + offenders);
    }

    @Test
    void theScanWouldActuallyCatchSomething() {
        // Kontrola samego testu: gdyby warunek byl zawsze prawdziwy, test nic nie pilnuje.
        String pretendClassFile = "some bytes net/minecraft/world/item/ItemStack more bytes";
        boolean caught = false;
        for (String forbidden : FORBIDDEN) {
            if (pretendClassFile.contains(forbidden)) caught = true;
        }
        assertTrue(caught);
        assertFalse("com/spege/tombtweaks/core/ItemKey".contains("net/minecraft"));
    }
}
