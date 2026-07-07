# Spell Mechanics & Visuals Rework Implementation Plan (part 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an SRP-particle network path, rework three spells (Yelloweye Gland charge-up, Parasite Shroud tiers/armour/break, Purifying Pulse parasite searing + COTH cleanse) and fill the remaining silent-feedback gaps.

**Architecture:** One new `PacketSrpParticle` (server→client, discriminator 4) lets server-side code spawn SRP's client-only particles (FLASH/DOT/GCLOUD) with arbitrary RGB via new `SpellCastFeedback.srpBurst*` overloads. Mechanics changes are confined to the three spells' own classes/handlers; one small client-only tick handler renders Yelloweye charge particles locally without networking.

**Tech Stack:** Java 8, Minecraft 1.12.2 Forge, EBW 4.3.19 (CurseMaven dev jar), SRParasites 1.10.7 (`libs/`), ForgeGradle 3.

**Spec:** `docs/superpowers/specs/2026-07-07-spell-mechanics-visuals-design.md`

**Testing note:** No automated test suite exists (see CLAUDE.md). Per-task verification = `./gradlew build` green. Final task = manual `runClient` checklist.

**Key API facts (verified against decompiled sources, do not re-derive):**
- `SRPEnumParticle` (`com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle`) is server-safe (imports only Guava/javax). IDs: GCLOUD=2, FLASH=7, DOT=8. `getParticleFromId(int)` returns null for unknown ids.
- `ParticleSpawner.spawnParticle(SRPEnumParticle, x, y, z, vx, vy, vz, r, g, b)` is CLIENT-ONLY — call it only from `@SideOnly(Side.CLIENT)` classes.
- Network discriminators 0, 1, 3 are taken; 2 is historically skipped — the new packet uses **4**.
- EBW native chargeup: `"chargeup"` int in the spell JSON; wand auto-fires after that many ticks held, with built-in charge sound and HUD bar. `Spell.getChargeup()` reads it. Elapsed use ticks on the client = `player.getItemInUseMaxCount()`.
- Primitive-tier SRP parasites all extend `com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPPrimitive`.
- `srpcothimmunity` NBT (SRP 1.10.7): value 0 = immune; value > 0 = tracked COTH victim. Cleansing REMOVES the tag (re-infectable), never sets 0.

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/com/spege/insanetweaks/network/PacketSrpParticle.java` | Create | Server→client SRP particle burst |
| `src/main/java/com/spege/insanetweaks/network/InsaneTweaksNetwork.java` | Modify | Register packet (discriminator 4) |
| `src/main/java/com/spege/insanetweaks/util/SpellCastFeedback.java` | Modify | `srpBurst` / `srpBurstAt` overloads |
| `src/main/java/com/spege/insanetweaks/spells/SpellParasiteShroud.java` | Rewrite | Duration 240, armour synergy, tier from potency |
| `src/main/java/com/spege/insanetweaks/events/ParasiteShroudEventHandler.java` | Rewrite | Tier filter, break-on-attack, expiry/break feedback |
| `src/main/java/com/spege/insanetweaks/spells/SpellYelloweyeGland.java` | Rewrite | Remove cycle, always explosive |
| `src/main/resources/assets/insanetweaks/spells/yelloweye_gland.json` | Modify | chargeup 30, cost 140, cooldown 100 |
| `src/main/java/com/spege/insanetweaks/client/YelloweyeChargeHandler.java` | Create | Client-only charge particles |
| `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` | Modify | Register client handler |
| `src/main/java/com/spege/insanetweaks/entities/EntityPurifyingWave.java` | Modify | `affectBeckons` → `affectParasites` + COTH cleanse |
| `src/main/java/com/spege/insanetweaks/events/ImmuneBondHandler.java` | Modify | REDSTONE ring → violet SRP DOT every 40 ticks |
| `src/main/java/com/spege/insanetweaks/entities/ThrallSlotManager.java` | Modify | Dead `SMOKE_LARGE` call → packet burst |

`init/ModSpells.java` is NOT touched. Entity tracking IDs, existing packets, config flags unchanged.

---

### Task 1: `PacketSrpParticle` + `SpellCastFeedback` SRP overloads

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/network/PacketSrpParticle.java`
- Modify: `src/main/java/com/spege/insanetweaks/network/InsaneTweaksNetwork.java`
- Modify: `src/main/java/com/spege/insanetweaks/util/SpellCastFeedback.java`

- [ ] **Step 1: Create the packet**

