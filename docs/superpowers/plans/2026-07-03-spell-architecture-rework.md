# Spell Architecture Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deduplicate the five summon spells behind one `AbstractSrpSummonSpell` base class, replace `SpellImmuneBond`'s hand-rolled ray-trace with Wizardry's `RayTracer`, and add a `SpellCastFeedback` particle helper — with zero intended gameplay changes.

**Architecture:** New abstract class `AbstractSrpSummonSpell<T extends EntitySummonedCreature> extends SpellMinion<T>` owns the shared `spawnMinions` loop; per-spell differences live in three protected hooks (`getSummonCount`, `appliesAttackDamageModifier`, `customizeMinion`). Non-summon spells are untouched except `SpellImmuneBond` (ray-trace swap) and `SpellParasiteShroud` (uses the new feedback helper).

**Tech Stack:** Java 8, Minecraft 1.12.2 Forge, ForgeGradle 3, Electroblob's Wizardry 4.3.19 API (dev jar via CurseMaven), SRParasites 1.10.7 (dev jar in `libs/`).

**Spec:** `docs/superpowers/specs/2026-07-03-spell-architecture-rework-design.md`

**Testing note:** This project has no automated test suite (see CLAUDE.md) and MC 1.12.2 Forge mods have no practical unit-test harness here. Per-task verification = `./gradlew build` must pass (compiles against real EBW/SRP APIs, `-Xlint:all`). Final task = manual `runClient` checklist. This replaces the TDD test-first steps.

**Deviations from spec (decided during planning, from decompiled EBW 4.3.19 source):**
1. Generic bound is `T extends EntitySummonedCreature` (concrete EBW class all five minions already extend), not `EntityLivingBase & ISummonedCreature` — no intersection types, direct access to `setCaster`/`setLifetime`.
2. `customizeMinion` runs **before** the damage/health attribute modifiers — this is the order every current spell uses (lifetime → per-spell config → damage → health).
3. `SpellCastFeedback` wraps the particle burst only, plus one combined `impactAt` (particles + sound at an entity). Bare `world.playSound` calls are already one-liners; wrapping them alone adds nothing.
4. Known minor behavior change (accepted): `RayTracer.standardEntityRayTrace` respects line of sight — Immune Bond can no longer target entities through walls. Everything else must behave identically.
5. Two further Immune Bond deviations found in Task 8 quality review, accepted as inherent to `standardEntityRayTrace` (both from decompiled EBW 4.3.19 `RayTracer`): (a) aim tolerance is tighter — the old sweep grew every candidate hitbox by 0.3 blocks, the new call passes `aimAssist = 0` so there is no forgiveness margin (if playtesting in Task 9 shows the spell is frustrating to land, call `RayTracer.rayTrace(...)` directly with `aimAssist ≈ 0.3f`); (b) non-living entities (items, arrows, boats) on the ray can block a living target behind them — the closest `Entity` hit wins and the cast silently fails. Both judged low-impact; verify aim feel in Task 9.

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/com/spege/insanetweaks/spells/AbstractSrpSummonSpell.java` | Create | Shared summon spawn loop + hooks |
| `src/main/java/com/spege/insanetweaks/spells/AbstractSrpMinionSpell.java` | Delete | Dead placeholder |
| `src/main/java/com/spege/insanetweaks/spells/InsaneTweaksSpellMinion.java` | Delete | Dead placeholder |
| `src/main/java/com/spege/insanetweaks/spells/SpellSummonFerCow.java` | Rewrite | Migrate to base class |
| `src/main/java/com/spege/insanetweaks/spells/SpellSummonPrimitiveSummoner.java` | Rewrite | Migrate to base class |
| `src/main/java/com/spege/insanetweaks/spells/SpellSummonPrimitiveYelloweye.java` | Rewrite | Migrate to base class |
| `src/main/java/com/spege/insanetweaks/spells/SpellSummonWizard.java` | Rewrite | Migrate to base class |
| `src/main/java/com/spege/insanetweaks/spells/SpellCallOfDemise.java` | Rewrite | Migrate to base class |
| `src/main/java/com/spege/insanetweaks/spells/SpellImmuneBond.java` | Modify | RayTracer swap + feedback helper |
| `src/main/java/com/spege/insanetweaks/spells/SpellParasiteShroud.java` | Modify | Use feedback helper |
| `src/main/java/com/spege/insanetweaks/util/SpellCastFeedback.java` | Create | Particle/sound cast feedback |

`init/ModSpells.java` is NOT touched — registration and spell instances stay identical.

---

### Task 1: Create `AbstractSrpSummonSpell`, delete dead placeholders

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/spells/AbstractSrpSummonSpell.java`
- Delete: `src/main/java/com/spege/insanetweaks/spells/AbstractSrpMinionSpell.java`
- Delete: `src/main/java/com/spege/insanetweaks/spells/InsaneTweaksSpellMinion.java`

