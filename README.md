# Insane Tweaks — SRP & Wizardry

Gradle multi-project repo containing **four** Minecraft **1.12.2** Forge mods. They share a source tree and a build, but ship separately.

| Module | Modid | Where it ships | What it is |
|---|---|---|---|
| `insanetweaks/` | `insanetweaks` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srpwizardry-insanetweaks) · [Modrinth](https://modrinth.com/mod/srpwizardry-insanetweaks) | All gameplay content: the evolving Living/Sentient gear line, custom Electroblob's Wizardry spells, the Sanctuary Nexus, companions (Thrall / Sentinel / Sim Wizard / Assimilated Battlemage), Bauble Fruits, the Sentient Codex, Mmmm and Swift Picking enchantments, Property Books, and the Reskillable trait tree. |
| `tombtweaks/` | `tombtweaks` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ctombstone-tweaks) | Everything for Corail Tombstone: random-effect whitelist, exact-slot grave restore, grave item decay, the Curse of Possession fix, ritual-book cooldowns, per-perk caps for the ten native perks, two custom perks, a raid-mod alignment bridge, and the Knowledge of Death inventory tab. Split out of `insanetweaks` in 1.9.0. |
| `srpwizmixins/` | `srpwizmixins` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srp-wiz-mixins) | Mixin-only native fixes for Scape and Run: Parasites 1.10.7 — cap-purge protection, per-dimension mob caps, dimension starting points, thread-safe save data, infestation spread throttle. No registry objects, every fix off by default. |
| `srpwizcore/` | `srpwizcore` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srp-wiz-core) | Pack glue: concurrency fixes for threaded entity ticking and chunk generation, OpenTerrainGenerator and FutureMC worldgen crash guards, per-dimension Ice & Fire worldgen control, a per-dimension spawn engine, performance guards for Doomlike Dungeons / CQR / Raids / Defiled Lands / Enigmatic Legacy, a CQR × Spartan Weaponry integration, and the dormant-waystone travel system. |

The three sibling mods have **zero compile dependency** on the content mod, in both directions.

> **Note on `tombtweaks` and the two custom perks.** They are still registered under the `insanetweaks:` namespace on purpose — Corail Tombstone persists a player's perk levels by numeric registry id, and that id map lives in `level.dat` keyed by registry name. Renaming them would silently wipe every player's invested levels. Forge logs a non-matching-prefix warning for this; it is expected.

## Building

Requires **JDK 8** and ForgeGradle 3 (targets Forge `1.12.2-14.23.5.2860`).

```sh
./gradlew build                 # all four jars
./gradlew :insanetweaks:build   # just one
./gradlew runClient             # dev client (working dir ./run)
```

Jars land in `<module>/build/libs/`, reobfuscated. `gradle.properties` pins `org.gradle.java.home` to a local JDK 8 path — change it to yours.

Version numbers are per-mod. Bumping a mod means editing its `build.gradle` (`version` + manifest `Specification-Version`), its `VERSION` constant, and its `mcmod.info`.

## Side safety

Every mod here ships to dedicated servers, so client-only code must never be reachable from a common code path. Three rules learned the hard way (see 1.9.1–1.9.3):

- **Never put `registerEntityRenderingHandler` or any `IRenderFactory` behind a runtime `if (side == CLIENT)`.** The verifier resolves those types while loading the `@Mod` class, long before the guard runs. Use a sided proxy.
- **Never annotate a `SimpleNetworkWrapper` message handler `@SideOnly(CLIENT)`.** `registerMessage` instantiates the handler on both sides; the trailing `Side` argument only picks which side processes the message.
- **Never annotate a field `@SideOnly(CLIENT)` when its initialiser is inline.** The assignment lives in `<clinit>`, which carries no annotation, so SideTransformer strips the field and leaves the `putstatic` behind — `NoSuchFieldError` at class init.

Also avoid subclassing a vanilla class that carries a class-level client `@SideOnly` (`EmptyChunk` is the one that bit us). Such a failure resolves lazily, so the server boots fine and dies later.

## Runtime requirements

Mixins are loaded through **MixinBooter**, or by running on **Cleanroom**. The dev pack runs Cleanroom 0.6.2-alpha / Forge 14.23.5.2864 / Java 25 with CleanMix 0.4.6, sponge-mixin 0.8.7 and MixinBooter 11.5.

Base mods for the content module: Electroblob's Wizardry, Ancient Spellcraft, Spartan Weaponry, Scape and Run: Parasites (or Scape and Spartan: Parasites). `tombtweaks` needs Corail Tombstone. The CurseForge pages have the full optional-integration lists.

## License

MIT — see `LICENSE.txt`. Asset credits are in `CREDITS.txt`; note that some item textures derive from the Faithful 32x resource pack under its community license.
