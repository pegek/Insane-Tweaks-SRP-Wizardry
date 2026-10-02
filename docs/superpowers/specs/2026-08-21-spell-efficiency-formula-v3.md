# Formuła efektywności czaru — wersja 3 (2026-08-21)

Zastępuje [v1](2026-08-21-spell-efficiency-formula.md) i [v2](2026-08-21-spell-efficiency-formula-v2.md).

Obie poprzednie próbowały sprowadzić czar do **jednej liczby**, co wymagało kursu wymiany many na
czas — w v1 ukrytego (nasza regeneracja), w v2 jawnego (`λ`). To było postawienie problemu na
głowie. Mana nie jest walutą wymienialną na czas; jest **osobnym wskaźnikiem efektywności**.

v3 nie ma kursu wymiany, nie ma parametru `λ` i nie zależy od `manacore`. Ma dwie liczby.

---

## 1. Formuła

```
T_cyklu = (czas_ładowania + cooldown) / 20        [sekundy]

DPS     = D_bezpośrednie / T_cyklu  +  tempo_DoT × dostępność
D/mana  = (D_bezpośrednie + D_DoT_całkowite) / koszt_many
```

Dwie liczby, dwa różne pytania:

- **DPS** — „jak szybko ten czar zabija", przy założeniu, że rzucasz go w kółko.
- **D/mana** — „ile obrażeń kupujesz za jednostkę many", przy założeniu jednego rzutu, którego
  efekty dogrywają się do końca.

Obrażenia bierzemy **domyślne, bez bonusu potency** — potency to własność sprzętu, nie czaru.

### 1.1 🚨 DoT nie dzieli się przez czas cyklu

To jest jedyna nieoczywista część i pierwsza wersja tego dokumentu miała tu **błąd**, który
wyszedł dopiero na liczbach: `dart` wychodził na **41 DPS**, bo obrażenia z zatrucia dzieliłem
przez 0.5 s cyklu czaru.

Tak nie jest. Efekt trwa **swoje własne 10 sekund**, a rzucenie czaru ponownie go **odświeża, nie
sumuje**. Stąd:

```
tempo_DoT    = D_DoT_całkowite / czas_trwania_DoT      [obrażeń/s]
dostępność   = min(1, czas_trwania_DoT / T_cyklu)
```

Konsekwencja jest mocna i warta zapamiętania: **czar czysto DoT ma twardy sufit DPS równy tempu
tykania efektu, niezależnie od tego, jak szybko go rzucasz.** `ignite` nigdy nie przekroczy 1
obrażenia na sekundę, choćbyś rzucał go co pół sekundy — podpalenie się nie stackuje.

W `D/mana` DoT liczy się **w całości**, bo tam zakładamy jeden rzut dograny do końca. Przy
spamowaniu nadmiar się marnuje — i to jest właśnie ta różnica, którą dwie metryki pokazują, a
jedna by ukryła.

### 1.2 Cele obszarowe

```
D_bezpośrednie(N) = D_główny + D_poboczny × (N − 1)
DPS(N), D/mana(N) — jak wyżej, DoT nakładany na wszystkie N celów
```

Domyślnie **N = 1**: AoE nie liczy się, gdy bijesz jeden cel. Wartość obszarowa wchodzi jako
osobne pytanie: **ilu celów potrzeba, żeby czar obszarowy dogonił najlepszy jednocelowy** (§4).

---

## 2. Przykład z życia — dwa czary z pytania

| | Piorun | Toksyczny pocisk |
|---|---:|---:|
| mana | 20 | 15 |
| ładowanie | 30 t | 25 t |
| cooldown | 50 t | 40 t |
| obrażenia | 5 (AoE) | 3 + zatrucie II na 2 s |
| **T_cyklu** | **4.00 s** | **3.25 s** |
| **D (1 cel)** | 5.00 | 6.33 |
| **DPS** | 1.250 | **1.949** |
| **D/mana** | 0.250 | **0.422** |

Na jednym celu **Toksyczny pocisk wygrywa oba wskaźniki** — jest jednocześnie szybszy i tańszy.
Piorun nie broni się niczym poza obszarem.

Ile celów potrzebuje, żeby to odwrócić?

```
próg DPS:     N = 1.56  →  od 2 celów Piorun wygrywa
próg D/mana:  N = 1.69  →  od 2 celów Piorun wygrywa
```

To jest typ odpowiedzi, którego chcemy: **„Piorun opłaca się od dwóch celów"** — zdanie, na
podstawie którego da się zmienić liczbę w configu.

