# Abomination, part two: finishing the element, its economy, and a balance pass (insanetweaks)

**Date:** 2026-08-08
**Mod:** `insanetweaks` (content) — every change below is gameplay, so content owns all of it
**Status:** designed, not implemented
**Target EBW:** 4.3.19 (CurseMaven file id `8320066`) — every bytecode claim was read off that jar
**Predecessor:** `2026-08-08-ebw-abomination-element-design.md`. That spec made Abomination a real
`Element`; read it first, because its §9 exclusion table is what this spec partly undoes.

## Problem

Abomination is a real element as of insanetweaks 1.15.2, but it is an element with nothing behind
it. Three gaps, in descending order of how much they matter:

1. **No source the player can aim at.** The spells appear in EBW's generic loot (`treasure` /
   `looting` / `trades` are open on 12 of 14), which makes them a lottery, not a goal. Nothing in
   the world says "this is where Abomination comes from".
2. **The element's own items are hidden.** `ebwizardry:spectral_dust` meta 8 and
   `ebwizardry:magic_crystal` meta 8 already exist — the metadata *is* `Element.ordinal()` — but
   the previous spec hid them from creative and JEI because they had no textures and led nowhere.
3. **The numbers were tuned for a mechanic that no longer exists.** Costs and cooldowns were set
   while a foreign-spell mana penalty multiplied them; that penalty is gone, and what is left is
   incoherent (see §3).

There is also one latent crash and one silent gap, both measured:

- **Receptacle NPE.** `BlockReceptacle.PARTICLE_COLOURS` is an `EnumMap` populated for exactly the
  eight vanilla elements. `randomDisplayTick` does `int[] colours = PARTICLE_COLOURS.get(element)`
  and immediately reads `colours[0]` with no null check. An Abomination-dust receptacle would NPE
  on the client every render tick.
- **Missing loot table.** `WizardryLoot.RUINED_SPELL_BOOK_LOOT_TABLES` is built from
  `Element.values()`, so EBW *already registers* `ebwizardry:gameplay/imbuement_altar/ruined_spell_book_abomination`.
  Only the file is missing — that is the `Couldn't find resource table` WARN in the 1.15.1 launch log.

## Scope

**In scope.** Element completion (§1), a second crafting currency and the recipes around it (§2),
a spell and wand-capacity balance pass (§3).

**Out of scope, deliberately.** `EntitySimWizard` and `EntitySimBattlemage` as *entities* — their
own spawning in infested zones, tier scaling, strength, AI, and which spells they cast. That is a
separate session and a separate spec. This spec touches those mobs through exactly three files:
their existing loot tables. The consequence is handled in §2.3.

**Also out of scope.** Abomination wizard armour and wands under the `ebwizardry` namespace, i.e.
"full element" in the sense of EBW wizards, shrines, obelisks and trades. The predecessor spec's
exclusion mixins stay. §4 records what would have to change if that ever happens.

## §1 Finishing the element

### 1.1 Spectral dust and crystal, metadata 8

`ItemSpectralDust.getModel` and `ItemCrystal.getModel` derive
`ebwizardry:spectral_dust_<name>` / `ebwizardry:crystal_<name>` from the element name, and their
model-registration loops iterate the whole enum. Our mixins narrow only `getSubItems` — the model
loops were never redirected, so EBW is *already* asking for `spectral_dust_abomination` and
`crystal_abomination` and silently getting nothing.

Ship both models and both textures **from our jar, under `assets/ebwizardry/`**. This is safe
because they are filenames EBW does not have: resource packs merge at file granularity, so we add
without replacing. Then delete the two `getSubItems` redirects
(`MixinItemSpectralDustElements`, `MixinItemCrystalElements`) so the items appear in creative and
JEI.

🚨 **The crystal *block* stays hidden.** `BlockCrystal` renders through a single
`assets/ebwizardry/blockstates/crystal_block.json` that lists every variant. Shipping our own copy
would *replace* EBW's file and take the other eight variants with it. `MixinBlockCrystalElements`
therefore stays exactly as it is. This is the one place where "add a file to their domain" does not
work, and the reason is the difference between one-file-per-name (models) and one-file-for-all
(blockstates).

### 1.2 Receptacle particles — no mixin needed

`BlockReceptacle.PARTICLE_COLOURS` is declared `public static final Map<Element, int[]>` and built
with `Maps.newEnumMap(Element.class)`. The reference is final; **the map is not**. So the fix is one
`put` of an Abomination colour triple, not an injection.