- [ ] **Step 1: Create the base class**

Full file content:

```java
package com.spege.insanetweaks.spells;

import java.util.function.Function;

import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;

import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.SpellMinion;
import electroblob.wizardry.util.BlockUtils;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Shared spawn loop for InsaneTweaks SRP summon spells.
 *
 * <p>Owns the logic every summon spell used to copy-paste: floor-space search
 * (with the flying variant), caster wiring, COTH-immunity safety, lifetime and
 * health scaling. Per-spell differences go through three hooks:
 * <ul>
 *   <li>{@link #getSummonCount(SpellModifiers)} — how many minions to spawn;</li>
 *   <li>{@link #appliesAttackDamageModifier()} — whether potency also scales
 *       the minion's melee attack-damage attribute;</li>
 *   <li>{@link #customizeMinion(EntitySummonedCreature, World, SpellModifiers)}
 *       — per-spell minion configuration, called before the attribute
 *       modifiers are applied (same order the old copies used).</li>
 * </ul>
 */
@SuppressWarnings("null")
public abstract class AbstractSrpSummonSpell<T extends EntitySummonedCreature> extends SpellMinion<T> {

    public AbstractSrpSummonSpell(String modID, String name, Function<World, T> minionFactory) {
        super(modID, name, minionFactory);
    }

    @Override
    protected boolean spawnMinions(World world, EntityLivingBase caster, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        int totalCount = this.getSummonCount(modifiers);

        for (int i = 0; i < totalCount; i++) {
            int summonRadius = this.getProperty(SUMMON_RADIUS).intValue();
            BlockPos pos = BlockUtils.findNearbyFloorSpace(caster, summonRadius, summonRadius * 2);

            if (this.flying) {
                if (pos != null) {
                    pos = pos.up(2);
                } else {
                    pos = caster.getPosition().add(world.rand.nextInt(summonRadius * 2) - summonRadius, 2,
                            world.rand.nextInt(summonRadius * 2) - summonRadius);
                }
            } else if (pos == null) {
                return false;
            }

            T minion = this.createMinion(world, caster, modifiers);
            minion.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            minion.setCaster(caster);
            SummonInfectionSafetyHelper.onSummonServerTick(minion);
            minion.setLifetime((int) (this.getProperty(MINION_LIFETIME).floatValue()
                    * modifiers.get(WizardryItems.duration_upgrade)));

            this.customizeMinion(minion, world, modifiers);

            if (this.appliesAttackDamageModifier()) {
                IAttributeInstance damage = minion.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
                if (damage != null) {
                    damage.applyModifier(new AttributeModifier(POTENCY_ATTRIBUTE_MODIFIER,
                            modifiers.get(POTENCY_ATTRIBUTE_MODIFIER) - 1.0F, 2));
                }
            }

            IAttributeInstance health = minion.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (health != null) {
                health.applyModifier(new AttributeModifier(HEALTH_MODIFIER, modifiers.get(HEALTH_MODIFIER) - 1.0F, 2));
                minion.setHealth(minion.getMaxHealth());
            }

            this.addMinionExtras(minion, pos, caster, modifiers, i);
            world.spawnEntity(minion);
        }

        return true;
    }

    /**
     * Number of minions to spawn. Default: the MINION_COUNT spell property
     * plus the flat wand-upgrade bonus.
     */
    protected int getSummonCount(SpellModifiers modifiers) {
        return this.getProperty(MINION_COUNT).intValue() + getFlatMinionCountBonus(modifiers);
    }

    /**
     * Whether the potency modifier is also applied to the minion's melee
     * attack-damage attribute. Default false; ranged/caster minions scale
     * their damage through {@link #customizeMinion} instead.
     */
    protected boolean appliesAttackDamageModifier() {
        return false;
    }

    /**
     * Per-spell minion configuration (potency multipliers, loadouts, ...).
     * Runs after caster/lifetime wiring and before attribute modifiers.
     */
    protected void customizeMinion(T minion, World world, SpellModifiers modifiers) {
        // default: no per-spell configuration
    }

    protected static int getFlatMinionCountBonus(SpellModifiers modifiers) {
        return Math.max(0, Math.round(modifiers.get(MINION_COUNT)) - 1);
    }
}
```

