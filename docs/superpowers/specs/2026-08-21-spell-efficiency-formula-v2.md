# Formuła efektywności czaru — wersja 2 (2026-08-21)

Zastępuje [wersję 1](2026-08-21-spell-efficiency-formula.md). Trzy zmiany wobec niej:

1. **Niezależna od `manacore`.** v1 przeliczała czas na manę po naszej regeneracji gracza. To
   wiązało miarę z naszym modem — w waniliowym układzie mana siedzi na narzędziu i takiego kursu
   po prostu nie ma. Kurs jest teraz **jawnym parametrem `λ`**, a nie ukrytym założeniem.
2. **Mniej zmiennych.** Zasięg wypadł. Zostają cztery, o które chodziło: obrażenia, cooldown,
   czas ładowania, koszt many.
3. **Bogatszy model obrażeń.** Obrażenia obszarowe, obrażenia w czasie i skalowanie z potency są
   teraz częścią licznika, więc do tabeli wchodzą czary, których v1 nie umiała porównać.

---

## 1. Formuła

### 1.1 Licznik — obrażenia efektywne

```
D_eff = (D_bezposrednie + D_dot) × N × P
```

| Człon | Znaczenie |
|---|---|
| `D_bezposrednie` | `damage`, `direct_damage` lub `primary_damage` z JSON-a, plus rozprysk na cele poboczne |
| `D_dot` | obrażenia w czasie przeliczone na punkty — podpalenie, wither, zatrucie |
| `N` | **założona** liczba trafionych celów; dla jednocelowych bez znaczenia |
| `P` | `1 + k·s`, gdzie `s = 1` jeśli czar skaluje się z potency |

**`P` wycenia opcjonalność, nie moc.** Przy potency = 1.0 czar skalujący się i nieskalujący zadają
tyle samo — różnica polega na tym, że w pierwszy da się zainwestować pierścieniami i zbroją, a w
drugi nie. `k` to zakładany typowy bonus potency w paczce (domyślnie **0.30**). Czary czysto
podpaleniowe (`ignite`, `firestorm`) mają `s = 0`, bo potency skaluje obrażenia, a nie długość
płonięcia.

Przeliczenia DoT:

```
podpalenie:  D = czas_plonięcia[s] × r_fire × d_dot
wither:      D = czas[t] / (40 >> amp) × d_dot
zatrucie:    D = czas[t] / (25 >> amp) × d_dot × d_poison
```

`d_dot = 0.6` — obrażenia rozłożone w czasie są warte mniej niż natychmiastowe: cel ma czas
zareagować, uciec albo zostać dobity przez co innego. `d_poison = 0.8` dodatkowo, bo zatrucie
**nie zabija**.

### 1.2 Mianownik — koszt z jawnym kursem wymiany

```
T = (czas_ładowania + cooldown) / 20        [sekundy]
C = koszt_many + λ × T
E = D_eff / C
```

🚨 **`λ` (mana na sekundę) jest parametrem, nie stałą — i to jest sedno tej wersji.** W układzie,
gdzie mana siedzi na narzędziu, nie istnieje kanoniczny kurs wymiany czasu na manę, bo oba zasoby
uzupełnia się **inaczej**: manę przy Arcane Workbench albo kryształem, a cooldown wyłącznie
czekaniem. Wymyślenie jednej liczby udawałoby wiedzę, której nie ma.

Zamiast tego liczymy przy trzech wartościach, a `λ` czytamy jako **rodzaj niedoboru**:

| `λ` | Znaczy | Kiedy tak jest |
|---|---|---|
| `0` | mana jest wszystkim, czas nic nie kosztuje | głęboko w lochu, bez kryształów, bez dostępu do stołu |
| `2` | jedno i drugie się liczy | zwykła gra |
| `10` | czas jest wszystkim, many pod dostatkiem | walka z bossem, różdżka z condenserem, stół w pobliżu |

Czar słaby przy **każdym** `λ` jest słaby naprawdę. Czar, który wygrywa tylko przy jednym końcu
skali, jest wyspecjalizowany — i to jest informacja, a nie wada.

---

## 2. Wyniki — 16 czarów EBW

Stałe: `r_fire = 1.0`, `d_dot = 0.6`, `d_poison = 0.8`, `k = 0.30`.

### 2.1 Jeden trafiony cel