Full file content:

```java
package com.spege.insanetweaks.network;

import java.util.Random;

import com.dhanantry.scapeandrunparasites.client.particle.ParticleSpawner;
import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server-to-client burst of SRP 1.10.7 particles (FLASH / DOT / GCLOUD, ...)
 * with an arbitrary RGB colour. SRP's own SRPPacketParticle only supports a
 * handful of hardcoded scenarios, hence this packet. The handler is client-only
 * because {@link ParticleSpawner} is a client-only class (same sided pattern as
 * PacketOpenSentinelLoot).
 */
public class PacketSrpParticle implements IMessage {

    private double x;
    private double y;
    private double z;
    private byte typeId;
    private int rgb;
    private byte count;
    private float spreadH;
    private float spreadV;
    private float speed;

    public PacketSrpParticle() {
    }

    public PacketSrpParticle(double x, double y, double z, SRPEnumParticle type, int rgb,
            int count, float spreadH, float spreadV, float speed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.typeId = (byte) type.getParticleID();
        this.rgb = rgb;
        this.count = (byte) Math.min(count, 127);
        this.spreadH = spreadH;
        this.spreadV = spreadV;
        this.speed = speed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.typeId = buf.readByte();
        this.rgb = buf.readInt();
        this.count = buf.readByte();
        this.spreadH = buf.readFloat();
        this.spreadV = buf.readFloat();
        this.speed = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeByte(this.typeId);
        buf.writeInt(this.rgb);
        buf.writeByte(this.count);
        buf.writeFloat(this.spreadH);
        buf.writeFloat(this.spreadV);
        buf.writeFloat(this.speed);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketSrpParticle, IMessage> {

        @Override
        public IMessage onMessage(PacketSrpParticle message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                SRPEnumParticle type = SRPEnumParticle.getParticleFromId(message.typeId);
                if (type == null) {
                    return;
                }

                Random rand = new Random();
                int r = (message.rgb >> 16) & 0xFF;
                int g = (message.rgb >> 8) & 0xFF;
                int b = message.rgb & 0xFF;

                for (int i = 0; i < message.count; i++) {
                    double px = message.x + (rand.nextFloat() * 2.0F - 1.0F) * message.spreadH;
                    double py = message.y + (rand.nextFloat() * 2.0F - 1.0F) * message.spreadV;
                    double pz = message.z + (rand.nextFloat() * 2.0F - 1.0F) * message.spreadH;
                    ParticleSpawner.spawnParticle(type, px, py, pz,
                            rand.nextGaussian() * message.speed,
                            rand.nextGaussian() * message.speed,
                            rand.nextGaussian() * message.speed,
                            r, g, b);
                }
            });
            return null;
        }
    }
}
```

- [ ] **Step 2: Register the packet**

In `InsaneTweaksNetwork.init()`, after the `PacketOpenSentinelLoot` line, add:

```java
        CHANNEL.registerMessage(PacketSrpParticle.Handler.class, PacketSrpParticle.class, 4, Side.CLIENT);
```

- [ ] **Step 3: Add SRP overloads to `SpellCastFeedback`**

Add these imports to `src/main/java/com/spege/insanetweaks/util/SpellCastFeedback.java`:

```java
import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketSrpParticle;
import net.minecraftforge.fml.common.network.NetworkRegistry;
```

Add these two methods at the end of the class (before the closing brace):

```java
    /**
     * Server-side SRP particle burst: sends {@link PacketSrpParticle} to all
     * clients within 48 blocks. No-op client-side. {@code rgb} is 0xRRGGBB.
     */
    public static void srpBurst(World world, double x, double y, double z,
            SRPEnumParticle type, int rgb, int count, float spreadH, float spreadV, float speed) {
        if (world.isRemote) {
            return;
        }
        InsaneTweaksNetwork.CHANNEL.sendToAllAround(
                new PacketSrpParticle(x, y, z, type, rgb, count, spreadH, spreadV, speed),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), x, y, z, 48.0D));
    }

    /**
     * SRP particle burst anchored to an entity at {@code heightFraction} of its
     * height (0 = feet, 0.5 = torso, 1 = head).
     */
    public static void srpBurstAt(World world, EntityLivingBase entity, double heightFraction,
            SRPEnumParticle type, int rgb, int count, float spreadH, float spreadV, float speed) {
        srpBurst(world, entity.posX, entity.posY + entity.height * heightFraction, entity.posZ,
                type, rgb, count, spreadH, spreadV, speed);
    }
```

