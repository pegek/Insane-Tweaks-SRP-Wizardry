# Formuła efektywności czaru — wersja 1 (2026-08-21)

> **ZASTĄPIONA przez [wersję 3](2026-08-21-spell-efficiency-formula-v3.md).**
> v1 i v2 sprowadzały czar do jednej liczby, co wymagało kursu wymiany many na czas. To było
> postawienie problemu na głowie: mana jest osobnym wskaźnikiem, nie walutą wymienialną na czas.
> v3 daje dwie liczby (DPS i D/mana) i nie ma żadnego kursu.

> **ZASTĄPIONA przez [wersję 2](2026-08-21-spell-efficiency-formula-v2.md)** (ten sam dzień).
> v1 przeliczała czas na manę po regeneracji gracza z `manacore`, więc była związana z naszym
> modem; v2 wystawia ten kurs jako jawny parametr `λ`. v1 nie modelowała też obrażeń obszarowych,
> w czasie ani skalowania z potency. Zostawiona dla historii rozumowania o zasięgu (`sqrt(zasięg/16)`),
> który w v2 świadomie wypadł.

Cel: policzalna miara „ile czar daje za to, co zabiera", żeby normalizacja kosztów między modami
(faza 4) opierała się na liczbach, a nie na wyczuciu. Dane wejściowe pochodzą z plików
`assets/ebwizardry/spells/*.json` z EBW 4.3.19.

## 1. Zakres — jedna kategoria, celowo

EBW ma **70 czarów ofensywnych** (`type` = `attack` lub `projectile`). Ta wersja obejmuje z nich
**piętnaście: pociski i uderzenia jednocelowe z bezpośrednimi obrażeniami.**

To ograniczenie jest warunkiem sensowności, nie oszczędnością. Czar obszarowy o `damage=8` i
`effect_radius=5` zadaje 8 obrażeń *na cel*, więc jego wartość zależy od liczby trafionych mobów —
zmiennej, której w JSON-ie nie ma. Wrzucenie `plague_of_darkness` do jednej tabeli z
`magic_missile` dałoby liczbę wyglądającą na porównanie i nią niebędącą.

**Promienie ciągłe** (`flame_ray`, `frost_ray`, `lightning_ray`, `life_drain`) też są poza tabelą,
z innego powodu: mają `cooldown = 0` i koszt liczony na sekundę, więc ich mianownik znaczy co
innego. Do tego `damage=3` przy promieniu to obrażenia **na trafienie**, a interwał trafień jest w
kodzie, nie w JSON-ie — dopóki nie zostanie odczytany z bajtkodu, każde porównanie promieni z
pociskami byłoby zgadywaniem. To pierwsza rzecz do dołożenia w wersji 2.

## 2. Formuła

Trzy wielkości, budowane po kolei.

### 2.1 Jakość zasięgu

```
R = sqrt(zasieg / 16)
```

Zasięg wchodzi **pierwiastkiem**, nie liniowo, bo ma malejącą użyteczność krańcową: skok z 8 na 18
bloków zmienia to, w co gracz w ogóle może trafić, a skok z 30 na 40 jest marginalny — na obu
dystansach cel i tak jest poza swoim zasięgiem. Odniesienie 16 bloków ≈ zasięg walki, więc `R = 1`
znaczy „normalny dystans".

### 2.2 Wartość czaru

```
Value = obrazenia × R
```

### 2.3 Koszt całkowity — kurs wymiany czasu na manę

To jest jedyne miejsce, gdzie formuła podejmuje decyzję, a nie mierzy:

```
T        = (chargeup + cooldown) / 20        [sekundy]
ManaEq   = koszt_many + T × regen
E        = Value / ManaEq
```

