# manacore — checklista testów w grze (2026-08-21)

Wersje do przetestowania: **`manacore-0.8.0`**, **`reskilltweaks-1.1.0`** (obie już w instancji).

Poniższe weszło **bez ani jednego uruchomienia gry**. Kolejność jest od rzeczy, których awaria jest
najtrudniejsza do zauważenia później, do tych, które widać od razu.

---

## A. Regresje — te muszą przejść, zanim cokolwiek innego ma znaczenie

Trzy rzeczy, które już raz działały. Jeśli któraś padła, zrobiła to przez zmiany z tej sesji.

- [ ] **A1. Heal przy pełnym HP nie zabiera many.** Pełne HP, rzuć `heal`. Czar się nie odpala,
      pasek many **nie może drgnąć**. To jest powód powstania całego projektu i jedyny test, który
      pilnuje reguły „płacimy dopiero po udanym caście".
- [ ] **A2. Zwykły czar zabiera manę z puli, nie z różdżki.** Rzuć `magic_missile`. Pula spada o 5,
      mana różdżki w NBT (F3+H) **nietknięta**.
- [ ] **A3. Czar ciągły nadal pobiera manę.** Kanałuj `flame_ray` przez ~3 sekundy — pula ma
      spadać skokowo (co pół sekundy), nie płynnie i nie zero. To działało w 0.4.0; priorytet
      `LOWEST` dotknął tej ścieżki.

## B. Osiągnięcia → trwała maksymalna mana

Najgroźniejszy wariant awarii jest cichy: bonus nalicza się, a potem znika po śmierci.

- [ ] **B1. Przyznanie.** `/advancement grant @s only ebwizardry:crystal` → maksimum +5, komunikat
      na pasku akcji „+5 max mana".
- [ ] **B2. Przeżycie relogu.** Wyjdź i wejdź. Maksimum bez zmian.
- [ ] **B3. 🚨 Przeżycie śmierci.** `/kill`, respawn. **Maksimum bez zmian.** To jest test na
      powtórzenie błędu `setmax` — modyfikatory atrybutów nie przeżywają śmierci i tylko
      przeliczenie na `PlayerRespawnEvent` je odtwarza.
- [ ] **B4. 🚨 Odebranie odbiera manę.** `/advancement revoke @s only ebwizardry:crystal` →
      maksimum **spada** o 5. To odróżnia przeliczanie od bankowania; wersja z `grantedMax`
      zostawiłaby manę na zawsze.
- [ ] **B5. Osiągnięcie ASC.** `/advancement grant @s only ancientspellcraft:relics` → +5.
      Sprawdza, czy dopisane ręcznie wpisy w configu w ogóle się wczytały.
- [ ] **B6. Wpis spoza tabeli milczy.** Zdobądź dowolne osiągnięcie waniliowe → brak komunikatu,
      maksimum bez zmian.
- [ ] **B7. Sufit ścina po cichu.** `/mana setmax 2000`, potem przyznaj osiągnięcie. Przy
      `pool.hardCap = 2000` komunikat **nie powinien** się pojawić (przyrost = 0). Jeśli się
      pojawi, poprawka „uczciwego komunikatu" nie zadziałała.

## C. Efekty many z innych modów

- [ ] **C1. Potion ASC.** Podaj sobie `ancientspellcraft:mana_regeneration` → pula rośnie o ~2/s
      **ponad** normalny regen. Poziom II ma dawać ~4/s.
- [ ] **C2. Meditation.** Odblokuj trait, stań nieruchomo na >1 s → pula rośnie o 2/s. W
      `latest.log` przy logowaniu ma być `[ReskillTweaks] ManaCore found`.
- [ ] **C3. Meditation bez ManaCore** (opcjonalnie, tylko jeśli będziesz kiedyś rozdzielał mody) —
      ładuje manę zbroi jak dawniej. Fallback ma działać, bo `reskilltweaks` musi zostać
      shippowalny sam.

## D. Artefakty EBW przepięte na pulę