- [ ] **Step 2: Delete the dead placeholder files**

```powershell
Remove-Item src\main\java\com\spege\insanetweaks\spells\AbstractSrpMinionSpell.java -Confirm:$false
Remove-Item src\main\java\com\spege\insanetweaks\spells\InsaneTweaksSpellMinion.java -Confirm:$false
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (the new class compiles; nothing references the deleted files).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/
git commit -m "feat: add AbstractSrpSummonSpell shared summon loop, drop dead placeholders"
```

---

### Task 2: Migrate `SpellSummonFerCow`

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/spells/SpellSummonFerCow.java`

Old behavior to preserve: default count (property + flat bonus), ground spawn, attack-damage modifier ON, no custom config.

- [ ] **Step 1: Replace the file content entirely**

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityFerCowMinion;

@SuppressWarnings("null")
public class SpellSummonFerCow extends AbstractSrpSummonSpell<EntityFerCowMinion> {

    public SpellSummonFerCow() {
        super(InsaneTweaksMod.MODID, "summon_fer_cow", EntityFerCowMinion::new);
    }

    @Override
    protected boolean appliesAttackDamageModifier() {
        return true;
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellSummonFerCow.java
git commit -m "refactor: migrate SpellSummonFerCow to AbstractSrpSummonSpell"
```

---

### Task 3: Migrate `SpellSummonPrimitiveSummoner`

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/spells/SpellSummonPrimitiveSummoner.java`

Old behavior to preserve: default count, ground spawn, NO attack-damage modifier, `setPotencyMultiplier(potency)` before health modifier.

- [ ] **Step 1: Replace the file content entirely**

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityPrimitiveSummonerMinion;

import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellSummonPrimitiveSummoner extends AbstractSrpSummonSpell<EntityPrimitiveSummonerMinion> {

    public SpellSummonPrimitiveSummoner() {
        super(InsaneTweaksMod.MODID, "summon_primitive_summoner", EntityPrimitiveSummonerMinion::new);
    }

    @Override
    protected void customizeMinion(EntityPrimitiveSummonerMinion minion, World world, SpellModifiers modifiers) {
        minion.setPotencyMultiplier(modifiers.get(POTENCY_ATTRIBUTE_MODIFIER));
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellSummonPrimitiveSummoner.java
git commit -m "refactor: migrate SpellSummonPrimitiveSummoner to AbstractSrpSummonSpell"
```

---

### Task 4: Migrate `SpellSummonPrimitiveYelloweye`

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/spells/SpellSummonPrimitiveYelloweye.java`

Old behavior to preserve: default count, **flying** spawn (`pos.up(2)` / random offset fallback — the only `flying(true)` spell), NO attack-damage modifier, `setProjectileDamageMultiplier(potency)` before health modifier.

- [ ] **Step 1: Replace the file content entirely**

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityPrimitiveYelloweyeMinion;

import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellSummonPrimitiveYelloweye extends AbstractSrpSummonSpell<EntityPrimitiveYelloweyeMinion> {

    public SpellSummonPrimitiveYelloweye() {
        super(InsaneTweaksMod.MODID, "summon_primitive_yelloweye", EntityPrimitiveYelloweyeMinion::new);
        this.flying(true);
    }

    @Override
    protected void customizeMinion(EntityPrimitiveYelloweyeMinion minion, World world, SpellModifiers modifiers) {
        minion.setProjectileDamageMultiplier(modifiers.get(POTENCY_ATTRIBUTE_MODIFIER));
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellSummonPrimitiveYelloweye.java
git commit -m "refactor: migrate SpellSummonPrimitiveYelloweye to AbstractSrpSummonSpell"
```