This works because of ordering. `EnumMap`'s key universe comes from `Element.class.getEnumConstants()`
at construction, and Forge's `EnumHelper.addEnum` clears that cache when it appends the constant. We
register the element from the `@Mod` constructor; `BlockReceptacle.<clinit>` runs later, during block
registration. By then the universe is nine elements wide.

Do the `put` in content's `init` phase, guarded on `ModElements.EXTENDED`. EBW is `required-after` in
our `@Mod` dependencies, so no `Loader.isModLoaded` guard is needed. The map is a plain colour table
with no client-only types, so this is side-safe.

Result: the crash becomes the feature it should have been — Abomination dust glows red in a
receptacle, like every other element glows its own colour.

### 1.3 The ruined spell book loot table

Ship `assets/ebwizardry/loot_tables/gameplay/imbuement_altar/ruined_spell_book_abomination.json`.
Structurally a copy of EBW's `ruined_spell_book_fire.json` with `"elements": ["fire"]` changed to
`"elements": ["abomination"]`.

Two things make this cheaper than it looks. EBW registers the ResourceLocation itself (the array is
built from `Element.values()`), so no `LootTableList.register` call is needed on our side. And the
`ebwizardry:random_spell` loot function filters candidates by loot context, so `call_of_demise`
(`treasure: false`) drops out on its own — no per-spell list to maintain.

**To verify at implementation time:** 1.12.2's `LootTableManager` reads
`/assets/<domain>/loot_tables/<path>.json` through the classloader, and every mod jar is on the
LaunchClassLoader, so our file should be found. If a launch shows the WARN persisting, fall back to
`LootTableLoadEvent` and build the table in code.

### 1.4 JEI: three of the four exclusions come out

The predecessor spec added four JEI redirects (`MixinJeiImbuementAltarElements` ×3,
`MixinJeiArcaneWorkbenchElements` ×1) because Abomination had no textures and led nowhere. Now that
it does, each is re-decided on its own merits:

| target | verdict | why |
|---|---|---|
| `ImbuementAltarRecipeCategory.generateCrystalRecipes` | **remove redirect** | crystal item meta 8 now has a model; the recipe genuinely works |
| `generateCrystalBlockRecipes` | **keep redirect** | the crystal *block* stays hidden (§1.1), so a JEI entry would show an output with no model |
| `generateArmourRecipes` | **remove redirect** | it filters itself — see below |
| `ArcaneWorkbenchRecipe.generateCrystalStacks` | **remove redirect** | Abomination crystals charge a wand like any other |

🚨 `generateArmourRecipes` needs no exclusion because EBW already wrote the guard: it calls
`TileEntityImbuementAltar.getImbuementResult(...)` and then `if (output.isEmpty()) continue;`.
`getArmour(ABOMINATION, …)` is a registry lookup that misses, returns null, and `new ItemStack(null)`
is empty — so the Abomination rows drop out on their own. The player still sees every other
element's armour recipe, which is how they learn the altar exists, and the Abomination row appears
automatically the day `living_warlock_armour` is registered (§4). Do not add a mixin for this.

### 1.5 Element icon

`element_icon_abomination.png` is still a byte-copy of EBW's "None" icon. Replace it with real art.
Carried over from the predecessor spec, unfinished.

## §2 Economy: a second currency

### 2.1 The problem with one currency

`itLivingNucleus` gates five of our recipes: `adaptation_upgrade`, `living_wand`, `living_aegis`,
`living_spellblade`, `parasite_living_nunchaku`. It is also a key component of SRParasites' own
weapons and of every parasite addon in the pack, including `swparasites`. Every magical thing we
gate on it competes with the whole pack's parasite economy for the same drops.

It is also thematically wrong for one of them: `adaptation_upgrade` is a *wand* upgrade — the thing
that lets a foreign wand cast Abomination. Gating it on parasite meat says nothing about what it does.

### 2.2 The split

| currency | made from | gates |
|---|---|---|
| `itLivingNucleus` (unchanged) | living core + adapted drops + infected flesh | `living_aegis`, `living_spellblade`, `parasite_living_nunchaku` |
| **`insanetweaks:magic_nucleus`** (new, ore name `itMagicNucleus`) | Abomination spectral dust + SRP drops that nothing else uses | `adaptation_upgrade`, `living_wand` |

The rule a player can feel: **meat gates weapons, harvested magic gates wands.** Every future recipe
assigns itself.