Note: `SRPEnumParticle` is safe to reference in server-loaded classes — the enum has no client imports. Only `ParticleSpawner` (used exclusively inside the `@SideOnly` handler) is client-only.

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/network/ src/main/java/com/spege/insanetweaks/util/SpellCastFeedback.java
git commit -m "feat: add PacketSrpParticle and SRP burst overloads in SpellCastFeedback"
```

---

### Task 2: Parasite Shroud — tiers, armour synergy, break-on-attack

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/spells/SpellParasiteShroud.java`
- Rewrite: `src/main/java/com/spege/insanetweaks/events/ParasiteShroudEventHandler.java`

Behavior: base duration 160 → 240 ticks; +15% duration per worn `ItemWizardArmour` piece (any element); tier 2 (hides from ALL parasites, current behavior) requires potency ≥ 1.3, tier 1 hides only from `EntityPPrimitive`; any player attack breaks the shroud (red FLASH + glass break); natural expiry shows grey GCLOUD, no sound. Scent disruption stays tier-independent.

- [ ] **Step 1: Rewrite `SpellParasiteShroud.java`**

Full file content:

```java
package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.ParasiteShroudEventHandler;
import com.spege.insanetweaks.util.SpellCastFeedback;

import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellParasiteShroud extends Spell {

    // Raised from 160 in exchange for the shroud breaking when the player attacks.
    private static final int BASE_DURATION_TICKS = 240;
    private static final int MIN_DURATION_TICKS = 60;
    private static final float ARMOUR_DURATION_BONUS = 0.15F;
    private static final float FULL_SHROUD_POTENCY_THRESHOLD = 1.3F;

    public SpellParasiteShroud() {
        super(InsaneTweaksMod.MODID, "parasite_shroud", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (!world.isRemote) {
            float armourMultiplier = 1.0F + ARMOUR_DURATION_BONUS * countWizardArmourPieces(caster);
            int duration = Math.max(MIN_DURATION_TICKS, Math.round(BASE_DURATION_TICKS
                    * modifiers.get(WizardryItems.duration_upgrade) * armourMultiplier));
            int tier = modifiers.get(SpellModifiers.POTENCY) >= FULL_SHROUD_POTENCY_THRESHOLD ? 2 : 1;

            ParasiteShroudEventHandler.applyShroud(caster, duration, tier);

            SpellCastFeedback.impactAt(world, caster, 0.6D,
                    EnumParticleTypes.SPELL_MOB_AMBIENT, 20, 0.45D, 0.7D, 0.45D, 0.02D,
                    SoundEvents.ENTITY_ENDERMEN_AMBIENT, SoundCategory.PLAYERS,
                    0.8F, 0.7F + world.rand.nextFloat() * 0.1F);
        }

        return true;
    }

    private static int countWizardArmourPieces(EntityPlayer player) {
        int count = 0;
        for (ItemStack stack : player.inventory.armorInventory) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemWizardArmour) {
                count++;
            }
        }
        return count;
    }
}
```

- [ ] **Step 2: Rewrite `ParasiteShroudEventHandler.java`**

Full file content:

