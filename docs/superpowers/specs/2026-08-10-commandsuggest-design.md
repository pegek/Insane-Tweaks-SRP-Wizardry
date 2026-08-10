# commandsuggest — projekt (2026-08-10)

Mod podpowiadający komendy na czacie 1.12.2, w miejsce **brigo** (usuniętego z paczki DEv 1.2 dnia
2026-08-10 po tym, jak wywalał klienta przy każdym naciśnięciu TAB).

- **modid:** `commandsuggest` · **group:** `com.spege.commandsuggest` · **archivesBaseName:** `commandsuggest`
- **Status:** projekt zatwierdzony przez usera. Następny krok — plan wdrożenia.
- **Spec wstępny:** `notes/cmdsuggest_spec_2026-08-10.md` (geneza, crash brigo, rozeznanie rynku).
  Ten dokument go **zastępuje** wszędzie tam, gdzie się różnią; różnice wypunktowane w §2.
- **Jar referencyjny:** `notes/decompiled_mods/brigo_forge-1.1.1+1.12.x.jar`

---

## 1. Cele i nie-cele

**Cele**

1. Popup podpowiedzi pod polem czatu — nawigacja klawiaturą, zatwierdzanie, klik i scroll myszą.
2. Typy argumentów tam, gdzie je znamy: kolorowanie, walidacja zakresu, linia usage.
3. Pokrycie ekosystemu **konfiguracją, nie kodem** — opis komendy nowego moda nie wymaga rekompilacji.
4. Łagodna degradacja: na obcym serwerze i bez moda po drugiej stronie ma dalej działać.
5. **Zero ASM.** Po crashu brigo to wymaganie twarde, nie preferencja.

**Nie-cele (v1)**

- Nie odtwarzamy semantyki Brigadiera z 1.13+ (konteksty wykonania, redirecty, `run()`).
- Nie ruszamy wykonywania komend. Parsowanie i egzekucja zostają waniliowe — tylko podpowiadamy.
- Nie piszemy własnego GUI czatu ani nie zastępujemy `GuiChat`.

## 2. Czym to się różni od specu wstępnego

| Spec wstępny | Ten projekt | Dlaczego |
|---|---|---|
| modid `cmdsuggest` (roboczy) | **`commandsuggest`** | decyzja usera |
| D4: Brigadier shadowany i zrelokowany | **brak Brigadiera** | brigo potrzebowało go, bo czytało pliki `.commodore`; my mamy własny model drzewa (§4) i nie wykonujemy komend, więc zostałby nam z niego `SuggestionsBuilder` za cenę shadowania i relokacji |
| §5: pakiet `C2SSuggest` dla wartości dynamicznych | **brak własnego C2S** | w 1.12.2 gracze, itemy, bloki i encje są po stronie klienta (rejestry synchronizowane przy loginie); reszta idzie waniliowym `CPacketTabComplete` |
| §4 źródło 1: interfejs API dla naszych modów, wykrywany przez `instanceof` | **wypada** | żaden z sześciu naszych modów nie rejestruje dziś ani jednej komendy (`grep "extends CommandBase"` trafia wyłącznie w cudze zdekompilowane źródła) — API dla zera konsumentów. Zastąpi je w v2 skanowanie `assets/<modid>/commandsuggest/*.json` przez `CraftingHelper.findFiles`, które daje to samo bez sprzężenia |
| §5: dwa tryby (z drzewem / waniliowy) z przełącznikiem stanu | **jeden tryb** | drzewo daje strukturę i typy, `CPacketTabComplete` daje żywe wartości; „tryb waniliowy" to po prostu puste drzewo. Zero `if (mode == VANILLA)` w kodzie |
| §3 D3: „`GuiChat.inputField` jest prywatne" | jest **`protected`** | z handlera poza pakietem i tak nieosiągalne, więc refleksja zostaje — ale opis był nieścisły |
| §9: popup a „waniliowa lista kandydatów" | w 1.12.2 **nie ma waniliowej listy** | `ChatTabCompleter.complete()` przy >1 kandydacie skleja je przecinkami i **wypisuje na czat** przez `printChatMessageWithOptionalDeletion`. Zastępujemy więc nie prostokąt, tylko śmiecenie w oknie czatu |

## 3. Fundament — zweryfikowane, nie założone

Cała decyzja „zero mixinów" wisi na czterech hakach i dwóch polach. Sprawdzone w źródłach Forge
`1.12.2-14.23.5.2860` (`forge-…-sources.jar` z cache ForgeGradle) i na bajtkodzie
(`joined.tsrg` + `javap` na `client.jar` z `mcp_config/1.12.2`), nie z pamięci.

