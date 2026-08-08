# Abomination: a real Wizardry element instead of the `Element.MAGIC` hack (insanetweaks)

**Date:** 2026-08-08
**Mod:** `insanetweaks` (content)
**Status:** designed, not implemented
**Target EBW:** 4.3.19 (CurseMaven file id `8320066`) — every bytecode claim below was read off that jar

## Problem

Every insanetweaks spell declares `"element": "magic"` and is therefore, to Electroblob's Wizardry,
the elementless `Element.MAGIC` — the one whose lang entry literally reads `element.magic=None`.
Two consequences:

1. **The identity is faked in exactly one place.** `MixinGuiSpellInfo` redirects
   `Element.getFormattingCode()` / `getDisplayName()` inside `GuiSpellInfo.drawForegroundLayer` and
   `SpellDisplayUtils` substitutes red `"Abomination"`. Every other surface still says grey "None":
   spell-book and scroll tooltips, the wand HUD, the wizard handbook, the arcane workbench, JEI.
2. **The mechanic is keyed on a string, not on the element.** `ArcaneBridgeEventHandler` decides
   what counts as our magic with
   `InsaneTweaksMod.MODID.equals(spellId.getResourceDomain())`, and it pays for that proxy with a
   custom foreign-spell mana-cost multiplier layered on top of EBW's own systems.

There is also collateral: **Ancient Spellcraft occupies the same fake element.** ASC 1.8.3 ships
`gui.ancientspellcraft:element.magic=Ancient`, i.e. it relabels `Element.MAGIC` as "Ancient" in its
own GUIs. Our spells are therefore labelled *Ancient* wherever ASC does the drawing. ASC does **not**
add an element of its own — verified: its `@Mod` class `<clinit>` calls `EnumHelper.addEnum` for
`SpellType.METAMAGIC` and two `SpellCastEvent.Source` constants, and nothing else.

### The blocker was never metadata

The long-standing belief was that elements are item metadata and there is no room left. That is not
what the code says. `Element` is a plain `enum` in `electroblob.wizardry.constants` with eight
constants; the blocker was that a normal mod cannot append an enum constant. Metadata does encode
the element in four places, and none of them is out of room:

| site | encoding | real ceiling |
|---|---|---|
| `ItemCrystal` (`magic_crystal`) | meta = `Element.ordinal()` | 32767 |
| `ItemSpectralDust` | meta = `Element.ordinal()` | 32767 |
| `BlockCrystal` | `PropertyEnum<Element> ELEMENT` | **16** (4-bit block meta) |
| `BlockRunestone` | `PropertyEnum<Element> ELEMENT` | **16** |

Eight of sixteen block-metadata slots are used. A ninth element fits with room to spare.

### And it is no longer a blocker at all

EBW 4.3.19 ships `electroblob.wizardry.api.WizardryEnumHelper`, a public API whose whole purpose is
this:

```java
addTier(String, int, int, int, Style, String)
addElement(String enumName, Style colour, String unlocalisedName, String modid)
addSpellType(String, String)
addSpellContext(String, String)
```

`addElement` is a thin wrapper over Forge's `EnumHelper.addEnum(Element.class, …)`. The private
three-argument `Element` constructor is `(Style colour, String unlocalisedName, String modid)` and
builds the icon path itself as
`<modid>:textures/gui/container/element_icon_<unlocalisedName>.png` — **the icon lives in our
namespace, not EBW's**. `Element.fromName` matches on `unlocalisedName`, so spell property JSONs
resolve a new element by name with no code involved.

That the reflective route survives this pack's runtime (Cleanroom / Java 25) is established
empirically: ASC does the same `EnumHelper.addEnum` call at mod construction and the pack boots.

## Goals

Stated by the user, in priority order:

1. **Mechanical identity.** Abomination spells are castable only from our own foci, or from a
   foreign wand carrying the Adaptation upgrade. With a real element this gate stops being a
   registry-domain string test.
2. **Visual identity everywhere**, not only in `GuiSpellInfo`.
3. **Less debt.** The custom foreign-spell mana penalty goes away; balance moves into plain numbers
   (wand mana capacity, spell costs) instead of multiplier machinery.
