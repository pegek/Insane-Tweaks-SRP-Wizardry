# manacore — osiągnięcia jako źródło trwałej many (spec)

Data: 2026-08-20. Gałąź `feat/manacore`.
Spec bazowy: [2026-08-18-manacore-design.md](2026-08-18-manacore-design.md) (faza 6).
Stan przekazania: [2026-08-19-manacore-handoff.md](../plans/2026-08-19-manacore-handoff.md).

## 1. Cel

Osiągnięcia (advancementy) mają trwale podnosić maksymalną manę gracza. Docelowo to główne
długoterminowe źródło wzrostu puli, obok dwóch istniejących budżetów progresji — z castowania i z
konsumowanych przedmiotów.

Punktem wyjścia jest drzewko Electroblob's Wizardry (29 widocznych osiągnięć), ale mechanizm nie
jest z nim związany: tabela przyjmuje dowolne ID osiągnięcia, więc dołożenie Ancient Spellcraft,
FTB Quests czy czegokolwiek innego to linijka w configu, nie nowy kod.

## 2. Mechanizm — modyfikator przeliczalny, NIE `grantedMax`

🚨 **To jest centralna decyzja tego speca i jest sprzeczna z pierwszą intuicją.**

`ManaAPI.addGrantedMax` wygląda na stworzone dokładnie do tego („jednorazowe nagrody, np.
achievementy" — tak brzmi jego własny javadoc). Jest inaczej: `grantedMax` to **jedna wspólna
liczba** dla nagród **jednokierunkowych**, a stan osiągnięć jest **przeliczalny i odwracalny**.
Wersja oparta na `grantedMax` psuje się na trzy sposoby:

- `/advancement revoke` odbiera osiągnięcie, ale mana zostaje — na zawsze. Wspólny bufor nie wie,
  która część sumy pochodziła z którego osiągnięcia, więc nie potrafi odjąć swojego wkładu.
- Zmiana wartości w configu nie działa wstecz. Raz zabankowane zostaje zabankowane.
- Ponowne zdobycie osiągnięcia po odebraniu podwaja bonus.

Zamiast tego bonus jest **jednym `AttributeModifier` na `manacore.maxMana`, z własnym stałym
UUID**, którego wartość jest **przeliczana z rzeczywistego stanu osiągnięć gracza**:

```
suma = Σ { kwota(id) : id ∈ tabela ∧ gracz ukończył id }
bonus = min(suma, advancements.cap)
```

Wynikające z tego własności — wszystkie za darmo, bez żadnego dodatkowego kodu:

- odebranie osiągnięcia odbiera manę,
- zmiana wartości w configu działa od następnego przeliczenia,
- podwojenie jest niemożliwe z konstrukcji, bo nic się nie akumuluje,
- **zero nowych pól w capability i zero zmiany formatu zapisu** — stan już jest na dysku, w
  danych osiągnięć gracza.

To jest dokładnie ten wzorzec, który przepisuje javadoc `ManaAPI.addGrantedMax` dla źródeł
przeliczalnych: własny modyfikator z własnym UUID, przeliczany przy każdej zmianie wejścia.

## 3. Kiedy przeliczamy

| Zdarzenie | Po co |
|---|---|
| `AdvancementEvent` | gracz właśnie zdobył osiągnięcie |
| `PlayerLoggedInEvent` | tu lądują zmiany w configu i to jedyny moment, w którym widzimy pełny stan po wczytaniu |
| `PlayerRespawnEvent` | 🚨 modyfikatory `MAX_MANA` **nie przeżywają śmierci** |
| `PlayerChangedDimensionEvent` | spójność z resztą mechanizmów odświeżania w tym modzie |

🚨 Trzy ostatnie nie są nadmiarowe. Wanilla buduje na respawnie nową encję gracza i **nie kopiuje
na nią mapy atrybutów**, więc bez `PlayerRespawnEvent` bonus znikałby po pierwszym zgonie. To ta
sama pułapka, która sprawiła, że `/mana setmax` resetowało się przed 2026-08-19 — z tą różnicą, że
tam rozwiązaniem było pole w capability, a tu przeliczenie z danych, które i tak już są trwałe.

Wszystkie cztery to zdarzenia rzadkie. Nic tu nie leci per tick.

## 4. Konfiguracja

Nowa kategoria `advancements` w `manacore.cfg` (szósty obiekt-kategoria w `ManaCoreConfig`, który
ma `category = ""`).

| Pole | Typ | Domyślnie | Znaczenie |
|---|---|---|---|
| `enabled` | boolean | `true` | Czytane **na żywo**. Wyłączone NIE oznacza „handler nie robi nic” — przeliczenie nadal leci, tylko traktuje tabelę jako pustą i daje 0, przez co modyfikator zostaje **usunięty**. Gdyby handler wychodził wcześnie na tej fladze, wyłączenie zostawiałoby ostatnią wartość zamrożoną na graczu aż do śmierci. |
| `bonuses` | String[] | tabela z §5 | Wpisy `namespace:sciezka=ilosc`. |
| `cap` | double | `200.0` | Sufit sumy ze wszystkich osiągnięć. |
| `announce` | boolean | `true` | Komunikat na pasku akcji przy przyznaniu. |

Żadne z pól nie potrzebuje `@Config.RequiresMcRestart` — nie ma tu mixinu ani rejestracji
zależnej od flagi, a `enabled` obsługuje się samo przez przeliczenie do zera.

### Reguły parsowania

- Podział na **ostatnim** znaku `=`. ID zawiera dwukropek, nie znak równości, ale wartość może
  mieć minus czy kropkę — dzielenie od końca jest jednoznaczne.
- Lewa strona musi zawierać `:`. Bez namespace'u wpis jest odrzucany, a nie domyślnie
  uzupełniany o `minecraft:` — cicha zmiana znaczenia wpisu byłaby gorsza niż ostrzeżenie.
- Prawa strona musi być skończoną liczbą. `NaN`, nieskończoności i wartości niedające się
  sparsować są odrzucane. **Ujemne są dozwolone** — osiągnięcie odbierające manę to sensowna
  mechanika (klątwa, zła ścieżka), a nic nie kosztuje jej dopuszczenie; suma i tak jest ścinana
  do zera od dołu.
- Duplikat ID: wygrywa **ostatni** wpis, żeby dopisanie linijki na końcu listy nadpisywało
  wcześniejszą, zamiast być po cichu ignorowane.
- Wpis odrzucony trafia do logu jako `WARN` z podaniem treści wpisu i powodu. Reszta listy działa
  dalej — jeden literówkowy wpis nie może wyłączyć całej tabeli.

### Parsujemy przy każdym przeliczeniu, bez cache'u

Kuszące jest sparsowanie tabeli raz i trzymanie jej w polu statycznym. **Nie robimy tego.**
Przeliczenia zdarzają się cztery razy na sesję plus raz na osiągnięcie, a szesnaście operacji
`String.split` przy takiej częstotliwości jest niemierzalne. W zamian znika cały problem
unieważniania cache'u przy `OnConfigChangedEvent` — czyli klasa błędów, w której gracz zmienia
config, nic się nie dzieje i nikt nie wie dlaczego.

## 5. Tabela domyślna

Szesnaście wpisów, wszystkie z EBW, razem **160** przy sufcie **200** — zapas jest po to, żeby
paczka mogła dopisać własne pozycje bez przebudowy jara.

| ID (`ebwizardry:`) | Ramka | + | Za co |
|---|---|---|---|
| `crystal` | task | 5 | pierwszy magiczny kryształ |
| `arcane_initiate` | task | 5 | pierwsza różdżka |
| `apprentice` | task | 10 | ulepszenie różdżki tomem |
| `advanced` | task | 10 | różdżka tieru advanced |
| `master` | task | 20 | różdżka mistrzowska |
| `armour_set` | task | 10 | pełny komplet zbroi maga |
| `special_upgrade` | task | 5 | pierwszy specjalny upgrade |
| `defeat_evil_wizard` | task | 5 | pokonany zły mag |
| `visit_shrine` | task | 5 | przebudzona świątynia |
| `artefact` | task | 10 | pierwszy artefakt |
| `defeat_remnant` | task | 5 | pokonany remnant |
| `restore_imbuement_altar` | task | 10 | odbudowany ołtarz |
| `max_out_wand` | goal | 15 | maksymalnie ulepszona różdżka mistrzowska |
| `discover_master_spell` | challenge | 10 | identyfikacja czaru mistrzowskiego przez próby |
| `all_artefacts` | challenge | 15 | wszystkie pierścienie, amulety i charmy |
| `all_spells` | challenge | 20 | każdy czar w grze |

Krzywa idzie za drzewkiem EBW: `task` 5–10, `goal` 15, `challenge` 10–20 za rzeczy naprawdę długie.
Liczby są punktem startowym do wyważenia w rozgrywce — po to są w configu.

**Świadomie pominięte:** jedenaście osiągnięć `ebwizardry:handbook/*`. Nie mają sekcji `display`,
są wewnętrznymi znacznikami postępu w podręczniku — mana za nie byłaby niewidoczna i myląca.
Pominięty też `root` (samo posiadanie moda) i drobne pozycje fabularne (`anger_wizard`,
`wizard_trade`, `spell_failure`, `enchant_scroll`, `craft_lectern`).

## 6. Komponenty

| Plik | Rola | Typy MC? |
|---|---|---|
| `core/AdvancementBonusTable.java` | parsowanie `String[]` → `Map<String, Double>`, ścinanie sumy do sufitu | **nie** — testowalne JUnitem |
| `handler/AdvancementManaHandler.java` | cztery subskrypcje, odczyt stanu osiągnięć, nałożenie modyfikatora, komunikat | tak |
| `config/categories/AdvancementCategory.java` | cztery pola z §4 | nie |
| `config/ManaCoreConfig.java` | + pole `advancements` | nie |
| `assets/manacore/lang/en_us.lang` | + klucz komunikatu | — |

`AdvancementBonusTable` trzyma się konwencji pakietu `core`: zero typów Minecrafta, klucze jako
zwykłe `String`, konwersja na `ResourceLocation` dopiero w handlerze. To ten sam podział, który w
`commandsuggest` pozwolił przetestować 72 przypadki na czystej JVM.

Handler jest **samodzielny** — ma własne subskrypcje login/respawn/wymiar, zamiast doczepiać się
do `ManaCapabilityHandler.refreshAndSync`. Dokłada to trzy adnotacje, ale trzyma logikę osiągnięć
z dala od warstwy capability, która nic o nich nie wie i nie powinna wiedzieć. Modyfikatory mają
osobne UUID, więc kolejność wykonania nie ma znaczenia.

### Odczyt stanu — API zweryfikowane kompilacją (2026-08-20)

```java
Advancement adv = server.getAdvancementManager().getAdvancement(new ResourceLocation(id));
AdvancementProgress progress = player.getAdvancements().getProgress(adv);
boolean done = progress != null && progress.isDone();
```

`server` z `player.getServer()`. Cała ścieżka jest server-only; handler wychodzi wcześnie dla
czegokolwiek, co nie jest `EntityPlayerMP`.

## 7. Przypadki brzegowe

| Sytuacja | Zachowanie |
|---|---|
| ID nieznane serwerowi (mod usunięty, literówka) | `getAdvancement` zwraca `null` → wpis pomijany, `DEBUG` do logu. **Nie** `WARN`: paczka może celowo mieć wpisy dla modów opcjonalnych, a ostrzeżenie na każdy login byłoby szumem. |
| Suma powyżej sufitu | ścięta do `cap`; komunikat pokazuje **rzeczywisty** przyrost, nie wartość z tabeli |
| Suma ujemna | ścięta do zera |
| Suma równa zero | modyfikator **usuwany**, nie nakładany jako zerowy — spójnie z `applyMaxModifier` |
| `enabled = false` | przeliczenie daje zero → modyfikator usuwany |
| Osiągnięcie odebrane | najbliższe przeliczenie obniża bonus; `current` ponad nowe maksimum konfiskuje `ManaRegenHandler` |
| Zdobycie osiągnięcia spoza tabeli | przeliczenie leci, wynik bez zmian, brak komunikatu |

## 8. Testy

`AdvancementBonusTableTest` (JUnit 4.12, czysta JVM):

- wpis poprawny, z białymi znakami wokół
- brak `=`, brak `:`, pusty ID, pusta wartość
- wartość nieliczbowa, `NaN`, `Infinity`
- wartość ujemna (przyjmowana)
- duplikat ID — wygrywa ostatni
- pusta tablica, `null` w tablicy
- podział na ostatnim `=` przy wartości ujemnej
- `clampTotal`: poniżej sufitu, powyżej, ujemna suma, sufit zerowy

Reszta jest nierozłączna z Minecraftem i weryfikuje się w grze — patrz §9.

## 9. Kryteria akceptacji

1. Zdobycie `ebwizardry:crystal` podnosi maksimum o 5 i pokazuje komunikat.
2. Wartość utrzymuje się po **wylogowaniu i zalogowaniu**.
3. Wartość utrzymuje się po **śmierci** — to jest test, który wyłapałby powtórzenie błędu
   `setmax`.
4. `/advancement revoke <gracz> only ebwizardry:crystal` **obniża** maksimum o 5.
5. Zmiana liczby w configu i relog zmienia bonus — bez „zabankowanej" starej wartości.
6. Wpis z literówką nie psuje pozostałych; w logu jest `WARN` z jego treścią.
7. `cleanmix.log` bez zmian — ten etap nie dodaje żadnego mixinu.

## 10. Poza zakresem

- **Poziom drzewka Reskillable.** Należy do `reskilltweaks`, który ma już tę zależność; wywoła
  `ManaAPI.addMaxModifier` z własnym UUID. Osobne, mniejsze zadanie.
- **Hooki na noszony ekwipunek** (`bonusMana`) — artefakty, quality, enchanty.
- **Bilans „mana crystal" z Trinkets and Baubles** i znalezienie roli dla magic crystal.
- **Cudze efekty many**: ASC `PotionManaRegeneration`, czary EBW z rodziny mana font, trait
  `meditation` z `reskilltweaks` (regeneracja przy bezruchu) — dziś prawdopodobnie nie działają z
  naszą pulą. Osobny etap.
- **Mana exhaustion** — odłożone świadomie do przemyślenia mechaniki.
