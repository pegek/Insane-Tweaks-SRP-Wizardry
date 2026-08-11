# Handoff — dalsza praca nad systemem magii Abomination

**Status:** żywioł kompletny i zweryfikowany w grze. `insanetweaks 1.16.1`, zmergowane do `main`
(`b76a1d1`) i wypchnięte.
**Data:** 2026-08-11.
**Kanon:** `docs/superpowers/specs/2026-08-08-ebw-abomination-element-design.md` (żywioł) +
`docs/superpowers/specs/2026-08-08-abomination-economy-and-balance-design.md` (ekonomia i balans).
Oba przeczytaj, zanim dotkniesz czegokolwiek związanego z `Element` w EBW.

---

## Gdzie jesteśmy

Abomination jest **prawdziwym** żywiołem EBW — enum rozszerzany przez `WizardryEnumHelper.addElement`
z konstruktora `@Mod`, ordinal 8. Ma własny pył widmowy i kryształ (metadana 8 istniejących itemów
EBW), własną tablicę lootu ołtarza imbuement, drugą walutę craftingową i przeliczony balans zaklęć.

Zweryfikowane na żywej paczce DEv 1.2 (2026-08-11, 02:44): 29 mixinów `APPLY`, zero
`InvalidInjectionException`, zero brakujących modeli i tablic lootu, stary świat wstaje bez ekranu
brakujących wpisów rejestru.

Czego **nie ma** i co jest tego świadomą konsekwencją: żywioł nie występuje w generatorach EBW.
Nie ma magów abominacji, shrine'ów, obelisków ani handlu. Sześć redirectów trzyma go poza tymi
ścieżkami, bo wejście do nich wymaga 4 różdżek i co najmniej 4 części zbroi zarejestrowanych
**pod nazwami `ebwizardry:`**. Zaklęcia pojawiają się natomiast w zwykłym loocie EBW — to była
osobna decyzja i została utrzymana.

---

## Otwarte, w kolejności zależności

### A. Przebieg graficzny — cztery ikony

Najtańsza rzecz na liście i jedyna, która nie wymaga żadnego rozumowania.

| plik | stan |
|---|---|
| `assets/insanetweaks/textures/gui/container/element_icon_abomination.png` | bajtowa kopia ikony „None" z EBW |
| `assets/ebwizardry/textures/items/spectral_dust_abomination.png` | obrót barwy necromancy → 348° |
| `assets/ebwizardry/textures/items/crystal_abomination.png` | obrót barwy necromancy → 0° |
| `assets/insanetweaks/textures/items/magic_nucleus.png` | obrót barwy living_nucleus → 280° |

Wszystkie działają i są rozróżnialne, ale żadna nie jest rysowana. Pył celowo siedzi na 348°
(karmazyn), nie na 0° — `spectral_dust_fire` stoi na 11° i przy 16 px oba czytały się jako ten sam
płomień.

### B. Rozszerzenie ekonomii — zamówione, nierozpoczęte

Trzy rzeczy, o które prosił autor po weryfikacji:

1. **`magic_nucleus` jako składnik zbroi i miecza.** Dziś bramkuje wyłącznie `adaptation_upgrade`
   i `living_wand`. Zasada, którą wprowadziliśmy i której warto się trzymać: **mięso bramkuje broń,
   zassana magia bramkuje rzeczy czarodziejskie.** Jeśli zbroja i miecz mają wziąć nucleus, trzeba
   zdecydować, czy zasada się zmienia, czy te konkretne przedmioty przechodzą po stronie magii.
2. **Pył abominacji w craftingu Bauble Fruits.**
3. **Nowy pomysł na crafting** — autor zapowiedział, że ma koncepcję „ciekawszego systemu".
   To jest miejsce na brainstorm, nie na dopisywanie receptur do istniejącego szkieletu.

### C. Sim wizard i battlemage jako encje — największy kawałek

Świadomie wyłączone z poprzedniego zakresu; dotknęliśmy wyłącznie ich trzech tablic lootu.