4. Decoupling from `Element.MAGIC`, and therefore from ASC's "Ancient".

## Scope decision: minimum now, expansion left open

EBW enumerates the full `Element.values()` in seventeen places that matter — thirteen picks and
enumerations across eleven classes, plus four more in its JEI integration. We **exclude**
Abomination from all of them rather than supplying the assets they would demand. Abomination exists
only on insanetweaks items.

This is deliberate and explicitly reversible: every exclusion reads the same
`NativeElements.values()` helper, so promoting Abomination to a fully world-generating element later
means deleting entries from one list — not rewriting call sites. See *Future work*.

## Design

### 1. `init/ModElements` — the only owner of the element

```java
public static final Element ABOMINATION;
public static final boolean EXTENDED;   // false if the reflective add failed
public static void init() {}            // no-op, forces <clinit>
public static boolean isAbomination(Spell spell);
```

Registration:

```java
WizardryEnumHelper.addElement("ABOMINATION",
        new Style().setColor(TextFormatting.RED),
        "abomination",
        InsaneTweaksMod.MODID);
```

`RED` is the only sensible free colour — EBW already uses GRAY, DARK_RED, AQUA, DARK_AQUA,
DARK_PURPLE, DARK_GREEN, GREEN and YELLOW — and it is the colour players already see from the
current hack, so nothing changes visually on the day of the switch.

### 2. Call ordering — the one hard constraint

`ModElements.init()` goes on the **first line of the `InsaneTweaksMod` constructor**, before
`OldConfigBackup.backupOldConfigIfPresent()`. It must precede two things:

- our own `ModItems.<clinit>`, which constructs wands through `super(tier, element)`;
- EBW's block registration, because **three** blocks run
  `PropertyEnum.create("element", Element.class)` in their `<clinit>` — `BlockCrystal`,
  `BlockRunestone` and `BlockPedestal` — and that call **snapshots `values()`**. An element added
  after the snapshot is not a legal blockstate value and `getStateFromMeta` would throw
  `IllegalArgumentException`. Each of the three also builds an `EnumMap<Element, MapColor>` in the
  same `<clinit>`, and an `EnumMap` captures its key universe at construction too, so the constraint
  is the same for both.

The `@Mod` constructor is a safe slot, verified rather than assumed: `WizardryBlocks.<clinit>` and
`WizardryItems.<clinit>` assign nothing but `placeholder()` results (the fields are
`@ObjectHolder`-injected), so `BlockCrystal.<clinit>` only runs inside
`RegistryEvent.Register<Block>` — after every mod constructor. `insanetweaks` declares
`required-after:ebwizardry`, so EBW is constructed first either way.

### 3. Failure mode: fall back to today, not to something new

If `addEnum` ever breaks (a Java or Cleanroom jump), `ModElements` catches, logs an error, sets
`EXTENDED = false` and leaves `ABOMINATION` **null**.

That nullability is deliberate and was tightened during implementation. A non-null impostor —
`ABOMINATION = Element.MAGIC` — would be stored or dereferenced by later code and behave silently as
Wizardry's own elementless MAGIC: a grey wizard, a `crystal_magic` blockstate, the wrong icon. Each
reads as a content bug with nothing in the log at the point of failure. Null fails loudly at the
misuse site instead, and costs nothing at the only call site that matters, because
`isAbomination()`'s fallback branch never touches the field.

The naive version of that fallback is a trap: with `ABOMINATION == Element.MAGIC`, an
`isAbomination()` written as an element comparison would also match EBW's own MAGIC-element spells
(magic missile and friends), and the casting gate would start blocking *foreign* spells on an
unadapted wand — a regression relative to the current behaviour. So `isAbomination()` branches on
`EXTENDED`:

- `EXTENDED == true` → `spell.getElement() == ABOMINATION`
- `EXTENDED == false` → the current registry-domain test

Degraded mode is then exactly the pre-change behaviour. `NativeElements` (§9) is gated on the same
flag, so all exclusion mixins become no-ops too.