**Eventy.** `GuiScreen.handleInput()` postuje `MouseInputEvent.Pre` i `KeyboardInputEvent.Pre`
**wewnątrz** pętli `while (Mouse.next())` / `while (Keyboard.next())`, a anulowanie daje `continue`:

```java
while (Keyboard.next()) {
    this.keyHandled = false;
    if (MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.KeyboardInputEvent.Pre(this))) continue;
    this.handleKeyboardInput();
    ...
}
```

Wnioski: (a) oba `Pre` są `@Cancelable`, (b) `Keyboard.getEventKey()`, `getEventKeyState()`,
`getEventCharacter()`, `Mouse.getEventButton()`, `Mouse.getEventDWheel()` są w handlerze **ważne**,
bo statyczny stan LWJGL należy do bieżącego zdarzenia, (c) anulowanie pomija dla tego zdarzenia
także `mc.dispatchKeypresses()` — czyli F3, screenshot, fullscreen. Nieszkodliwe, bo anulujemy
wyłącznie klawisze, które sami obsługujemy, i tylko przy otwartym popupie.

**Pola `GuiChat`** (notch `bkn`):

| MCP | SRG | typ | widoczność | po co |
|---|---|---|---|---|
| `inputField` | `field_146415_a` | `GuiTextField` (notch `bje`) | `protected` | odczyt treści i pozycji kursora |
| `tabCompleter` | `field_184096_i` | `TabCompleter` (notch `blq`) | `private` | **podmieniamy** — patrz niżej |

Obie przez `ObfuscationReflectionHelper`, **raz na `InitGuiEvent.Post`**, uchwyty trzymane w stanie
sesji. Nigdy w pętli rysowania.

**Podmiana `tabCompleter` zastępuje dwa mixiny brigo** (`NetHandlerPlayClient` i `ChatTabCompleter`).
Ścieżka odpowiedzi serwera to `NetHandlerPlayClient.handleTabComplete` → `GuiChat.setCompletions(…)`
→ `tabCompleter.setCompletions(…)`. Waniliowy `TabCompleter.setCompletions` robi jedną z dwóch
rzeczy i żadna nam nie służy:

- przy `requestedCompletions == false` — **nie robi nic**, odpowiedź przepada;
- przy `true` — **przepisuje pole tekstowe** wspólnym prefiksem (`StringUtils.getCommonPrefix`)
  i przy braku wspólnego prefiksu woła `complete()`, wstawiając pierwszego kandydata.

Podstawiamy więc własny `SpyTabCompleter extends TabCompleter`, który `setCompletions` **przechwytuje
zamiast mutować tekst**. `TabCompleter` jest `public abstract` z konstruktorem `(GuiTextField, boolean)`
i jedną metodą abstrakcyjną `getTargetBlockPos()` — zwykłe dziedziczenie.

**Tabela haków**

| Potrzeba | Hak | Uwagi |
|---|---|---|
| wejście w otwarty czat | `GuiScreenEvent.InitGuiEvent.Post`, filtr `gui instanceof GuiChat` | tu obie refleksje, raz na ekran |
| TAB / ↑ / ↓ / Enter / Esc | `GuiScreenEvent.KeyboardInputEvent.Pre` (cancel) | tylko te klawisze i tylko przy otwartym popupie |
| klik w pozycję, scroll listy | `GuiScreenEvent.MouseInputEvent.Pre` (cancel) | |
| rysowanie popupu | `GuiScreenEvent.DrawScreenEvent.Post` | |
| odpowiedź serwera na tab-complete | `SpyTabCompleter` w `field_184096_i` | zamiast mixina |

Skutek uboczny w naszą stronę: skoro anulujemy TAB przed `keyTyped`, to `ChatTabCompleter.complete()`
nigdy nie wykona się i nigdy nie wypisze swojej listy przez przecinki na czat.

**Gdyby kiedyś mixin okazał się nieunikniony:** `minVersion 0.8` (regresja, która zabiła brigo, siedzi
w ścieżce `INJECT_PREPARE_LEGACY` dla klas wewnętrznych), osobny config, gate w `IMixinConfigPlugin`.

## 4. Warstwa `core` — czysta Java, zero klas Minecrafta

Jedyna warstwa sensownie testowalna jednostkowo. Model:

