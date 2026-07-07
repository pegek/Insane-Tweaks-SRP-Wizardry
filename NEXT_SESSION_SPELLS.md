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

## Thrall fixes & mob-ignore (plan `2026-07-07-thrall-fixes.md`) — deferred manual checklist

**Status: wszystkie taski 1–7 zrobione i zacommitowane (2026-07-07).** Finalny przegląd całości wykrył i naprawił
(commit d7dc71b): mob-ignore był bramkowany `enableSpells` mimo bezwarunkowej rejestracji encji thralla (krytyczne);
`.gitignore` `config/` połykał `src/**/config/` (zakotwiczone do `/config/`); status "Shift done" nadpisywał
statusy Collecting (kosmetyczne).

Build green after every task; in-game testing deferred by the user (2026-07-07). Verify in `runClient`
(and one `runServer` boot for sanity):

- [ ] Collecting near-home pickup: deploy thrall onto a target block cluster < 30 blocks from home; it mines locally before ring-teleporting.
- [ ] Collecting loop: after a session the thrall rests at home ~5 s, deposits, then re-searches with the same targets.
- [ ] Collecting work-timer expiry: with `thrallWorkDurationHours` > 0, on expiry the thrall returns home, deposits, and shows "Waiting for items" (NOT Stay).
- [ ] Collecting full bag: filling the bag mid-session does NOT hijack the thrall into a Stay/return via the generic auto-return (collecting handles its own RETURNING).
- [ ] Collecting NBT restore: save/quit while collecting is DONE, reload — thrall shows "Waiting for items", accepts fresh targets.
- [ ] GatherItems interlock: while collecting is "Waiting for items", tossed staging items are NOT walked-to and swallowed by the gather task.
- [ ] Mob-ignore: parasites (primitive + advanced) and vanilla zombies/skeletons never approach or swing at the thrall; check with `srpcothimmunity` unset.
- [ ] SRP blacklist: log shows "Added 'insanetweaks:thrall_minion' to SRP mobattackingBlackList" at load.
- [ ] Farming home-fallback: park the thrall at its home chest, off the field; it still finds farm work within `farmRadius` of home and walks/teleports to it.
- [ ] Porter hotbar: TO_HOME porter never removes items from hotbar slots 0-8, armour, or offhand.
- [ ] Porter FROM_HOME: set `porterDirection = FROM_HOME`; thrall tops up the owner's partial main-inventory stacks from home chests, never adding new types or touching hotbar/armour/offhand; leftovers returned to chests.
- [ ] GUI: two columns (modes left, actions right); hovering each button shows a wrapped one-line tooltip.
- [ ] `runServer` boots without a client-classloading crash (ThrallTargetProtectionHandler + SRPConfig append are server-safe).
- [ ] Mob-ignore works with `Enable Spells = false` (protection is unconditional since d7dc71b — thrall spawned earlier must still be ignored).

**Config migration note:** existing `insanetweaks.cfg` files keep the OLD `collectingMinTpDistance=30`;
Forge only writes new defaults for missing keys. Delete or reset that key to get the new default 8.

## Farming freeze & Ray-vs-Beckon (plan `2026-07-07-farming-freeze-ray-beckon.md`) — deferred checks

Fixed 2026-07-07 after in-game testing (commits 6fdf53a, 1c22842, a9f5cbe, 648da30). Verify:

- [ ] Farming: no freeze on tiles approached from any diagonal (the old corner-vs-center oscillation); with `enableThrallDebugLogs`, no NAVIGATING↔WORKING flip-flop and the nav-timeout teleport still fires for genuinely fenced-off targets (~5 s).
- [ ] Farming: knockback mid-work to 2.0–2.5 blocks does NOT abandon the action (hysteresis band); spot-check TILL/PLANT/BONEMEAL besides harvest; test a multi-tier (sloped) field.
- [ ] Ray vs Beckon: sustained ray on each Beckon tier (SI–SIV) — first hit procs 20 DMG at potency 1.0 / 80 at ≥1.2 (linear between), kill credit + loot on death; re-cast re-procs; sweeping onto a second Beckon mid-cast procs it independently.
- [ ] Ray vs Beckon: with SRP `disloBurningDeath` gene rolled, the Beckon still dies (OUT_OF_WORLD fallback) instead of sticking at 1 HP.
- [ ] Ray: non-Beckon targets and `enableSpells=false` behave exactly like vanilla EBW; hurt flash/sound shows on the non-lethal purge hit.