```java
package com.spege.insanetweaks.events;

import java.util.List;

import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.dhanantry.scapeandrunparasites.entity.EntityParasiticScent;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPPrimitive;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.spege.insanetweaks.util.SpellCastFeedback;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings("null")
public class ParasiteShroudEventHandler {

    public static final String SHROUD_TICKS_KEY = "InsaneTweaksParasiteShroudTicks";
    public static final String SHROUD_TIER_KEY = "InsaneTweaksParasiteShroudTier";

    private static final double SHROUD_HORIZONTAL_RADIUS = 34.0D;
    private static final double SHROUD_VERTICAL_RADIUS = 11.0D;
    private static final double SCENT_HORIZONTAL_RADIUS = 67.0D;
    private static final double SCENT_VERTICAL_RADIUS = 17.0D;
    private static final int DISRUPT_INTERVAL = 7;
    private static final float DISRUPT_CHANCE = 0.7F;

    private static final int EXPIRY_RGB = 0x9A9A9A;
    private static final int BREAK_RGB = 0xC81E1E;

    public static void applyShroud(EntityPlayer player, int durationTicks, int tier) {
        if (player == null || durationTicks <= 0) {
            return;
        }

        NBTTagCompound data = player.getEntityData();
        int current = data.getInteger(SHROUD_TICKS_KEY);
        data.setInteger(SHROUD_TICKS_KEY, Math.max(current, durationTicks));
        // Recasting never downgrades an active full shroud.
        data.setInteger(SHROUD_TIER_KEY, Math.max(data.getInteger(SHROUD_TIER_KEY), tier));
    }

    public static boolean hasShroud(EntityPlayer player) {
        return player != null && player.getEntityData().getInteger(SHROUD_TICKS_KEY) > 0;
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) {
            return;
        }

        NBTTagCompound data = player.getEntityData();
        int ticksLeft = data.getInteger(SHROUD_TICKS_KEY);
        if (ticksLeft <= 0) {
            return;
        }

        if (ticksLeft <= 1) {
            endShroud(player, data, true);
            return;
        }

        data.setInteger(SHROUD_TICKS_KEY, ticksLeft - 1);

        if (player.ticksExisted % DISRUPT_INTERVAL != 0) {
            return;
        }

        this.disruptNearbyScents(player);
        this.disruptNearbyParasites(player, data.getInteger(SHROUD_TIER_KEY) >= 2);
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || player.world.isRemote) {
            return;
        }

        NBTTagCompound data = player.getEntityData();
        if (data.getInteger(SHROUD_TICKS_KEY) <= 0) {
            return;
        }

        endShroud(player, data, false);
    }

    private static void endShroud(EntityPlayer player, NBTTagCompound data, boolean natural) {
        data.removeTag(SHROUD_TICKS_KEY);
        data.removeTag(SHROUD_TIER_KEY);

        if (natural) {
            SpellCastFeedback.srpBurstAt(player.world, player, 0.5D,
                    SRPEnumParticle.GCLOUD, EXPIRY_RGB, 10, 0.5F, 0.6F, 0.01F);
        } else {
            SpellCastFeedback.srpBurstAt(player.world, player, 0.5D,
                    SRPEnumParticle.FLASH, BREAK_RGB, 6, 0.4F, 0.5F, 0.02F);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8F, 0.8F);
        }
    }

    private void disruptNearbyScents(EntityPlayer player) {
        player.removePotionEffect(SRPPotions.PREY_E);
        player.removePotionEffect(SRPPotions.SPOT_E);

        AxisAlignedBB area = player.getEntityBoundingBox().grow(SCENT_HORIZONTAL_RADIUS, SCENT_VERTICAL_RADIUS,
                SCENT_HORIZONTAL_RADIUS);
        List<EntityParasiticScent> scents = player.world.getEntitiesWithinAABB(EntityParasiticScent.class, area);

        for (EntityParasiticScent scent : scents) {
            if (scent == null || !scent.isEntityAlive()) {
                continue;
            }

            EntityLivingBase target = scent.getTargetToKill();
            if (target != player) {
                continue;
            }

            if (player.getRNG().nextFloat() > DISRUPT_CHANCE) {
                continue;
            }

            scent.setDead();
        }
    }

    private void disruptNearbyParasites(EntityPlayer player, boolean fullShroud) {
        AxisAlignedBB area = player.getEntityBoundingBox().grow(SHROUD_HORIZONTAL_RADIUS, SHROUD_VERTICAL_RADIUS,
                SHROUD_HORIZONTAL_RADIUS);
        List<EntityParasiteBase> parasites = player.world.getEntitiesWithinAABB(EntityParasiteBase.class, area);

        for (EntityParasiteBase parasite : parasites) {
            if (parasite == null || !parasite.isEntityAlive()) {
                continue;
            }

            // Tier 1 only hides from primitive-stage parasites; tier 2 from all.
            if (!fullShroud && !(parasite instanceof EntityPPrimitive)) {
                continue;
            }

            EntityLivingBase attackTarget = parasite.getAttackTarget();
            EntityLivingBase revengeTarget = parasite.getRevengeTarget();
            if (attackTarget != player && revengeTarget != player) {
                continue;
            }

            if (player.getRNG().nextFloat() > DISRUPT_CHANCE) {
                continue;
            }

            clearTargeting(parasite);
        }
    }

    private static void clearTargeting(EntityParasiteBase parasite) {
        parasite.setAttackTarget(null);
        parasite.setRevengeTarget(null);
        if (parasite instanceof EntityLiving) {
            ((EntityLiving) parasite).getNavigator().clearPath();
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (the only caller of `applyShroud` is `SpellParasiteShroud`, updated in the same task).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellParasiteShroud.java src/main/java/com/spege/insanetweaks/events/ParasiteShroudEventHandler.java
git commit -m "feat: Parasite Shroud tiers, wizard-armour synergy, break-on-attack"
```

