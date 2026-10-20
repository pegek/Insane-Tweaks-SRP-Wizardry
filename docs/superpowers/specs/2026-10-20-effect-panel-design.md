# Effect Panel — design (srpwizcore)

Status: agreed in brainstorming 2026-10-20, awaiting spec review.
Owner mod: **srpwizcore** (private, DEv 1.2 pack glue). Client-only feature. All code, lang and
player-facing text in English.

This is part 1 of 3 agreed with the user, in this order:

1. **Effect panel** (this document).
2. **Wit's Hunger** — a new EBW-curse effect for the Sentient Codex starvation penalty (content).
3. **Effect source tracking** — server-side attribution of who/what applied an effect, synced to
   the client and shown in this panel (srpwizcore).

Parts 2 and 3 get their own documents. Nothing here may depend on them.

## 1. Why

Players see effect tiles in the inventory but have no in-game way to learn what an effect does.
Modded effects are the worst case: `Wit's Hunger II` or `Bleeding` means nothing on its own. The
user wants a pack-wide, mouse-over description, and eventually wants every effect in the pack
described.

The pack already ships **Just Enough Effects 1.0.3** (`effectinfo`), which tries to do this and is
broken in every pack: it draws its popup from `GuiScreenEvent.DrawScreenEvent.Pre`, i.e. *before*
the container draws, so the effect tiles are painted over the popup. Confirmed by the user in game
(2026-10-20, hovering Night Vision: the popup appears underneath the tile) and in JEE's bytecode
(`com.pinball3d.effectinfo.Render.onRenderScreen(DrawScreenEvent$Pre)`).

## 2. Player experience

1. The player opens an inventory screen that shows active effects (survival inventory, creative
   inventory, or any modded GUI built on vanilla's `InventoryEffectRenderer`).
2. The player moves the mouse over an effect tile. Immediately, the tile's icon and name disappear
   and the tile becomes a description panel:
   - same width as the tile, same left edge, same top edge;
   - the description is drawn in a **small font, scale ~0.6** (user's choice from mockup "C"),
     word-wrapped to the tile width;
   - dark text on the tile's light-grey background (vanilla's GUI label convention, `0x404040`);
   - the panel grows **downwards only as far as the text needs**, covering the tiles below it for
     as long as the mouse stays there;
   - the effect's **remaining duration stays**, in grey, as the last line (user's decision).
3. The mouse leaves the tile: everything is drawn exactly as vanilla draws it again.
4. If no description exists anywhere, the panel instead tells the player **where to add one**: the
   exact line to add and the file it belongs in (§5).

