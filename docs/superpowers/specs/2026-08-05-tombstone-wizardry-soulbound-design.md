# Tombstone × Electroblob's Wizardry — soulbinding a wand with a grave's soul

Date: 2026-08-05. Mod: **`tombtweaks`**. Status: design approved, not implemented.

## Summary

At a decorative grave that holds a soul, a player holding the **Ankh of Prayer in the main hand**
and a **wand in the off hand** spends the soul to give the wand Ancient Spellcraft's
`soulbound_upgrade`, so the wand stays in the inventory on death instead of going into the grave.

This is the first slice of a larger Tombstone↔Wizardry bridge. Everything else discussed —
retuning the existing perks to affect casting, knowledge from magic, further death-and-magic
mechanics — is explicitly **out of scope here** and gets its own spec.

## Why this shape

Every claim below was read out of the 4.7.6 / 1.8.3 / 4.3.19 bytecode during design, not assumed.

### The soul-consumer contract is a public Forge capability

`ovh.corail.tombstone.api.capability.ISoulConsumer` is exposed as
`TBSoulConsumerProvider.CAP_SOUL_CONSUMER` with a public provider. Any mod can attach it to any
`ItemStack` via `AttachCapabilitiesEvent<ItemStack>`. **No mixin is required anywhere in this
feature**, which is why it was chosen over patching Tombstone or Wizardry.

### How the grave dispatches, exactly

`BlockDecorativeGrave.onBlockActivated` (decompiled control flow):

```java
if (!isValidPlayer(player) || hand != MAIN_HAND) return false;
if (mainhand.getItem() == receptacle_of_soul) { target = mainhand; }
else {
    if (!offhand.isEmpty()) {
        Optional.ofNullable(offhand.getCapability(CAP_SOUL_CONSUMER, null))
                .filter(ISoulConsumer::isUsingOffhandToEnchant)     // ← BootstrapMethods #0
                .ifPresent(c -> { consumer = c; target = offhand; });
    }
    if (target.isEmpty() && !mainhand.isEmpty()) {
        // mainhand consumer; isUsingOffhandToEnchant() then picks which stack is the target
    }
}
```

Two consequences that shape the design:

- The **off hand is checked first**, so an off-hand consumer wins over whatever is in the main hand.
- The filter is `isUsingOffhandToEnchant`, so a consumer is **either** an off-hand target **or** a
  main-hand one — never both. Ours returns **`true`**.

### The Ankh keeps both of its jobs

`ItemAnkhOfPrayer.onItemUseFirst` returns `PASS` at a soul-bearing grave, deliberately stepping
aside for the block; otherwise it falls through to `onItemRightClick`. So:

| held | grave | behaviour | owner |
|---|---|---|---|
| Ankh, off hand empty | has soul | perk respec (`setEnchant` → `ITBCapability.resetPerks`) | Tombstone, untouched |
| Ankh + **wand off hand** | has soul | **soulbind the wand** | **this feature** |
| Ankh | no soul | prayer, random beneficial effect (`onItemUseFinish` → `PrayerHelper.onGrave`) | Tombstone, untouched |

The three paths are separate branches of Tombstone's own code, so they coexist without conflict.

### The upgrade is Ancient Spellcraft's, not Wizardry's

`ancientspellcraft:soulbound_upgrade` — "Wand Soulbinding Upgrade",
`com.windanesz.ancientspellcraft.item.ItemSoulboundWandUpgrade`. Registered through
`WandHelper.registerSpecialUpgrade`, so it counts toward `WandHelper.getTotalUpgrades`.

🚨 Three unrelated things in this pack are called "soulbound"; do not confuse them. Tombstone's own
`EnchantmentSoulBound` is a plain enchantment handled in its `onHandleSoulbound(PlayerDropsEvent)`,
and UniversalTweaks / CorpseComplex only mention the word for keep-on-death compatibility.

### The upgrade already works alongside graves

`ItemSoulboundWandUpgrade.storeSoulboundWands` runs on `LivingDeathEvent`;
`restoreStoredWandsToInventory` on `PlayerRespawnEvent`. Tombstone builds the grave on
`PlayerDropsEvent`, which vanilla fires **after** `LivingDeathEvent`. The ordering is therefore
guaranteed by the engine, not by listener priority — the wand leaves the inventory before the grave
is filled. No duplication, no loss.

## Design

### Components

| unit | package | responsibility |
|---|---|---|
| `WandSoulbindConsumer` | `com.spege.tombtweaks.wizardry` | implements `ISoulConsumer` for a wand stack |
| `WandSoulbindAttacher` | `com.spege.tombtweaks.wizardry` | `@Mod.EventBusSubscriber`, attaches the capability to wand stacks |
| `WandSoulbindingConfig` | `config.categories.TombstoneCategory` | nested config section `wandsoulbinding` |

### Contract of `WandSoulbindConsumer`

- `isUsingOffhandToEnchant()` → true **only while the feature is actually operating**: the master
  switch and this section are enabled, and the configured upgrade item resolves. Load-bearing, and
  more than a constant: this is the grave's filter, and returning true commits the grave to the
  soul-consumer branch and swallows the click. Anything meaning "not operating" has to be answered
  here, or a disabled feature would still take the interaction away from Tombstone.
- `isEnchanted(stack)` → true when the wand already has the upgrade
  (`WandHelper.getUpgradeLevel(stack, upgradeItem) > 0`).