🚨 **`EntitySimWizard` nie ma własnego spawnu.** Powstaje wyłącznie wtedy, gdy SRP asymiluje
`ebwizardry:wizard` / `evil_wizard` albo ich odpowiedniki z ASC. Podaż to iloczyn dwóch rzeczy,
których nie kontrolujemy — a to właśnie te moby mają być główną ścieżką do pyłu i zaklęć.
Autor wybrał **własny spawn w strefach skażenia**. Dopóki go nie ma, ekonomia stoi na recepturze
zapasowej, która jest celowo gorsza od dropu.

W tej samej sesji: tiery i siła (czy master faktycznie jest groźny), AI, oraz które zaklęcia rzucają
— dziś **11 z 14 naszych zaklęć ma `npcs: false`**, więc sim wizard nie używa własnego żywiołu.
Otwarcie tego mocno zmienia trudność i jest osobną decyzją projektową.

### D. `living_warlock_armour` — furtka już otwarta

Ołtarz imbuement przyjmuje bezżywiołową szatę maga i cztery receptakle jednego żywiołu, po czym
szuka `ebwizardry:<klasa>_<część>_<żywioł>`. Dla abominacji nie znajduje niczego, więc guard zwraca
pusty stack, a JEI samo pomija ten wiersz.

**Guard bramkuje na „lookup nie dał używalnej zbroi", nie na tożsamości żywiołu.** To jest cała
sztuczka: w dniu, w którym zarejestrujesz cztery części pod domeną `ebwizardry`, guard przestaje
działać sam z siebie, a ołtarz i jego wpis w JEI zapalają się bez jednej linii zmiany gdziekolwiek.

🚨 Jeśli zarejestrujesz **jedną** klasę zbroi bez pozostałych trzech, otworzysz drugie, niezabezpieczone
miejsce o tym samym kształcie: `ItemWizardArmour.applyUpgrade` też buduje stack z chybionego lookupu
i rzutuje. Albo cztery klasy naraz, albo rozszerz guard.

### E. Kryształ-blok — zwykła robota, nie ściana

Wcześniejszy draft twierdził, że `BlockCrystal` renderuje się z jednego pliku blockstate i dlatego
nie da się dołożyć dziewiątego wariantu. **Nieprawda.** `WizardryModels` używa
`StateMap.Builder().withName(ELEMENT).withSuffix("_crystal_block")`, a EBW wiezie **osiem osobnych
plików** (`fire_crystal_block.json` itd.). Dziewiąty wchodzi obok.

Do odkrycia potrzeba: pliku blockstate, modelu bloku, tekstury i receptury
`crystal_block_to_crystals_abomination` (EBW ma osiem takich, po jednej na żywioł). Wtedy znika też
ostatni redirect JEI (`generateCrystalBlockRecipes`) i drugi warunek w guardzie ołtarza.

### F. Pełnoprawny żywioł — magowie, shrine'y, handel

Wymaga 4 różdżek + minimum 4 części zbroi klasy WIZARD pod domeną `ebwizardry`, bo `getWand`
i `getArmour` to lookupy po nazwie w tej domenie i nic innego. Gdy istnieją, kasujesz redirecty na
`onInitialSpawn` (trzy klasy), `getRandomItemOfTier`, `populateSpells`, `WorldGenShrine.spawnStructure`
i `WorldGenObelisk.spawnStructure`.

🚨 **Shrine'y mają twardy sufit i jesteśmy o jeden żywioł od niego.** `BlockPedestal` pakuje
`meta = element.ordinal() + (natural ? ELEMENT.getAllowedValues().size() : 0)`, używając `ordinal()`
zamiast `ordinal()-1`, przez co marnuje indeks 0. Przy ośmiu nieMAGIC żywiołach daje to maks. meta 16
w polu 4-bitowym — dokładnie ten `AIOOBE`, który wywalił start przy 1.15.0. Zmiana pakowania na
`(ordinal()-1) + (natural ? size : 0)` daje równo 16 metadanych dla ośmiu żywiołów: **komplet**.
Czyli pedestały abominacji są możliwe, a *drugi* własny żywioł z pedestałami — nigdy.