The cause of an effect, where known, is simply a sentence inside the description ("Granted by
eating food enchanted with Mmmm."). The user explicitly accepted that; a separate source line is
part 3.

The top-right HUD icons are out of scope: there is no mouse cursor over them.

## 3. Scope

In scope:

- The hover panel, on every `InventoryEffectRenderer` screen.
- A pack descriptions file in `config/`, re-read on F3+T — where the pack's own descriptions are
  written (§4a). User's decision 2026-10-20.
- A description lookup chain over that file plus the lang conventions already present in the
  pack (§4).
- Missing-description hint (§5).
- Neutralising JEE's own renderer while keeping JEE installed as a text source (§6).
- A client command that writes every undescribed effect in the pack to a template lang file (§7).
- Config category `effectPanel` in `srpwizcore.cfg` (§8).

Out of scope:

- Wit's Hunger and its description (part 2).
- Effect source tracking (part 3). Leave room for it (§9), build none of it.
- HUD icons, potion bottle tooltips (`potiondescriptions` already does those), non-English lang.
- JEE's Java API (`JEEAPI.addDesc`). Recon found no mod in the pack that uses it; lang keys only.
- Writing the descriptions themselves, beyond what already exists.

Anti-goals — failures even if everything else works:

- Any change to how effect tiles look when the mouse is **not** over one.
- A second, overlapping popup (JEE's) anywhere.
- Anything that can load a client-only class on a dedicated server.

## 4a. The pack descriptions file

**User's decision 2026-10-20:** the descriptions the pack author writes live in a plain file in
the config folder, not in a jar, so a new description shows up after **F3+T** with no rebuild and
no restart. The config folder ships with the pack, so for players it is as good as a jar.

- Path: `config/srpwizcore/effect_descriptions.lang`.
- Format: one `<registry name>=<text>` per line, e.g. `minecraft:speed=Makes you walk faster.`
  Keyed by **registry name** because translation names are inconsistent across mods
  (`effect.moveSpeed`, `potion.insanetweaks.cleanse`, `mob_effect.srparasites:bleed`). Split on
  the first `=`; trim the key; keep the text as written. `#` starts a comment line; blank lines
  are ignored. Read as UTF-8, so `§` formatting codes work.
- The text is **not** passed through `I18n.format`/`String.format`: a `%` is a literal `%`. This
  is the one place that differs from the lang files of rules 2–4 — say so in the file's header.
- An entry with an **empty** text counts as missing (that is what the dump writes, §7).
- If the file does not exist at client start, create it with a header comment explaining the
  format and the authoring loop, and no entries.
- Read once at client start and again on every client resource reload (F3+T, language change):
  register an `IResourceManagerReloadListener` on the client resource manager. A malformed line
  is skipped with one WARN naming the line number; it must never stop the rest of the file
  loading.
- English only. No per-locale variant (out of scope).

## 4. Description lookup chain

For each hovered effect, try these in order and use the **first one that exists**. `<name>` is
the potion's translation name, `Potion.getName()` (e.g. `effect.moveSpeed`,
`potion.insanetweaks.cleanse`, `mob_effect.srparasites:bleed`). `<ns>` / `<path>` are the two
halves of the potion's **registry name** (e.g. `minecraft` / `speed`). Rules 2–4 are lang keys,
tested with `net.minecraft.client.resources.I18n.hasKey`.

| # | Source | Who ships it | Notes |
|---|---|---|---|
| 1 | entry `<ns>:<path>` in the pack descriptions file (§4a), non-empty | the pack author | **The pack's own text and override. Always wins.** The hint and the dump point here. |
| 2 | `<name>.<N>.desc`, N = amplifier … 4 | JEE (41 vanilla), unseens_dungeon_additions (9), Deeper Depths (6), Fish's Undead Rising (12, `mob_effect.` prefix), **insanetweaks** (4) | JEE's own semantics, copied exactly: start at N = the effect's amplifier, walk up to 4, first hit wins — so a `.4.` key covers every level. Amplifiers above 4 start at 4. |
| 3 | `description.<name>` | potiondescriptions (76 vanilla + a few mods), OceanicExpanse (4) | |
| 4 | `bestiary.effect.<path>.desc`, **only when `<ns>` is `srparasites`** | SRParasites 1.10.9 bestiary | Bytecode + lang recon 2026-10-20: all **33** SRP potion ids have a matching key (`srparasites:bleed` → `bestiary.effect.bleed.desc`). |

Order rationale (decided with the user): for vanilla, both JEE and potiondescriptions have text;
JEE wins because it gives numbers ("+20% Speed per level.") where potiondescriptions gives
generalities ("Increases walking speed."). Rule 1 sits above both so the pack can override any
single text without touching another mod.

Notes for the builder:

- Resolve once per potion and cache (potion → which rule answered and with what, or "none").
  Clear the cache in the same resource-reload listener that re-reads the descriptions file (§4a),
  so F3+T picks up both new file entries and changed lang text.
- Rules 2–4 are lang values and go through `I18n.format`, which is `String.format`: their authors
  write a literal `%` as `%%` (JEE's own file does). Rule 1 text is used verbatim (§4a).
- Recon numbers come from scanning every `en_us.lang` in the DEv 1.2 `mods/` folder on 2026-10-20
  (`scan_lang.py`, not committed). About 460 effect-name keys exist across 32 jars, some of them
  bottle names; the four sources above cover very roughly 40% of effects.

## 5. Missing description

When the chain finds nothing, the panel shows, in the same small font:

```
No description yet.
Add to config/srpwizcore/effect_descriptions.lang:
<ns>:<path>=
```

followed by the duration line as usual. The last line must be exactly the line to add, so it can
be copied by eye. Long lines simply wrap. (The hint's own wording is srpwizcore lang text, §11.)

Controlled by `showMissingHint` (§8), **default ON** — builder's call recorded here, not the
user's: the pack is in active development and the hint is how the user finds gaps. With it OFF an
undescribed effect is not expanded at all (the tile stays vanilla).

## 6. Neutralising JEE

JEE registers its renderer through `@Mod.EventBusSubscriber` on the class
`com.pinball3d.effectinfo.Render` (a static `@SubscribeEvent` method), so FML registers it on
`MinecraftForge.EVENT_BUS` keyed by the `Class` object. Remove it with:

```java
MinecraftForge.EVENT_BUS.unregister(Class.forName("com.pinball3d.effectinfo.Render"));
```

- Client only, only when `Loader.isModLoaded("effectinfo")`, after FML's automatic subscriber
  registration has run — `FMLPostInitializationEvent` on the client is the natural place.
- No compile dependency on JEE: the class is only ever named as a string. `Class.forName` on the
  client is safe; JEE's `Render` names only client types JEE itself loads.
- Log one INFO line saying it was done (or that JEE was absent). If `unregister` throws, log once
  at WARN and carry on — the panel must still work; the worst case is JEE's broken popup under it.
- Gated by `suppressJeeRenderer` (§8), default ON.
- JEE stays in the pack: its 41 lang keys are rule 2 of the chain.

## 7. Dump command

**Builder's call recorded here, not the user's** (the user was asked and had not answered when the
document was written; it directly serves their goal of describing every effect, and is cheap).
Strike it at review if unwanted.

A client-side command (registered through `ClientCommandHandler`, so it works in multiplayer
without server support), e.g. `/effectdesc missing`:

- Walks `ForgeRegistries.POTIONS`, sorted by registry name.
- For each potion that the chain (§4) cannot describe **and** that has no line at all in the
  descriptions file yet, **appends** to `config/srpwizcore/effect_descriptions.lang`:
  ```
  # <display name> — <mod name>
  <ns>:<path>=
  ```
  under one dated section comment per run. It never rewrites or reorders existing lines, and an
  effect already present with an empty value is not appended again — so running it twice is
  harmless. (Appending to the live file rather than a separate todo file is the builder's call:
  the loop becomes "run, fill the blanks, F3+T" with one file.)
- Prints to chat how many lines were appended, and coverage: how many effects are described in
  total and by which rule.

The authoring loop: `/effectdesc missing` → fill in the blank lines → F3+T → hover in game.

## 8. Config — new category `effectPanel` in `SrpWizCoreConfig`

`SrpWizCoreConfig` is a `category = ""` root, so the new field must be a category **object**
(a plain field there is a hard crash — CLAUDE.md, memory `forge-config-category-empty-gotcha`).

| Field | Default | Read |
|---|---|---|
| `enabled` | true | live — checked in the draw handler |
| `fontScale` | 0.6 (range 0.3–1.0) | live |
| `showMissingHint` | true | live |
| `suppressJeeRenderer` | true | once at client post-init — `@Config.RequiresMcRestart`, and say so in the comment |

Comment style per the file's neighbours; "read live" wording where it is.

## 9. Room for part 3 (do not build)

Part 3 will add one more line to the panel — the effect's source. Keep the panel's text assembly
as a list of lines (description lines, then optional extra lines, then duration) so part 3 can
insert one without restructuring. Nothing more.

## 10. Rendering notes for the builder

These are constraints and known traps, not a prescribed implementation.

- **Hook:** `GuiScreenEvent.DrawScreenEvent.Post`, gui `instanceof InventoryEffectRenderer`.
  Post is the whole point: it runs after the container has drawn its tiles.
- **Side safety:** `GuiScreenEvent` is class-level `@SideOnly(CLIENT)`. Use the srpwizcore
  precedent, `@Mod.EventBusSubscriber(modid = SrpWizCore.MODID, value = Side.CLIENT)`
  (`dormant/DormantEyeTooltipHandler`), for every client handler. Never register a class that
  names a client type from `SrpWizCore.init`.
- **Geometry must match vanilla exactly**, or the hover hits the wrong tile. Mirror
  `InventoryEffectRenderer.drawActivePotionEffects` for this Forge version: tiles 140×32 at
  `x = guiLeft - 124`, starting at `guiTop`, spacing 33 or `132 / (n - 1)` when there are more
  than 5 effects; effects sorted with `Ordering.natural().sortedCopy(...)` and filtered by
  `Potion.shouldRender(effect)`; only when the screen's `hasActivePotionEffects` is true. When
  tiles overlap (compressed spacing), the **last-drawn** tile under the mouse is the hovered one.
  Read the vanilla/Forge source from the Forge sources jar to confirm every number. JEE's bytecode
  is a useful second reference for the two quirks it handles: the recipe book in
  `GuiInventory` (`recipeBookGui`, `widthTooNarrow`) and `GuiContainerCreative`.
- **GL state in Post:** after a container draws, item lighting and depth may be enabled. Disable
  lighting (and set the colour to white) before drawing, restore after; draw at a raised z so the
  panel sits above the tiles and below item tooltips.
- **EBW curses** (`electroblob.wizardry.potion.Curse` and subclasses) draw their own purple tile
  and hide the timer (`shouldRenderInvText` → false). For those, draw the panel on a purple
  background with light text, and omit the duration line. Detect by class name via a cached
  `Class.forName(...).isAssignableFrom`, so srpwizcore gains no compile dependency on EBW.
  General rule for the duration line: draw it only when `potion.shouldRenderInvText(effect)` is
  true — i.e. exactly when vanilla would have.
- **Screen edges:** with many effects and a long text the panel can run past the bottom of the
  screen; move it up just enough to stay on screen.
- **Font:** scaled text below 1.0 is not pixel-crisp at every GUI scale. `fontScale` exists so
  the user can tune it in game; do not over-engineer.
- **Pack check done:** Dynamic Surroundings' "Disable Potion Icons in Inventory Display" is
  `false` in DEv 1.2, so tiles are drawn and the panel has something to replace.
- **HEI bookmark overlay** (builder's call, recorded here). DEv 1.2 runs HadEnoughItems 4.36.1
  with `bookmarkOverlayEnabled=true`, which draws a bookmark column left of the GUI — the same
  area as the effect tiles — from its own screen-draw hooks. No HEI detection and no HEI
  dependency: register the panel's `DrawScreenEvent.Post` handler at a **high** priority so it
  draws after the container but before HEI's later-drawn overlay and item tooltips, which stay on
  top. If that turns out wrong in game, the priority is the one knob to turn. Test step 11 checks it.

## 11. Shipping

- srpwizcore version bump: `srpwizcore/build.gradle` `version` and `SrpWizCore.VERSION`
  (`mcmod.info` is derived from `version` now).
- The panel's own strings (§5 hint wording, command messages, the descriptions file header is
  plain text written by code) go in srpwizcore's existing `en_us.lang`; srpwizcore already ships
  `pack.mcmeta`. **No effect descriptions go in the jar** — they live in the config file (§4a).
- Content's four description keys (`potion.insanetweaks.*.4.desc`, already written, currently
  **uncommitted** in `insanetweaks/.../lang/en_us.lang`) are rule 2 data: commit them with a
  content patch bump. The DEv 1.2 instance may currently be running a content jar without them.
- Build → copy the changed jars into the DEv 1.2 `mods/` folder, removing the old jar of each
  modid.

## 12. Testing

No unit tests (srpwizcore has no test setup and this is all GUI). Manual, in DEv 1.2:

1. `/effect @p minecraft:speed 60` → hover: JEE's "+20% Speed per level." in a panel replacing
   the tile, duration underneath. No second popup anywhere.
2. `/effect @p insanetweaks:nourished 60` → our text (rule 2).
3. Any SRP effect, e.g. `/effect @p srparasites:bleed 30` → bestiary text (rule 4).
4. An effect with no description → the hint with the exact rule-1 key.
5. Six or more effects at once → hover the top, a middle and the bottom tile: the right one
   expands each time.
6. Open the recipe book, then hover again. Creative inventory, effects tab-independent.
7. An EBW curse (e.g. via an EBW curse spell) → purple panel, no duration line.
8. `/effectdesc missing` → blank lines appended to `config/srpwizcore/effect_descriptions.lang`,
   counts in chat. Fill one in, F3+T, hover: the new text shows, no restart. Run the command again:
   nothing is appended twice. Give vanilla Speed a rule-1 line: it now beats JEE's text.
   Delete the file and restart: it is recreated with only its header.
9. `effectPanel.enabled=false` (config GUI) → vanilla tiles, no panel, no restart needed.
10. Dedicated server boot with srpwizcore installed → no `invalid side` / `NoClassDefFoundError`
    in the server log.
11. With HEI bookmarks shown on the left: hover an effect tile where a bookmark overlaps it, and
    hover a bookmark over a tile — HEI's item tooltip must stay readable and on top.