SRP inputs chosen because a repo-wide grep found no other recipe using them:
`srparasites:ada_yelloweye_drop` (Yelloweye Bone — we already have a `yelloweye_gland` spell and
summon yelloweyes, so the theme is established), `srparasites:ada_burrower_drop` (Figment),
`srparasites:ada_viscera_drop` (Chipped Motherly Membrane), `srparasites:hive_scrap`.

Concrete recipes — starting points, tunable:

```
spectral dust (abomination) x2          magic_nucleus x1
   Y                                       B
  FCF                                     DKD
   H                                       V

Y srparasites:ada_yelloweye_drop        B srparasites:ada_burrower_drop
F srparasites:assimilated_flesh         D ebwizardry:spectral_dust meta 8
C ebwizardry:magic_crystal (meta 0)     K ebwizardry:magic_crystal meta 8
H srparasites:hive_scrap                V srparasites:ada_viscera_drop
```

`magic_nucleus` consuming an **Abomination crystal** is deliberate: it gives the crystal a job
beyond looking good in a chest, and it makes the imbuement altar a step on the way to the wand
upgrade rather than a side attraction. Its shape mirrors `living_nucleus` (`" S "`, `"FLF"`,
`" M "`) so the two read as siblings.

`ruined_spell_book`: shapeless, `minecraft:book` + 2 dust → 1 `ebwizardry:ruined_spell_book`.
Combined with the four dust the altar consumes, one crafted spell book costs six dust.

`adaptation_upgrade` and `living_wand` keep their existing shapes; only the `itLivingNucleus`
ingredient becomes `itMagicNucleus`.

`magic_nucleus` gets an ore name for symmetry with `ModOreDict`, even though no other mod provides a
counterpart today. It costs one line and means an addon can substitute one later.

🚨 **`adaptation_upgrade`'s old `itLivingNucleus` recipe is replaced, not kept alongside.** Keeping
both would leave the bottleneck in place and forfeit half the point. No world data is involved
(recipes are not saved), so this is safe mid-playthrough; a player mid-progression simply sees a
different recipe in JEI.

### 2.3 Where the dust comes from

Two sources, deliberately unequal:

1. **Drops from `sim_wizard` / `sim_battlemage`**, added to the three existing per-tier loot tables
   (`entities/sim_wizard`, `sim_wizard_adept`, `sim_wizard_master`) as two new pools:

   | table | dust | spell book |
   |---|---|---|
   | `sim_wizard` (novice) | 0–1, ~⅓ chance of any | — |
   | `sim_wizard_adept` | 1–2 | ~3% |
   | `sim_wizard_master` | 2–4 | ~8% |

   Both expressed with weighted `empty` entries, matching the `scavenged_focus` pool already in
   `sim_wizard_master`. `EntitySimBattlemage` inherits these through `getLootTable` and its ADEPT
   tier floor, so it needs no table of its own. The spell-book pool uses the same
   `ebwizardry:random_spell` function as §1.3, so gated spells exclude themselves.
2. **A workbench recipe** turning SRP drops plus a magic crystal into dust (§2.2). Deliberately
   expensive and low-yield.

The second source exists because of the scope boundary. `EntitySimWizard` has **no spawn of its
own** — it is produced only when SRP assimilates an `ebwizardry:wizard` / `evil_wizard` (or the ASC
equivalents), so its supply is the product of two things we do not control. Giving those mobs their
own spawning is exactly the separate session this spec excludes. Without a craftable path,
`adaptation_upgrade` would come out of this spec *harder to get than it is today* — a regression
lasting until that session lands.

When the entity session gives the mobs a real spawn, the drop becomes the good path and the recipe
stays as the grind fallback. Nothing has to be undone.

Dust also crafts `ebwizardry:ruined_spell_book`, so the imbuement altar has a steady fuel supply and
the altar path does not depend on a rare drop either.

### 2.4 The two acquisition paths, end to end

```
sim_wizard / sim_battlemage ──drop──> spectral dust (abomination)
SRP leftovers + magic crystal ──craft──> spectral dust (abomination)
                                            │
        ┌───────────────────────────────────┼──────────────────────────┐
        │                                   │                          │
        │                            4 receptacles                 craft
        │                                   │                          │
        │                            imbuement altar            ruined_spell_book
        │                              │           │                   │
        │              magic crystal ──┘           └── ruined book ─────┘
        │                    │                              │
        └── + SRP leftovers ─┴──> magic_nucleus     random Abomination
                                        │              spell book
                             adaptation_upgrade,
                                  living_wand
```

