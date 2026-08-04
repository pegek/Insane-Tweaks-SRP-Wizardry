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

- `isUsingOffhandToEnchant()` → `true`. Load-bearing: it is the grave's filter.
- `isEnchanted(stack)` → true when the wand already has the upgrade
  (`WandHelper.getUpgradeLevel(stack, upgradeItem) > 0`).
- `canEnchant(world, pos, player, stack)` → false unless **all** of: the feature is enabled, the
  upgrade item exists in the registry, the main hand holds the Ankh, and the wand lacks the upgrade.
- `setEnchant(World, BlockPos, EntityPlayerMP, ItemStack, int soulStrength)` → applies the upgrade
  and returns `ConsumeResult.success(...)`, or `ConsumeResult.fail(<reason>)`.
- `getKnowledge()` → `0`. The soul is the whole price; a knowledge cost was considered and rejected.

The grave calls **`canEnchant` immediately before `setEnchant`** and skips the whole block on false,
so a refusal there costs the player nothing. `canEnchant` is the gate; `setEnchant` re-checks only
what it must.

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

Every failure returns `ConsumeResult.fail` with a player-visible reason and leaves the soul intact:
no Wizardry installed, no wand in the off hand, no Ankh in the main hand, upgrade item unknown,
wand already soulbound, wand at its upgrade limit.

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