```java
final class CommandTree { String name; List<String> aliases; CmdNode root; }

final class CmdNode {
    String literal;        // null tylko w korzeniu
    List<CmdArg> args;     // uporządkowana sekwencja po literale
    List<CmdNode> sub;     // rozgałęzienie na kolejne literały
    boolean executable;    // ścieżka może się tu skończyć
    String usage;          // opcjonalna linia pod listą
}

final class CmdArg { String name; ArgType type; Map<String,Object> props; }
```

**`ArgType` rozstrzyga, skąd biorą się wartości** — i to jest cały zysk z typowania:

| Rozwiązywane lokalnie na kliencie | Źródło |
|---|---|
| `bool` | `true` / `false` |
| `choice` | lista z `props` |
| `player` | `NetHandlerPlayClient.getPlayerInfoMap()` |
| `item`, `block`, `entity` | `ForgeRegistries` — rejestry są synchronizowane przy loginie |
| `blockpos` | `mc.objectMouseOver` (raytrace) |
| `dimension` | `DimensionManager.getIDs()` |
| `int`, `float` | brak listy; walidacja zakresu i podpowiedź w linii usage |

| Odsyłane na serwer | |
|---|---|
| `word`, `greedy`, `unknown` | `CPacketTabComplete` + `ClientCommandHandler.instance.autoComplete` |

**Parser** dzieli linię **identycznie jak `CommandHandler`**: na pojedynczych spacjach, z pustym
tokenem na końcu, gdy linia kończy się spacją. Żadnych cudzysłowów — wanilia ich nie zna, a
rozjechanie się z tym, co komenda naprawdę dostanie, byłoby gorsze niż brak funkcji.

**Matcher** v1: prefiks, bez rozróżniania wielkości liter. Dopasowanie po inicjałach → v2.

**Przycinanie po uprawnieniach** też mieszka w `core`, ale jako `CommandTree.prune(Predicate<String>)`
po nazwie komendy — bez ani jednego typu MC. To `TreeBuilder` (warstwa `server`) podaje predykat
opakowujący `ICommand.checkPermission(server, sender)`. Dzięki temu sam mechanizm przycinania jest
testowalny jednostkowo, a `core` zostaje czysty.

**`TreeCodec`**: JSON → drzewo (Gson, jest w MC) oraz drzewo ⇄ `ByteBuf` dla pakietu.

## 5. Format opisów

Jeden plik na komendę, pisany ręcznie:

```json
{
  "command": "srpevolution",
  "aliases": ["srpevo"],
  "sub": [
    { "lit": "set",   "args": [{"name": "target", "type": "player"},
                               {"name": "points", "type": "int", "min": 0}], "exec": true },
    { "lit": "reset", "args": [{"name": "target", "type": "player"}], "exec": true },
    { "lit": "info",  "exec": true }
  ]
}
```

`sub` zagnieżdża się dowolnie głęboko. Nazwy typów są krótkie, a mapa na parsery Brigadiera istnieje
**tylko w dokumentacji** — kosztuje jedną tabelkę, a otwiera drogę importerowi w v2:

| nasz | Brigadier 1.13+ |
|---|---|
| `word` / `greedy` | `brigadier:string` (`SINGLE_WORD` / `GREEDY_PHRASE`) |
| `int` / `float` / `bool` | `brigadier:integer` / `brigadier:float` / `brigadier:bool` |
| `player` | `minecraft:game_profile` |
| `entity` | `minecraft:entity` |
| `blockpos` | `minecraft:block_pos` |
| `item` / `block` | `minecraft:item_stack` / `minecraft:block_state` |
| `dimension` | `minecraft:dimension` |
| `choice` | brak odpowiednika (w 1.13+ to literały) |

**Źródła, w kolejności rosnącego pierwszeństwa:**

1. wbudowane `assets/commandsuggest/commands/*.json` w naszym jarze — pokrycie paczki
   (FTBQuests, `/srpevolution`, `/srpvectors`, JourneyMap, Reskillable, `/gamerule`, WorldEdit…);
   **to jest nasza przewaga nad brigo**, który miał trzy definicje zaszyte w kodzie
2. `config/commandsuggest/commands/*.json` — nadpisuje wbudowane po polu `command`
3. fallback: cokolwiek nieopisanego to `literal + greedy`, usage z `ICommand.getUsage(sender)`,
   wartości z `CPacketTabComplete`. Dzięki temu 268 modów działa od pierwszego dnia, tylko bez typów

Ładowane przy starcie serwera i na `/commandsuggest reload`.