**`regen` to nasza własna regeneracja many na sekundę** (`regen.amountPerCycle / cycleSeconds`,
domyślnie 1.0). To nie jest liczba wzięta z sufitu — to naturalny kurs wymiany: sekunda spędzona
na cooldownie kosztuje gracza dokładnie tyle many, ile by w tym czasie odzyskał. Dzięki temu
licznik i mianownik są w jednej jednostce i `E` znaczy **obrażenia na jednostkę many-równoważnej**.

Konsekwencja, którą warto zrozumieć: **zmiana `regen` w configu przesuwa cały ranking.** Przy
szybkiej regeneracji mana tanieje i wygrywają czary drogie, ale szybkie; przy wolnej wygrywają
tanie. To nie jest wada formuły — to jest realna zależność, którą formuła ujawnia.

## 3. Wyniki (regen = 1.0 many/s, odniesienie zasięgu = 16)

| Czar | Tier | Koszt | Ładow. | CD [s] | Obr. | Zasięg | R | Value | Obr./many | Obr./s | **E** |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `dart` | novice | 5 | 0 | 0.50 | 4 | 15 | 0.97 | 3.87 | 0.80 | 8.00 | **0.704** |
| `homing_spark` | apprentice | 10 | 0 | 1.00 | 6 | 25 | 1.25 | 7.50 | 0.60 | 6.00 | **0.682** |
| `magic_missile` | novice | 5 | 0 | 0.25 | 3 | 18 | 1.06 | 3.18 | 0.60 | 12.00 | **0.606** |
| `lightning_disc` | advanced | 25 | 5 | 3.25 | 12 | 30 | 1.37 | 16.43 | 0.48 | 3.69 | **0.582** |
| `ice_shard` | apprentice | 10 | 0 | 0.50 | 6 | 15 | 0.97 | 5.81 | 0.60 | 12.00 | **0.553** |
| `lightning_arrow` | apprentice | 15 | 0 | 1.00 | 7 | 25 | 1.25 | 8.75 | 0.47 | 7.00 | **0.547** |
| `darkness_orb` | advanced | 20 | 0 | 1.00 | 8 | 30 | 1.37 | 10.95 | 0.40 | 8.00 | **0.522** |
| `fireball` | apprentice | 10 | 0 | 0.75 | 5 | 20 | 1.12 | 5.59 | 0.50 | 6.67 | **0.520** |
| `force_arrow` | apprentice | 15 | 0 | 1.00 | 7 | 20 | 1.12 | 7.83 | 0.47 | 7.00 | **0.489** |
| `firebolt` | apprentice | 10 | 0 | 0.50 | 5 | 15 | 0.97 | 4.84 | 0.50 | 10.00 | **0.461** |
| `ice_lance` | advanced | 20 | 5 | 1.25 | 10 | 15 | 0.97 | 9.68 | 0.50 | 8.00 | **0.456** |
| `freeze` | novice | 5 | 0 | 0.50 | 3 | 10 | 0.79 | 2.37 | 0.60 | 6.00 | **0.431** |
| `arc` | novice | 5 | 0 | 0.75 | 3 | 8 | 0.71 | 2.12 | 0.60 | 4.00 | **0.369** |
| `thunderbolt` | novice | 10 | 0 | 0.75 | 3 | 12 | 0.87 | 2.60 | 0.30 | 4.00 | **0.242** |
| `lightning_bolt` | advanced | 40 | 10 | 4.50 | 5 | 40 | 1.58 | 7.91 | 0.12 | 1.11 | **0.178** |

## 4. Co z tego wynika

### 4.1 Efektywność SPADA z tierem — i to jest w EBW zamierzone

Novice `dart` (0.704) bije advanced `lightning_bolt` (0.178) czterokrotnie. W waniliowym EBW to
działa, bo różdżka niższego tieru **fizycznie nie zmieści** drogiego czaru — pojemność jest bramką
dostępu, a nie tylko zasobem.