---

## 3. Czary EBW, jeden cel

| Czar | Tier | Mana | T [s] | D_bezp. | D_DoT | DoT/s | **DPS** | **D/mana** |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| `magic_missile` | nov | 5 | 0.25 | 3 | — | — | **12.00** | 0.600 |
| `ice_shard` | app | 10 | 0.50 | 6 | — | — | **12.00** | 0.600 |
| `firebolt` | app | 10 | 0.50 | 5 | 5 | 1.00 | **11.00** | 1.000 |
| `dart` | nov | 5 | 0.50 | 4 | 16.7 | 1.67 | **9.67** | **4.133** |
| `ice_lance` | adv | 20 | 1.25 | 10 | — | — | **8.00** | 0.500 |
| `fireball` | app | 10 | 0.75 | 5 | 5 | 1.00 | **7.67** | 1.000 |
| `force_arrow` | app | 15 | 1.00 | 7 | — | — | **7.00** | 0.467 |
| `freeze` | nov | 5 | 0.50 | 3 | — | — | **6.00** | 0.600 |
| `firebomb` (AoE) | app | 15 | 1.25 | 5 | 7 | 1.00 | **5.00** | 0.800 |
| `disintegration` | adv | 35 | 2.00 | 8 | 10 | 1.00 | **5.00** | 0.514 |
| `spark_bomb` (AoE) | app | 15 | 1.25 | 6 | — | — | **4.80** | 0.400 |
| `arc` | nov | 5 | 0.75 | 3 | — | — | **4.00** | 0.600 |
| `force_orb` (AoE) | adv | 20 | 1.00 | 4 | — | — | **4.00** | 0.200 |
| `lightning_pulse` (AoE) | adv | 25 | 3.75 | 8 | — | — | **2.13** | 0.320 |
| `plague_of_darkness` (AoE) | mst | 75 | 10.75 | 8 | 14 | 2.00 | **2.05** | 0.293 |
| `wither` | app | 10 | 1.00 | 1 | 10 | 1.00 | **2.00** | 1.100 |
| `ignite` | nov | 5 | 0.50 | 0 | 10 | 1.00 | **1.00** | 2.000 |
| `firestorm` (AoE) | mst | 80 | 13.50 | 0 | 15 | 1.00 | **1.00** | 0.188 |

---

## 4. Ile celów potrzebuje czar obszarowy

Odniesienie: najlepszy jednocelowy w każdej metryce — DPS `magic_missile` = 12.00, D/mana `dart` = 4.13.

| Czar AoE | N dla DPS | N dla many |
|---|---:|---:|
| `firebomb` | **4** | 6 |
| `spark_bomb` | **4** | 20 |
| `force_orb` | **3** | 21 |
| `lightning_pulse` | 6 | 13 |
| `plague_of_darkness` | 6 | 15 |
| `firestorm` | 12 | 23 |

Ten sam zestaw przy konkretnych scenariuszach:

| Czar | N = 1 | N = 3 | N = 6 |
|---|---|---|---|
| `firebomb` | DPS 5.00 · M 0.80 | DPS 11.80 · M 2.13 | DPS 22.00 · M 4.13 |
| `spark_bomb` | DPS 4.80 · M 0.40 | DPS 9.60 · M 0.80 | DPS 16.80 · M 1.40 |
| `force_orb` | DPS 4.00 · M 0.20 | DPS 12.00 · M 0.60 | DPS 24.00 · M 1.20 |
| `lightning_pulse` | DPS 2.13 · M 0.32 | DPS 6.40 · M 0.96 | DPS 12.80 · M 1.92 |
| `plague_of_darkness` | DPS 2.05 · M 0.29 | DPS 6.14 · M 0.88 | DPS 12.28 · M 1.76 |
| `firestorm` | DPS 1.00 · M 0.19 | DPS 3.00 · M 0.56 | DPS 6.00 · M 1.12 |

---

## 5. Co z tego wynika

### 5.1 Dwie metryki naprawdę się nie zgadzają i o to chodzi

`magic_missile` ma **najlepszy DPS** i **przeciętną** efektywność manową. `dart` jest odwrotnie:
czwarty DPS, ale **siedmiokrotnie** lepsza efektywność manowa. `ignite` to skrajność — prawie
najgorszy DPS w tabeli i drugi najlepszy stosunek do many.

To jest podział na dwa style gry, nie ranking. Sprowadzenie tego do jednej liczby, jak próbowały
v1 i v2, **zniszczyłoby właśnie tę informację**.

