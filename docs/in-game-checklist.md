# EbreduxAddon 0.1.0: testy w grze

**Instalacja:** Forge 1.20.1 **47.3.19 lub nowszy**, Electroblob's Wizardry Redux **0.8.9**, jar
`ebreduxaddon-1.20.1-0.1.0.jar`. Do części B dodatkowo Fungal Infection: Spore **2.2.0j (Forge 1.20.1)**.

## Co już sprawdzono automatycznie (nie trzeba powtarzać)

- **Serwer dedykowany** w trzech wariantach: (a) Redux, (b) Redux i Spore, (c) Redux, Spore, Tombstone
  9.1.4 i TombTweaks 0.1.0. Start bez jednego błędu i ostrzeżenia od addonu (`tools/server-check.sh`).
- **29 GameTestów w świecie**, z samym Redux i ze Spore (`./gradlew runGameTestServer [-PwithSpore]`):
  - promień Cleanse trafia cel albo rzucającego, a Cleansing zdejmuje złe efekty i zostawia dobre;
  - wachlarz 5 kolców, kałuża co 4. salwę, trucizna, jedna chmura;
  - Grasp: chwyt, unieruchomienie obu stron, trzymanie po odwróceniu wzroku, obrażenia, egzekucja,
    zwolnienie przy `endCast` i przez sweeper;
  - fala Purifying Pulse: grzybnia → trawa, inne bloki nietknięte, leczenie, odepchnięcie, moby
    neutralne w spokoju, obrażenia dla moba Spore;
  - różdżki: bonusy bez narastania, `Pre`/`Post` przez szynę Redux, ewolucja z zaklęciami i maną, tom
    nie podmienia różdżki;
  - zbroja: bonus z części, podział obrażeń, ewolucja jednej części z zachowaniem nazwy, Last Stand
    (raz, cooldown, nie zatrzymuje pustki, nie działa na niepełnym zestawie);
  - przepisy się ładują.
- **Klient w dev pod Xvfb** (`tools/client-smoke.sh`): zero błędów modeli i tekstur addonu. Na
  stojakach renderują się zbroje, różdżki są w ramkach i na pasku, kolec leci z cząsteczkami, widać
  HUD zaklęć Redux z ikoną Cleanse i ikonę efektu Cleansing. Zrzut: `docs/screenshots/2026-10-03-client-smoke.png`.

## A. Bez Spore

### Zaklęcia (rzucaj z różdżki, nie komendą)

- [ ] **A1. Cleanse.** Promień w moba z trucizną → trucizna znika i nie wraca przez 10 s. Promień w
      powietrze → efekt Cleansing na sobie. Cząsteczki: lodowo-białe iskry.
- [ ] **A2. Spine Volley.** Wachlarz 5 kolców. Czwarta salwa zostawia zieloną chmurę trucizny przy
      trafieniu środkowego kolca. Czy chargeup (0,5 s) i dźwięk (pluśnięcie lamy) nie przeszkadzają?
- [ ] **A3. Grasp** (wymaga Sentient Wand, bo tier master). Złap zombie: zombie stoi, ty też nie możesz
      chodzić. **Ważne: czy klient nie „szarpie” przy próbie ruchu** (root to atrybut prędkości,
      synchronizowany przez serwer). Zombie poniżej 20% HP ginie z animacją i lupem. Puszczenie
      przycisku od razu zwalnia obie strony.
- [ ] **A4. Grasp na graczu** (drugi gracz albo LAN): stoi, dostaje obrażenia, ale **nie** jest egzekwowany.
- [ ] **A5. Purifying Pulse** (master). Celuj w ziemię: złoty pierścień rozchodzi się przez sekundę,
      wrogie moby odlatują, twoje zdrowie rośnie, krowy i owce zostają w spokoju. Celuj w niebo:
      nic się nie dzieje i nie ma cooldownu.

### Różdżki

- [ ] **A6. Przepis.** Advanced Wand Redux + oko pająka fermentowane + zgniłe mięso + kryształ magii →
      Symbiotic Wand. (Zaklęcia ze starej różdżki przepadają, bo to nowy przedmiot.)
- [ ] **A7. Tooltip** pokazuje „Symbiosis: X / 6000 (Y%)” i aktualny bonus. Po kilku rzutach liczby
      rosną o koszt many.
- [ ] **A8. Kanał** (np. Grasp, Life Drain) przez 10 s: koszt many po puszczeniu jest normalny i nie
      spada do zera. To test na nasz znacznik bonusu.
- [ ] **A9. Ewolucja.** `/give` albo NBT `ebreduxaddon:symbiosis:6000L`, schowaj i wyjmij różdżkę →
      zmienia się w Sentient Wand z komunikatem i dźwiękiem, zaklęcia i mana zostają.
- [ ] **A10. Tom.** Arcane Tome w stole Redux nie zmienia Symbiotic Wand w zwykłą różdżkę.

### Zbroja

- [ ] **A11. Przepisy** na cztery części Grafted (żelazna część + zgniłe mięso + 2 kryształy).
- [ ] **A12. Wygląd na graczu.** Warstwy modelu to placeholder z tekstur 1.12.2 (inny układ modelu).
      Zanotuj, jak to wygląda: to pierwsza rzecz do docelowej grafiki.
- [ ] **A13. Postęp.** Tooltip części Grafted: „Absorbed: X / 1500”. Obrażenia dzielą się po noszonych
      częściach Grafted. Przy 1500 część zmienia się w Sentient z komunikatem.
- [ ] **A14. Last Stand.** Pełny zestaw (dowolny miks), śmiertelne trafienie od moba → zostajesz z 3 HP,
      dźwięk totemu, pół sekundy nietykalności. Druga śmierć w ciągu 90 s już normalna. `/kill` nie jest
      zatrzymywany.
- [ ] **A15. Bonus do zaklęć** z 4 częściami Sentient: koszt many −20%, siła +12% (np. obrażenia Spine
      Volley).

## B. Ze Spore

- [ ] **B1. Purifying Pulse** na zainfekowanym terenie: infested_* wraca do zwykłych bloków, roślinność
      grzybowa znika, a spawnery, laboratoria, hive_spawn i biomasa zostają. Zarażone moby dostają
      obrażenia i odlatują.
- [ ] **B2. Cleanse** zdejmuje Mycelium Infection, Corrosion, Madness i resztę listy. Last Stand też.
- [ ] **B3. Zabicie zarażonego** z Symbiotic Wand w ręce: +40 punktów symbiozy.
- [ ] **B4. Log startu** nie ma ostrzeżeń `[EbreduxAddon] config`.

## Znane ograniczenia 0.1.0

- Placeholderowa grafika: modele zbroi na graczu, ikony zaklęć z Redux z innym odcieniem,
  tekstura kolca to dart z Redux.
- Brak własnych dźwięków (używane są vanilla: zombie villager cure, llama spit, warden heartbeat,
  totem).
- **Błąd Redux 0.8.9** (nie nasz, ale dotyka graczy): zniżki kosztu z atrybutów zaklęć (np. zbroje
  maga Redux) narastają w kanale i długi kanał schodzi prawie do zera many. Opis:
  `docs/research/2026-10-02-spore-and-landscape.md`, sekcja 3. Nasza zbroja i różdżki tego błędu nie
  mają.
