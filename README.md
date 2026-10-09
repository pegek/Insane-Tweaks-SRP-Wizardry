# EbreduxAddon (Minecraft 1.20.1)

Addon do [Electroblob's Wizardry Redux](https://modrinth.com/mod/electroblobs-wizardry-redux)
inspirowany modem Insane Tweaks z 1.12.2: magia oczyszczania i pasożytów oraz sprzęt, który rośnie
razem z graczem. Wymaga [Fungal Infection: Spore](https://modrinth.com/mod/fungal-infectionspore).

**Wersja:** 0.3.1. **Licencja:** GPL-3.0-only (jak Redux, po którym dziedziczy).

## Wymagania

- Minecraft 1.20.1, Forge **47.3.19+**
- Electroblob's Wizardry Redux **0.8.9** (zakres `[0.8.9,0.9)`)
- Fungal Infection: Spore **2.2.x dla Forge 1.20.1** (zakres `[2.2,2.3)`, sprawdzone na 2.2.0j); od 0.3.1 wymagany

## Zawartość

| | |
|---|---|
| **Cleanse** (healing, advanced) | Promień: cel albo ty dostajesz Cleansing, czyli ciągłe zdejmowanie złych efektów. |
| **Spine Volley** (earth, advanced) | Wachlarz 5 zatrutych kolców; co 4. salwa zostawia chmurę trucizny. |
| **Grasp** (necromancy, master) | Ciągły chwyt: cel i ty stoicie, cel traci HP, poniżej 20% ginie. |
| **Purifying Pulse** (healing, master) | Fala: leczy ciebie i sojuszników, odpycha wrogów, oczyszcza teren Spore. |
| **Symbiotic Wand → Sentient Wand** | Rośnie z wydaną maną, ewoluuje przy 6000; tańsze i dłuższe zaklęcia. |
| **Zarażony mag** (ze Spore) | Mag Redux zmarły z Mycelium Infection wstaje jako członek roju; rzuca zaklęcia, tier rośnie z liczbą Hivemindów. |
| **Mykomanta** (ze Spore) | Zarażony mag po zabójstwie i czasie ewolucji Spore staje się Mykomantą: master, więcej zaklęć, osłania rój Resistance. |
| **Grafted → Sentient** (4 części) | Rośnie z przyjętymi obrażeniami, ewoluuje częściami; Last Stand przy pełnym zestawie. |

Wszystkie liczby: `config/ebreduxaddon-common.toml` i `data/ebreduxaddon/spells/*.json` (datapack).

## Budowanie i testy

```bash
./gradlew check build                    # JUnit dla core + jar w build/libs
./gradlew runGameTestServer              # 42 testy w świecie (z Redux i Spore)
tools/server-check.sh b|c <katalog>      # jar produkcyjny na serwerze dedykowanym (a: bez Spore, od 0.3.1 nie startuje)
tools/client-smoke.sh <świat> <katalog>  # klient pod Xvfb, zrzuty ekranu (SMOKE_WITH_SPORE=1: ze Spore)
```

Jary Redux i Spore pobiera Gradle z Maven Modrinth; nigdy nie trafiają do repo.

## Dokumenty

- Spec: `docs/specs/2026-10-02-ebreduxaddon-v0.1-design.md`, `docs/specs/2026-10-03-ebreduxaddon-v0.2-infected-wizard-design.md`,
  `docs/specs/2026-10-05-ebreduxaddon-v0.3-mycomancer-design.md`
- Plan: `docs/plans/2026-10-02-ebreduxaddon-v0.1-plan.md`
- Research (Spore, błąd Redux): `docs/research/2026-10-02-spore-and-landscape.md`
- Testy w grze: `docs/in-game-checklist.md`
