# Insane Tweaks SRPWIZ: Evolution Bridge Module
MOD IS IN VERY ALPHA STAGE, EXPECT BUGS AND UNBALANCED FEATURES IM ALSO UNEXPERIENCED SO EXPECT BAD CODING PRACTICES

Minecraft: 1.12.2
Forge: 14.23.5.2860+
Recommended Environment: Cleanroom 5.6 (Java 25)

Insane Tweaks SRP&WIZ is an integration mod designed to unify magic and combat systems in the 1.12.2 environment. Originally prototyped in GroovyScript, this Java rewrite provides optimized bridging between Electroblob's Wizardry, Ancient Spellcraft, Spartan Weaponry, and mechanics inspired by Scape and Run: Parasites.

---

## Technical Features and Mechanics

### Aegis Shield Progression
A specialized shield based on Ancient Spellcraft's Runic Shield logic. It follows an evolutionary path:
* **Living Aegis**: The craftable base tier (Armor +2.0, Durability 1750).
* **Sentient Aegis**: The evolved form (Armor +3.0, Durability 2500).
Evolution triggers after blocking **2000 damage**. Features a scaling retribution system (Fire, Immaleable, Corrosion) and synergistic Blazing Might mechanics.

### Spellblade Evolution
Weapons that transform from **Living** to **Sentient** variant upon reaching **1700 decimated** enemies, gaining improved attributes and synergy.

### Custom Cores (Anvil Upgrades)
Anvil-applied modules (Speedcast, Cost, and Potency) providing persistent buffs to Wizardry stats on any armor piece (Max 2 upgrades per type).

### Golden Book
A specialized utility item. When enchanted at an Enchanting Table or Anvil, it transforms into a standard Enchanted Book containing those enchantments.

### Curse of Possession Patch
Ensures that items with the *Curse of Possession* are properly deleted upon death, fixing an issue where grave-modifying mods would unintentionally "save" them. An emergency rescue system is available via the `/restorecursed` command for admins.

---

## Installation and Requirements

This mod requires the following dependencies to be present in the environment:

* Electroblob's Wizardry (4.3.15)
* Ancient Spellcraft (1.8.1)
* Spartan Weaponry (1.6.1)
* Scape and Run: Parasites (1.10.3)
* Scape and Spartan: Parasites (v4)
* SRP Extra (1.10.7.5)
* Enigmatic Legacy (Legacy 2.6.0)
* PotionCore (1.9)
* Reskillable (Fork)
* CompatSkills (Fork)

---

## Credits and Acknowledgements

This project was made possible by the following creators and their respective APIs and frameworks:

* Electroblob: Core magic framework and mana system (Electroblob's Wizardry).
* Windanesz: Battlemage equipment base and Runic shield systems (Ancient Spellcraft).
* oblivioussp: Weapon properties and combat API (Spartan Weaponry).
* Dhanantry / The SRP Team: Evolutionary concepts and world-building (Scape and Run: Parasites).
* Raptor / SWParasites Team: Parasitic material attributes (Scape and Spartan: Parasites).
* Aizistra & KELETU: Shield mechanics and enchanting logic (Enigmatic Legacy).
* CursedFlames: Shield-related mechanic inspirations (Bountiful Baubles).
* Tmtravlr: Status effect cleansing mechanics (PotionCore).
* Wizardry And Fire Team: Battlemage equipment texture bases.
* CleanroomMC / GroovyScript Teams: Prototyping and runtime tools.
* Faithful Team: "Core" items textures were created using modified assets originally from the Faithful 32x Resource Pack. Distributed in accordance with their community license.

*Created by Isuth.*