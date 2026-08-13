# Tombstone × Electroblob's Wizardry — Concentration cools stowed wands

Date: 2026-08-13. Mod: **`tombtweaks`** (1.9.0). Status: design approved, not implemented.

## Summary

On a pack that runs Wizardry's `wandsMustBeHeldToDecrementCooldown`, a wand in the backpack never
cools down at all. Tombstone's **Concentration** perk lets it: each level restores a share of the
normal cooldown rate — 10% by default — to every `ItemWand` the player is carrying but not holding.

Second slice of the Tombstone↔Wizardry bridge, after wand soulbinding
(`2026-08-05-tombstone-wizardry-soulbound-design.md`). Everything else — Alchemist, Rune Inscriber,
Gladiator, knowledge from magic — is out of scope and gets its own spec.

## Why this shape

Every claim below was read out of 4.8.0 / 4.3.19 bytecode during design.

### Half of Concentration has no counterpart, and that decided the feature

The perk advertises two things: *"reduces the casting time with magic items"* and *"prevents damages
from interrupting"*. Both are implemented against Tombstone's own items — `IChanneling.onChanneling`
and `EventHandler.onLivingHurt`, the only two places `ModPerks.concentration` is read.

A full scan of every class in Wizardry found **zero calls to `stopActiveHand` or `resetActiveHand`**.
Wizardry never interrupts casting, by damage or otherwise. So the second half has nothing to bridge
to: there is no interruption to prevent. Building one — making damage interrupt casting so the perk
could protect against it — was considered and rejected: it imposes a penalty on every player in
order to sell protection from it to perk holders.

The first half maps onto `Spell.getChargeup()`, read by `ItemWand.onItemRightClick` and
`ItemWand.onUsingTick`. **Deliberately left alone for now.** Charge-up is a per-spell balance value
and touching it is its own decision; this spec does not.

### What the feature actually attaches to

`ItemWand.onUpdate`, decompiled control flow:

```java
boolean held = isSelected
        || (entity instanceof EntityLivingBase
            && ItemStack.areItemStacksEqual(stack, ((EntityLivingBase) entity).getHeldItemOffhand()));
if (!Wizardry.settings.wandsMustBeHeldToDecrementCooldown || held) {
    WandHelper.decrementCooldowns(stack);
}
```

Three consequences:

- The offhand counts as held, so **a wand in the offhand already cools normally**. This feature
  concerns stowed wands only — inventory slots other than the selected one and the offhand.
- With the setting off, everything cools everywhere and there is nothing to grant. The feature is
  therefore inert in that case, by design rather than by accident.
- `WandHelper.decrementCooldowns(ItemStack)` is **public static** and is the exact method Wizardry
  itself calls. Nothing needs patching to reuse it.

### This does not duplicate Wizardry's own cooldown upgrade

Wizardry ships its own `cooldown_upgrade` wand upgrade, scaled by
`Settings.cooldownReductionPerLevel` through `Constants`. That shortens a cooldown. This governs
whether a cooldown advances at all while the wand is stowed. Different axis, so the two stack
without either becoming redundant. (`condenser_upgrade` is the mana one and is unrelated — it is
what `ItemWand.onUpdate` reads a few instructions after the branch this feature attaches to.)

## Design

### Approach: an event handler, no mixins

A `PlayerTickEvent` listener that walks the player's inventory and calls the same public method
Wizardry calls. Wizardry's own path is untouched — it still declines to decrement a stowed wand, and
we add ticks alongside it.

Rejected alternative: a mixin on `ItemWand.onUpdate`. More precise and avoids our own iteration, but
it anchors into another mod's bytecode to do something its public API already offers. Same reasoning
that made the soulbinding slice a capability rather than four mixins.

The iteration cost is the honest price, and it is smaller than it looks: Minecraft already calls
`onUpdate` on **every** stack **every** tick, where this runs once per `scanIntervalTicks`. It is
also gated three times before any loop starts — feature enabled, Wizardry's setting on, perk level
above zero — so a player without the perk costs one capability lookup per interval.

### Components

| unit | package | responsibility |
|---|---|---|
| `ConcentrationCooldownHandler` | `com.spege.tombtweaks.wizardry` | the tick listener, the whole feature |
| `ConcentrationCooldownConfig` | `config.categories.TombstoneCategory` | nested section `concentrationcooldown` |

