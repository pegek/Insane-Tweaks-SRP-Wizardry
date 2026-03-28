# Insane Tweaks SRP&Wizardry — Evolution Bridge Module
> **Version 1.0.7 | Alpha** — Expect bugs, balance issues, and rough edges. This mod is under active development.

**Minecraft:** 1.12.2 | **Forge:** 14.23.5.2860+ | **Recommended:** Cleanroom 0.5.6 (Java 25)

---

Insane Tweaks SRP&Wizardry is an integration mod that bridges the gap between magic and combat
in the 1.12.2 ecosystem. Originally prototyped in GroovyScript, the mod has been fully rewritten
in native Java (Forge) for improved stability and performance.

At its core, the mod creates an evolutionary gear progression system — items that grow with you,
reflecting the corruption and power of the parasite world you inhabit. It also introduces a
permanent character expansion system through the Bauble Fruit mechanic.

---

## Systems and Mechanics

### Living Mage Armor → Sentient Mage Armor
A parasite-infused battlemage armor set that physically evolves as you battle.

- **Living Mage Armor** — the craftable base tier. As you absorb damage (tracked per piece),
  parasitic energy accumulates. At **1500 absorbed damage per piece**, the armor evolves.
- **Sentient Mage Armor** — the evolved tier. Grants flat **-1% spell resistance** per equipped
  piece (stacks up to -4% for a full set). Continues absorbing damage — reach **10,000** and
  something changes...
- **Hardcap (Full Set Bonus)**: When **below 25% HP**, damage instances of 10+ (or lethal hits)
  are capped at **2.0 damage**, reduced by 90%. Triggers a **CLEANSE** burst — removing all
  negative effects. **90-second cooldown**.
- Custom spell modifier integration: flat **-1% mana cost, -1% cast time, -1% cooldown** per
  Living or Sentient piece equipped (stacks to -4% each for a full set).

### Living Aegis → Sentient Aegis
A runic shield that hardens through combat.

- **Living Aegis** — craftable base tier (+2 Armor).
- **Sentient Aegis** — evolved form (+3 Armor). Becomes **indestructible** (immune to lava, fire,
  and void when dropped).
- Evolution triggers after blocking **1500 damage**.
- Features a **retribution system** and **Blazing Might synergy** with Ancient Spellcraft.

### Living Spellblade → Sentient Spellblade
Weapons that grow more powerful through use.

- Evolve after defeating **1200 enemies**.
- The Sentient form gains improved attributes and spell synergy with Spartan Weaponry weapon
  properties (Bleeding, Viral, Heavy, and others).

### Custom Cores (Anvil Upgrades)
Apply enchantment-like modules to any Wizardry armor piece at an Anvil:

- **Cost Core** — reduces mana cost.
- **Potency Core** — increases spell power.
- **Speedcast Core** — reduces cast time.

Maximum **2 upgrades per core type** per armor piece. Effects persist permanently on the item.

### Golden Book
A utility item that bridges the Enchanting Table and Wizardry systems. Apply enchantments to a
Golden Book at an Enchanting Table or Anvil — it automatically converts into a standard Enchanted
Book, preserving the enchantments for use elsewhere.

### Cleanse Effect
A protective potion-like effect triggered by the armor Hardcap. While active, removes all negative
status effects every 10 ticks. Custom HUD icon. Configurable visibility.

### Curse of Possession Patch
Fixes a compatibility issue between Corail Tombstone (and similar grave mods) and the Curse of
Possession enchantment. Cursed items now properly vanish on death instead of being saved in your
grave.

- Use `/restorecursed` to recover deleted items in case of an emergency (admin command).

---

## Bauble Fruits
One-time consumable items that permanently expand your bauble slot capacity.
Eat a fruit — gain a slot. Forever.

Each fruit targets a specific bauble type:

| Item                  | Slot Expanded |
|-----------------------|---------------|
| Bauble Fruit - Ring   | +1 Ring       |
| Bauble Fruit - Amulet | +1 Amulet     |
| Bauble Fruit - Body   | +1 Body       |
| Bauble Fruit - Head   | +1 Head       |
| Bauble Fruit - Charm  | +1 Charm      |
| Bauble Fruit - Belt   | +1 Belt       |

**Important details:**
- One-time effect per player, tracked across deaths (saved in persistent player NBT).
- Non-stackable (max stack size: 1). Indestructible when dropped (survives lava and fire).
- Requires **BaublesEX (v2.0+)** for full slot expansion functionality.
- With original Baubles (v1.5.x), runs in **Legacy Mode**: grants +1 Luck instead of a real slot.
  Switching to BaublesEX later allows eating a new fruit to get the actual slot bonus.

---

## Configuration

All systems can be toggled via the mod config file (`insanetweaks.cfg`):

| Option                    | Default | Description                                           |
|---------------------------|---------|-------------------------------------------------------|
| Enable SRP-EBWizardry Bridge | true | Registers armor, aegis, and spellblade systems        |
| Enable Custom Cores       | true    | Registers Speedcast / Cost / Potency Cores            |
| Enable Bauble Fruits      | true    | Registers the Bauble Fruit item set                   |
| Enable Curse of Possession Patch | false | Deletes cursed items on death (opt-in)          |
| Display DEBUG INFO        | false   | Extra in-game messages for mechanic debugging         |
| Cleanse Effect List       | [...]   | Additional effect IDs removed by CLEANSE              |
| Hide Cleanse Effect from GUI | true | Suppresses the CLEANSE icon from HUD / inventory      |

---

## Installation and Requirements

### Required
- Electroblob's Wizardry (4.3.15)
- Ancient Spellcraft (1.8.1)
- Spartan Weaponry (1.6.1)
- Scape and Run: Parasites (1.10.3)
- Scape and Spartan: Parasites (v4)
- PotionCore (1.9)
- SomeMany Enchantments

### Recommended (Optional)
- **SRPExtra (1.10.7.5)** — provides proper crafting ingredients for full recipe balance.
  Without it, fallback recipes are registered using alternative ingredients.
  A startup warning will appear in-game if SRPExtra is absent.
- **BaublesEX (2.3.4+)** — required for full Bauble Fruit slot expansion.
  Original Baubles (1.5.x) is supported with a legacy fallback (see above).
- Enigmatic Legacy (Legacy 2.6.0)
- Reskillable (Fork)
- CompatSkills (Fork)

---

## Credits and Acknowledgements

This project was made possible by the following creators:

- **Electroblob** — Core magic framework and mana system (Electroblob's Wizardry).
- **Windanesz** — Battlemage equipment base and Runic Shield systems (Ancient Spellcraft).
- **oblivioussp** — Weapon properties and combat API (Spartan Weaponry).
- **Dhanantry / The SRP Team** — Evolutionary concepts and world-building (Scape and Run: Parasites).
- **Raptor / SWParasites Team** — Parasitic material attributes (Scape and Spartan: Parasites).
- **Aizistra & KELETU** — Shield mechanics and Bauble Fruit item logic inspiration (Enigmatic Legacy).
- **darktire** — BaublesEX API for Bauble Fruit slot expansion.
- **CursedFlames** — Shield-related mechanic inspirations (Bountiful Baubles).
- **Tmtravlr** — Status effect cleansing mechanics (PotionCore).
- **Wizardry And Fire Team** — Battlemage equipment texture bases.
- **CleanroomMC / GroovyScript Teams** — Prototyping environment and runtime scripting tools.
- **Faithful Team** — "Core" item textures created using modified assets from the Faithful 32x
  Resource Pack, distributed in accordance with their community license.

*Created by Isuth.*