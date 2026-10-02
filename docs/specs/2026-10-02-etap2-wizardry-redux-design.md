# TombTweaks 1.20.1, etap 2: integracja z Electroblob's Wizardry Redux

**Data:** 2026-10-02
**Status:** szkic. Sekcja 5 to decyzje autora, przed planem.
**Wersje:** Tombstone 9.1.4, Wizardry Redux **0.8.9** (forge, 21.09.2026, najnowsza), Forge 47.3.19+.
**Poprzedni etap:** `2026-08-14-tombtweaks-1201-port-design.md` (rdzeń, wydany jako 0.1.0).

---

## 1. Najważniejsze odkrycie: soulbind różdżki na 1.20.1 jest natywny

Na 1.12.2 „dusza z grobu wiąże różdżkę” było główną funkcją integracji, bo Tombstone 4.x nie umiał
wiązać obcych przedmiotów, a do tego potrzebne było ulepszenie z Ancient Spellcraft. Na 9.1.4 tak
już nie jest:

- `ItemBookOfSoulbound.setEnchant` ustawia flagę NBT `soulbound` na **dowolnym niestackowalnym**
  przedmiocie w głównej ręce (`getMaxStackSize() == 1`). Różdżka Redux jest niestackowalna.
- `Helper.isSoulbound(stack)` zwraca `NBT soulbound || enchant Soulbound > 0`.
- `DeathHandler.handleSoulbound` / `storeSoulboundsOnBody` / `restoreSoulbounds` same
  przechowują takie przedmioty przy śmierci i oddają je po odrodzeniu.

Wniosek: **gracz już dziś może związać różdżkę Redux Księgą Soulbound.** Przeniesienie funkcji
z 1.12.2 (Ankh i dusza zamiast księgi) dałoby tylko inną cenę za ten sam efekt. Do potwierdzenia
w grze: różdżka z flagą wraca po śmierci, a jej NBT (zaklęcia, mana, ulepszenia, progres) jest
nietknięte.

## 2. Ustalenia (bajtkod Tombstone 9.1.4, źródła Redux 0.8.9)

| Ustalenie | Znaczenie |
|---|---|
| `BlockDecorativeGrave.use` szuka konsumenta przez `ItemStack.getCapability(SoulConsumerProvider.CAP_SOUL_CONSUMER)`, najpierw w drugiej ręce, z filtrem `isUsingOffhandToEnchant` | ten sam kontrakt co na 1.12.2, bez mixina |
| `SoulConsumerProvider` ma publiczny konstruktor `(ISoulConsumer)` i publiczne pole `CAP_SOUL_CONSUMER` | własny konsument doczepia się przez `AttachCapabilitiesEvent<ItemStack>`; 🚨 klasa leży poza pakietem `api`, więc zakres Tombstone 9.1.x zostaje wąski |
| `ISoulConsumer` doszedł do `onSneakGrave(...)` i `getCorruptionLevel(stack)`, a `ConsumeResult` to rekord `(result, message, soulStrength)` | `success(Component, int)` / `fail(Component)` jak na 1.12.2 |
| Perki magiczne: **Channeler** (max 3, „faster casting with magical items”), **Glyphographer** („improves scrolls”), **Rune Inscriber** („tablets and scrolls”), **Scribe** („books”), **Necromancer** („Zombify and Pray of Undead”) | dziś działają **wyłącznie** na magii Tombstone'a (`ItemCastableMagic`) |
| `EntityHelper.getPerkLevelWithBonus(Player, Perk)`: public static. `ModPerks.channeler` i reszta: public static | odczyt poziomu perka bez mixina; 🚨 też poza pakietem `api` |
| Redux: `WizardryEventBus.getInstance().register(SpellCastEvent.Pre.class, listener)` | własna szyna Redux, niezależna od loadera; zdarzenie da się anulować |
| `SpellCastEvent.getModifiers()` to `SpellModifiers` z kluczami `ebwizardry.potency/cost/chargeup/cooldown/duration/range/blast`, a `getSource()` (`Sources.WAND`, `SCROLL`, …) mówi, skąd jest rzut | most perk → zaklęcie to mnożenie modyfikatora w `Pre`, zero mixinów, ale **bez `chargeup`**, patrz wiersz niżej |
| 🚨 **`chargeup` jest liczony przed `Pre`.** `WandItem.use` → `calculateModifiers` → `calcCharge` → `startCharging` zapisuje modyfikatory w `WizardData`, z których `onUseTick` liczy ładowanie. `Pre` odpala dopiero przy `castingTick == 0`, na **świeżo** policzonych modyfikatorach. `calculateModifiers` nie ma haka dla addonów i działa po obu stronach, klienta i serwera | w `Pre` działają koszt, cooldown, potency, zasięg i czas trwania. Skrócenie ładowania wymaga mixina w `CastItemUtils.calculateModifiers` **i** poziomu perka po stronie klienta, inaczej klient i serwer się rozjadą |
| `WandUpgrades.register(Item, String)`: publiczne API dla addonów. `WandItem.applyUpgrade(player, wand, upgrade)` jest publiczne i pilnuje limitu tieru | własne ulepszenie różdżki bez mixina |
| `CastItemDataHelper.addProgression(stack, n)`: różdżki Redux zbierają progres do następnego tieru | możliwa nagroda za duszę |
| Redux: GPL-3.0, `forge [47.1.25,)`, `minecraft [1.20.1,1.22)`, opcjonalnie Curios/Accessories. Maven: `maven.modrinth:electroblobs-wizardry-redux:0.8.9-forge` (sprawdzone, HTTP 200) | zależność `compileOnly` z Maven Modrinth, jar nigdy w repo |

