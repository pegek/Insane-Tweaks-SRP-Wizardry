# Insane Tweaks — SRP & Wizardry

Gradle multi-project repo containing **three** Minecraft **1.12.2** Forge mods. They share a source tree and a build, but ship separately.

| Module | Modid | Where it ships | What it is |
|---|---|---|---|
| `insanetweaks/` | `insanetweaks` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srpwizardry-insanetweaks) · [Modrinth](https://modrinth.com/mod/srpwizardry-insanetweaks) | All gameplay content: the evolving Living/Sentient gear line, custom Electroblob's Wizardry spells, the Sanctuary Nexus, companions (Thrall / Sentinel / Sim Wizard), Bauble Fruits, the Sentient Codex enchantment, Reskillable traits. |
| `srpwizmixins/` | `srpwizmixins` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srpwiz-mixins) · [Modrinth](https://modrinth.com/mod/srpwiz-mixins) | Mixin-only native fixes for Scape and Run: Parasites 1.10.7. No registry objects, no dependency on the content mod, every fix off by default. |
| `srpwizcore/` | `srpwizcore` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/srpwiz-core) · [Modrinth](https://modrinth.com/mod/srpwiz-core) | Pack glue and worldgen control: EntityTracker concurrency fix, OpenTerrainGenerator structure-gen guards, FutureMC bamboo worldgen guard, per-dimension Ice & Fire worldgen control, and the dormant-waystone travel system. |

The two sibling mods have **zero compile dependency** on the content mod, in both directions.

## Building

Requires **JDK 8** and ForgeGradle 3 (targets Forge `1.12.2-14.23.5.2860`).

```sh
./gradlew build                 # all three jars
./gradlew :insanetweaks:build   # just one
./gradlew runClient             # dev client (working dir ./run)
```

Jars land in `<module>/build/libs/`, reobfuscated. `gradle.properties` pins `org.gradle.java.home` to a local JDK 8 path — change it to yours.

Version numbers are per-mod. Bumping the content mod means editing `insanetweaks/build.gradle` (`version` + manifest `Specification-Version`), `InsaneTweaksMod.VERSION` and `insanetweaks/src/main/resources/mcmod.info`; the two sibling mods follow the same three-place pattern in their own subproject.

## Runtime requirements

Mixins are loaded through **MixinBooter**, or by running on **Cleanroom**. The dev pack runs Cleanroom 0.6.2-alpha / Forge 14.23.5.2864 / Java 25 with CleanMix 0.4.6, sponge-mixin 0.8.7 and MixinBooter 11.5.

Base mods for the content module: Electroblob's Wizardry, Ancient Spellcraft, Spartan Weaponry, Scape and Run: Parasites (or Scape and Spartan: Parasites). The CurseForge page has the full optional-integration list.

## License

MIT — see `LICENSE.txt`. Asset credits are in `CREDITS.txt`; note that some item textures derive from the Faithful 32x resource pack under its community license.
