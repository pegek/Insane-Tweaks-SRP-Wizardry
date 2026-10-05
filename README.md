# Insane Tweaks — SRP & Wizardry

Gradle multi-project repo containing **seven** Minecraft **1.12.2** Forge mods. They share a source tree and a build, but ship separately.

| Module | Modid | Where it ships | What it is |
|---|---|---|---|
| `insanetweaks/` | `insanetweaks` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srpwizardry-insanetweaks) · [Modrinth](https://modrinth.com/mod/srpwizardry-insanetweaks) | All gameplay content: the evolving Living/Sentient gear line, custom Electroblob's Wizardry spells, the Sanctuary Nexus, companions (Thrall / Sentinel / Sim Wizard / Assimilated Battlemage), Bauble Fruits, the Sentient Codex, Mmmm and Swift Picking enchantments, Property Books, the Abomination spell element, and the Auto Lock Picker. |
| `tombtweaks/` | `tombtweaks` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ctombstone-tweaks) | Everything for Corail Tombstone: random-effect whitelist, exact-slot grave restore, grave item decay, the Curse of Possession fix, ritual-book cooldowns, per-perk caps for the ten native perks, two custom perks, a raid-mod alignment bridge, and the Knowledge of Death inventory tab. Split out of `insanetweaks` in 1.9.0. |
| `srpwizmixins/` | `srpwizmixins` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srp-wiz-mixins) | Mixin-only native fixes for Scape and Run: Parasites 1.10.7 — cap-purge protection, per-dimension mob caps, dimension starting points, thread-safe save data, infestation spread throttle. No registry objects, every fix off by default. |
| `srpwizcore/` | `srpwizcore` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srp-wiz-core) | Pack glue: concurrency fixes for threaded entity ticking and chunk generation, OpenTerrainGenerator and FutureMC worldgen crash guards, per-dimension Ice & Fire worldgen control, a per-dimension spawn engine, performance guards for Doomlike Dungeons / CQR / Raids / Defiled Lands / Enigmatic Legacy, a CQR × Spartan Weaponry integration, and the dormant-waystone travel system. |
| `enchanteraser/` | `enchanteraser` | — | Makes a configured list of enchantments unobtainable **without unregistering them**, so gear that already carries one keeps working instead of crashing. Closes the enchanting table, `enchant_with_levels` and `enchant_randomly` loot, fishing treasure, librarian trades, the anvil, and Infernal Mobs elite drops; optionally hides the books from JEI/HEI and the creative tabs, and marks any surviving copy in the tooltip. Mixin-only, one config list, not pack-specific. |
| `reskilltweaks/` | `reskilltweaks` | — | The Reskillable integration: 20 custom traits across all eight skill trees, two rewritten native trait descriptions, a tuned `reskillable.cfg` deployed with a backup, and a middle-click trait refund. Split out of `insanetweaks` in 1.13.0 so the content mod boots without Reskillable. Depends on `insanetweaks`. |
| `manacore/` | `manacore` | — | A unified player mana pool with bridges to Electroblob's Wizardry and Trinkets and Baubles: EBW wands and mana artefacts and other mods' mana-regeneration potions feed the same pool. No compile dependency in either direction; `reskilltweaks` finds it at runtime by reflection. |

`tombtweaks`, `srpwizmixins`, `srpwizcore`, `enchanteraser` and `manacore` never depend on the content mod and ship alone. Two compile edges exist, both `compileOnly` and both pointing *towards* an extracted mod's API, never back: `insanetweaks → srpwizmixins` (the Sanctuary hands it a `ProtectedAreaProvider` so meteors miss protected areas) and `reskilltweaks → insanetweaks` (an add-on that requires it at runtime).

> **Note on `tombtweaks` and the two custom perks.** They are still registered under the `insanetweaks:` namespace on purpose — Corail Tombstone persists a player's perk levels by numeric registry id, and that id map lives in `level.dat` keyed by registry name. Renaming them would silently wipe every player's invested levels. Forge logs a non-matching-prefix warning for this; it is expected.

## Building

Requires **JDK 8** and ForgeGradle 3 (targets Forge `1.12.2-14.23.5.2860`).

```sh
./gradlew build                 # all seven jars
./gradlew :insanetweaks:build   # just one
./gradlew runClient             # dev client (working dir ./run)
```

Jars land in `<module>/build/libs/`, reobfuscated. Gradle has to run on JDK 8: set `org.gradle.java.home` in your **user** `~/.gradle/gradle.properties`, not in the repo's one. Most third-party mod jars are read from a local `libs/` folder, which is git-ignored.

Version numbers are per-mod. Bumping a mod means editing its `build.gradle` (`version` + manifest `Specification-Version`), its `VERSION` constant, and its `mcmod.info`.

## Side safety

Every mod here ships to dedicated servers, so client-only code must never be reachable from a common code path. Four rules learned the hard way (see 1.9.1–1.9.4):

- **Never put `registerEntityRenderingHandler` or any `IRenderFactory` behind a runtime `if (side == CLIENT)`.** The verifier resolves those types while loading the `@Mod` class, long before the guard runs. Use a sided proxy.
- **Never annotate a `SimpleNetworkWrapper` message handler `@SideOnly(CLIENT)`.** `registerMessage` instantiates the handler on both sides; the trailing `Side` argument only picks which side processes the message.
- **Never annotate a field `@SideOnly(CLIENT)` when its initialiser is inline.** The assignment lives in `<clinit>`, which carries no annotation, so SideTransformer strips the field and leaves the `putstatic` behind — `NoSuchFieldError` at class init.
- **Never hand a class that mixes sides to `EventBus.register`.** Registration calls `getMethods()`, which resolves the parameter types of every *public* method — so one client-typed listener drags its types in even though the event never fires server-side. Split the class, or annotate just the client method `@SideOnly(CLIENT)` so SideTransformer strips it. Method *bodies* are fine; resolution there is lazy.

Also avoid subclassing a vanilla class that carries a class-level client `@SideOnly` (`EmptyChunk` is the one that bit us). Such a failure resolves lazily, so the server boots fine and dies later.

**The worst case is not a crash.** A class holding both a server mechanic and its tooltip is an inviting target for a class-level `@SideOnly(CLIENT)` — and that annotation takes the mechanic down with the tooltip. The server then starts with no error at all and quietly stops doing its job, which is far harder to notice than a stack trace. `EnchantGrantAnvilHandler` (the anvil veto) and `EnchantGrantTooltipHandler` (the explanatory line) are two classes for exactly this reason. When a handler mixes sides, split it rather than annotate it.

## Runtime requirements

Mixins are loaded through **MixinBooter**, or by running on **Cleanroom**. The dev pack runs Cleanroom 0.6.2-alpha / Forge 14.23.5.2864 / Java 25 with CleanMix 0.4.6, sponge-mixin 0.8.7 and MixinBooter 11.5.

Base mods for the content module: Electroblob's Wizardry, Ancient Spellcraft, Spartan Weaponry, Scape and Run: Parasites (or Scape and Spartan: Parasites). `tombtweaks` needs Corail Tombstone. `enchanteraser` needs nothing — every target is vanilla apart from an optional Infernal Mobs patch. The CurseForge pages have the full optional-integration lists.

## License

MIT — see `LICENSE.txt`. Asset credits are in `CREDITS.txt`; note that some item textures derive from the Faithful 32x resource pack under its community license.