## 3. Propozycje

### A. Most perków Tombstone'a do zaklęć Redux (rekomendowany rdzeń)

Perki magiczne Tombstone'a zaczynają działać także na magię Redux. Wszystko dzieje się w jednym
słuchaczu `SpellCastEvent.Pre`, który mnoży modyfikatory zależnie od poziomu perka rzucającego
gracza:

| Perk | Efekt na Redux (propozycja, wszystko w configu) |
|---|---|
| Channeler (1–3) | −10% `cooldown` na poziom (`chargeup` tylko z mixinem, patrz §2 i decyzja 4) |
| Glyphographer / Rune Inscriber | rzuty ze **zwoju** (`Source` = zwój): +potency albo −koszt |
| Necromancer | +potency dla zaklęć żywiołu **necromancy** Redux |
| Scribe | do decyzji: Redux nie „czyta” ksiąg przy rzucie, więc raczej nic |

Dlaczego to: gracz, który zainwestował w perki Tombstone'a, nie traci ich wartości, gdy jego główną
magią jest Redux. To ten sam pomysł, który na 1.12.2 odłożono jako „retuning the existing perks to
affect casting”, a na 1.20.1 kosztuje jeden słuchacz bez mixina.

### B. Dusza z grobu karmi różdżkę

Nowe zachowanie grobu zamiast zdublowanego soulbinda: Ankh w głównej ręce, różdżka w drugiej,
dusza zostaje zużyta, a różdżka dostaje **progres tieru** (`addProgression`, mocna dusza = 2×) albo
**pełną manę**. Konsument przez `ISoulConsumer`, wzorzec 1:1 z 1.12.2 (§ „Contract” w tamtym specu,
w tym zasada „każda odmowa przez `ConsumeResult.fail`, nigdy przez `canEnchant`”).

### C. Parytet z 1.12.2: Ankh i dusza soulbindują różdżkę

Ta sama flaga `soulbound` co w Księdze, więc śmierć obsługuje Tombstone. Tanie, ale tylko zmienia
cenę funkcji, którą Tombstone już ma. Rekomendacja: **nie robić**, chyba że gracze z 1.12.2 mają
dostać identyczny gest.

### D. Własne ulepszenie różdżki „soulbound” (`WandUpgrades.register`)

Zajmuje slot ulepszenia, tak jak decyzja z 1.12.2 („convenience, not power”). Wymaga jednak
własnego przedmiotu, rejestracji i tekstury, a więc **osobnego moda-addonu do Redux**, a nie
`tombtweaks`. Ma sens tylko jako część takiego addonu, patrz decyzja 3.

## 4. Kształt techniczny (dla A i B)

- Pakiet `com.spege.tombtweaks.compat.wizardry`, ładowany tylko gdy `ModList.isLoaded("ebwizardry")`.
  Klasy dotykające typów Redux nie mogą być ładowane bez Redux, więc tylko przez osobną klasę
  bramkową.
- `build.gradle`: `compileOnly fg.deobf("maven.modrinth:electroblobs-wizardry-redux:0.8.9-forge")`
  z repozytorium `exclusiveContent` Modrinth. Do testów w dev: `runtimeOnly`.
- `mods.toml`: zależność `ebwizardry`, `mandatory=false`, `versionRange="[0.8.9,)"`, `ordering="AFTER"`.
- Logika decyzji (mnożniki per poziom, klamry) w `core`, bez typów MC i składnią Javy 8, zgodnie
  z regułą projektu.
- Nowa sekcja configu `[wizardry]` z przełącznikiem głównym i wartościami per perk.

## 5. Decyzje autora

1. **Które z A / B / C robimy?** Rekomendacja: **A + B**, a C nie.
2. **B: nagroda za duszę.** Progres tieru czy pełna mana? Rekomendacja: progres, bo to trwała
   wartość. Mana odnawia się sama.
3. **Osobny addon do Redux.** Wspominałeś o „addonie do nowej wersji Wizardry na 1.20.1”. Czy to ma
   być osobny mod (własne przedmioty i zaklęcia, np. port treści `insanetweaks`, takich jak żywioł
   Abomination czy gear Living/Sentient), czy wystarczy integracja w `tombtweaks`? To osobny
   projekt i potrzebuje własnego specu oraz zakresu.
4. **Liczby w A**, np. Channeler −10% cooldown na poziom. Do akceptacji albo podmiany. Jeśli
   Channeler ma skracać **ładowanie**, jak sugeruje jego opis, potrzebny jest mixin
   w `CastItemUtils.calculateModifiers` i synchronizacja poziomu perka na klienta. Rekomendacja:
   v1 bez tego, czyli cooldown.
