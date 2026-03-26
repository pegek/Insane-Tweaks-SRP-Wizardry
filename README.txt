# Insane Tweaks SRPWIZ: Evolution Bridge Module

Minecraft: 1.12.2
Forge: 14.23.5.2860+
Recommended Environment: Cleanroom 5.6 (Java 25)

Insane Tweaks SRP&WIZ is an integration mod designed to unify magic and combat systems in the 1.12.2 environment. Originally prototyped in GroovyScript, this Java rewrite provides optimized bridging between Electroblob's Wizardry, Ancient Spellcraft, Spartan Weaponry, and mechanics inspired by Scape and Run: Parasites.

---

## Technical Features and Mechanics

### Sentient Spellblade
The Spellblade system extends Ancient Spellcraft's Battlemage Sword logic to include support for Spartan Weaponry properties such as Viral, Bleeding, and Uncapped. These weapons follow an evolutionary path starting from a Living variant. Upon reaching a 1700-kill threshold, the weapon transforms into its Sentient form, gaining improved attributes.

### Sentient Battlemage Armor
A protective set featuring Adaptation Logic. Wearing the full set hard-caps incoming damage instances to a maximum of 6.0 damage. Triggering this cap activates a Reactive Cleanse effect (using PotionCore's Cure mechanic) to dispel negative status ailments, followed by a brief internal cooldown. It also provides native mana efficiency and spell-scaling synergy.

### Parasite Aegis (Shield)
A specialized shield based on Ancient Spellcraft's Runic Shield logic, enhanced with mechanics inspired by the Shield of the Blazing Might from Enigmatic Legacy. It features a retribution system that applies fire damage and debuffs to attackers, breaking their invulnerability frames in the process.

### Custom Cores
Anvil-applied upgrade modules (Speedcast, Cost, and Potency) that provide persistent and stackable buffs to Wizardry-related statistics on any armor piece.

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

*Created by Isuth.*