🚨 **The fallback needs one mixin of its own, or degraded mode silently guts all fourteen spells.**
`SpellProperties` parses the JSON element with the **one-argument** `Element.fromName(String)`, and
that overload ends in `throw new IllegalArgumentException("No such element with unlocalised name: …")`
— it is the two-argument overload that takes a default.

It is **not** a crash, and an earlier draft of this spec was wrong to call it one. Verified on
bytecode: `SpellProperties`' constructor has an exception-table entry catching
`IllegalArgumentException` across the tier/element/type parse and rethrows it as
`JsonSyntaxException`, and each loader (`loadSpellPropertiesFromDir` and its config/built-in
siblings) catches `JsonParseException` and logs `"Parsing error loading spell property file for …"`,
finishing with `"N spells that are missing properties files!"`.

The real failure mode is worse than a crash in one respect: with `EXTENDED = false` and no mixin, all
fourteen spells load **without properties**. `Spell.getElement()` then returns `MAGIC` permanently,
and tier, cost, cooldown and the per-source `enabled` flags come from nothing at all — a broken mod
that still boots, reported only in a line most players never read.

`MixinElementFromName` closes it: `@Inject` at `HEAD`, `cancellable = true`, on
`Element.fromName(Ljava/lang/String;)`; when `!ModElements.EXTENDED` **and** the argument equals
`"abomination"`, it returns `Element.MAGIC`. It costs one boolean test on the normal path.

It belongs on the **manifest (early) route**, not the late one: early configs are installed at
coremod time, so the transformer is guaranteed to be in place before anything loads `Element` —
whereas our own `@Mod` constructor loads `Element` during mod construction, the same phase in which
late configs are queued. Confirmed against the pack's own `cleanmix.log`: early configs are prepared
on the main thread ~14 s before `LateMixinBooter` logs its first "Queued late mixin config" line.

Specifically it goes in **`mixins.insanetweaks.compat.json`**, not `mixins.insanetweaks.early.json`.
This mod's two early configs are split by target, not by timing: `early.json` holds vanilla-class
targets, `compat.json` holds early mixins aimed at a **mod** class — today
`MixinTileEntityImbuementAltar`, whose target is EBW's `TileEntityImbuementAltar`. That existing
entry is also the proof that an early config can carry an EBW target: the log shows it prepared at
coremod time and applied normally.

Note what degraded mode then widens: with `EXTENDED == false`, `SpellPredicate` and
`RandomSpell$Serializer` also resolve `"abomination"` to `MAGIC`, so an advancement or loot entry
written against the element would match EBW's own MAGIC spells. Nothing in the pack does that today.

### 4. Assets and lang

- New: `assets/insanetweaks/textures/gui/container/element_icon_abomination.png`. Dimensions copied
  from EBW's `element_icon_magic.png`.
- New lang keys (`getDisplayName()` reads `element.<unlocalisedName>`, `getWizardName()` reads
  `element.<unlocalisedName>.wizard`):
  - `element.abomination=Abomination`
  - `element.abomination.wizard=Abominator` — read by `getWizardName()`, which the handbook and
    workbench call even though no Abomination wizard spawns. Purely cosmetic; change the string, not
    the key.
