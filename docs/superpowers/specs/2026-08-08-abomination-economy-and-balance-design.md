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

`ItemSpectralDust.getModelName` and `ItemCrystal.getModelName` derive
`ebwizardry:spectral_dust_<name>` / `ebwizardry:crystal_<name>` from the element name. Model
registration runs through `WizardryModels.registerMultiTexturedModel`, which **iterates
`getSubItems`** and calls `setCustomModelResourceLocation` per returned stack — so while our two
redirects narrow `getSubItems`, meta 8's model is not requested *at all*. That is why the log is
silent about it today: not a swallowed error, simply no registration. Removing the redirects is what
creates the demand, and the files must land in the same change.

Ship both models and both textures **from our jar, under `assets/ebwizardry/`**. This is safe
because they are filenames EBW does not have: resource packs merge at file granularity, so we add
without replacing. Then delete the two `getSubItems` redirects
(`MixinItemSpectralDustElements`, `MixinItemCrystalElements`) so the items appear in creative and
JEI.

🚨 **The crystal needs a lang key; the dust does not.** `ItemCrystal` overrides
`getUnlocalizedName(ItemStack)` as `"item." + getModelName(stack)`, so it asks for
`item.ebwizardry:crystal_abomination.name` — a key EBW ships for its eight elements and cannot ship
for ours. Without it the item's name renders as the raw key, and JEI cannot find it by search.
`ItemSpectralDust` has no such override: all eight dusts share `item.ebwizardry:spectral_dust.name`
and the element is carried by the texture alone. Supply the crystal key from our own lang file
(keys are global; escape the colon the way EBW does).

