# Next session: Spell rework — status

**Stan na 2026-07-07 (wieczór).**

## Część 1 — architektura (plan `2026-07-03-spell-architecture-rework.md`)

- ✅ Taski 1–8 zrobione i zacommitowane (szczegóły w planie).
- ⏸ Task 9 (manualna weryfikacja w `runClient`) — **odroczony na życzenie użytkownika** (2026-07-07). Checklista w planie; dodatkowo sprawdzić odczucie celowania Immune Bond (deviation 5a).

## Część 2 — mechaniki i wizualia (plan `2026-07-07-spell-mechanics-visuals.md`)

- ✅ Task 1: `PacketSrpParticle` (discriminator 4) + `SpellCastFeedback.srpBurst*` (42abc35)
- ✅ Task 2: Parasite Shroud — tiery wg potency, synergia z wizard armour, break-on-attack (5dd538c)
- ✅ Task 3: Yelloweye Gland — chargeup 30 tick., zawsze explosive, klienckie particle ładowania (d2d47de)
- ✅ Task 4: Purifying Pulse — sear wszystkich pasożytów + COTH cleanse (58e4cda) — spec+quality OK; **accepted deviation 1:** cleanse obejmuje też graczy (w tym castera) — patrz plan
- ✅ Task 5: Immune Bond fioletowy DOT co 40 tick. + burst przy spawnie thralla (df62723, 0b91b69) + **bonus:** ten sam burst na ścieżce recall thralla — luka planu wykryta w quality review (e0f2f16)
- ⏸ Task 6 (manualna weryfikacja w `runClient` + `runServer` sanity) — **odroczony na życzenie użytkownika** (2026-07-07). Checklista w planie (rozszerzona o: cure gracza z COTH, bonded mob NIE czyszczony, brak feedbacku na niezainfekowanej krowie, burst przy recall thralla, aura bonda na małych/wysokich mobach).

Build zielony po każdym tasku. Wersja EBW 4.3.19 / SRP 1.10.7 bez zmian.

## Następny wątek (rozpoczęty 2026-07-07)

Rework **Summon Thrall** — AI/taski, naprawa wadliwych trybów Collecting / Farming / Porter, gwarancja nieśmiertelności i ignorowania thralla przez WSZYSTKIE moby. Analiza w toku.