- Removed: `insanetweaks.element.abomination` (the hack's private key).
- **Nothing is shipped under the `ebwizardry` namespace.** See §9 for why the blockstate route does
  not work.

### 5. Spell migration

`"element": "magic"` → `"element": "abomination"` in all 14 files under
`assets/insanetweaks/spells/`. That is the entire migration. Spell registry names do not change, so
saved wands, spell books, scrolls and per-player discovered-spell data carry over untouched.

### 6. Casting gate

In `ArcaneBridgeEventHandler.onSpellCastPre`, replace the domain test with
`ModElements.isAbomination(event.getSpell())`. Everything else stands: source `WAND` plus
`getEffectiveAdaptationLevel(stack) <= 0` cancels the cast with the existing message.

Legal sources are unchanged from today: our foci (Living/Sentient wand and spellblade, which carry
`getDefaultAdaptationLevel() == 1`) and any EBW wand with the Adaptation upgrade applied. Scrolls,
books, dispensers and commands are not gated.

The gain is that the rule is now about the element, so a spell declared Abomination outside this mod
— a datapack, or a future module — is covered without touching the handler.

### 7. Mana-cost changes

**Remove the foreign-spell penalty.** Deleted:

- `ArcaneBridgeEventHandler` lines 54–59 (the `adaptationLevel > 0` cost multiplier);
- `AdaptationUpgradeHelper.getForeignSpellCostMultiplier` and `getForeignSpellCostPenaltyPercent`;
- `getArcaneAdaptationPenaltyPercent` in `BaseCustomWandItem` and `BridgeSpellblade`;
- the `"+X% Foreign Mana Cost"` / `"(No Foreign Mana Penalty)"` tooltip lines in
  `WandTooltipHandler` (three sites) and `SpellbladeTooltipHandler`.

**Keep the Arcane Adapted Fruit exactly as it is**, including its own
`FOREIGN_SPELL_COST_MULTIPLIER`. The user considers the fruit likely obsolete after this change and
a candidate for removal or rebalance alongside a planned unified mana pool, but that is a separate
piece of work.

🚨 **Preserve the shadowing.** Today `if (adaptationLevel > 0) { …; return; }` sits *above* the fruit
branch, so a player holding an adapted focus never pays the fruit penalty. Deleting the penalty
would delete that `return` with it and silently start charging adapted foci. Keep a bare
`if (adaptationLevel > 0) return;` in its place.

**Inverted penalty: shipped as a number, not as a decision.** The idea of charging extra when an
Abomination spell is cast from a *foreign* wand that only qualifies through the applied upgrade is
still open. Implement the mechanism, default it off: inside the Abomination branch, when
`AdaptationUpgradeHelper.getDefaultAdaptationLevel(stack) == 0` (i.e. not one of our four items),
multiply `SpellModifiers.COST` by a value chosen from the applied upgrade level. Turning it on is
then a config edit, not a code change. This works because `getDefaultAdaptationLevel` already
distinguishes our items from foreign ones.

Config shape: three named `double` fields in the existing `gear` category —
`foreignFocusAbominationCostLevel1/2/3`, `@Config.RangeDouble(min = 1.0, max = 16.0)`, all defaulting
to `1.0`, **no** `@Config.RequiresMcRestart` (read live inside the handler). Three named fields
rather than one array: they read better in the config GUI, and they sidestep the array question
entirely — Forge's `category = ""` rule only bans plain values on the *root*, but there is no reason
to test that boundary here.

### 8. Our wands and armour keep `element = null` — deliberately

An earlier draft of this spec proposed setting `ModElements.ABOMINATION` on `LivingWandItem`,
`SentientWandItem` and the four armour classes, to trade our removed cost penalty for EBW's native
elemental discount. **Rejected**, on two findings and one design call.

The design call, from the user: our wands and armour are the pack's top-end gear, and they are meant
to be the best choice for *every* mage, including one who never casts an Abomination spell. An
element-gated discount contradicts that outright.

The findings that make the alternative unattractive anyway:

- All four armour classes **fully replace** `applySpellModifiers` — their own discount, no `super`
  call — so setting the element alone would change nothing. Adding `super` would stack two cost
  discounts on Abomination spells *and* two cooldown reductions on everything, because EBW writes
  cooldown into a different modifier key (`SpellModifiers.set(Item, …)`) than our
  `modifiers.set("cooldown", …)`.
- On a wand, a non-null element additionally engages EBW's `ELEMENTAL_PROGRESSION_MODIFIER`, which
  would make our wands level faster on Abomination spells than on anything else — the same
  asymmetry, by a side door.

So: no change to `ModItems`, to the wand classes, or to the armour classes. The whole difference
between our magic and foreign magic is the casting gate in §6; balance lives in plain numbers (wand
mana capacity, per-spell `cost`).

### 9. Isolating the element from EBW's own loops

**`util/NativeElements`** — cached `Element[]` without Abomination; returns plain `Element.values()`
when `ModElements.EXTENDED` is false. It lives in `util/`, not in `mixins.*`: referencing a
non-mixin helper from inside the mixin package throws `IllegalClassLoadError` (repo rule).

Eleven target classes and **thirteen** redirect sites, all `@Redirect` on `Element.values()`, all
added to `mixins.insanetweaks.late.json` (the mod's existing unconditional EBW-targeting config):

| target class | method | failure being prevented |
|---|---|---|
| `EntityWizard` | `func_180482_a` / `onInitialSpawn` | `values()[rand.nextInt(len-1)+1]` — random wizard element |
| `EntityWizard` | `getRandomItemOfTier` | **eight** `values()` calls forming four `values()[rand.nextInt(len)]` expressions — three feed `WizardryItems.getWand`, the fourth feeds `getArmour`; this is the wizard's trade stock, wands **and armour** |
| `EntityWizard` | `populateSpells` (static) | when the passed element is MAGIC, picks a random one |
| `EntityEvilWizard` | `func_180482_a` / `onInitialSpawn` | random wizard element |
| `EntityRemnant` | `func_180482_a` / `onInitialSpawn` | random remnant element |
| `WorldGenShrine` | `spawnStructure` | random structure element → Abomination runestones with no model |
| `WorldGenObelisk` | `spawnStructure` | same |
| `RandomSpell` | `pickRandomSpell` | falls back to `Arrays.asList(values())` when no element filter is set |
| `BlockPedestal` | `func_149666_a` / `getSubBlocks` | third element-keyed block, same `copyOfRange` shape |
| `BlockPedestal` | `<clinit>` (static) | **fixes a hard crash** — see §9c |
| `BlockRunestone` | `<clinit>` (static) | keeps the blockstate set at its pre-Abomination content |

🚨 **The `RandomSpell` row is the highest-stakes entry in this table, not the lowest.** An earlier
draft justified the loot redirects with "the spell filter returns an empty set, because our spells
are `treasure: false`". That is false, and measured: only **two** of the fourteen spell JSONs
(`call_of_demise` and the disabled `test_projectile`) close `treasure`/`trades`/`looting`. The other
twelve leave all three open. So a registered Abomination element without this redirect becomes a
legitimate loot theme, and those twelve spells — mostly master-tier minion summons with no other
gating — appear in vanilla dungeon chests and mob drops. Closing the flags in the JSONs would be an
alternative, but it would also disable the same spells for any future deliberate loot placement; the
redirect keeps that door available.

🚨 **`WizardryLoot.<clinit>` was in this table and has been removed — redirecting it is a bug.** It
builds `RUINED_SPELL_BOOK_LOOT_TABLES` as one `ResourceLocation` per element, and
`TileEntityImbuementAltar` reads it with **raw ordinal arithmetic**:
`RUINED_SPELL_BOOK_LOOT_TABLES[element.ordinal() - 1]` (verified on bytecode: `ordinal`, `iconst_1`,
`isub`, `aaload`). Narrowing the array shrinks it from eight entries to seven while ordinals still
run to 8, so an Abomination element reaching the altar throws `ArrayIndexOutOfBoundsException` on the
server thread inside a tile-entity tick. It is reachable — the receptacle bounds-checks incoming dust
against the *un-narrowed* `Element.values().length`, so `/give ebwizardry:spectral_dust 1 8` gets in.

Leaving it alone costs nothing: the array stays dense, and the extra entry names a loot table that
does not exist, which `LootTableManager` resolves to the empty table. A `<clinit>` redirect would
also have been the least forgiving possible place to depend on `ModElements` having initialised
first. If Abomination ruined spell books ever need suppressing, the hook is the altar or the
receptacle, never the shared array.
| `ItemCrystal` | `func_150895_a` / `getSubItems` | ninth subtype with no model, in creative and JEI |
| `ItemSpectralDust` | `func_150895_a` / `getSubItems` | same |
| `BlockCrystal` | `func_149666_a` / `getSubBlocks` | same, for the block |
| `BlockRunestone` | `func_149666_a` / `getSubBlocks` | same |

🚨 **Do NOT redirect these**, even though they call `Element.values()` in the same classes — they are
lookups or snapshots, not random picks, and narrowing the array would corrupt loading:

- `EntityWizard` / `EntityEvilWizard` / `EntityRemnant`: `getElement()` (data-manager int) and
  `func_70037_a` (`readEntityFromNBT`, `values()[nbt.getInteger(…)]` — note `EntityRemnant` spells
  the NBT key `"Element"` with a capital E, unlike the two wizards)
- `ItemCrystal` / `ItemSpectralDust`: `getModelName` — metadata lookup plus a clamp against
  `values().length`
- `BlockCrystal` / `BlockRunestone` / `BlockPedestal`: `func_176203_a` (`getStateFromMeta`)
- `WizardryItems.register` and `registerBannerPatterns` — the registration loops that create the
  per-element items and banner patterns in the first place.

### 9b. EBW's JEI integration needs its own two mixins

Found by the final review, after the eleven above were already written and verified. **EBW's JEI
classes build their ingredient stacks straight from `Element.values()` and never go through
`getSubItems`**, so none of the redirects above reaches them. Four more sites, in two classes, all
`static`:

| class | method |
|---|---|
| `integration.jei.ImbuementAltarRecipeCategory` | `generateCrystalRecipes` |
| `integration.jei.ImbuementAltarRecipeCategory` | `generateCrystalBlockRecipes` |
| `integration.jei.ImbuementAltarRecipeCategory` | `generateArmourRecipes` |
| `integration.jei.ArcaneWorkbenchRecipe` | `generateCrystalStacks` |

Without them the Imbuement Altar category shows a ninth crystal and crystal-block recipe pair, and
the Arcane Workbench offers an Abomination crystal as a charging input — all with no model and no
lang key. `generateArmourRecipes` is arguably already safe (it discards empty imbuement results, and
there is no Abomination armour) but is redirected anyway, because three of four would be an
inconsistency the next reader has to re-derive.

These live in their own config, `mixins.insanetweaks.jei.json`, gated in `LateMixinBooter` on
`Loader.isModLoaded("jei")` and listed under **`client`** — JEI is client-side and these classes
implement JEI types. 🚨 Note the pack does not actually ship JEI: it ships **HadEnoughItems 4.34**,
which keeps both the `mezz.jei` package and `modid = "jei"`, so the gate fires. Verified on the
`@Mod` annotation of both jars, not on `mcmod.info` — the same trap that once made an Infernal Mobs
gate silently never fire.

An earlier draft listed `BlockRunestone.func_180661_e` (`createBlockState`) here instead. That was
wrong twice over: the method contains **no** `Element.values()` call at all (it only reads the static
`ELEMENT` field), and listing it drew attention away from the `<clinit>` that does the real
snapshotting.

### 9c. `BlockPedestal.<clinit>` — the real "no room for a ninth element"

🚨 **This one crashed the game, and this section reverses an instruction earlier drafts gave.**
`BlockRunestone.<clinit>` and `BlockPedestal.<clinit>` were on the do-not-touch list above, on the
grounds that narrowing "shrinks the block's legal blockstate variant set". Both **must** be
redirected. The reasoning was incomplete: narrowing restores each property to *exactly* its
pre-Abomination content — the seven non-MAGIC natives — because Abomination is the only thing
removed and no world has ever held an Abomination pedestal or runestone. It is the **un-narrowed**
version that is new, and for the pedestal it is fatal.

`BlockPedestal` is the only EBW block with two blockstate properties, `ELEMENT` and a `NATURAL`
boolean, and it packs them like this:

```java
meta = element.ordinal() + (natural ? ELEMENT.getAllowedValues().size() : 0)
```

Note `ordinal()`, not `ordinal() - 1` — so index 0 is wasted, since the property starts at FIRE.
With eight elements the allowed set is 7, ordinals run 1–7, and metas run 1–7 and 8–14: maximum 14,
inside the four bits a block gets. With nine, the allowed set is 8, ordinals run 1–8, and metas run
1–8 and 9–**16**. Forge's per-block registry array is exactly 16 long, so registration dies with
`ArrayIndexOutOfBoundsException: Index 16 out of bounds for length 16` in
`GameData$BlockCallbacks.onAdd`, every launch.

So the folk memory this whole project started from — *"there is no room for another element, it is a
metadata limit"* — was **right after all**, just not where anyone remembered. It is not the enum, and
it is not the crystal's subtypes. It is this one block's arithmetic, which had exactly enough
headroom for the elements EBW ships and not one more.

The decoder is safe because it reads the same `getAllowedValues().size()`, so encode and decode stay
symmetric under narrowing. `getStateFromMeta` therefore stays un-redirected.

`BlockCrystal.<clinit>` needs nothing: it uses the two-argument `PropertyEnum.create(String, Class)`,
makes no `Element.values()` call at all, and its nine states with `meta = ordinal()` fit.

### 9d. Ancient Spellcraft outranks us on `RandomSpell`

`MixinRandomSpellElements` failed to apply on the first real launch:

```
InvalidInjectionException: … cannot inject into RandomSpell::pickRandomSpell
merged by com.windanesz.ancientspellcraft.mixin.ebwizardry.MixinRandomSpell with priority 1000
```

ASC replaces `pickRandomSpell` wholesale. Mixin refuses to inject into a method merged by a mixin
whose priority is greater than or equal to yours — the same wall `enchanteraser`'s
`MixinContainerRepairErase` hit against `noexpensive`. Fixed with `priority = 1500`, which is sound
because ASC's replacement body still contains the `elements.isEmpty()` →
`addAll(Arrays.asList(Element.values()))` fallback this redirect targets. That makes the mixin
dependent on ASC keeping that call: **an ASC update is a reason to re-check it.**

🚨 **A consequence of the ordering that nothing else in this document states.** Because
`ModElements.init()` deliberately runs *before* the three blocks' `<clinit>`, their `PropertyEnum`
gains an `abomination` variant, and `WizardryItems.register` builds a nine-entry subtype-name array
for `crystal_block`, `runestone` and `runestone_pedestal`. The client will therefore log
missing-variant and missing-model-definition errors for `element=abomination` at resource load. It is
log noise, not a crash, and it is the price of the ordering — but the verification list below must
expect those lines rather than treat them as a failure. Related and unreachable in normal play:
`BlockCrystal.getMapColor` reads a hand-built `EnumMap` of eight constants, so an Abomination
crystal-block state would return `null`.

Note that `BlockCrystal.<clinit>` is the odd one out: it uses the two-argument
`PropertyEnum.create(String, Class)`, which snapshots through `clazz.getEnumConstants()` rather than
through `Element.values()`. It is therefore invisible to a `values()` sweep while still binding the
ordering constraint in §2 — a reminder that "no `values()` call" does not mean "no snapshot".

Trimming is safe by construction because **Abomination is appended, so it always holds the last
ordinal** — `NativeElements.values()` drops the tail and every pre-existing index is unchanged.

Injections use the config's `defaultRequire: 1` (i.e. no `require = 0`). A silently missing redirect
here does not degrade gracefully: it produces Abomination wizards holding a null wand and Abomination
shrines made of absent blocks, hours later, in worldgen. Failing at load is the lesser evil, and the
`EXTENDED=false` fallback cannot help — by then the element exists.

The last four cannot be solved by shipping assets instead. Item models are one file per item, so
`assets/ebwizardry/models/item/crystal_abomination.json` would merge cleanly — but **a blockstate is
one file per block**, so `assets/ebwizardry/blockstates/crystal_block.json` from our jar would
*replace* EBW's file wholesale rather than add a variant. Redirecting is both cheaper and safer, and
it keeps the whole feature inside one namespace.

Per repo rules: `remap = false`, and every `Element.values()` call site must be attributed to its
enclosing method with `javap -p -c` before writing the selector — `grep -A` spills across method
boundaries and produces `Scanned 0 target(s)` at load. Where one class calls `values()` from several
methods, write one redirect per method.

### 10. Cleanup — larger than first assumed

`SpellDisplayUtils` has **seven** consumers, not one. The fake element is re-applied at every surface
EBW draws, which is exactly the maintenance burden a real element removes.

Deleted outright:

| file | what it faked |
|---|---|
| `util/SpellDisplayUtils.java` | the whole helper |
| `mixins/MixinGuiSpellInfo.java` | spell-info GUI title and element line |
| `mixins/MixinGuiSpellDisplay.java` | wand HUD spell name colour |
| `mixins/MixinItemSpellBook.java` | spell-book tooltip element line |
| `mixins/MixinItemScroll.java` | scroll tooltip element line |
| `events/SpellBookGuiHandler.java` | redrew title + `Element:` line over EBW's own in `GuiSpellBook` |
| `events/SpellItemTooltipHandler.java` | item tooltip element line |

Trimmed, not deleted: `mixins/MixinSpell.java` loses
`insanetweaks$makeOwnMagicSpellsAbominationColored` (`getDisplayNameWithFormatting`) and
`insanetweaks$makeOwnMagicSpellComponentAbominationColored` (`getNameForTranslationFormatted`), and
**keeps** `insanetweaks$nullSafeIsEnabled` — an unrelated null-safety fix on `Spell.isEnabled`.

Also: drop `MixinGuiSpellInfo`, `MixinGuiSpellDisplay`, `MixinItemSpellBook` and `MixinItemScroll`
from the `client` list in `mixins.insanetweaks.late.json`; remove the two
`MinecraftForge.EVENT_BUS.register(...)` calls at `InsaneTweaksMod.java:491` and `:495`; remove the
`insanetweaks.element.abomination` lang key.

Incidental benefit: `SpellDisplayUtils` imports `net.minecraft.client.resources.I18n`, a client-only
class, so its removal takes a latent side-safety hazard with it.

## Out of scope / future work

- **Promoting Abomination to a full element.** Registering `novice/apprentice/advanced/master_abomination_wand`,
  twelve armour pieces, a crystal and runestone variants under the `ebwizardry` namespace would let
  EBW generate Abomination wizards and shrines naturally, and the exclusion mixins would be deleted
  one row at a time. Explicitly left open by this design; it is a texture and model project, not an
  architectural one.
- **Unified mana pool** across EBW and Trinkets and Baubles — planned separately. The Arcane Adapted
  Fruit is expected to be revisited then.
- **Rebalance pass.** Removing the multipliers shifts numbers; wand mana capacity and per-spell
  `cost` values in the 14 spell JSONs are the intended knobs.

## Verification plan

There is no test suite; verification is a build, a jar copy into `DEv 1.2`, and a launch.

1. `logs/cleanmix.log`: an `APPLY` line for each of the eleven mixins; no `InvalidInjectionException`,
   no `Scanned 0`, no `VerifyError`.
2. `logs/latest.log`: the `ModElements` line confirming `EXTENDED=true`.
3. Client surfaces show red "Abomination": spell-book tooltip, scroll tooltip, wand HUD, wizard
   handbook, arcane workbench, JEI.
4. Creative tabs and JEI: no ninth magic crystal, spectral dust, crystal block or runestone.
5. Fresh chunks plus a spawned wizard and evil wizard: no Abomination anywhere.
6. Gate: Abomination spell from a vanilla wand is blocked; from a Living Wand it casts; from a
   vanilla wand with the Adaptation upgrade it casts. A foreign spell on an adapted focus costs its
   base mana with no surcharge.
7. Dedicated server starts. `ModElements` only touches `Style`/`TextFormatting`, but a clean server
   launch is the only proof that counts.

## Risks

| risk | mitigation |
|---|---|
| `EnumHelper.addEnum` breaks on a future Java/Cleanroom bump | `EXTENDED=false` fallback to the current domain test; loud log |
| `addElement` runs after `BlockCrystal.<clinit>` | first line of the `@Mod` constructor; ordering verified against `WizardryBlocks.<clinit>` |
| A `values()` call site missed → Abomination wizard or shrine in the world | the eleven-class sweep above is the audit; re-run the `values()` grep on every EBW update |
| Mixin selector mismatch after an EBW update | eleven `@Redirect`s on foreign code is the standing cost of this approach; `cleanmix.log` check is part of the release routine |
| An EBW update changes `WizardryEnumHelper` | it is public API introduced for exactly this use; low, but the fallback covers it |

## Version bump

`insanetweaks/build.gradle` **and** `InsaneTweaksMod.VERSION` — both, since that pair is the one
that drifts.