---

### Task 3: Yelloweye Gland — charge-up replaces the shot cycle

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/spells/SpellYelloweyeGland.java`
- Modify: `src/main/resources/assets/insanetweaks/spells/yelloweye_gland.json`
- Create: `src/main/java/com/spege/insanetweaks/client/YelloweyeChargeHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

Behavior: the every-4th-shot cycle (NBT tag `insanetweaksYelloweyeGlandCycle`) is deleted; every shot is the explosive variant; chargeup rises 12 → 30 ticks with cost 120 → 140 and cooldown 70 → 100 as balance; a client-only tick handler shows yellow-green DOT particles converging on the hand while charging (EBW already provides the charge sound + HUD bar natively).

- [ ] **Step 1: Rewrite `SpellYelloweyeGland.java`**

Full file content:

```java
package com.spege.insanetweaks.spells;

import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.SRPAttributes;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfigMobs;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeGlandProjectile;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellYelloweyeGland extends Spell {

    private static final float PROJECTILE_VELOCITY = 1.25F;

    public SpellYelloweyeGland() {
        super(InsaneTweaksMod.MODID, "yelloweye_gland", SpellActions.POINT, false);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        // Every shot is the heavy explosive variant; the cast is gated by the
        // chargeup time in yelloweye_gland.json instead of the old 4-shot cycle.
        float potency = modifiers.get(SpellModifiers.POTENCY);
        EntityYelloweyeGlandProjectile projectile = new EntityYelloweyeGlandProjectile(world);
        projectile.setCaster(caster);
        projectile.setBaseDamage(SRPAttributes.EMANA_RANGED_DAMAGE);
        projectile.setDurationAmplifier(SRPConfigMobs.emanaPoisonDuration, SRPConfigMobs.emanaPoisonAmplifier);
        projectile.setGearDamage(SRPConfigMobs.emanaGearD);
        projectile.setPotencyMultiplier(potency);
        projectile.setExplosiveShot(true);
        projectile.configureNade(3, 60, SRPAttributes.EMANA_RANGED_DAMAGE * potency);
        projectile.aim(caster, PROJECTILE_VELOCITY);
        world.spawnEntity(projectile);

        world.playSound(null, caster.posX, caster.posY, caster.posZ, SRPSounds.EMANA_SHOOTING,
                SoundCategory.PLAYERS, 2.0F, 2.0F);
        return true;
    }
}
```

- [ ] **Step 2: Update `yelloweye_gland.json`**

Change exactly three values (rest of the file unchanged): `"cost": 120` → `"cost": 140`, `"chargeup": 12` → `"chargeup": 30`, `"cooldown": 70` → `"cooldown": 100`.

- [ ] **Step 3: Create the client charge handler**

Full file content:

```java
package com.spege.insanetweaks.client;

import java.util.Random;

import com.dhanantry.scapeandrunparasites.client.particle.ParticleSpawner;
import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.spege.insanetweaks.init.ModSpells;

import electroblob.wizardry.item.ISpellCastingItem;
import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders yellow-green SRP DOT particles converging on the casting hand while
 * the Yelloweye Gland chargeup is in progress. Purely cosmetic and local to
 * each client, so no networking; EBW itself provides the charge sound and the
 * HUD charge bar.
 */
@SideOnly(Side.CLIENT)
public class YelloweyeChargeHandler {

    private static final int CHARGE_RGB_R = 190;
    private static final int CHARGE_RGB_G = 210;
    private static final int CHARGE_RGB_B = 60;

    private final Random rand = new Random();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != Side.CLIENT || event.phase != TickEvent.Phase.END) {
            return;
        }

        EntityPlayer player = event.player;
        if (!player.isHandActive()) {
            return;
        }

        ItemStack stack = player.getActiveItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ISpellCastingItem)) {
            return;
        }

        Spell spell = ((ISpellCastingItem) stack.getItem()).getCurrentSpell(stack);
        if (spell != ModSpells.YELLOWEYE_GLAND || player.getItemInUseMaxCount() >= spell.getChargeup()) {
            return;
        }

        Vec3d look = player.getLookVec();
        double anchorX = player.posX + look.x * 0.6D;
        double anchorY = player.posY + player.getEyeHeight() - 0.35D + look.y * 0.6D;
        double anchorZ = player.posZ + look.z * 0.6D;

        for (int i = 0; i < 2; i++) {
            double px = anchorX + (this.rand.nextFloat() * 2.0F - 1.0F) * 0.7D;
            double py = anchorY + (this.rand.nextFloat() * 2.0F - 1.0F) * 0.5D;
            double pz = anchorZ + (this.rand.nextFloat() * 2.0F - 1.0F) * 0.7D;
            ParticleSpawner.spawnParticle(SRPEnumParticle.DOT, px, py, pz,
                    (anchorX - px) * 0.2D, (anchorY - py) * 0.2D, (anchorZ - pz) * 0.2D,
                    CHARGE_RGB_R, CHARGE_RGB_G, CHARGE_RGB_B);
        }
    }
}
```