Osobny sufit `BlockCrystal` to 16 żywiołów łącznie; jesteśmy na 9.

### G. Wspólna pula many

Autor wspominał o połączeniu pul z Trinkets and Baubles i EBW. Jeśli to przyjdzie, **jedyne do
przeliczenia są dwie liczby** — pojemności różdżek w `gear.wands`. Koszty zaklęć zostają ważne, bo
reguła jest wyrażona jako procent puli, jakakolwiek by ona nie była.

---

## Twarde fakty, których nie odkryjesz bez bólu

Każdy z nich kosztował crash albo recenzję. Pięć błędów w specyfikacji z tej sesji miało **jedną
wspólną przyczynę: przeczytanie fragmentu klasy EBW i uogólnienie go.** Czytaj docelowe metody do
końca.

- **`BlockReceptacle.PARTICLE_COLOURS` wygląda na zapisywalną i nie jest.** Pole jest zadeklarowane
  jako `java.util.Map`, ale `<clinit>` kończy się `Maps.immutableEnumMap(...)`. `put` kompiluje się
  bez ostrzeżenia i rzuca `UnsupportedOperationException` w runtime. Jedyny moment zapisu to redirect
  na to wywołanie w `<clinit>`. Pięć miejsc czyta tę mapę i dereferencuje bez sprawdzenia null.
- **Ołtarz rzutował `AIR` na `IManaStoringItem`.** `getImbuementResult` w gałęzi zbroi robi
  `new ItemStack(getArmour(...))`, a potem `((IManaStoringItem) result.getItem()).setMana(...)` —
  wyjątek leci linię przed `return`, więc `if (output.isEmpty()) continue;` w JEI nigdy go nie łapie.
  I to nie jest problem JEI: kafelek ołtarza woła tę samą metodę, więc był to crash serwera.
- **Zaklęć nie da się tak po prostu usunąć.** `Spell` dziedziczy po `IForgeRegistryEntry.Impl`,
  a `Spells.createRegistry` **nie woła `disableSaving()`** — mapa id siedzi w `level.dat`. Usunięcie
  zarejestrowanego zaklęcia daje ekran brakujących wpisów na każdym istniejącym świecie. Potrzebny
  handler `RegistryEvent.MissingMappings<Spell>` z `ignore()` (nie `remap()`, bo `ignore()` zostawia
  martwy slot i nie przesuwa numerycznych id, które `ItemSpellBook` trzyma w metadanej).
- **Mana różdżki jest trzymana jako damage**, a `getMana = pojemność − damage`, bez klamry.
  Obniżenie pojemności w konfiguracji wpędza każdą zapisaną różdżkę w ujemną manę: `isManaEmpty`
  sprawdza `== 0`, więc różdżka melduje się jako niepusta, trzyma modyfikatory obrażeń i nie rzuca
  niczego. `BaseCustomWandItem.onUpdate` klamruje to do zera — nie usuwaj tego.
- **Nasz plik lang nie ma `# PARSE_ESCAPES`,** więc Minecraft dzieli linie zwykłym
  `Splitter.on('=')` i **nie zdejmuje escapów**. Dwukropki w kluczach pisze się **gołe**
  (`item.ebwizardry:crystal_abomination.name`). Skopiowanie stylu z pliku EBW, który ten marker ma,
  daje klucz z dosłownym backslashem i surową nazwę w grze.
- **Plik lang SRParasites zawiera sieroty.** `item.srparasites.ada_burrower_drop.name=§cFigment`
  istnieje, a przedmiotu nie ma. Weryfikuj składnik SRP po `assets/srparasites/models/item/`, nigdy
  po langu. Awaria jest cicha: `safeItem` notuje pudło, `registerFallback` wyrzuca recepturę,
  zostaje jedna linia WARN.