## 6. Protokół

Kanał `SimpleNetworkWrapper` o nazwie `commandsuggest`.

| Pakiet | Kierunek | Dysk. | Kiedy | Zawartość |
|---|---|---|---|---|
| `S2CCommandTree` | S→C | 0 | `PlayerLoggedInEvent`; `/commandsuggest reload` (do wszystkich); `/commandsuggest refresh` (do wołającego) | pula stringów (varint + UTF) + węzły jako indeksy varint, GZIP powyżej 4 KiB (jeden bajt flagi na początku) |

Drzewo jest **per gracz**, filtrowane przy budowie przez `ICommand.checkPermission(server, sender)`
z tym graczem jako `sender` — podane do `core` jako predykat, patrz §4. Budowa raz, przy loginie.
Gdyby pomiar pokazał, że to boli — cache per poziom uprawnień (0–4) zamiast per gracz.

Brak pakietu w oknie `Handshake Timeout Ticks` po dołączeniu → puste drzewo → ścieżka
zdegradowana. Jedna linia INFO w obu przypadkach, mówiąca który tryb i dlaczego.

**Uczciwa luka:** Forge nie ma eventu na zmianę poziomu uprawnień, więc po `/op` drzewo jest
nieaktualne aż do przelogowania. v1 daje na to `/commandsuggest refresh`. Automat dopiero, gdyby
ktoś zgłosił, że przeszkadza.

**Rozmiar:** limit `SPacketCustomPayload` w stronę klienta to 1 048 576 bajtów. Przy ~1000 komend
raczej nie podejdziemy, ale to **mierzymy na paczce przed pisaniem chunkowania**, nie zgadujemy.

## 7. Klient

Popup rysowany w `DrawScreenEvent.Post` nad polem tekstowym: podświetlenie wyboru, ↑/↓, TAB i Enter
zatwierdzają, Esc zamyka, klik i scroll myszą, kolor argumentu wg typu, linia usage pod listą.
Kody LWJGL: TAB 15, Esc 1, Enter 28 i 156, ↑ 200, ↓ 208.

- Cache drzewa na sesję; unieważnienie przy `S2CCommandTree` i przy rozłączeniu.
- Przeliczanie podpowiedzi **tylko przy zmianie treści pola**, z debounce — nie co klatkę.
  Żadnych alokacji ani syscalli w pętli rysowania (zasada paczki).
- **Cały tekst widziany przez gracza po angielsku** (konwencja paczki).
  Lang w `assets/commandsuggest/lang/en_us.lang`, a jar **musi** wieźć `pack.mcmeta`
  z `pack_format: 3` — bez tego `assets/` nie staje się domeną zasobów i każdy klucz renderuje się
  jako własna nazwa, cicho i bez śladu w logu (przewróciły się na tym trzy poprzednie ekstrakcje).

## 8. Podprojekt i pliki

`settings.gradle` += `include 'commandsuggest'`. `build.gradle` wzorowany na `reskilltweaks`,
**minus** `evaluationDependsOn`, minus wszystkie `fg.deobf` (zero zależności od modów),
minus `mixinbooter` (zero mixinów). `mcmod.info` i `Specification-Version` wyprowadzone z `version`.
`jar.finalizedBy('reobfJar')`. Mod jedzie na **klienta i serwer** — nie trafia do `dist/client_only.txt`.

```
commandsuggest/src/main/java/com/spege/commandsuggest/
  CommandSuggestMod.java        @Mod, @SidedProxy, VERSION
  CommonProxy.java / client/ClientProxy.java
  config/CommandSuggestConfig.java
  core/    CommandTree, CmdNode, CmdArg, ArgType, InputParser, PrefixMatcher, TreeCodec
  server/  TreeBuilder, DescriptorLoader, CommandCommandSuggest
  client/  ChatScreenHandler, SuggestionPopup, SpyTabCompleter, ClientTreeCache, LocalValueSource
  net/     PacketHandler, S2CCommandTree
commandsuggest/src/main/resources/
  mcmod.info, pack.mcmeta, assets/commandsuggest/lang/en_us.lang,
  assets/commandsuggest/commands/*.json
commandsuggest/src/test/java/…/core/     (JUnit 4)
```

Wszystkie handlery klienckie rejestrowane z `ClientProxy` — `GuiScreenEvent` jest klasowo
`@SideOnly(Side.CLIENT)`, więc jego podklasy też, i instancjonowanie handlera na dedyku byłoby
crashem przy ładowaniu klasy (zasada z CLAUDE.md § Side safety).

