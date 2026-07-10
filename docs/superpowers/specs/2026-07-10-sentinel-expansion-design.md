# Spec: Sentinel — combat AI rework, GUI revamp, modes & utility

Date: 2026-07-10
Status: approved by user (brainstorming session 2026-07-10)

Acquisition path: **deliberately deferred** (user decision) — the Sentinel
remains /summon-only for now.

Playtest symptoms driving this spec: the "show loot" GUI flow is clunky, and
the Sentinel **very rarely casts spells** (same symptom class as the
sim_wizard; casting is currently done by Ancient Spellcraft's
`EntityAIBattlemageSpellcasting`, which we do not control).

Implementation order: F1 → F4 → F2 → F3, each stage independently testable.

## F1. Combat AI — own state machine replacing ASC tasks

Replace `EntityAIBattlemageSpellcasting` + `EntityAIBattlemageMelee` with
`EntityAISentinelCombat`, sharing the state-machine architecture built for the
sim_wizard (spec 2026-07-10-sim-wizard-ai), in a **battlemage variant**:

- distance > 6: casting stance — spell selection by distance band / target HP /
  cluster detection (heuristics ported from `EntityAISimWizardCast`), optional
  telegraph;
- distance ≤ 6: melee with shield — `EntityAIBlockWithShield` stays (separate
  mutex, works today); melee swings via the existing attack attribute;
- same diagnostic logging infrastructure as sim_wizard E1
  (`enableSentinelDebugLogs` or a shared flag).

Owner protection: any entity that attacks the owner immediately becomes the
Sentinel's top-priority target (revenge-by-proxy target task).

## F2. Modes & commands

- **Stance toggle: Aggressive / Defensive.** Aggressive = proactively targets
  hostile mobs in range (current behaviour); Defensive = fights only when it or
  the owner is attacked.
- **Target priorities**: configurable ordered classes, default
  parasites > undead > other hostiles. Implemented as a comparator over
  candidate targets, config as string list.
- **Guard radius editable from the GUI** (replaces the hardcoded
  DEFAULT_GUARD_RADIUS = 20 as the only option; sensible bounds, e.g. 8–48).
- Wire-up: extend `PacketSentinelCommand` with the new command ids; persist
  stance/radius/priorities in entity NBT.

## F3. Utility (out-of-combat)

- **Auto-deposit**: in GUARD mode, when loot inventory is nearly full, deposit
  into chests near the guard anchor (reuse `ThrallChestHelper`); toggleable per
  Sentinel from the GUI.
- **Out-of-combat regeneration**: slow self-heal when no target for N seconds
  (config: rate + delay, can be disabled).
- **Pickup filter**: simple GUI toggle — "collect everything" vs "valuables
  only" (curated list: ores/ingots/gems/wizardry items; config-extendable).

## F4. GUI revamp — one screen

Single `GuiSentinelControl` replaces the current control GUI + separate clunky
loot view:

- top strip: status — HP, current mode/stance, guard anchor coords;
- left column: command buttons (follow/guard/patrol/return), stance toggle,
  guard radius stepper, utility toggles (deposit, pickup filter);
- right pane: **loot inventory inline** (the 20 slots rendered directly;
  shift-click withdrawal), replacing `PacketOpenSentinelLoot` round-trip flow
  where possible — the loot container GUI remains for slot interaction, but is
  opened as part of this screen, not a separate menu hop.
- Networking: new/extended packets piggyback on the existing
  `PacketSentinelCommand` pattern; loot sync mechanism unchanged.

## Acceptance criteria

1. With debug logs on, Sentinel cast cadence matches config and uses varied
   spells (≥4 distinct over a few minutes) — the "rarely casts" symptom is
   gone and its cause documented.
2. Defensive stance: Sentinel ignores hostiles until owner/self is hit.
3. Priorities: with a parasite and a zombie both in range, parasite dies first.
4. GUI: all commands + loot access reachable from one screen; guard radius
   changes take effect immediately.
5. Auto-deposit: full Sentinel in GUARD mode empties into anchor chests.