- **Nie hoistuj wyników `safeItem` do wspólnych zmiennych** w `ModRecipes`. `PENDING_MISSING` jest
  czyszczone przez każde `registerFallback`, więc hoistowana zmienna oddaje swoje pudło pierwszej
  recepturze, a późniejsze rejestrują się ze składnikiem `AIR`.
- **`mixins.insanetweaks.altarguard.json` ma `required: true`,** w odróżnieniu od `late.json`.
  To celowe: guard jest jedynym mixinem, którego ciche zniknięcie **samo w sobie jest crashem**.
  Zmiana w EBW ma być głośnym błędem z naszą nazwą, nie `ClassCastException` w cudzym kodzie.
- **Trzy tablice lootu sim wizardów zaszywają metadaną `8`,** bo JSON nie umie policzyć
  `ABOMINATION.ordinal()`; receptury czytają ordinal i celowo tego nie zaszywają. Jeśli paczka
  kiedykolwiek dostanie drugi mod dodający żywioł, **receptury pójdą za nowym ordinalem, a dropy
  nie** — te trzy pliki są pierwszym miejscem do sprawdzenia.
- **`NativeElements` to jedyny punkt rozszerzenia.** Zwraca żywioły EBW minus abominacja i jest tym,
  czym zwężamy podtypy i generatory. Drugi własny żywioł musi tam dojść, inaczej wraca crash
  pedestału.

---

## Dźwignie balansowe, świadomie zostawione

- **Reguła:** narzędzie ≤ 5% puli, rytuał ≥ 10%, pas między nimi pusty **celowo**. Wzorcem jest
  **w pełni rozwinięta Living Wand** — 4000 many przy jej 20% zniżce — co w surowych liczbach z JSON
  daje: narzędzie ≤ 250, rytuał ≥ 500. Sentient Wand celowo czyni rytuały tanimi; to znaczy być
  różdżką końcową.
- **Księgi master są farmowalne** przez ołtarz: zniszczona księga jest u nas kraftowalna (w EBW
  wypada tylko z lootu), a tablica ołtarza nie ma klucza `tiers`. Przejrzane i **zaakceptowane**
  2026-08-08. Dźwignia to jedna linia: `"tiers": ["novice","apprentice","advanced"]` w puli ołtarza.
- **Pula ksiąg jest jednorodna tierowo.** Z 12 kwalifikujących się zaklęć **11 jest na master**,
  a `immune_bond` jest jedynym `advanced` — więc będzie nieproporcjonalnie rzadki. Nie błąd, ale
  jeśli chcesz zróżnicować łupy z niższych tierów, tu jest przyczyna.
- **`npcs: false` na 11 z 14 zaklęć** — patrz punkt C.

---

## Gdzie co leży

- **Specyfikacje:** `docs/superpowers/specs/2026-08-08-ebw-abomination-element-design.md`,
  `docs/superpowers/specs/2026-08-08-abomination-economy-and-balance-design.md`.
  Oba niosą korekty w miejscu, z uzasadnieniem, dlaczego pierwsza wersja była błędna.
- **Plany:** `docs/superpowers/plans/2026-08-08-ebw-abomination-element.md`,
  `docs/superpowers/plans/2026-08-08-abomination-economy-and-balance.md`.
- **Pamięć projektu:** `abomination-element-canonical`, `srp-lang-orphans`, `mod-split-routing`,
  `spell-guide-canonical`.
- **Kod, punkty wejścia:** `init/ModElements` (jedyny właściciel żywiołu),
  `util/NativeElements` (punkt rozszerzenia), `mixins/MixinImbuementAltarGuard` (dwie gałęzie),
  `mixins/MixinBlockReceptacleColours`, `init/ModRecipes` (blok pod `if (ModElements.EXTENDED)`),
  `config/categories/GearCategory.Wands` (pojemności many).