## 9. Config

`commandsuggest.cfg`, `@Config(modid = "commandsuggest", category = "general")` — same pola proste,
więc **nie** `category = ""` (to jest twardy crash w `ConfigManager.sync` dla pól nie-kategorii).
Własny `@Mod.EventBusSubscriber` na `OnConfigChangedEvent` → `ConfigManager.sync`.

| Pole | Typ | Domyślnie |
|---|---|---|
| `Enabled` | `boolean` | `true` |
| `Max Visible Rows` | `int` | `10` |
| `Colour Arguments By Type` | `boolean` | `true` |
| `Show Usage Line` | `boolean` | `true` |
| `Recompute Debounce Ms` | `int` | `50` |
| `Handshake Timeout Ticks` | `int` | `100` (5 s) |
| `Log Handshake Mode` | `boolean` | `true` |

Wszystkie czytane na żywo — żadne nie bramkuje rejestracji handlerów, więc `@Config.RequiresMcRestart`
byłoby fikcją.

## 10. Testy

**Jednostkowe (`core`, JUnit 4.12).** `core` nie zna ani jednej klasy MC, więc chodzą na zwykłej JVM:
parser (zgodność podziału z waniliowym `CommandHandler`), matcher, JSON → drzewo,
round-trip drzewo → bajty → drzewo, `CommandTree.prune(Predicate)` (przycinanie po uprawnieniach —
sam predykat, bez `ICommand`).

🚨 To **zmienia fakt „No test suite, no lint task" z CLAUDE.md** — po zmergowaniu trzeba go tam
poprawić.

**W grze, ręcznie:** TAB na pustym `/`; TAB w środku argumentu; komenda nieopisana (fallback);
komenda opisana (pełne typy); singleplayer; serwer z modem; serwer bez moda; `/op` w trakcie sesji
i `/commandsuggest refresh`.

## 11. Ryzyka

- **VintageFix `tab_complete_ddos`** obcina listę do 100 pozycji i **dopisuje linijkę na czat**.
  Bez mixina tego nie stłumimy. Dotyka wyłącznie ścieżki `CPacketTabComplete`, czyli argumentów
  `word`/`greedy` i nieopisanych komend — i to jest dokładnie powód, dla którego typowanie zarabia
  na siebie: opisany `item` czy `player` idzie z rejestru klienta i tam problem nie istnieje.
- **Chunk-Pregenerator** wozi własny `AdvancedTabCompleter`
  (`pregenerator/impl/client/gui/chat/`) — pierwszy test sprawdza, czy nie sięga po `field_184096_i`.
- **CleanMix / Cleanroom** — regresja z §3. Dopóki nie ma mixinów, nas nie dotyczy.
- **`fg.deobf(files)` w modDev jest no-opem** — nie dotyczy, bo nie linkujemy się do żadnego moda.
- **Rozmiar drzewa** — patrz §6, mierzymy przed optymalizacją.

## 12. Zakres

**v1** — popup z nawigacją, klikiem i scrollem; drzewo z serwera z filtrem uprawnień; fallback
generyczny z `getTabCompletions`; degradacja do pustego drzewa; kolorowanie argumentów wg typu;
`/commandsuggest reload` i `/commandsuggest refresh`.

**v2** — walidacja składni w locie (czerwony ogon przy złym argumencie); dopasowanie po inicjałach
(`/gr dD t` → `/gamerule doDaylightCycle true`); historia komend w popupie; skanowanie
`assets/<modid>/commandsuggest/*.json` w cudzych jarach przez `CraftingHelper.findFiles`;
importer formatu drzewa z 1.13+; komplet opisów per-mod dla paczki.

## 13. Referencje

- Spec wstępny z genezą i crashem brigo: `notes/cmdsuggest_spec_2026-08-10.md`
- Jar brigo: `notes/decompiled_mods/brigo_forge-1.1.1+1.12.x.jar` — warte obejrzenia
  `client/gui/CommandSuggestions` (GUI listy) i `command/CommandTreeConverter`
  (rejestr `ICommand` → drzewo). `compat/mods/*` — jak **nie** robić compatu.
- Crash-reporty: `crash-2026-08-10_14.46.56-client.txt`, `crash-2026-08-10_14.53.04-client.txt`
  w katalogu paczki.
- Brigo upstream: <https://modrinth.com/mod/brigo> , <https://github.com/xhyrom/brigo>
