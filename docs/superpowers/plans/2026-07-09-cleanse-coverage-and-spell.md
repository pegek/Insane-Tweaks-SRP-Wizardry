# Cleanse Coverage + Spell Cleanse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Cleanse effect remove every harmful parasite-ecosystem effect (SRParasites / SRPExtra / SW:Parasites), and add a Master-tier Abomination spell "Cleanse" that applies it to a targeted entity or the caster.

**Architecture:** A hardcoded built-in removal list is added as "pass 1.5" inside `PotionCleanse.performEffect()` (config stays as the user extension channel). The spell is a `SpellRay` subclass following EB Wizardry's `Petrify` pattern, registered through the existing `ModSpells` flow with a JSON spell file.

**Tech Stack:** Forge 1.12.2, Java 8, Electroblob's Wizardry 4.3.19 (`SpellRay`, spell JSON), no test suite — verification via `./gradlew build` + manual `runClient` checks.

**Spec:** `docs/superpowers/specs/2026-07-09-cleanse-coverage-and-spell-design.md`

---

## Audit results (completed 2026-07-09, CFR decompile of all three jars)

Source of truth: `com.dhanantry.scapeandrunparasites.init.SRPPotions` (SRP 1.10.7),
`energon.srpextra.init.SRPEEffects` (SRPExtra 1.10.7.5). **SW:Parasites (swparasites-v4)
registers NO potions of its own** — its weapon properties apply SRP effects, so it needs
no separate entries.

All SRP effects are `SRPEffectBase extends Potion` calling `super(isBadEffect, color)` and
never `setBeneficial()`. Registry names are `srparasites:<name>` (the exact strings below
were read from the decompiled constructor calls).

### Bucket (2) — REMOVE (goes into the built-in list)

| Registry ID | ctor `isBadEffect` | Why remove |
|---|---|---|
| `srparasites:coth` | false | Call of the Hive — conversion pressure (project rule: always clear COTH) |
| `srparasites:fear` | false | Action-blocking fear |
| `srparasites:bleed` | true | Damage over time |
| `srparasites:corrosive` | true | Armor/health corrosion DoT |
| `srparasites:viral` | true | Infection DoT (already in old config default) |
| `srparasites:vomit` | false | −90 % follow-range attribute debuff |
| `srparasites:senses` | false | Parasites sense the afflicted |
| `srparasites:prey` | true | Marked as prey (already in old config default) |
| `srparasites:debar` | false | Interaction denial |
| `srparasites:needler` | false | Needler DoT |
| `srparasites:foster` | false | Breeds parasites inside the victim |
| `srparasites:link` | false | Hostile damage link |
| `srparasites:parate` | false | Parasite-inflicted debuff |
| `srparasites:spotted` | false | Position revealed to parasites |
| `srparasites:braining` | false | Mind-affecting debuff |
| `srparasites:novision` | false | Blinds (already in old config default) |
| `srparasites:indeaf` | false | Hard root — zeroes motion every tick |
| `srparasites:overheating` | true | Overheat DoT |
| `srparasites:conta` | true | Contamination |
| `srparasites:muscleout` | true | Muscle failure debuff |
| `srparasites:effectpos` | true | Damages victim per active beneficial effect |
| `srparasites:effectneg` | true | Amplifies/stacks victim's negative effects |
| `srparasites:the_sign` | false | Hostile mark |
| `srparasites:thornshade_thorns` | false | Thorns DoT |
| `srpextra:stung` | true | DoT + −15 % speed |
| `srpextra:confused` | true | Confusion/transformation debuff |

### Bucket (3) — EXCLUDE (must never appear in any cleanse list)

| Registry ID | Why excluded |
|---|---|
| `srparasites:repel` (EPEL_E) | Anti-assimilation protection — our summons/restores depend on it |
| `srparasites:antimall` (RES_E) | Player-brewable protective potion |
| `srparasites:rage` | Parasite-side buff, not a player affliction |
| `srparasites:jugg` | Parasite-side buff |
| `srparasites:pivot` | Parasite-side mechanic |
| `srparasites:primitive`/`adapted`/`pure`/`crude`/`feral`/`nexus` | Player-usable kill/lure mechanic potions |
| `srparasites:distorted_enlightenment` | Bestiary/discovery mechanic |
| `srparasites:dod_smoke_trail` | Cosmetic trail effect |