Plus the pre-existing EBW generic loot, which stays open — the previous spec's decision, unchanged.

## §3 Balance

### 3.1 The rule

The wand's full pool is the unit of measure. Two bands, and nothing between them:

- **Tool** — cost ≤ 5% of a full wand, cooldown 5–15 s, chargeup ≤ 30 ticks. Cast several times in
  one fight.
- **Ritual** — cost ≥ 10%, cooldown ≥ 60 s, chargeup ≥ 60 ticks. Once a fight or rarer.

**The 5–10% band is left empty on purpose.** A spell that lands there is not a compromise; it is a
spell whose role we have not decided.

### 3.2 What the rule flags in the current numbers

| spell | now | problem |
|---|---|---|
| `dispatcher_grasp` | 45 mana, 12 s CD | 1.4% of the pool — effectively free |
| `immune_bond` | 280 mana, **advanced** tier, 6 s CD | costs more than most *master* tools |
| `parasite_shroud` | 175 mana, 180 s CD | the cheapest ritual with nearly the longest cooldown |
| `yelloweye_gland` | 140 mana, 5 s CD | a tool's price at a frequency below everything else |

### 3.3 Proposed values

Tools:

| spell | cost | chargeup | cooldown |
|---|---|---|---|
| `dispatcher_grasp` | 130 | 20 | 200 (10 s) |
| `yelloweye_gland` | 150 | 30 | 240 (12 s) |
| `immune_bond` | 140 | 30 | 300 (15 s) |

Rituals:

| spell | cost | chargeup | cooldown |
|---|---|---|---|
| `summon_thrall` | 320 | 40 | 1200 (60 s) |
| `summon_fer_cow` | 340 | 45 | 1300 |
| `summon_wizard` | 420 | 55 | 1600 |
| `summon_primitive_yelloweye` | 450 | 45 | 1800 |
| `summon_light_bomber` | 500 | 45 | 1800 |
| `summon_primitive_summoner` | 560 | 60 | 2600 |
| `parasite_shroud` | 400 | 80 | 1800 (90 s) |
| `cleanse` | 500 | 60 | 3600 |
| `purifying_pulse` | 800 | 100 | 6000 |
| `call_of_demise` | 1800 | 180 | 12000 |

`call_of_demise` is unchanged — it is the capstone and the one number that was already right.
`test_projectile` is untouched (see §5.1).

These are a starting point, not a verdict; they exist so playtesting has something coherent to
adjust rather than a spread to untangle.

### 3.4 Wand capacity

`ItemWand.getManaCapacity(stack)` returns `getMaxDamage(stack)`, which is
`super.getMaxDamage(stack) * (1 + STORAGE_INCREASE_PER_LEVEL * storageUpgradeLevel)`. The base comes
from `tier.maxCharge`, set in the constructor. So the clean override is **`setMaxDamage` in
`BaseCustomWandItem`'s constructor** — not overriding `getManaCapacity`, which would bypass storage
upgrades.

