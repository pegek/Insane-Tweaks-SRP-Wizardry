# Thrall Fixes & Mob-Ignore Design (2026-07-07)

Approved scope (user, 2026-07-07): hard bugs in Collecting, the mob-ignore invariant, Farming/Porter quality improvements, and a GUI overhaul (two columns + hover tooltips). Based on the read-only analysis of the same date (findings C-1..C-4, X-2, F-1, P-2, invariants A/B).

## Verdicts from analysis (context)

- **Invariant A (immortality): HOLDS.** `attackEntityFrom` → false, `isEntityInvulnerable` → true, `canDespawn` → false cover all damage paths. **No code changes.**
- **Invariant B (ignored by all mobs): DOES NOT HOLD.** No `LivingSetAttackTargetEvent` handler exists; SRP targeting selectors check neither EPEL nor `srpcothimmunity`. Parasites and vanilla hostiles can target and body-block the thrall.
- Porter's historical bugs (C3 NPE, C4 race, M4 over-pull) are already fixed in code.

## 1. Collecting fixes

File: `entities/EntityThrallMinion.java`, `entities/ai/ThrallAICollecting.java`, `entities/ai/ThrallAIGatherItems.java`, `config/ModConfig.java`.

1. **(C-1)** `onUpdate`'s full-inventory auto-return condition additionally excludes `ThrallMode.COLLECTING` (same rationale as the existing PORTER exclusion — the collecting AI has its own RETURNING phase and `smartDeposit`).
2. **(C-2a)** A collecting session begins with a **local scan** around the thrall's current position (no teleport) before any ring-teleport search points.
3. **(C-2b)** Config default `collectingMinTpDistance`: 30 → **8** (range bounds unchanged).
4. **(C-3)** Session looping: after a session completes (`finishDone`), the thrall rests briefly at home (~100 ticks), deposits, and starts a new session with the **same target list** (if non-empty). `COLLECTING` is added to the work-timer mode list in `onUpdate`, so `thrallWorkDurationHours` bounds the looping. When the timer expires: return home, deposit, phase → WAITING_FOR_ITEMS (status "Waiting for items") — **not** STAY.
5. **(C-4)** NBT restore landing in `mode == COLLECTING && phase == DONE` forces phase → WAITING_FOR_ITEMS.
6. **(X-2)** `ThrallAIGatherItems.shouldExecute` returns false while the collecting AI is in WAITING_FOR_ITEMS (stops it from swallowing items the player is staging for target selection).

## 2. Mob-ignore (invariant B)

1. **New handler** `events/ThrallTargetProtectionHandler` (server-side): subscribes `LivingSetAttackTargetEvent`; if `event.getTarget() instanceof EntityThrallMinion` and the aggressor is an `EntityLiving`, clear its attack target (`setAttackTarget(null)`) and its revenge target. Generic — covers every mod's `EntityLiving` mobs. Registered in `InsaneTweaksMod.init` under the same module flag that gates the other thrall handlers.
2. **SRP second layer:** at startup, append the thrall's registry name to SRP's parsed `mobattackingBlackList` structure if it is publicly reachable (to be verified against `notes/decompiled_mods/srp_sourcecode_analis/decompiled_src` during planning). If not reachable without reflection hacks, skip the injection and rely on the handler alone; add a README/config note recommending the modpack-level SRP config entry.

## 3. Farming (F-1)

File: `entities/ai/ThrallAIFarming.java`.

`tickSearching`: when the scan centred on the thrall finds nothing and a home point exists, run a second scan centred on **home** (same `farmRadius`); navigate/teleport to found work as with any target. No behaviour change when the thrall already stands on the farm.

## 4. Porter (P-2 + direction)

Files: `entities/ai/ThrallAIPorter.java`, `config/ModConfig.java`.

1. **Hotbar exclusion:** `pullFromOwner` never touches hotbar slots 0–8; the manifest applies to main inventory (slots 9–35) only.
2. **New config** `porterDirection` (enum `TO_HOME` / `FROM_HOME`, default `TO_HOME`, `@Config.RequiresMcRestart` NOT needed — read per cycle).
3. **FROM_HOME semantics (reverse porter):** each cycle the thrall teleports home, pulls from nearby chests only item types **the player already carries with non-full stacks**, teleports to the owner and tops those stacks up to full (never introduces new item types, never touches armour/offhand); leftovers are returned to the home chests on the way back. Budgeting reuses the existing `ChestBudgetPool` pattern.

## 5. GUI (`client/gui/GuiThrallControl.java` + `assets/insanetweaks/lang/en_us.lang`)

1. **Two-column layout:** left column = mode buttons (Follow, Stay, Woodcutting, Mineshaft, Farming, Porter, Collecting), right column = actions (Set Home, Inventory, Dismiss, and the remaining buttons). Button ids and packet actions unchanged — layout only.
2. **Hover tooltips:** each button gets a short localized description (`gui.insanetweaks.thrall.tooltip.<key>` in `en_us.lang`), rendered in `drawScreen` via `drawHoveringText` with `fontRenderer.listFormattedStringToWidth` wrapping (~180 px).

## Must NOT change (design invariants)

Immortality code paths; never-aggressive (`setAttackTarget` no-op, no combat AI, no GUARD mode); 27-slot inventory; teleport departure+arrival feedback; work-AI mutex bits = 3; Java 8 only; entity tracking IDs; existing packet discriminators and PacketThrallCommand action ids.

## Verification

`./gradlew build` green per task (no test suite). In-game testing is **deferred** by the user — extend the deferred manual checklist with: collecting near-home pickup (< 30 blocks), collecting loop + work-timer expiry to WAITING, full-bag collecting session not hijacked to STAY, parasites/zombies ignoring the thrall (no approach, no swings), farming fallback scan from outside the field, porter leaving hotbar alone, FROM_HOME restock cycle, GUI two-column layout + tooltips.