**The crystal *block* stays hidden — by choice, not necessity.** An earlier draft claimed
`BlockCrystal` renders from one `crystal_block.json` listing every variant, so ours would replace
EBW's file. That is wrong. `WizardryModels` uses `new StateMap.Builder().withName(ELEMENT)
.withSuffix("_crystal_block")`, and EBW ships **eight separate files** — `fire_crystal_block.json`,
`ice_crystal_block.json`, and so on. A ninth would slot in beside them exactly like the item models
in the paragraph above.

Nor is the blockstate property narrowed: `MixinBlockCrystalElements` redirects `getSubBlocks` only,
and its own javadoc explains why it must — `getStateFromMeta` has to keep seeing the whole enum or a
blockstate round trip resolves to the wrong element. `BlockCrystal.ELEMENT` is built during block
registration, after the `@Mod` constructor appended Abomination, so it genuinely has nine values.

So un-hiding it is ordinary work, not a wall: one blockstate file, one block model, one texture, and
a `crystal_block_to_crystals_abomination` recipe to mirror EBW's eight. It stays out of scope here
because it is art plus a recipe for a decorative block, and §1.4b's guard is what makes leaving it
out safe — without that guard the altar happily mints a metadata with no blockstate file behind it
and no way back.

🚨 **But hiding it is not the same as blocking it, and the altar does not care.**
`getImbuementResult` accepts `Item.getItemFromBlock(crystal_block)` as well as the crystal item, so
a player can feed nine magic crystals' worth of crystal block plus four dust into an altar and get
`crystal_block` metadata 8 back: a block with no blockstate variant, no model, and no
`crystal_block_to_crystals` recipe to reverse it. That is item loss, not a cosmetic gap, and it
becomes reachable the moment §2.3 makes dust cheap. §1.4b's guard must cover this branch too —
same key, "the result has no registered form", not "the element is Abomination".

### 1.2 Receptacle particles

`BlockReceptacle.PARTICLE_COLOURS` is a `Map<Element, int[]>` populated for the eight vanilla
elements, and **five** places read it and dereference the result unchecked — `randomDisplayTick`,
`TileEntityImbuementAltar`, `EntityRemnant.onUpdate`, `RenderImbuementAltar`, `RenderDonationPerks`.
All five are client-side. The imbuement altar is the one a player meets first, since receptacles are
how the altar works at all.

🚨 **The map cannot be written to after the fact, and the declaration hides that.** The field is
`public static final Map`, and `<clinit>` fills a `Maps.newEnumMap(Element.class)` — but its last
line is:

```java
PARTICLE_COLOURS = Maps.immutableEnumMap((Map) map);
```

Guava's `ImmutableEnumMap.put` throws `UnsupportedOperationException` unconditionally. Because the
*field* is declared as plain `java.util.Map`, a `PARTICLE_COLOURS.put(...)` call site compiles
without a warning and dies at runtime. The first draft of this spec called for exactly that `put`; it
would have been a hard crash on every correctly-configured launch, on both sides. Reflection is not
an escape either — the pack runs Java 25, where the `Field.modifiers` trick no longer works.

So it is a mixin, on the one writable moment: a `@Redirect` of the single `Maps.immutableEnumMap`
invoke in `BlockReceptacle.<clinit>`, which adds Abomination to the still-mutable builder and then
delegates. Verified descriptor:

```
Lcom/google/common/collect/Maps;immutableEnumMap(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;
```

One occurrence in the class. Fixing it here covers all five read sites at once.

The ordering reasoning that survives from the first draft: the builder is an `EnumMap`, whose key
universe comes from `Element.class.getEnumConstants()` at construction, and Forge's
`EnumHelper.addEnum` clears that cache when it appends a constant. We register from the `@Mod`
constructor and `<clinit>` runs later, during block registration, so the universe is nine elements
wide by then. That was always right — it was just attached to the wrong object.

Routing: content, late config (`mixins.insanetweaks.late.json`), alongside the other EBW-targeting
mixins. `BlockReceptacle` carries no class-level `@SideOnly`, so the mixin is side-safe.

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
| `generateCrystalBlockRecipes` | **keep redirect** | the crystal *block* stays hidden by choice (§1.1), so a JEI entry would advertise an output we ship no blockstate for |
| `generateArmourRecipes` | **remove redirect, but only once §1.4b lands** | it filters itself *after* the crash below is guarded |
| `ArcaneWorkbenchRecipe.generateCrystalStacks` | **remove redirect** | Abomination crystals charge a wand like any other |

### 1.4b The armour branch crashes, and it is not only a JEI problem

An earlier draft of this spec said `generateArmourRecipes` needs no exclusion because EBW already
guards it with `if (output.isEmpty()) continue;`. **That guard is never reached.** Read
`TileEntityImbuementAltar.getImbuementResult` to the end of its armour branch:

```java
ItemStack result = new ItemStack(ItemWizardArmour.getArmour(receptacleElements[0], …));
result.setTagCompound(input.getTagCompound());
((IManaStoringItem) result.getItem()).setMana(result, …);
```

For Abomination the lookup misses, so `result` is empty, so `result.getItem()` is `Items.AIR`, and
the cast to `IManaStoringItem` throws `ClassCastException` — one line before the method returns and
long before JEI's `isEmpty` check runs.

🚨 **Do not write the guard as `getArmour(...) == null`.** What a missed lookup yields is not
settled by reading one declaration: `Item.REGISTRY` is declared `RegistryNamespaced`, which returns
null, but Forge substitutes a wrapper with a default key of `minecraft:air`, which returns `AIR`.
Asking `instanceof IManaStoringItem` is false in both cases and is also exactly the question the
crashing cast asks. A later "simplification" to a null check would silently reopen the crash on
whichever of the two readings is wrong.

🚨 **And `getImbuementResult` is not a JEI method.** The altar's own tile entity calls it every time
its contents change. So a player who sets up four Abomination receptacles and drops in a plain
wizard robe crashes the server. That path has been unreachable only because the dust had no source
at all; §1.1 and §2.2 are precisely what give it one.

The fix is one guard at the source, and it must be phrased so it disappears by itself:

```java
@Inject(method = "getImbuementResult", at = @At("HEAD"), cancellable = true)
```

reproduce EBW's own branch condition, then ask whether
`ItemWizardArmour.getArmour(element, armourClass, slot)` yields something that is actually an
`IManaStoringItem`. If it does, return and let EBW run. If it does not, set the return value to
`ItemStack.EMPTY`.

Keying on **"the lookup produced no usable armour"** rather than on **"the element is Abomination"**
is the whole point: the day `living_warlock_armour` is registered (§4) the guard stops firing on its
own, with no edit. It also covers any other mod that appends an element without armour.

With that guard in place the JEI armour rows genuinely do filter themselves, and the redirect on
`generateArmourRecipes` can go. Not before.

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
summon yelloweyes, so the theme is established), `srparasites:ada_vermin_drop` (Corrosive Mucus),
`srparasites:ada_viscera_drop` (Chipped Motherly Membrane), `srparasites:hive_scrap`,
`srparasites:assimilated_flesh`.

🚨 **`srparasites:ada_burrower_drop` does not exist, and its lang entry says otherwise.** An earlier
draft used it, on the strength of `item.srparasites.ada_burrower_drop.name=§cFigment` sitting in
SRP's `en_us.lang`. There is no registration, no model and no texture for it — the lang line is an
orphan. Only ten `ada_*_drop` items actually register. The failure mode is quiet: `safeItem` records
a miss, `registerFallback` drops the recipe, and you get an item that exists, is ore-dicted, gates
two other recipes and is uncraftable, with one warn line to show for it.

**Verify an SRP ingredient against `SRPItems` bytecode, or at minimum against
`assets/srparasites/models/item/`, never against the lang file.** `ada_vermin_drop` replaced it:
same `§d` tier as `ada_viscera_drop`, and both are drops SRP's own tooltips pointedly do *not*
describe as weapon components — which is exactly the line this currency is drawn along.

Concrete recipes — starting points, tunable:

```
spectral dust (abomination) x2          magic_nucleus x1
   Y                                       B
  FCF                                     DKD
   H                                       V

Y srparasites:ada_yelloweye_drop        B srparasites:ada_vermin_drop
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

🚨 **This makes an EBW loot-only item renewable, deliberately.** EBW ships no recipe for
`ruined_spell_book` at all — in the whole 4.3.19 jar it appears only in three loot tables. Ours
makes it craftable, and because §1.3's altar table carries no `tiers` key, `RandomSpell` rolls over
every tier: **master Abomination spell books become farmable**, at roughly one ruined book plus
three magic crystals plus twelve SRP drops per roll. Reviewed and accepted on 2026-08-08 — the
crystal-and-drop cost is the intended gate. If it ever proves too generous, the one-line lever is a
`"tiers": ["novice", "apprentice", "advanced"]` on the altar pool, which pushes master spells back
onto the sim-wizard drop and natural loot.

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

   🚨 **These three tables hard-code the dust metadata as `8`, and the recipes deliberately do not.**
   Loot-table JSON has no way to compute `ModElements.ABOMINATION.ordinal()`; a code recipe does.
   The asymmetry is accepted, but it has a sharp edge: if the pack ever gains a second
   element-appending mod, the **recipes follow the new ordinal and the drops do not** — which is
   worse than uniform hard-coding, because half the economy silently switches to another element's
   dust. These three files are the first place to look, and the only defence is that no other mod in
   DEv 1.2 appends an element.
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

Two bands, and nothing between them:

- **Tool** — cost ≤ 5% of the yardstick wand, cooldown 5–15 s, chargeup ≤ 30 ticks. Cast several
  times in one fight.
- **Ritual** — cost ≥ 10%, cooldown ≥ 60 s, chargeup ≥ 60 ticks. Once a fight or rarer.

**The 5–10% band is left empty on purpose.** A spell that lands there is not a compromise; it is a
spell whose role we have not decided.

🚨 **The yardstick is the fully-evolved Living Wand, and the percentage is of what the player
actually pays.** Two corrections, both from a review that caught the first draft measuring against
a number that was never real:

- **Our wands hold 4000 and 6500, not EBW's stock 2500.** `LivingWandItem` and `SentientWandItem`
  have called `setMaxDamage(4000)` / `setMaxDamage(6500)` in their constructors all along. Anything
  in an earlier draft that reasoned from 2500 was wrong.
- **No player ever pays the raw JSON cost on these wands.** `BaseCustomWandItem.calculateModifiers`
  applies a cost reduction of 0.05→0.20 on the Living Wand (by evolution progress) and a flat 0.20
  on the Sentient. Measuring the rule against the raw number describes a price nobody is charged.

Taking the Living Wand at 4000 with its full 20% discount, effective cost is `raw × 0.8`, so the
bands become arithmetic on the raw JSON number:

| band | raw cost |
|---|---|
| tool | ≤ 250 |
| *(deliberately empty)* | 251–499 |
| ritual | ≥ 500 |

The Living Wand is the yardstick because it is the entry-level of our two. The Sentient Wand is
supposed to make rituals feel cheap — that is what being the endgame wand means — so measuring
against it would collapse the distinction by design rather than by accident.

### 3.2 What the rule flags in the current numbers

| spell | now | problem |
|---|---|---|
| `dispatcher_grasp` | 45 mana, 12 s CD | 1.4% of the pool — effectively free |
| `immune_bond` | 280 mana, **advanced** tier, 6 s CD | costs more than most *master* tools |
| `parasite_shroud` | 175 mana, 180 s CD | the cheapest ritual with nearly the longest cooldown |
| `yelloweye_gland` | 140 mana, 5 s CD | a tool's price at a frequency below everything else |

### 3.3 Proposed values

Tools — all comfortably under the 250 ceiling:

| spell | cost | chargeup | cooldown |
|---|---|---|---|
| `dispatcher_grasp` | 130 | 20 | 200 (10 s) |
| `immune_bond` | 140 | 30 | 300 (15 s) |
| `yelloweye_gland` | 150 | 30 | 240 (12 s) |

Rituals — every one at or above the 500 floor, and every chargeup at or above 60:

| spell | cost | chargeup | cooldown |
|---|---|---|---|
| `summon_thrall` | 520 | 60 | 1200 (60 s) |
| `summon_fer_cow` | 540 | 60 | 1300 |
| `parasite_shroud` | 600 | 80 | 1800 (90 s) |
| `summon_wizard` | 640 | 60 | 1600 |
| `summon_primitive_yelloweye` | 700 | 60 | 1800 |
| `summon_light_bomber` | 760 | 60 | 1800 |
| `cleanse` | 760 | 60 | 3600 |
| `summon_primitive_summoner` | 850 | 60 | 2600 |
| `purifying_pulse` | 1100 | 100 | 6000 |
| `call_of_demise` | 1800 | 180 | 12000 |

`call_of_demise` is unchanged — it is the capstone and the one number that was right from the start.
`test_projectile` is untouched (see §5.1).

Two things moved from the first draft, both because that draft measured against a capacity the wands
never had. **Every ritual under 500 went up** — the cheap summons and `parasite_shroud` were priced
as tools by the corrected arithmetic. And **five rituals had their chargeup raised to 60**
(`summon_thrall`, `summon_fer_cow`, `summon_primitive_yelloweye`, `summon_light_bomber`,
`summon_wizard`, previously 40–55): the rule names three axes and the first draft only enforced two,
so a "ritual" could be cast with a shorter wind-up than a tool.

These are a starting point, not a verdict; they exist so playtesting has something coherent to
adjust rather than a spread to untangle.

### 3.4 Wand capacity

`ItemWand.getManaCapacity(stack)` returns `getMaxDamage(stack)`, which is
`super.getMaxDamage(stack) * (1 + STORAGE_INCREASE_PER_LEVEL * storageUpgradeLevel)`. The base comes
from `tier.maxCharge`, set in the constructor. So the clean override is **`setMaxDamage` in
`BaseCustomWandItem`'s constructor** — not overriding `getManaCapacity`, which would bypass storage
upgrades.

🚨 **This task is exposing existing values to config, not changing them.** EBW's stock master wand
is 2500, but ours are **4000 and 6500** — `setMaxDamage` in each subclass's constructor, there all
along. The config defaults must be exactly those two numbers.

Lowering them is not a balance lever, it is data loss. **Mana is stored as damage**, and
`getMana = capacity − damage`, with nothing clamping the result. Drop the Sentient Wand from 6500 to
4400 and every existing wand below ~32% charge reports negative mana: `isManaEmpty` tests `== 0` so
the wand claims to be *not* empty and keeps its melee attribute modifiers, `canCast` fails for every
spell, and the durability bar renders a negative width. It is recoverable by recharging, but it
reads as a corrupted item and it hits every wand in every existing world.

So: defaults 4000 and 6500, and a clamp in `onUpdate` so that a pack author who *does* lower the
config gets a wand pinned to zero rather than one lying about being non-empty. Once config owns the
number, delete the `setMaxDamage` literals — two authoritative-looking constants that no longer
decide anything are worse than none.

This touches no other mod's wands and no shared config. If the unified cross-mod mana pool
(Trinkets and Baubles + EBW) ever happens, these two numbers are the only thing to revisit — the
spell costs stay valid because they are expressed against whatever pool exists.

## §4 Extension points

Recorded so a future session does not have to re-derive them.

**The imbuement altar's armour path is left open on purpose.** With four Abomination receptacles,
`getImbuementResult` tries `ItemWizardArmour.getArmour(ABOMINATION, class, slot)`, which is a
registry lookup for `ebwizardry:<class>_<piece>_abomination`. Nothing is registered, so it returns
null and the altar does nothing.

We are **not** closing this combination — but it does not "cost nothing" to leave open, as an
earlier draft claimed. It costs the crash in §1.4b, and the guard there is what makes leaving it
open safe. With that guard the altar silently produces nothing (the same as any mismatched dust) and
JEI never advertises it, while the player still sees every other element's armour recipe, which is
how they learn what the altar is for.

The empty slot is the natural hook for a future **`living_warlock_armour`** — Abomination armour of
the WARLOCK class. Because the guard keys on "the lookup produced no usable armour" rather than on
the element's identity, registering those four items under the `ebwizardry` namespace makes the
guard stop firing, and both the altar and its JEI entry light up, with no other change *to the altar
path*. The altar reads `armourClass` off the input, so an elementless warlock hood imbues to
`warlock_hood_abomination` and a wizard hat to `wizard_hat_abomination`.

🚨 **But `ItemWizardArmour.applyUpgrade` has the same unguarded shape** — `getArmour(this.element,
armourClass, slot)`, then a stack built straight from it, then a cast. It is unreachable today
because it needs a piece that already carries an element whose SAGE / BATTLEMAGE / WARLOCK
counterpart is missing, and no Abomination piece can exist at all. Registering **one** class without
the other three would make it reachable. So: register all four classes, or extend §1.4b's guard to
that method too.

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