Bucket (1) is empty in practice — SRP flags don't map to `isBeneficial()`, which is exactly
why the explicit list exists.

---

## File Structure

- Modify: `src/main/java/com/spege/insanetweaks/potions/PotionCleanse.java` — built-in list + pass 1.5
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java` — config comment/default update
- Create: `src/main/java/com/spege/insanetweaks/spells/SpellCleanse.java` — the spell
- Modify: `src/main/java/com/spege/insanetweaks/init/ModSpells.java` — registration
- Create: `src/main/resources/assets/insanetweaks/spells/cleanse.json` — spell properties
- Create: `src/main/resources/assets/insanetweaks/textures/spells/cleanse.png` — icon (placeholder)
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`, `ru_ru.lang` — spell name/desc

---

### Task 1: Built-in cleanse list in PotionCleanse (pass 1.5)

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/potions/PotionCleanse.java`

- [ ] **Step 1: Add the built-in list and lazy resolver**

Insert after the `CLEANSE_TEXTURE` field (around line 41):

```java
    /**
     * Built-in parasite-ecosystem effects removed by Cleanse (audit 2026-07-09,
     * SRParasites 1.10.7 + SRPExtra 1.10.7.5; SW:Parasites registers no own potions).
     * Shipped in code rather than as config defaults because Forge @Config values
     * already written to disk never pick up new defaults.
     * Deliberately EXCLUDED (protective/mechanic — never add): srparasites:repel,
     * srparasites:antimall, srparasites:rage, srparasites:jugg, srparasites:pivot,
     * srparasites:primitive/adapted/pure/crude/feral/nexus,
     * srparasites:distorted_enlightenment, srparasites:dod_smoke_trail.
     */
    private static final String[] BUILT_IN_CLEANSED_EFFECTS = {
            "srparasites:coth", "srparasites:fear", "srparasites:bleed",
            "srparasites:corrosive", "srparasites:viral", "srparasites:vomit",
            "srparasites:senses", "srparasites:prey", "srparasites:debar",
            "srparasites:needler", "srparasites:foster", "srparasites:link",
            "srparasites:parate", "srparasites:spotted", "srparasites:braining",
            "srparasites:novision", "srparasites:indeaf", "srparasites:overheating",
            "srparasites:conta", "srparasites:muscleout", "srparasites:effectpos",
            "srparasites:effectneg", "srparasites:the_sign",
            "srparasites:thornshade_thorns",
            "srpextra:stung", "srpextra:confused" };

    /** Resolved lazily on first cleanse pulse — registries are complete by then. */
    private static java.util.List<Potion> builtInResolved = null;

    private static java.util.List<Potion> getBuiltInCleansed() {
        if (builtInResolved == null) {
            builtInResolved = new java.util.ArrayList<>();
            for (String id : BUILT_IN_CLEANSED_EFFECTS) {
                Potion p = ForgeRegistries.POTIONS.getValue(new ResourceLocation(id));
                if (p != null) builtInResolved.add(p);
            }
        }
        return builtInResolved;
    }
```

- [ ] **Step 2: Insert pass 1.5 into performEffect**

In `performEffect(...)`, between the existing pass 1 loop and the pass 2 loop, insert:

```java
        // Pass 1.5: built-in parasite-ecosystem effects (see BUILT_IN_CLEANSED_EFFECTS).
        for (Potion builtIn : getBuiltInCleansed()) {
            if (entity.isPotionActive(builtIn)) {
                entity.removePotionEffect(builtIn);
            }
        }
```

Also update the `performEffect` javadoc (currently describes two passes) to mention the
three passes: 1 = non-beneficial, 1.5 = built-in parasite list, 2 = config-driven extras.

- [ ] **Step 3: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/potions/PotionCleanse.java
git commit -m "feat: built-in parasite effect list for Cleanse (SRP/SRPExtra audit)"
```

---