---

### Task 5: Migrate `SpellSummonWizard`

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/spells/SpellSummonWizard.java`

Old behavior to preserve: default count, ground spawn, NO attack-damage modifier, `configureLoadout(Element.MAGIC, randomClass, randomTextureVariant, scaledPotency)` before health modifier. The potency-step scaling and the four-class pool stay as private members of this spell.

- [ ] **Step 1: Replace the file content entirely**

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityWizardMinion;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellSummonWizard extends AbstractSrpSummonSpell<EntityWizardMinion> {

    private static final float SUMMON_POTENCY_STEP = 0.20F;
    private static final float SUMMON_DAMAGE_BONUS_PER_STEP = 0.10F;
    private static final ItemWizardArmour.ArmourClass[] SUMMONABLE_WIZARD_CLASSES = new ItemWizardArmour.ArmourClass[] {
            ItemWizardArmour.ArmourClass.WIZARD,
            ItemWizardArmour.ArmourClass.SAGE,
            ItemWizardArmour.ArmourClass.WARLOCK,
            ItemWizardArmour.ArmourClass.BATTLEMAGE
    };

    public SpellSummonWizard() {
        super(InsaneTweaksMod.MODID, "summon_wizard", EntityWizardMinion::new);
    }

    @Override
    protected void customizeMinion(EntityWizardMinion minion, World world, SpellModifiers modifiers) {
        minion.configureLoadout(Element.MAGIC, this.getRandomWizardClass(world),
                world.rand.nextInt(EntityWizardMinion.TEXTURE_VARIANT_COUNT),
                this.getWizardMinionSpellPotency(modifiers));
    }

    private float getWizardMinionSpellPotency(SpellModifiers modifiers) {
        float basePotency = modifiers.get(SpellModifiers.POTENCY);
        float bonusPotency = Math.max(0.0F, basePotency - 1.0F);

        if (bonusPotency <= 0.0F) {
            return basePotency;
        }

        float summonBonus = bonusPotency * (SUMMON_DAMAGE_BONUS_PER_STEP / SUMMON_POTENCY_STEP);
        return basePotency + summonBonus;
    }

    private ItemWizardArmour.ArmourClass getRandomWizardClass(World world) {
        return SUMMONABLE_WIZARD_CLASSES[world.rand.nextInt(SUMMONABLE_WIZARD_CLASSES.length)];
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellSummonWizard.java
git commit -m "refactor: migrate SpellSummonWizard to AbstractSrpSummonSpell"
```

---

### Task 6: Migrate `SpellCallOfDemise`

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/spells/SpellCallOfDemise.java`

Old behavior to preserve: **fixed count of 1** (boss balance — ignores MINION_COUNT upgrades), ground spawn, attack-damage modifier ON, no custom config.

- [ ] **Step 1: Replace the file content entirely**

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityBeckonSivMinion;

import electroblob.wizardry.util.SpellModifiers;

@SuppressWarnings("null")
public class SpellCallOfDemise extends AbstractSrpSummonSpell<EntityBeckonSivMinion> {

    public SpellCallOfDemise() {
        super(InsaneTweaksMod.MODID, "call_of_demise", EntityBeckonSivMinion::new);
    }

    @Override
    protected int getSummonCount(SpellModifiers modifiers) {
        // Strictly one boss minion to balance the spell. Does not scale with
        // MINION_COUNT upgrades to prevent lag and OPness.
        return 1;
    }

    @Override
    protected boolean appliesAttackDamageModifier() {
        return true;
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellCallOfDemise.java
git commit -m "refactor: migrate SpellCallOfDemise to AbstractSrpSummonSpell"
```

---