- [ ] **Step 4: Register the handler client-side**

In `InsaneTweaksMod.java`, inside the existing `if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {` block in `init` (the one registering `SpellItemTooltipHandler` etc., around line 336), add after the `ThrallClientInteractionHandler` line:

```java
            if (com.spege.insanetweaks.config.ModConfig.modules.enableSpells) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.client.YelloweyeChargeHandler());
            }
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/spells/SpellYelloweyeGland.java src/main/resources/assets/insanetweaks/spells/yelloweye_gland.json src/main/java/com/spege/insanetweaks/client/YelloweyeChargeHandler.java src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat: Yelloweye Gland charge-up shot replaces 4-shot cycle"
```

---

### Task 4: Purifying Pulse — sear all parasites, cleanse COTH

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntityPurifyingWave.java`

Behavior: the wave's per-entity pass is generalized from Beckons-only to all living entities in the ring: Beckons keep today's harsh treatment (6 dmg + glow + weakness/slowness amp 1 + CRIT_MAGIC); other SRP parasites get a lighter "sear" (2 magic dmg + weakness/slowness amp 0 for 80 ticks + golden FLASH); non-parasite mobs with an active COTH infection get cured (strip `COTH_E` potion, REMOVE `srpcothimmunity` tag — never set 0; bonded mobs hold the tag at 0 and are skipped by the `> 0` check) with a white-gold DOT burst + XP-orb sound. Block purification (`purifyRing`) is untouched.

- [ ] **Step 1: Rename the dedup set**

In `EntityPurifyingWave.java`, rename **all** occurrences of the identifier `affectedBeckons` to `affectedEntities` (field declaration and every use — a plain find-and-replace within this one file).

- [ ] **Step 2: Add imports**

Add to the import block (keep alphabetical grouping with existing imports):

```java
import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.spege.insanetweaks.util.SpellCastFeedback;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
```

(Skip any of these already present — the file already imports `SoundEvents`/`SoundCategory` only if used; check and de-duplicate.)

- [ ] **Step 3: Add constants**

Next to the existing `BECKON_*` constants add:

```java
    private static final int SEAR_DEBUFF_DURATION = 80;
    private static final float SEAR_DAMAGE = 2.0F;
    private static final int SEAR_RGB = 0xFFC83C;
    private static final int CLEANSE_RGB = 0xFFF0BE;