🚨 **Ta bramka u nas przetrwała, ale ledwo.** `MixinItemWand` porównuje koszt z
`getManaCapacity(stack)`, więc różdżka nowicjusza nadal nie rzuci czaru za 40. Natomiast *w
obrębie* tego, co różdżka może rzucić, gracz płaci z jednej wspólnej puli — więc surowa
efektywność liczy się teraz bardziej niż w wanilii, gdzie każda różdżka miała własny zbiornik.
Wniosek dla fazy 4: **normalizacja nie powinna wyrównywać `E` między tierami**, bo spadek jest
celowy; powinna wyrównywać `E` **wewnątrz** tieru i między modami.

### 4.2 Trzy pozycje są wyraźnie poza krzywą

- **`thunderbolt`** (0.242) jest zdominowany: te same 3 obrażenia co `magic_missile` i `arc`, przy
  **dwukrotnym** koszcie i gorszym cooldownie. Nie ma parametru, którym by się bronił.
- **`lightning_bolt`** (0.178) jest najgorszy w zestawie mimo tieru advanced — 40 many i 4.5 s
  zaangażowania za 5 obrażeń.
- **`dart`** (0.704) jest najlepszy w całej tabeli będąc czarem nowicjusza.

### 4.3 Czego formuła NIE widzi

Zanim ktokolwiek zacznie na jej podstawie zmieniać liczby w configu:

- **Trafienie natychmiastowe.** `lightning_bolt` trafia od razu i przez przeszkody; `fireball` to
  pocisk, który można wyminąć. Formuła traktuje je tak samo, co silnie zaniża `lightning_bolt` i
  jest najprawdopodobniej głównym powodem jego ostatniego miejsca.
- **Efekty statusowe.** `freeze` daje spowolnienie, `dart` zatrucie, `ice_shard` spowolnienie —
  wszystkie liczone tu jako czyste obrażenia.
- **Naprowadzanie.** `homing_spark` i `lightning_disc` mają `seeking_strength`, czyli realnie
  wyższą szansę trafienia.
- **Wielocelowość.** Poza zakresem z definicji (patrz §1).
- **Mnożniki dla nieumarłych, przebicie zbroi, podpalenie.** `burn_duration` to realne dodatkowe
  obrażenia w czasie, tu pominięte.

Innymi słowy: **`E` jest dolnym oszacowaniem** dla czarów z dodatkowymi własnościami, a dokładne
tylko dla czystych pocisków obrażeniowych. Czar wyraźnie poniżej krzywej wart jest sprawdzenia;
czar powyżej niekoniecznie jest przesadzony.

## 5. Wersja 2 — co dołożyć

1. **Promienie ciągłe** — wymaga odczytania interwału obrażeń z bajtkodu `SpellRay`, nie z JSON-a.
2. **Mnożnik trafienia** — parametr `H` (hitscan 1.0, naprowadzany 0.9, zwykły pocisk 0.75),
   wchodzący do `Value`. Wartości do skalibrowania w rozgrywce, nie do wymyślenia.
3. **Wycena efektów statusowych** — `burn_duration` przeliczalne na obrażenia; spowolnienie i
   zatrucie wymagają decyzji balansowej.
4. **Czary obszarowe** — z jawnym założeniem „ile celów", np. 1 / 3 / 6, jako trzy kolumny zamiast
   jednej liczby udającej pewność.
5. **Ancient Spellcraft i Trinkets and Baubles** — dopiero po tym, bo to one są właściwym celem
   normalizacji.

## 6. Odtworzenie

Dane wejściowe: `assets/ebwizardry/spells/*.json` w jarze EBW (`tier`, `element`, `type`, `cost`,
`chargeup`, `cooldown`, `base_properties.damage`, `base_properties.range`). Filtr:
`type in (attack, projectile)` i obecność `damage` oraz `range`, ręcznie zawężone do celów
pojedynczych. Cała formuła to trzy linijki z §2 — nie ma tu nic, czego nie da się przeliczyć w
arkuszu.