### Task 8: Swap `SpellImmuneBond` ray-trace to Wizardry's `RayTracer`

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/spells/SpellImmuneBond.java`

Replaces the private ~30-line `rayTraceEntity` with `electroblob.wizardry.util.RayTracer.standardEntityRayTrace(World, Entity, double range, boolean hitLiquids)` (verified signature, decompiled 4.3.19). `RayTracer.ignoreEntityFilter` already excludes the caster and dying entities. **Accepted minor change:** line of sight is now respected (no more bonding through walls).

- [ ] **Step 1: Replace the file content entirely**

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.ImmuneBondHandler;
import com.spege.insanetweaks.util.SpellCastFeedback;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.RayTracer;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellImmuneBond extends Spell {

    private static final int BASE_DURATION = 1800; // 90 seconds
    private static final double TARGET_RANGE = 24.0D;

    public SpellImmuneBond() {
        super(InsaneTweaksMod.MODID, "immune_bond", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        EntityLivingBase target = null;
        RayTraceResult hit = RayTracer.standardEntityRayTrace(world, caster, TARGET_RANGE, false);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY
                && hit.entityHit instanceof EntityLivingBase) {
            target = (EntityLivingBase) hit.entityHit;
        }

        if (target == null) {
            return false;
        }

        // We only bond with non-player entities for now
        // (Player bonding can be implemented later with a specific artifact check)
        if (target instanceof EntityPlayer) {
            return false;
        }

        // Duration calculation: base * duration_upgrade + potency bonus (40 ticks per 10% potency step)
        float durationMult = modifiers.get(WizardryItems.duration_upgrade);
        float potency = modifiers.get(SpellModifiers.POTENCY);
        int potencyBonus = Math.max(0, Math.round((potency - 1.0f) / 0.1f)) * 40;
        int duration = Math.round(BASE_DURATION * durationMult) + potencyBonus;

        ImmuneBondHandler.applyBond(caster, target, duration);

        // Instant visual feedback on cast
        SpellCastFeedback.impactAt(world, target, 0.5D,
                EnumParticleTypes.SPELL_WITCH, 15, 0.4D, 0.5D, 0.4D, 0.05D,
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS,
                0.7F, 1.2F + world.rand.nextFloat() * 0.15F);

        return true;
    }
}
```

Note: this file uses the `SpellCastFeedback.impactAt` helper created in Task 7, which runs before this task.

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellImmuneBond.java
git commit -m "refactor: SpellImmuneBond uses Wizardry RayTracer and SpellCastFeedback"
```

---

### Task 7: Create `SpellCastFeedback` helper and migrate `SpellParasiteShroud`

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/util/SpellCastFeedback.java`
- Modify: `src/main/java/com/spege/insanetweaks/spells/SpellParasiteShroud.java`

This task runs before the `SpellImmuneBond` task (Task 8), which calls `impactAt`.

- [ ] **Step 1: Create the helper**

Full file content:

```java
package com.spege.insanetweaks.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Server-side cast feedback for spells: particle bursts and the common
 * "particle burst + sound at an entity" impact pattern. Centralizes the
 * {@code world instanceof WorldServer} dance every spell used to repeat.
 */
public final class SpellCastFeedback {

    private SpellCastFeedback() {
    }

    /** Spawns a server-side particle burst at the given position. No-op client-side. */
    public static void particleBurst(World world, double x, double y, double z,
            EnumParticleTypes type, int count, double spreadX, double spreadY, double spreadZ, double speed) {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(type, x, y, z, count, spreadX, spreadY, spreadZ, speed);
        }
    }

    /**
     * Particle burst anchored to an entity, at {@code heightFraction} of its
     * height (0 = feet, 0.5 = torso, 1 = head).
     */
    public static void particleBurstAt(World world, EntityLivingBase entity, double heightFraction,
            EnumParticleTypes type, int count, double spreadX, double spreadY, double spreadZ, double speed) {
        particleBurst(world, entity.posX, entity.posY + entity.height * heightFraction, entity.posZ,
                type, count, spreadX, spreadY, spreadZ, speed);
    }

    /** Particle burst + sound at the same entity — the usual cast/impact feedback. */
    public static void impactAt(World world, EntityLivingBase entity, double heightFraction,
            EnumParticleTypes type, int count, double spreadX, double spreadY, double spreadZ, double speed,
            SoundEvent sound, SoundCategory category, float volume, float pitch) {
        particleBurstAt(world, entity, heightFraction, type, count, spreadX, spreadY, spreadZ, speed);
        world.playSound(null, entity.posX, entity.posY, entity.posZ, sound, category, volume, pitch);
    }
}
```