### Task 2: Config default migration

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java`

- [ ] **Step 1: Empty the default and update the comment**

Replace the `cleanseAdditionalEffects` field (currently defaulting to
`{ "srparasites:novision", "srparasites:prey", "srparasites:viral" }`) with:

```java
    @Config.Comment({
            "Additional effects removed by the CLEANSE effect, on top of (a) all effects where",
            "isBeneficial() == false and (b) the built-in parasite-effect list shipped in the mod",
            "(all harmful SRParasites/SRPExtra effects — see PotionCleanse.BUILT_IN_CLEANSED_EFFECTS).",
            "Add effect IDs here only if some other mod's negative effect is not removed automatically.",
            "Example: minecraft:glowing" })
    @Config.Name("Cleanse Effect List")
    public String[] cleanseAdditionalEffects = {};
```

Note: existing config files on disk keep the three old entries — that is a harmless
duplicate of the built-in list, no migration needed.

- [ ] **Step 2: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java
git commit -m "chore: cleanse config list defaults to empty (built-in list took over)"
```

---

### Task 3: SpellCleanse class

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/spells/SpellCleanse.java`

- [ ] **Step 1: Create the spell class**

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModPotions;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.SpellRay;
import electroblob.wizardry.util.ParticleBuilder;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Cleanse — Master-tier Abomination (JSON element "magic") ray spell.
 *
 * Hit a living entity: applies the InsaneTweaks CLEANSE potion to it
 * (removes all harmful effects, incl. the built-in parasite list, one pulse
 * per 10 ticks for the whole duration).
 * Hit a block or nothing: falls back to self-cast on the caster.
 *
 * Pattern reference: EB Wizardry's Petrify (SpellRay + duration property).
 */
@SuppressWarnings("null")
public class SpellCleanse extends SpellRay {

    /** Duration (ticks) of the applied CLEANSE effect. Set in cleanse.json. */
    public static final String EFFECT_DURATION = "effect_duration";

    public SpellCleanse() {
        super(InsaneTweaksMod.MODID, "cleanse", SpellActions.POINT, false);
        this.soundValues(1.0f, 1.2f, 0.2f);
        this.addProperties(EFFECT_DURATION);
    }

    @Override
    protected boolean onEntityHit(World world, Entity target, Vec3d hit, EntityLivingBase caster,
            Vec3d origin, int ticksInUse, SpellModifiers modifiers) {
        if (!(target instanceof EntityLivingBase)) {
            return false;
        }
        applyCleanse(world, (EntityLivingBase) target, modifiers);
        return true;
    }

    @Override
    protected boolean onBlockHit(World world, BlockPos pos, EnumFacing side, Vec3d hit,
            EntityLivingBase caster, Vec3d origin, int ticksInUse, SpellModifiers modifiers) {
        // Aiming at the ground counts as "no target" — self-cast fallback.
        applyCleanse(world, caster, modifiers);
        return true;
    }

    @Override
    protected boolean onMiss(World world, EntityLivingBase caster, Vec3d origin, Vec3d direction,
            int ticksInUse, SpellModifiers modifiers) {
        applyCleanse(world, caster, modifiers);
        return true;
    }

    private void applyCleanse(World world, EntityLivingBase target, SpellModifiers modifiers) {
        if (world.isRemote) {
            return;
        }
        int duration = (int) (getProperty(EFFECT_DURATION).floatValue()
                * modifiers.get(WizardryItems.duration_upgrade));
        target.addPotionEffect(new PotionEffect(ModPotions.CLEANSE, duration, 0, false, true));
        world.playSound(null, target.posX, target.posY, target.posZ,
                SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.PLAYERS, 0.9f, 1.3f);
    }

    @Override
    protected void spawnParticle(World world, double x, double y, double z,
            double vx, double vy, double vz) {
        // Icy-white cleanse sparkle matching the CLEANSE potion colour (0xAADDFF).
        ParticleBuilder.create(ParticleBuilder.Type.SPARKLE).pos(x, y, z)
                .time(12 + world.rand.nextInt(8)).clr(0.67f, 0.87f, 1.0f).spawn(world);
    }
}
```

- [ ] **Step 2: Register in ModSpells**

In `src/main/java/com/spege/insanetweaks/init/ModSpells.java`:

Add import:
```java
import com.spege.insanetweaks.spells.SpellCleanse;
```

Add field after `SUMMON_THRALL`:
```java
    public static final Spell CLEANSE = new SpellCleanse();
```

Add registration after `registry.register(SUMMON_THRALL);`:
```java
        registry.register(CLEANSE);
```