| Czar | Tier | Koszt | T [s] | D_eff | E(λ=0) | E(λ=2) | E(λ=10) | D/s |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| `dart` | nov | 5 | 0.50 | 15.60 | 3.120 | **2.600** | 1.560 | 31.20 |
| `ignite` | nov | 5 | 0.50 | 6.00 | 1.200 | **1.000** | 0.600 | 12.00 |
| `poison` | app | 10 | 1.00 | 11.70 | 1.170 | **0.975** | 0.585 | 11.70 |
| `firebolt` | app | 10 | 0.50 | 10.40 | 1.040 | **0.945** | 0.693 | 20.80 |
| `wither` | app | 10 | 1.00 | 9.10 | 0.910 | **0.758** | 0.455 | 9.10 |
| `magic_missile` | nov | 5 | 0.25 | 3.90 | 0.780 | **0.709** | 0.520 | 15.60 |
| `ice_shard` | app | 10 | 0.50 | 7.80 | 0.780 | **0.709** | 0.520 | 15.60 |
| `firebomb` | app | 15 | 1.25 | 11.96 | 0.797 | **0.683** | 0.435 | 9.57 |
| `arc` | nov | 5 | 0.75 | 3.90 | 0.780 | **0.600** | 0.312 | 5.20 |
| `ice_lance` | adv | 20 | 1.25 | 13.00 | 0.650 | **0.578** | 0.400 | 10.40 |
| `force_arrow` | app | 15 | 1.00 | 9.10 | 0.607 | **0.535** | 0.364 | 9.10 |
| `disintegration` | adv | 35 | 2.00 | 18.20 | 0.520 | **0.467** | 0.331 | 9.10 |
| `spark_bomb` | app | 15 | 1.25 | 7.80 | 0.520 | **0.446** | 0.284 | 6.24 |
| `force_orb` | adv | 20 | 1.00 | 5.20 | 0.260 | **0.236** | 0.173 | 5.20 |
| `plague_of_darkness` | mst | 75 | 10.75 | 21.32 | 0.284 | **0.221** | 0.117 | 1.98 |
| `firestorm` | mst | 80 | 13.50 | 9.00 | 0.113 | **0.084** | 0.042 | 0.67 |

### 2.2 Cztery trafione cele

Zmienia się tylko dla czarów obszarowych — jednocelowe stoją w miejscu, co jest wbudowanym testem
poprawności modelu.

| Czar | D_eff | E(λ=2) | Zmiana wobec N=1 |
|---|---:|---:|---|
| `dart` | 15.60 | 2.600 | — |
| `firebomb` | 40.04 | **2.288** | ↑ z 0.683, **+235%** |
| `spark_bomb` | 19.50 | **1.114** | ↑ z 0.446, +150% |
| `ignite` | 6.00 | 1.000 | — |
| `poison` | 11.70 | 0.975 | — |
| `force_orb` | 20.80 | **0.945** | ↑ z 0.236, **+300%** |
| `firebolt` | 10.40 | 0.945 | — |
| `plague_of_darkness` | 85.28 | **0.884** | ↑ z 0.221, **+300%** |
| `wither` | 9.10 | 0.758 | — |
| `magic_missile` | 3.90 | 0.709 | — |
| `ice_shard` | 7.80 | 0.709 | — |
| `arc` | 3.90 | 0.600 | — |
| `ice_lance` | 13.00 | 0.578 | — |
| `force_arrow` | 9.10 | 0.535 | — |
| `disintegration` | 18.20 | 0.467 | — |
| `firestorm` | 36.00 | **0.336** | ↑ z 0.084, +300% |

---

## 3. Co z tego wynika

### 3.1 Model zachowuje się poprawnie na czarach obszarowych

`force_orb` jest fatalny solo (0.236) i przyzwoity w grupie (0.945). `plague_of_darkness` skacze
z 0.221 na 0.884. Dokładnie tak powinien wyglądać czar obszarowy — i to jest test, że licznik nie
jest wymyślony: gdyby model był popsuty, AoE wygrywałoby albo przegrywało w obu scenariuszach.

### 3.2 `firestorm` jest słaby przy każdym `λ` i przy obu `N`

Ostatni albo przedostatni w każdej kolumnie. 80 many i **13.5 sekundy** zaangażowania za obrażenia
wyłącznie z podpalenia, które w dodatku nie skalują się z potency. To pierwszy kandydat do
poprawki i jedyny czar w zestawie, którego nic nie broni.

### 3.3 `λ` rozdziela czary o długim i krótkim cooldownie