- [ ] **Step 2: Migrate `SpellParasiteShroud` to the helper**

Full file content:

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.ParasiteShroudEventHandler;
import com.spege.insanetweaks.util.SpellCastFeedback;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellParasiteShroud extends Spell {

    private static final int BASE_DURATION_TICKS = 160;

    public SpellParasiteShroud() {
        super(InsaneTweaksMod.MODID, "parasite_shroud", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        int duration = Math.max(60, Math.round(BASE_DURATION_TICKS * modifiers.get(WizardryItems.duration_upgrade)));

        if (!world.isRemote) {
            ParasiteShroudEventHandler.applyShroud(caster, duration);

            SpellCastFeedback.impactAt(world, caster, 0.6D,
                    EnumParticleTypes.SPELL_MOB_AMBIENT, 20, 0.45D, 0.7D, 0.45D, 0.02D,
                    SoundEvents.ENTITY_ENDERMEN_AMBIENT, SoundCategory.PLAYERS,
                    0.8F, 0.7F + world.rand.nextFloat() * 0.1F);
        }

        return true;
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/util/SpellCastFeedback.java src/main/java/com/spege/insanetweaks/spells/SpellParasiteShroud.java
git commit -m "feat: add SpellCastFeedback helper, migrate SpellParasiteShroud"
```

---

### Task 9: Manual verification in `runClient`

**Files:** none (verification only)

- [ ] **Step 1: Launch the dev client**

Run: `./gradlew runClient` (working dir `run/`). Create/enter a creative world.

- [ ] **Step 2: Verify each migrated spell** (obtain spells via Wizardry scrolls/wand in creative; JEI is in the dev env)

| Spell | Check |
|---|---|
| Summon Fer Cow | Spawns on ground near caster; count matches MINION_COUNT property; melee hits scale with potency wand upgrades |
| Summon Primitive Summoner | Spawns on ground; NO melee potency scaling; its own summons/potency behave as before |
| Summon Primitive Yelloweye | Spawns **in the air** (2 blocks up); projectile damage scales with potency |
| Summon Wizard | Spawns on ground; random armour class from {Wizard, Sage, Warlock, Battlemage}; random texture; casts spells |
| Call of Demise | Spawns exactly **1** Beckon even with minion-count wand upgrades |
| Immune Bond | Bonds a mob at up to 24 blocks; witch particles + enchantment sound on target; does NOT work through a wall (accepted change); does NOT bond players; aim feel acceptable despite zero aim-assist (deviation 5a — if too strict, switch to direct `RayTracer.rayTrace` with `aimAssist ≈ 0.3f`) |
| Parasite Shroud | Shroud applies; ambient particles + enderman sound at caster |

Also confirm for one summon: minion despawns after its lifetime, and health scales with a health-upgrade wand.

- [ ] **Step 3: Confirm COTH safety** — summon any minion near infested blocks/parasites and verify it never gets converted (the `srpcothimmunity = 0` fix from Stage 0 working together with the base class's `onSummonServerTick`).

- [ ] **Step 4: Final commit if any fixups were needed, then update `NEXT_SESSION_SPELLS.md`** to mark the architecture phase done and point to the upcoming mechanics-phase brainstorm.

---

## Self-review results

- **Spec coverage:** base class + 3 hooks (Task 1), five migrations (Tasks 2–6), RayTracer swap (Task 7), SpellCastFeedback (Task 8), dead-file deletion (Task 1), manual verification (Task 9), no ModSpells/network/config changes (file map). Stage 0 was already done and committed. ✓
- **Placeholders:** none — every code step carries full file content. ✓
- **Type consistency:** hook names (`getSummonCount`, `appliesAttackDamageModifier`, `customizeMinion`) and `SpellCastFeedback.impactAt` signature match across all tasks; generic bound `EntitySummonedCreature` used consistently. ✓
- **Ordering:** Task 7 (helper) intentionally precedes Task 8 (ImmuneBond, which uses the helper) — every task builds green on its own.