```

- [ ] **Step 4: Replace `affectBeckons` with `affectParasites`**

Replace the entire `affectBeckons(double currentRadius)` method with:

```java
    private void affectParasites(double currentRadius) {
        AxisAlignedBB area = new AxisAlignedBB(this.posX, this.posY, this.posZ, this.posX + 1.0D, this.posY + 1.0D,
                this.posZ + 1.0D).grow(currentRadius, this.verticalRange + 2, currentRadius);
        List<EntityLivingBase> entities = this.world.getEntitiesWithinAABB(EntityLivingBase.class, area);

        for (EntityLivingBase entity : entities) {
            if (entity == null || !entity.isEntityAlive()
                    || !this.affectedEntities.add(Integer.valueOf(entity.getEntityId()))) {
                continue;
            }

            double dx = entity.posX - this.posX;
            double dz = entity.posZ - this.posZ;
            if (dx * dx + dz * dz > currentRadius * currentRadius) {
                continue;
            }

            if (SrpPurificationHelper.isBeckon(entity)) {
                this.searBeckon(entity);
            } else if (entity instanceof EntityParasiteBase) {
                this.searParasite(entity);
            } else {
                this.cleanseCothInfection(entity);
            }
        }
    }

    private void searBeckon(EntityLivingBase entity) {
        EntityLivingBase owner = this.getOwner();
        entity.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, owner == null ? this : owner),
                BECKON_DAMAGE);
        entity.addPotionEffect(new PotionEffect(MobEffects.GLOWING, BECKON_GLOW_DURATION, 0, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, BECKON_DEBUFF_DURATION, 1, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, BECKON_DEBUFF_DURATION, 1, false, true));

        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.CRIT_MAGIC, entity.posX,
                    entity.posY + entity.height * 0.5D, entity.posZ, 12, entity.width * 0.35D,
                    entity.height * 0.25D, entity.width * 0.35D, 0.05D);
        }
    }

    private void searParasite(EntityLivingBase parasite) {
        EntityLivingBase owner = this.getOwner();
        parasite.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, owner == null ? this : owner),
                SEAR_DAMAGE);
        parasite.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, SEAR_DEBUFF_DURATION, 0, false, true));
        parasite.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, SEAR_DEBUFF_DURATION, 0, false, true));
        SpellCastFeedback.srpBurstAt(this.world, parasite, 0.5D, SRPEnumParticle.FLASH, SEAR_RGB, 2, 0.3F, 0.3F,
                0.01F);
    }

    private void cleanseCothInfection(EntityLivingBase entity) {
        boolean hadPotion = entity.isPotionActive(SRPPotions.COTH_E);
        NBTTagCompound tags = entity.getEntityData();
        // srpcothimmunity > 0 = tracked COTH victim (SRP 1.10.7). Value 0 means
        // immune (e.g. an Immune Bond target) — those are deliberately skipped.
        boolean hadTag = tags.getInteger("srpcothimmunity") > 0;
        if (!hadPotion && !hadTag) {
            return;
        }

        if (hadPotion) {
            entity.removePotionEffect(SRPPotions.COTH_E);
        }
        if (hadTag) {
            // Remove rather than zero: cured, but re-infectable later.
            tags.removeTag("srpcothimmunity");
        }

        SpellCastFeedback.srpBurstAt(this.world, entity, 0.5D, SRPEnumParticle.DOT, CLEANSE_RGB, 8, 0.4F, 0.5F,
                0.02F);
        this.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.6F, 0.8F);
    }
```

Note: `searBeckon` is the old Beckon branch extracted verbatim (same damage source construction, same effects, same CRIT_MAGIC burst). Keep the `isBeckon` check FIRST — Beckons are themselves `EntityParasiteBase` subclasses and must not fall through to the lighter sear.

- [ ] **Step 5: Update the call site**

In `onUpdate()`, change `this.affectBeckons(currentRadius);` to `this.affectParasites(currentRadius);`.

- [ ] **Step 6: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/entities/EntityPurifyingWave.java
git commit -m "feat: Purifying Pulse sears all parasites and cleanses COTH infection"
```

---

### Task 5: Immune Bond DOT swap + Thrall spawn burst

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/events/ImmuneBondHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/entities/ThrallSlotManager.java`

- [ ] **Step 1: Swap the bond ring for a violet SRP DOT pulse (every 2 s)**

In `ImmuneBondHandler.java`:

a) Change the particle cadence — in `onLivingUpdate`, replace:

```java
        boolean particleTick  = player.ticksExisted % 10 == 0;
```

with:

```java
        boolean particleTick  = player.ticksExisted % 40 == 0;
```

b) Replace the whole `spawnBondParticles` method (and its call) — the call site changes from:

```java
        if (particleTick) {
            spawnBondParticles((WorldServer) player.world, target, player.ticksExisted);
        }
```

to:

```java
        if (particleTick) {
            spawnBondParticles(player.world, target);
        }
```

and the method becomes:

```java
    private static void spawnBondParticles(World world, EntityLivingBase target) {
        SpellCastFeedback.srpBurstAt(world, target, 0.5D, SRPEnumParticle.DOT, BOND_RGB, 5,
                (float) Math.max(0.4D, target.width * 0.7D), (float) (target.height * 0.3D), 0.0F);
    }
```

c) Add the constant next to the class's fields:

```java
    private static final int BOND_RGB = 0x9646DC;
```

d) Fix imports: add

```java
import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.spege.insanetweaks.util.SpellCastFeedback;
import net.minecraft.world.World;
```

and remove the now-unused

```java
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
```

- [ ] **Step 2: Replace the dead thrall spawn particle call**

In `ThrallSlotManager.java` (around line 264), replace:

```java
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,
                thrall.posX, thrall.posY + 1.0, thrall.posZ, 0.0, 0.0, 0.0);
