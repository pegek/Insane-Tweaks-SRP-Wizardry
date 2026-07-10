# Spec: Quick wins — Zhonya master toggle + STAY anchor wander

Date: 2026-07-10
Status: approved by user (brainstorming session 2026-07-10)

## A1. Zhonya's Hourglass — master toggle, default OFF

User verdict: the artefact is weak; it should ship disabled. Applies ONLY to
`insanetweaks:zhonyas_hourglass` — `restoration_hourglass` is untouched.

- New config field `tweaks.enableZhonya` (`@Config.Name("Enable Zhonya's Hourglass")`),
  **default `false`**, `@Config.RequiresMcRestart`.
- When OFF:
  - Item stays registered (existing world items survive; no missing-item warnings).
  - `onItemRightClick` does nothing (no cost, no cooldown, no effects).
  - Tooltip gains a grey "Disabled in config" line (en_us + ru_ru).
  - The artefact is excluded from EB Wizardry's artefact loot pools. Mechanism:
    EB has a per-artefact enable system — if it is programmatically reachable
    (check decompiled `ItemArtefact` / `Wizardry.settings`), hook it; otherwise
    filter via `LootTableLoadEvent`. Resolved at plan time from
    `notes/decompiled_mods/ebwizardry_source/`.
- When ON: current behaviour, unchanged.

## A2. Thrall STAY mode — anchor + calmer wander

Current behaviour: `ThrallAIWander` uses `RandomPositionGenerator.getLandPos(thrall, 10, 7)`
at speed 0.6 with no anchor, so a thrall left in STAY drifts arbitrarily far.

- On entering STAY (via `setMode`, including auto-STAY transitions such as owner
  disconnect), the thrall records its current position as `stayAnchor`
  (persisted in entity NBT). Anchor cleared when leaving STAY.
- `ThrallAIWander` behaviour in STAY mode:
  - wander targets constrained to within `stayWanderRadius` blocks of the anchor
    (config, default **4**);
  - movement speed lowered to `stayWanderSpeed` (config, default **0.4** vs 0.6);
  - if the thrall finds itself outside the radius (pushed, teleport glitch), it
    paths back to the anchor.
- All other modes keep the existing wander parameters (10/7 range, speed 0.6).
- Config fields live in `ThrallCategory`.

## Testing (manual, runClient)

1. Zhonya OFF (default): right-click does nothing, tooltip shows disabled line,
   artefact absent from dungeon loot (spot-check via loot table dump or repeated
   chest generation). Restoration Hourglass still works.
2. Zhonya ON: behaviour identical to pre-change.
3. STAY: order thrall to stay, observe wander stays within 4 blocks, visibly
   slower; push it out of the radius → it returns; save/reload world → anchor
   persists.