### 5.2 Czary DoT są tanie i wolne — z konstrukcji

`ignite`, `wither`, `dart` zajmują trzy z czterech pierwszych miejsc w efektywności manowej i trzy
z czterech ostatnich w DPS. Powód jest strukturalny, nie balansowy: **DoT ma sufit DPS równy tempu
tykania**, bo się nie stackuje. Żadne przestrojenie kosztu tego nie zmieni.

Wniosek dla normalizacji: **czarów DoT nie należy porównywać po DPS z czarami bezpośrednimi.** Ich
rolą jest tani, utrzymywany nacisk, nie burst.

### 5.3 `firestorm` jest słaby w obu metrykach i w obu scenariuszach

80 many, 13.5 s zaangażowania, obrażenia wyłącznie z podpalenia — czyli z sufitem 1 obrażenia na
sekundę na cel. Potrzebuje **12 celów**, żeby dogonić DPS czaru nowicjusza za 5 many, i **23**,
żeby dogonić go w efektywności manowej. To jedyna pozycja w zestawie, której nie broni żaden
scenariusz.

### 5.4 `force_orb` jest zaskakująco dobrym czarem grupowym

Najgorsza efektywność manowa w tabeli na jednym celu (0.200), ale **próg DPS = 3 cele** — najniższy
ze wszystkich AoE. Przy sześciu celach ma najwyższy DPS w całym zestawieniu (24.0). Jego problemem
nie jest siła, tylko to, że solo jest bezużyteczny.

### 5.5 `disintegration` nie broni tieru

35 many, advanced — DPS 5.00 i D/mana 0.514. Apprentice'owy `firebolt` za 10 many ma **ponad
dwukrotnie** wyższy DPS i dwukrotnie lepszą efektywność manową. Drugi kandydat do przeglądu po
`firestorm`.

---

## 6. 🚨 Dług kalibracyjny

Struktura formuły jest niezależna od danych i już poprawna. Trzy stałe DoT **nie są pomiarem** —
to wartości przyjęte z pamięci o wanilii, których nie potwierdziłem na bajtkodzie (w cache'u
Gradle'a nie ma zdeobfuskowanego jara Minecrafta).

| Założenie | Przyjęte | Pomiar |
|---|---|---|
| podpalenie: 1 obrażenie / s | `1.0` | podpal moba na 10 s, policz HP |
| wither: 1 obrażenie co `40 >> amp` t | — | nałóż, licz w czasie |
| zatrucie: 1 obrażenie co `25 >> amp` t | — | jw. |
| `effect_strength` z JSON-a = amplifier | wprost | sprawdź w ekwipunku, czy `dart` daje **Poison II** czy **Poison I** |

Ostatni jest najgroźniejszy: jeśli `effect_strength = 1` znaczy poziom I, zatrucie tyka dwa razy
wolniej i `D/mana` dla `dart` spada z 4.13 do ~1.9 — czyli traci pierwsze miejsce.

**Wiersze bez DoT są już wiarygodne** (`magic_missile`, `ice_shard`, `ice_lance`, `force_arrow`,
`freeze`, `arc`, `spark_bomb`, `force_orb`, `lightning_pulse`). Wiersze z DoT są wstępne. Pomiary
są w checkliście jako K1–K3.

Osobno: zatrucie **nie zabija** (zostawia cel na 1 HP), więc jego obrażenia są warte mniej niż ta
sama liczba z innego źródła. Formuła tego nie uwzględnia — świadomie, żeby zostać prostą.

## 7. Czego formuła nie obejmuje

- **Czary czysto kontrolne** (`ice_statue`, `bubble`, `mind_control`) — nie zadają obrażeń, więc
  obie metryki dają zero. Potrzebują osobnej miary, nie tej.
- **Trafienie natychmiastowe kontra pocisk do wyminięcia** — `lightning_bolt` trafia przez
  przeszkody, `fireball` można wyminąć; tu liczone tak samo.
- **Zasięg** — świadomie poza formułą, na twoją prośbę o mniej zmiennych.
- **Czary ciągłe** (`flame_ray` i spółka) — `cooldown = 0`, koszt na sekundę, interwał obrażeń w
  kodzie a nie w JSON-ie. Wymagają odczytania interwału z bajtkodu `SpellRay`.
- **Efekty poboczne poza DoT** — spowolnienie z `ice_shard`, odrzut z `thunderbolt`.