EBW's stock master wand is 2500 (`Settings.masterMaxCharge`). Proposed: `LivingWandItem` 3200,
`SentientWandItem` 4400, both config-driven under `gear.wands`. Our wands are the top-end gear every
mage wants (the predecessor spec's §8 decision), so it is right that they carry the rituals.

This touches no other mod's wands and no shared config. If the unified cross-mod mana pool
(Trinkets and Baubles + EBW) ever happens, these two numbers are the only thing to revisit — the
spell costs stay valid because they are expressed against whatever pool exists.

## §4 Extension points

Recorded so a future session does not have to re-derive them.

**The imbuement altar's armour path is left open on purpose.** With four Abomination receptacles,
`getImbuementResult` tries `ItemWizardArmour.getArmour(ABOMINATION, class, slot)`, which is a
registry lookup for `ebwizardry:<class>_<piece>_abomination`. Nothing is registered, so it returns
null and the altar does nothing.

We are **not** closing this combination. It costs nothing to leave open: the altar silently produces
nothing (the same as any mismatched dust), and JEI never advertises it because
`generateArmourRecipes` skips empty outputs (§1.4). Meanwhile the player still sees every other
element's armour recipe, which is how they learn what the altar is for. The empty slot is the
natural hook for a future **`living_warlock_armour`** — Abomination armour of the WARLOCK class.
Registering those four items under the `ebwizardry` namespace makes both the altar and its JEI entry
light up with no other change anywhere.

**Promotion to a "full" element** (EBW wizards, shrines, obelisks, trades) needs 4 wands plus at
least the 4 WIZARD-class armour pieces registered as `ebwizardry:` names, because `getWand` and
`getArmour` are registry lookups against that namespace and nothing else. Once they exist, the
predecessor spec's redirects on `onInitialSpawn` (three classes), `getRandomItemOfTier`,
`populateSpells`, `WorldGenShrine.spawnStructure` and `WorldGenObelisk.spawnStructure` can all be
deleted.

🚨 **But shrines have a hard ceiling and it is one element away.** `BlockPedestal` packs
`meta = element.ordinal() + (natural ? ELEMENT.getAllowedValues().size() : 0)`, using `ordinal()`
rather than `ordinal() - 1` and so wasting index 0. With 8 non-magic elements that is a maximum meta
of 16 in a 4-bit field — the `ArrayIndexOutOfBoundsException` the previous session hit. Changing the
packing to `(ordinal() - 1) + (natural ? size : 0)` yields exactly 16 metas for 8 non-magic
elements: **completely full.** So Abomination pedestals are possible, and a *second* custom element
with pedestals is not, ever. (The separate `BlockCrystal` ceiling is 16 elements total; we are at 9.)

## §5 Risks and things that will bite

### 5.1 Removing a spell breaks old worlds

Found while considering deleting `test_projectile`, and worth keeping even though the deletion was
called off.

`Spell extends IForgeRegistryEntry.Impl<Spell>`, and `Spells.createRegistry` builds
`ebwizardry:spells` **without calling `disableSaving()`**. The id map therefore lives in
`level.dat`, keyed by registry name. Deleting a registered spell shows Forge's missing-registry-entry
screen on every world that has ever loaded with it.

**Decision: `test_projectile` stays.** It is `apprentice` tier, `npcs: false`, and closed to
`treasure`/`trades`/`looting`, so it never reaches a player in normal play, and leaving it costs
nothing.

**If a spell is ever removed**, it needs a `RegistryEvent.MissingMappings<Spell>` handler calling
`mapping.ignore()` — the same shape as content's existing `LegacyDormantRemapHandler`. `ignore()`
rather than `remap()` because it leaves the id slot dead and therefore cannot shuffle the numeric
ids that `ItemSpellBook` stores in item metadata.

### 5.2 Files in another mod's resource domain

Adding `assets/ebwizardry/...` files works for names EBW does not ship (models, textures, the loot
table). It does **not** work for files EBW does ship — see §1.1 on blockstates. Rule of thumb before
adding any file under a foreign domain: `unzip -l <their jar> | grep <path>` first; a hit means the
file is theirs and you would be replacing it.

### 5.3 Recipe change mid-playthrough

`adaptation_upgrade` changing inputs is invisible to save data but visible to a player who had
gathered materials for the old recipe. Acceptable; worth a line in the changelog.

### 5.4 Version bump

Content's version lives in **two** places and the second is the one that drifts:
`insanetweaks/build.gradle` (`version`) and `InsaneTweaksMod.VERSION`. The manifest and
`mcmod.info` derive from the first; `@Mod` reports the second. Bump both, or the log will name a
version nobody is running.

### 5.5 Verification checklist

- Fresh launch, `logs/cleanmix.log`: no `InvalidInjectionException`, `Scanned 0`, or `VerifyError`.
  This spec removes mixins and adds none, so the only expected change is three fewer `APPLY` lines.
- `logs/latest.log`: the `ruined_spell_book_abomination` WARN is gone.
- No `Unable to load model` for `spectral_dust_abomination` / `crystal_abomination`.
- Creative and JEI show the dust and the crystal item; the crystal **block** still shows eight
  variants, not nine.
- JEI imbuement altar category: an Abomination crystal recipe is present, an Abomination crystal
  *block* recipe is not, and no Abomination armour row appears.
- Place a receptacle, insert Abomination dust, watch it for several seconds on the client — this is
  the NPE path.
- Imbuement altar: ruined spell book + four Abomination receptacles yields an Abomination spell book.
- Craft `magic_nucleus`, then `adaptation_upgrade` and `living_wand` from it; confirm the old
  `itLivingNucleus` recipe for those two is gone and the other three still work.
- An old DEv 1.2 world loads with no missing-registry screen.
- Both wands report the new capacity in their tooltip, and a storage upgrade still raises it.