### The rate, and why a carry is needed

`WandHelper.decrementCooldowns` removes exactly 1 tick per call, so a fractional rate has to be
expressed as "how many calls per scan". The naive form — call once every `100 / percent` ticks —
collapses distinct levels onto the same interval: at 10% per level, levels 4 and 5 both round to
every-2-ticks, and the fifth level buys nothing.

So each scan computes `rate * scanIntervalTicks` and keeps the fractional remainder in a per-player
carry:

```java
carry += (level * percentPerLevel / 100.0D) * scanIntervalTicks;
int steps = (int) carry;
carry -= steps;
for (int i = 0; i < steps; i++) WandHelper.decrementCooldowns(stack);
```

At the defaults (10% per level, 10-tick scan) this is exactly `level` calls per scan with no
remainder, and any other config value is still honoured exactly rather than rounded to the nearest
representable interval. Cooldowns advance in small steps rather than smoothly, which is invisible:
the wand is in the backpack, and its cooldown is not drawn while it is stowed.

Carry state is a `Map<UUID, Double>` on the handler instance, dropped on `PlayerLoggedOutEvent`. The
whole map is bounded by the online player count.

### Scope of what gets cooled

Every `ItemWand` in `player.inventory.mainInventory`, **excluding the selected slot**; the offhand is
never touched. `instanceof ItemWand` rather than an item-id list, so Ancient Spellcraft's wands are
covered for free by being subclasses.

Perk level comes from `EntityHelper.getPerkLevelWithBonus(player, ModPerks.concentration)` — the same
call Tombstone uses in `onLivingHurt` and `reenchantOnDeath`, so bonus levels count here exactly as
they do everywhere else.

Server side only (`!world.isRemote`). Cooldown lives in stack NBT, the server copy is authoritative,
and the player's own container syncs changed stacks every tick, so the client is correct by the time
a stowed wand is drawn.

### Config — `tombstone.concentrationcooldown`

- `Enabled` (bool, default true) — read live.
- `Percent Per Level` (int, default 10, range 0–100) — share of the normal cooldown rate each
  Concentration level restores to a stowed wand. At the native cap of 5 levels the default gives 50%.
- `Scan Interval Ticks` (int, default 10, range 1–100) — how often the inventory is walked. Purely a
  cost/smoothness dial: the rate above is preserved at any value.

### Error handling

**Answered by not running at all:** Wizardry absent (the handler is never registered — it names both
Wizardry and Tombstone types, so the class must not load), feature disabled, Wizardry's
`wandsMustBeHeldToDecrementCooldown` off, or the player's Concentration level is zero. None of these
are error states and none say anything to the player; the pack simply behaves as though the feature
were not installed.

There is no failure mode that needs a message. The feature never refuses anything the player asked
for — it only adds ticks that would not otherwise happen.

### Verification

1. `wandsMustBeHeldToDecrementCooldown = true`, Concentration at 0: cast a long-cooldown spell,
   stow the wand, wait, draw it — cooldown unchanged. This is the control, and it must be run first,
   because it is what proves the rest of the test means anything.
2. Same with Concentration at 5: stowing for 20 seconds removes roughly 10 seconds of cooldown.
3. Wand in the **offhand** with the perk at 0 — still cools at full rate. Confirms we did not
   change Wizardry's own path.
4. `wandsMustBeHeldToDecrementCooldown = false`: behaviour identical with the perk at 0 and at 5,
   since cooldowns already run everywhere.
5. An Ancient Spellcraft wand is cooled too.
6. `Percent Per Level = 0` switches the effect off without disabling the feature.

Logs can only show the handler registering. Everything above is in-game.

## Out of scope

- **The tooltip.** `PerkConcentration.getCurrentBonusInfo` advertises Tombstone's casting-speed
  number and will not mention this. Unlike the seven `perkinfo` mixins, which exist because we
  *changed* a value Tombstone prints, here we *add* a bonus Tombstone never knew about — so the
  tooltip is not lying, only incomplete. Adding a line means a new mixin and a decision about how a
  third-party bonus should read in someone else's GUI; worth doing, not worth bundling here.
- Charge-up, per above.
- Alchemist, Rune Inscriber and Gladiator bridges; knowledge or alignment from magic. Each gets its
  own spec.