- [ ] **Step 3: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`. If `SpellRay` method signatures differ from the decompiled
reference (`notes/decompiled_mods/ebwizardry_source/decompiled_src/electroblob/wizardry/spell/Petrify.java`),
check `SpellRay.java` in the same tree and adjust the three `on*Hit/onMiss` overrides to match.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellCleanse.java src/main/java/com/spege/insanetweaks/init/ModSpells.java
git commit -m "feat: Spell Cleanse (Master/Abomination SpellRay, self-cast fallback)"
```

---

### Task 4: Spell assets (JSON, icon, lang)

**Files:**
- Create: `src/main/resources/assets/insanetweaks/spells/cleanse.json`
- Create: `src/main/resources/assets/insanetweaks/textures/spells/cleanse.png`
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`
- Modify: `src/main/resources/assets/insanetweaks/lang/ru_ru.lang`

- [ ] **Step 1: Spell JSON**

Create `cleanse.json` (numbers approved in the spec: mana 350, chargeup 3 s, cooldown 3 min):

```json
{
  "enabled": {
    "book": true,
    "scroll": true,
    "wands": true,
    "npcs": false,
    "dispensers": false,
    "commands": true,
    "treasure": true,
    "trades": true,
    "looting": true
  },
  "tier": "master",
  "element": "magic",
  "type": "defence",
  "cost": 350,
  "chargeup": 60,
  "cooldown": 3600,
  "base_properties": {
    "range": 12,
    "effect_duration": 200
  }
}
```

- [ ] **Step 2: Icon placeholder**

Copy the Purifying Pulse icon as a placeholder (final art comes later from the user):

```bash
cp src/main/resources/assets/insanetweaks/textures/spells/purifying_pulse.png src/main/resources/assets/insanetweaks/textures/spells/cleanse.png
```

(If the spells texture directory uses a different name, `ls src/main/resources/assets/insanetweaks/textures/` and copy whichever icon the purifying_pulse spell uses — the path convention is EB's `<modid>:textures/spells/<spell_name>.png`.)

- [ ] **Step 3: Lang entries**

Append to `en_us.lang` next to the other `spell.insanetweaks:` entries (around line 117):

```properties
spell.insanetweaks:cleanse=Cleanse
spell.insanetweaks:cleanse.desc=Channelled purge torn from the hive's own immune response. Strips every affliction — parasitic or mundane — from the target for a short while; cast into empty air, it turns inward and scours the caster instead.
```

Append the matching pair to `ru_ru.lang` (file is UTF-8; keep the same key names):

```properties
spell.insanetweaks:cleanse=Очищение
spell.insanetweaks:cleanse.desc=Направленная чистка, вырванная у иммунной системы самого улья. На короткое время снимает с цели все недуги — паразитические и обычные; выпущенная в пустоту, обращается внутрь и очищает самого заклинателя.
```

- [ ] **Step 4: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/assets/insanetweaks/spells/cleanse.json src/main/resources/assets/insanetweaks/textures/spells/cleanse.png src/main/resources/assets/insanetweaks/lang/en_us.lang src/main/resources/assets/insanetweaks/lang/ru_ru.lang
git commit -m "feat: cleanse spell assets (json, icon placeholder, lang en+ru)"
```

---

### Task 5: Manual verification (runClient)

No automated tests exist in this project; run `.\gradlew runClient` and verify:

- [ ] **Step 1: Coverage** — `/effect @p srparasites:novision 60`, `srparasites:bleed`,
  `srparasites:foster`, `srpextra:stung`, then `/effect @p insanetweaks:cleanse 60` —
  all four must disappear within 0.5 s.
- [ ] **Step 2: Exclusions survive** — apply `srparasites:repel` and `srparasites:antimall`,
  then cleanse — both must REMAIN active.
- [ ] **Step 3: Spell (target)** — `/give @p ebwizardry:spell_book 1 <cleanse metadata>` or
  use creative spell book; cast at a debuffed cow → cow's debuffs removed; check
  3 s chargeup, 3 min cooldown, "Abomination" light-red element in the spell book GUI.
- [ ] **Step 4: Spell (self-fallback)** — debuff self, cast at open sky → own debuffs removed.
- [ ] **Step 5: Commit any tuning** — if JSON numbers were adjusted during testing:

```bash
git add src/main/resources/assets/insanetweaks/spells/cleanse.json
git commit -m "balance: cleanse spell numbers after manual testing"
```