- `canEnchant(world, pos, player, stack)` → **always true.** 🚨 Not an oversight: Tombstone discards
  whatever reason this method computes. A false sends its own generic "not allowed" message and
  `setEnchant` is never called, so a refusal decided here would be silently replaced by a message
  that tells the player nothing. Every refusal must travel as a `ConsumeResult.fail` instead. This
  is what `ItemAnkhOfPrayer` does — it does not override `canEnchant` at all.
- `setEnchant(World, BlockPos, EntityPlayerMP, ItemStack, int soulStrength)` → applies the upgrade
  and returns `ConsumeResult.success(...)`, or `ConsumeResult.fail(<reason>)`.
- `getKnowledge()` → `0`. The soul is the whole price; a knowledge cost was considered and rejected.

The grave calls `canEnchant` immediately before `setEnchant`, but the gate is **not** where it looks
like it is. On false the grave sends `LangKey.MESSAGE_ENCHANT_ITEM_NOT_ALLOWED` and never calls
`setEnchant`, so the reason is thrown away; on true it calls `setEnchant` and spends the soul only
when the returned `ConsumeResult.result().success()`. Refusing from `setEnchant` therefore costs the
player exactly as little as refusing from `canEnchant`, and is the only way they learn why. So the
real division is: `isUsingOffhandToEnchant` decides whether this feature is involved at all, and
`setEnchant` decides and explains everything else.

`soulStrength` is Tombstone's own: `hasStrongSoul() ? 2 : 1`, and `0` when the grave has no tile
entity — the blue orb is 1, the pink one 2. v1 **ignores it**. It is noted because it is the obvious
lever if the feature is later graded (e.g. only a strong soul may bind a master-tier wand).

### Applying the upgrade

Through **`ItemWand.applyUpgrade(player, wand, upgradeStack)`** — Wizardry's own public method, the
one the arcane workbench uses. It enforces `Tier.upgradeLimit` against `WandHelper.getTotalUpgrades`.

🚨 Do **not** call `WandHelper.applyUpgrade` directly. That writes the NBT counter with no cap check
and would hand out an upgrade slot the wand has not earned.

Accepted consequence: soulbinding **occupies an upgrade slot**, so a wand already at its tier limit
is refused. The feature grants convenience, not power.

### Dependencies

- `ebwizardry` becomes a new deobf compile dependency of `tombtweaks` (the `ItemWand` /
  `WandHelper` types). It comes from CurseMaven, matching how content already takes it.
- **Ancient Spellcraft does not.** The upgrade item is resolved by registry name through
  `Item.getByNameOrId("ancientspellcraft:soulbound_upgrade")`; without ASC the lookup returns null
  and the feature declines silently.

### Config — `tombstone.wandsoulbinding`

- `Enabled` (bool, default true) — read live.
- `Upgrade Item` (string, default `ancientspellcraft:soulbound_upgrade`) — which upgrade a soul
  grants, so this is a tool rather than a hardcode.
- `Require Ankh Of Prayer` (bool, default true) — Tombstone's off-hand branch never inspects the
  main hand, so the Ankh requirement is ours to enforce.
- `Debug Logging` (bool, default false).

### Error handling

Failures split by where they can be answered, and the split is not cosmetic — only the second kind
can be explained to the player.

**Answered by stepping aside** (`isUsingOffhandToEnchant` returns false, Tombstone carries on as if
this mod were not installed): the feature is switched off, or the configured upgrade item is not
registered — which is also what happens with no Ancient Spellcraft. No message, by design; the
player sees stock behaviour.

**Answered by refusing** (`ConsumeResult.fail`, soul intact, reason shown): no Ankh in the main
hand, no wand in the targeted hand, wand already at its upgrade limit.

**Cannot arise:** the wand already soulbound — Tombstone checks `isEnchanted` first and answers with
its own message. The check stays as an honest precondition for any other caller.

Do not style these messages. Tombstone takes the `ITextComponent` out of the `ConsumeResult` and
applies its own `StyleType` — `MESSAGE_SPECIAL` on success, `COLOR_OFF` on failure — so plain
`TextComponentString` is correct and the result reads as native Tombstone text. That is why the
messages look identical in tone to Tombstone's own, and it is not a sign the custom text was lost.

### Verification

Logs can only show that the capability attached; the behaviour is in-game:

1. Wand without the upgrade, Ankh in main hand, wand in off hand, click a soul-bearing grave →
   wand tooltip lists the upgrade, the orb over the grave is gone.
2. Click again → refused, the second grave's soul is untouched.
3. Ankh alone at a soul grave → still a perk respec.
4. Ankh at a soulless grave → still a prayer.
5. Die → the wand is in the inventory on respawn, not in the grave.

## Known risk, to check during implementation

`SlotSnapshotHandler` (ours) and Ancient Spellcraft's `storeSoulboundWands` both listen to
`LivingDeathEvent` at NORMAL priority, so their order is decided by mod load order. If ours runs
first it records the wand in a slot the grave will never contain — a phantom entry. Believed
harmless because the restore only reapplies layout for items actually in the grave, but it is the
one non-deterministic point in the feature and must be confirmed.

## Out of scope

Retuning existing perks to affect casting; knowledge or alignment from magic; any other
death-and-magic mechanic. Each gets its own spec.
