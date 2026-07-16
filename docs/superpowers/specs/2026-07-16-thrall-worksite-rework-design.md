# Spec: Thrall work-site memory + HOME-as-depot rework

Date: 2026-07-16
Status: approved verbally by user (2026-07-16); awaiting spec review

## Goal

HOME stops being the work anchor and becomes purely the **depot**: the only place the
thrall TAKES supplies from (feed, shears, bone meal) and DEPOSITS items to. Work modes
(HUSBANDRY / FARMING / COLLECTING / WOODCUTTING) execute at **work sites** — wherever the
player takes the thrall and issues the command. The thrall remembers its recent work
sites and shuttles between the active site and HOME by teleport.

## Design (user-confirmed parameters)

### Work sites
- Issuing a work-mode command (HUSBANDRY/FARMING/COLLECTING/WOODCUTTING) records the
  thrall's current position as the **active work site** for that session and pushes it
  onto a remembered list.
- The thrall remembers the **3 most recent work sites** (config `workSiteMemorySize`,
  default 3; newest first, duplicates within a few blocks collapse into one entry).
  Persisted in NBT (position + mode ordinal each).
- All in-mode scanning/acting radii anchor on the **active work site**, not HOME.
  (MINESHAFT and PORTER are unchanged: mineshaft already digs from where it stands;
  porter IS the depot keeper and stays HOME-anchored.)

### Depot shuttle
- When the thrall needs the depot (bag full, needs feed/shears/bone meal, end-of-cycle
  deposit), it **teleports to HOME** (existing teleport flow with particles + sound),
  deposits/withdraws, then **teleports back to the active work site** and resumes the
  same mode — instead of today's behavior of staying at HOME.
- If HOME is unset: current fallback behavior (work in place, no shuttle).
- The GUI shows the active work site next to the HOME line ("Site: x, y, z / none").

### Husbandry changes
- **Periodic cull above cap**: instead of culling ALL excess adults in one cycle, the
  thrall culls **1 adult per cull interval** (config `husbandryCullIntervalSeconds`,
  default 120) whenever the species count is ABOVE the cap. Breeding may overshoot the
  cap by 1 (breed while `count <= cap`), so a tended herd oscillates around the cap and
  yields a steady meat trickle.
- Modded-animal support stays as-is (EntityAnimal + isBreedingItem) — explicitly out of
  scope per user.

### Debug logs
- Extend the existing `client.enableThrallDebugLogs` coverage: work-site push/switch,
  shuttle departures/returns (with reason: deposit / restock / cycle-end), husbandry
  job queue contents, and periodic-cull decisions.

## Compatibility
- New NBT tags (`ThrallWorkSites`, `ThrallActiveSite`) — absent on old saves → thrall
  behaves as today until the next work command records a site.
- STAY anchor (spec 2026-07-10) is unrelated and unchanged.

## Testing (manual, runClient)
1. Set HOME at a chest depot, walk the thrall 60 blocks away, order FARMING → it farms
   there; bag full → teleports to HOME, deposits, teleports back, resumes farming.
2. Order HUSBANDRY at a pen: feed fetched from HOME chests (teleport round-trip), herd
   bred to cap+1, exactly one adult culled every interval while above cap.
3. Issue commands at 4 different spots → GUI shows the newest site; NBT keeps last 3
   across world reload.
4. No HOME set → mode works locally as today (no shuttling, no crash).
