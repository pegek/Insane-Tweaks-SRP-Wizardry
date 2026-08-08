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

EBW picks a random element out of the full `Element.values()` in seven places. We **exclude**
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
- EBW's block registration, because `BlockCrystal.<clinit>` runs
  `PropertyEnum.create("element", Element.class)`, which **snapshots `values()`**. An element added
  after that snapshot is not a legal blockstate value and `getStateFromMeta` would throw
  `IllegalArgumentException`.

The `@Mod` constructor is a safe slot, verified rather than assumed: `WizardryBlocks.<clinit>` and
`WizardryItems.<clinit>` assign nothing but `placeholder()` results (the fields are
`@ObjectHolder`-injected), so `BlockCrystal.<clinit>` only runs inside
`RegistryEvent.Register<Block>` — after every mod constructor. `insanetweaks` declares
`required-after:ebwizardry`, so EBW is constructed first either way.

### 3. Failure mode: fall back to today, not to something new

If `addEnum` ever breaks (a Java or Cleanroom jump), `ModElements` catches, logs an error, sets
`EXTENDED = false` and assigns `ABOMINATION = Element.MAGIC`.

The naive version of that fallback is a trap: with `ABOMINATION == Element.MAGIC`, an
`isAbomination()` written as an element comparison would also match EBW's own MAGIC-element spells
(magic missile and friends), and the casting gate would start blocking *foreign* spells on an
unadapted wand — a regression relative to the current behaviour. So `isAbomination()` branches on
`EXTENDED`:

- `EXTENDED == true` → `spell.getElement() == ABOMINATION`
- `EXTENDED == false` → the current registry-domain test

Degraded mode is then exactly the pre-change behaviour. `NativeElements` (§8) is gated on the same
flag, so all exclusion mixins become no-ops too.

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
- **Nothing is shipped under the `ebwizardry` namespace.** See §8 for why the blockstate route does
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

### 8. Element on our own wands and armour

`LivingWandItem` and `SentientWandItem` currently pass `null` as the element; the four armour
classes pass `null` too. Pass `ModElements.ABOMINATION` instead.

This turns on EBW's native carrot in place of our removed stick.
`ItemWizardArmour.applySpellModifiers` does
`if (spell.getElement() == this.element) COST × armourClass.elementalCostReduction` — with `null`
that branch never fires, because no spell has a null element.

Two things to check during implementation rather than assume:

- All four of our armour classes **override** `applySpellModifiers`. Whether the reduction actually
  applies depends on whether they call `super`; fix if not.
- `ItemWizardArmour.isWearingFullSet` resolves pieces by registry name in the `ebwizardry`
  namespace, so it returns `false` for our armour. This only affects the SAGE full-set bonus, which
  we do not use.

### 9. Isolating the element from EBW's own loops

**`util/NativeElements`** — cached `Element[]` without Abomination; returns plain `Element.values()`
when `ModElements.EXTENDED` is false. It lives in `util/`, not in `mixins.*`: referencing a
non-mixin helper from inside the mixin package throws `IllegalClassLoadError` (repo rule).

Eleven target classes, all `@Redirect` on `Element.values()`, all in `mixins.insanetweaks.json`
behind the late loader gated on `ebwizardry`:

| target | failure being prevented |
|---|---|
| `EntityWizard`, `EntityEvilWizard`, `EntityRemnant` | random wizard element → `ItemWand.getWand` builds `<tier>_abomination_wand`, `ItemWizardArmour.getArmour` builds `<class>_abomination_<piece>`; neither exists |
| `WorldGenShrine`, `WorldGenObelisk` | random structure element → shrine built from Abomination runestones that have no model |
| `WizardryLoot`, `RandomSpell` | random element in loot generation → spell filter returns an empty set |
| `ItemCrystal`, `ItemSpectralDust` | `getSubItems` emits a ninth subtype with no model, in creative and JEI |
| `BlockCrystal`, `BlockRunestone` | `getSubBlocks` does the same for the two blocks |

The last four cannot be solved by shipping assets instead. Item models are one file per item, so
`assets/ebwizardry/models/item/crystal_abomination.json` would merge cleanly — but **a blockstate is
one file per block**, so `assets/ebwizardry/blockstates/crystal_block.json` from our jar would
*replace* EBW's file wholesale rather than add a variant. Redirecting is both cheaper and safer, and
it keeps the whole feature inside one namespace.

Per repo rules: `remap = false`, and every `Element.values()` call site must be attributed to its
enclosing method with `javap -p -c` before writing the selector — `grep -A` spills across method
boundaries and produces `Scanned 0 target(s)` at load. Where one class calls `values()` from several
methods, write one redirect per method.

### 10. Cleanup

Delete `mixins/MixinGuiSpellInfo.java` and `util/SpellDisplayUtils.java`, drop the mixin from
`mixins.insanetweaks.json`, remove the `insanetweaks.element.abomination` lang key, and grep for any
remaining `SpellDisplayUtils` callers before deleting. Incidental benefit: `SpellDisplayUtils`
imports `net.minecraft.client.resources.I18n`, a client-only class, so its removal takes a latent
side-safety hazard with it.

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