- [ ] **D1. `ring_condensing`** noszony → +0.5 many/s do puli.
- [ ] **D2. `amulet_arcane_defence`** noszony → +0.5 many/s. Oba naraz → +1.0/s (wkłady się
      **sumują**, w odróżnieniu od dwóch rąk przy upgrade'ach).
- [ ] **D3. `ring_siphoning` + `siphon_upgrade`** → zabij moba. Zysk ma być ×1.3 względem samego
      upgrade'u (przy domyślnych: 5 → 6.5 na poziom).

## E. Artefakty ASC obniżające koszt — test priorytetu `LOWEST`

To jedyny test na zmianę, która usuwa zależność od kolejności ładowania modów.

- [ ] **E1.** Zapisz koszt `force_orb` bez artefaktów (ma zejść 20 many).
- [ ] **E2.** Załóż `ancientspellcraft:amulet_mana` (−10%) → ma zejść **18**, nie 20.
- [ ] **E3.** Dołóż `charm_mana_orb` (−15%) → efekty mają się złożyć.
- [ ] **E4.** Załóż `ring_blast` (+25% kosztu) → koszt ma **wzrosnąć**. Jeśli którykolwiek z tych
      trzech nie rusza pobranej many, priorytet nie działa.

## F. Sufit i HUD

- [ ] **F1. `pool.hardCap` tnie.** Ustaw `hardCap = 150`, zaloguj się z maksimum > 150 → pasek
      pokazuje 150.
- [ ] **F2. Podniesienie sufitu przywraca.** Wróć na 2000 → maksimum wraca do pełnej wartości.
      Progresja zdobyta pod sufitem **nie zginęła** (ścinanie jest na odczycie, nie w atrybucie).
- [ ] **F3. Nadmiar jest konfiskowany.** `/mana set 200`, potem `hardCap = 150` → `current`
      schodzi do 150 w ciągu ticka.
- [ ] **F4. Tryby HUD.** `showBar`/`showNumber` w czterech kombinacjach; liczby nie mogą się
      przesuwać przy wyłączaniu paska.

## F2. Kalibracja stałych DoT — dla formuły efektywności czaru

Trzy stałe z [formuły v2](../specs/2026-08-21-spell-efficiency-formula-v2.md) są przyjęte z
pamięci, nie zmierzone. Bez nich czołówka rankingu (`dart`, `ignite`, `poison`, `wither`) jest
niepewna.

- [ ] **K1. Podpalenie.** Podpal moba na 10 s (`ignite`), policz utracone HP. Oczekiwane ~10.
- [ ] **K2. `effect_strength` = amplifier?** Rzuć `dart` (JSON: `effect_strength=1`) i sprawdź w
      ekwipunku, czy efekt to **Poison II** (amplifier 1) czy **Poison I** (amplifier 0). Jeśli I,
      przeliczenia DoT w formule są zawyżone dwukrotnie.
- [ ] **K3. Wither.** `wither` (200 t, strength 1) — policz obrażenia przez 10 s.

## G. Log — jedno spojrzenie po starcie

```bash
grep -E "manacore|ReskillTweaks|InvalidInjectionException|Scanned 0" "$HOME/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log" "$HOME/curseforge/minecraft/Instances/DEv 1.2/logs/latest.log"
```

- [ ] **G1.** Trzy `APPLY` dla `manacore` (`MixinItemWand`, `MixinMagicStats`, `MixinManaGui`).
- [ ] **G2.** Zero `InvalidInjectionException` / `Scanned 0`.
- [ ] **G3.** `[ReskillTweaks] ManaCore found` — most refleksyjny się rozwiązał.
- [ ] **G4.** Zero `Ignoring malformed advancement bonus entry` — dopisane ręcznie wpisy ASC są
      składniowo poprawne.

---

## Znane, świadome — NIE zgłaszać jako błąd

- `ring_extraction` nie daje many do puli. Świadomie pominięty: jego bramka miesza sprawdzenia
  `DamageType.FORCE` i `EntityForceOrb` i nie dała się rzetelnie odczytać z bajtkodu.
- `ring_mana_lesser`, `ring_mana_greater`, `charm_majestic_mana`, `clockwork_heart`,
  `voltaic_vessel`, `charm_hunger_casting`, `amulet_recovery` — cała rodzina „paliwo, gdy różdżka
  pusta". Czeka na decyzję projektową.
- `ebwizardry:font_of_mana` nie wpływa na manę. Tak jest w EBW — skraca cooldown, mimo nazwy.
- Książki czarów (EBW i `spellarchives`) pokazują koszt **bazowy**, bez naszych mnożników.
- Bonusy rasowe T&B nie wliczają się do maksimum.
- `tab.unitScale = 1.0` to wciąż zgadywanka.