`arc` spada z 0.780 przy `λ=0` do 0.312 przy `λ=10` — jego 15-tickowy cooldown boli dopiero, gdy
czas jest drogi. `magic_missile` przy tej samej cenie i obrażeniach trzyma się stabilnie (0.780 →
0.520), bo jego cooldown to 5 ticków. **Te dwa czary są nierozróżnialne po samych obrażeniach i
koszcie; różnicę widać dopiero przez `λ`.** To jest cały powód, dla którego czas w ogóle wszedł do
mianownika.

### 3.4 `disintegration` jest podejrzany

35 many, tier advanced — a wypada niżej niż apprentice'owy `firebolt` (10 many) przy każdym `λ`.
Do sprawdzenia w drugiej kolejności po `firestorm`.

### 3.5 `dart` wygrywa wszystko i to jest ostrzeżenie, nie wniosek

Novice'owy czar za 5 many na szczycie każdej kolumny, z `D_eff = 15.6`. Prawie cała ta wartość
pochodzi z przeliczenia zatrucia — czyli z **najmniej pewnej części formuły** (§4). Zanim ktoś
zetnie `dart`, trzeba skalibrować stałe DoT. To samo dotyczy `ignite`, `poison` i `wither`, które
też siedzą wysoko wyłącznie dzięki DoT.

---

## 4. 🚨 Dług kalibracyjny — trzy założenia, których NIE zweryfikowałem

Struktura formuły jest solidna. Trzy stałe **nie są pomiarem** — to wartości przyjęte z pamięci o
wanilii, których nie udało się potwierdzić na bajtkodzie (w cache'u Gradle'a nie ma zdeobfuskowanego
jara Minecrafta, a przeszukanie przekroczyło limit czasu).

| Założenie | Przyjęte | Jak sprawdzić |
|---|---|---|
| Podpalenie zadaje 1 obrażenie na sekundę | `r_fire = 1.0` | podpal moba na 10 s, policz utracone HP |
| `wither` tyka co `40 >> amp` ticków | — | nałóż efekt, licz w czasie |
| `zatrucie` tyka co `25 >> amp` ticków | — | jw. |
| `effect_strength` w JSON-ie = amplifier | zakładam wprost | porównaj tooltip efektu w grze z wartością w JSON-ie |

Ostatnia pozycja jest najgroźniejsza: jeśli `effect_strength = 1` znaczy **poziom I** (amplifier 0),
a nie amplifier 1, to zatrucie tyka co 25 ticków zamiast co 12 i `D_eff` dla `dart` spada z 15.6 do
około 10.2. Cała czołówka tabeli jednocelowej to czary DoT, więc ta jedna liczba przestawia ranking.

**Wniosek praktyczny: wiersze bez DoT** (`magic_missile`, `ice_shard`, `force_arrow`, `ice_lance`,
`force_orb`, `arc`, `spark_bomb`) **są już wiarygodne.** Wiersze z DoT trzeba traktować jako
wstępne do czasu pomiaru.

## 5. Czego formuła nadal nie widzi

- **Trafienie natychmiastowe kontra pocisk do wyminięcia.** Największy brak, przeniesiony z v1.
- **Efekty czysto kontrolne** — spowolnienie, zamrożenie, `ice_statue`. Nie mają wyceny i dlatego
  takich czarów nie ma w tabeli; wciśnięcie ich tam dałoby liczbę udającą porównanie.
- **Kształt obszaru.** `effect_radius` 3 i 6 traktowane jednakowo przez `N`; realnie większy
  promień to wyższe `N`, ale zależność zależy od gęstości mobów.
- **Zasięg.** Wypadł świadomie, na twoją prośbę o mniej zmiennych. Wracał w v1 jako `sqrt(zasięg/16)`
  i można go dokleić do licznika bez zmiany reszty.
- **Koszt czarów ciągłych.** Ich `cooldown = 0`, a koszt jest na sekundę — mianownik znaczy co
  innego. Wymagają odczytania interwału obrażeń z bajtkodu `SpellRay`.

## 6. Odtworzenie

Wejście: `assets/ebwizardry/spells/*.json` (`cost`, `chargeup`, `cooldown`, `base_properties`).
Model obrażeń jest jawny per czar, bo nazwy pól się różnią (`damage` / `direct_damage` /
`primary_damage` / `splash_damage` / `burn_duration` / `effect_duration` + `effect_strength`) —
patrz skrypt w historii commita. Cała matematyka to §1; da się to przeliczyć w arkuszu.