```

with (this code runs server-side, where `World.spawnParticle` was a silent no-op):

```java
        com.spege.insanetweaks.util.SpellCastFeedback.srpBurst(world,
                thrall.posX, thrall.posY + 1.0D, thrall.posZ,
                com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle.FLASH,
                0x781414, 2, 0.4F, 0.5F, 0.01F);
        com.spege.insanetweaks.util.SpellCastFeedback.srpBurst(world,
                thrall.posX, thrall.posY + 0.2D, thrall.posZ,
                com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle.GCLOUD,
                0x503232, 4, 0.4F, 0.2F, 0.01F);
```

(Fully-qualified names match this file's existing inline style — it already uses `net.minecraft.util.EnumParticleTypes` inline.)

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/events/ImmuneBondHandler.java src/main/java/com/spege/insanetweaks/entities/ThrallSlotManager.java
git commit -m "feat: SRP particles for bond aura and thrall spawn"
```

---

### Task 6: Manual verification in `runClient`

**Files:** none (verification only; update `NEXT_SESSION_SPELLS.md` at the end)

- [ ] **Step 1: Launch** `./gradlew runClient`, create/enter a creative world. (The deferred part-1 Task 9 checklist can be run in the same session.)

- [ ] **Step 2: Verify each change**

| Change | Check |
|---|---|
| Parasite Shroud tier 1 | Base-potency cast: primitive parasites (Rupter, Yelloweye, …) lose the player; advanced ones (adapted+) keep attacking |
| Parasite Shroud tier 2 | With potency ≥ 1.3 (wand upgrades/artefacts): advanced parasites also lose the player |
| Shroud armour synergy | Duration visibly longer with 4 wizard-armour pieces vs none (~+60%) |
| Shroud break | Attacking any mob while shrouded: red flash + glass-break sound, shroud gone immediately |
| Shroud expiry | Letting it run out: grey smoke puff, no sound |
| Yelloweye Gland | ~1.5 s charge (sound + HUD bar + yellow-green converging dots), every shot explodes, no 4-shot cycle |
| Purifying Pulse | Regular parasites in the wave: brief golden flash, weakness/slowness; Beckons: unchanged harsh hit; infected cow/villager (COTH): white-gold sparkle + XP-orb sound, conversion never completes; re-infection possible later |
| Pulse: player cure (deviation 1) | COTH-infected player (incl. the caster) in the wave gets cured too — confirm the feel is right |
| Pulse: bonded mob | Immune Bond target (`srpcothimmunity` = 0) in the wave is NOT cleansed (no DOT/XP-orb) |
| Pulse: uninfected passive mob | Plain cow in the wave gets no particle/sound (early-return path) |
| Immune Bond aura | Bonded mob shows a violet dot pulse ~every 2 s (yellow redstone ring is gone) |
| Thrall spawn | Casting Summon Thrall: dark-red flash + dark smoke at the thrall |
| Dedicated-server sanity | `./gradlew runServer` boots without a client-classloading crash (PacketSrpParticle handler is client-only) |

- [ ] **Step 3: Update `NEXT_SESSION_SPELLS.md`** — mark part 2 done (or record fixups), note any balance numbers changed during testing.

---

## Accepted deviations

1. **(Task 4) COTH cleanse reaches players.** The generalized `EntityLivingBase` query means a COTH-infected player (including the caster standing in their own wave) is cured by `cleanseCothInfection`. The spec wording says "non-parasite mobs", but curing players is a strict superset consistent with the "real cure" intent — accepted at quality review (2026-07-07); probe the feel in Task 6.

## Self-review results

- **Spec coverage:** packet+overloads (Task 1), Shroud tiers/armour/break/expiry (Task 2), Yelloweye chargeup+client particles (Task 3), Pulse sear+cleanse (Task 4), bond swap + thrall burst (Task 5), manual checklist incl. server sanity (Task 6). Spec's "not touched" list respected (no ModSpells/AbstractSrpSummonSpell/minion entity changes). ✓
- **Placeholders:** none — full code in every step. ✓
- **Type consistency:** `srpBurst`/`srpBurstAt` signatures identical across Tasks 1/2/4/5 (`World, [pos|entity+heightFraction], SRPEnumParticle, int rgb, int count, float spreadH, float spreadV, float speed`); `applyShroud(EntityPlayer, int, int)` matches between Task 2's two files; `affectedEntities` rename is confined to Task 4's file. ✓
- **Ordering:** Task 1 must run first (everything else calls `srpBurst*`); Tasks 2–5 are otherwise independent; each builds green on its own.
