# Spec: Cursed Ring parity for the Corrupted Seed loop + Infernal spectral dust drops

Date: 2026-07-10
Status: approved by user (brainstorming session 2026-07-10)

## C. Cursed Ring — full parity with Blessed Ring

The Corrupted Seed loop (spec 2026-07-09-blessed-ring-bauble-fruits) is currently
gated on `enigmaticlegacy:blessed_ring`. The plain
`enigmaticlegacy:cursed_ring` (Ring of the Seven Curses) becomes **fully
equivalent** — user decision: no reduced drop rate, no path restrictions.

- `EnigmaticLegacyCompat` gains `isWearingQualifyingRing(player)` accepting
  either ring (same Baubles-scan mechanism as today). All existing call sites
  (fragment drop handler, sapling growth owner-condition) switch to it.
- Existing config (`fragmentDropChance`, `fragmentDropEntities`, sapling options)
  applies to both rings unchanged.

### Player-facing information
1. Tooltips of Corrupted Seed Fragment, Corrupted Seed and Corrupted Fruit
   updated to name **both** rings (en_us + ru_ru).
2. One-time chat hint (per player, persisted NBT flag on the player): fires the
   first time a player wearing either qualifying ring kills a parasite from the
   `fragmentDropEntities` list — flavor line telling them the ring draws
   corrupted seeds out of high-tier parasites. Works identically for both rings.

## D. Infernal Mobs — spectral dust drops

Every infernal-elite mob (mod `infernalmobs`) drops spectral dust when killed
by a player.

- New handler `InfernalDustDropHandler` (`events/`), registered in `init` only
  when `Loader.isModLoaded("infernalmobs")` AND the config toggle is on.
- On `LivingDropsEvent`:
  - mob must be infernal: `InfernalMobsCompat.isRare(entity)`;
  - kill must be player-credited (`recentlyHit` / `getTrueSource() instanceof
    EntityPlayer`) — blocks fully automatic farms;
  - adds **1–2** (`infernalDustMin`..`infernalDustMax`) `ebwizardry:spectral_dust`
    of a random element (metas: fire 1, ice 2, lightning 3, necromancy 4,
    earth 5, sorcery 6, healing 7).
- Refactor: the reflection bridge `ItemInfernalCrownArtefact.InfernalMobsDirectAPI`
  moves to `util/InfernalMobsCompat` (existing compat-shim pattern); the Crown
  and the new handler share it. Behaviour of the Crown is unchanged.
- Config (interactions category): `enableInfernalDustDrops` (default true),
  `infernalDustMin` (1), `infernalDustMax` (2).

## Error handling

- Enigmatic Legacy absent: `isWearingQualifyingRing` returns false (as today).
- InfernalMobs absent: handler not registered; zero overhead.
- `spectral_dust` item lookup failure (EB missing — impossible given hard dep,
  but guarded): handler logs once and disables itself.

## Testing (manual, runClient)

1. Kill listed parasite wearing cursed_ring → fragments drop at the same rate
   as blessed_ring; one-time chat hint fires exactly once per player.
2. Sapling grows with owner wearing cursed_ring only.
3. Kill an infernal mob as player → 1–2 random-element dust among drops;
   non-infernal mob → none; infernal killed by environment/mob → none.
