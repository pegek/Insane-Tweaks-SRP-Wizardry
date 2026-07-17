# Handoff: AM2 NPC-cast fix + targeted base-mod analysis

> **For the executing agent:** this is a self-contained handoff. Everything you need is in
> this file plus the referenced sources — do NOT re-derive the diagnosis, it is settled and
> bytecode-verified. Follow superpowers:executing-plans discipline: implement task by task,
> `./gradlew build` after each (expect BUILD SUCCESSFUL; the ~24 pre-existing warnings are
> normal), commit per task with the trailer
> `Co-Authored-By: <your model name> <noreply@anthropic.com>`, work directly on `main`.
> Read CLAUDE.md first: Java 8 ONLY, `-Xlint:all`, no test suite (never add one).
>
> **Recommended models** (per controller): Task 1 = Sonnet-class (event-bus semantics are
> subtle); Task 2 = Haiku-class is sufficient (mechanical extraction with a fixed checklist).

## Established facts (do not re-verify, citations included)

1. **Ars Magica 2 breaks our NPC casters.** `am2/common/compat/electroblob/EBWizardryCompatHandler`
   (jar `ArsMagica2-1.12.2-1.6.2.jar`) subscribes to EB Wizardry's `SpellCastEvent.Pre` and:
   - cancels when the caster has AM2 `silence`;
   - otherwise runs AM2 burnout/mana accounting on EVERY caster with a non-null
     `am2.common.extensions.EntityExtension.For(caster)` and cancels when the caster can't
     pay. Our `EntitySimWizard`/`EntitySentinel` always fail this (they have no AM2 mana):
     playtest 2026-07-17 = **344/359 NPC casts vetoed**, only the cheapest novice spells
     (magic_missile 9/39, heal 2/7) occasionally passed.
   - A second AM2 handler cancels `EntityJoinWorldEvent` for any EB `ISummonedCreature`
     whose caster is at AM2's `getMaxSummons()` cap — it calls `entity.setDead()` AND
     `event.setCanceled(true)`, silently deleting our summons (fer cow, yelloweyes...).
2. Our cast pipelines already LOG these vetoes (commit `9747753`):
   `EntityAISimWizardCombat.fireCommittedCast` and `EntityAISentinelCombat.tickCasting`
   print "CANCELLED by another mod"/"VETOED" with the spell id, gated by
   `client.enableSimWizardDebugLogs` / `enableSentinelDebugLogs`.
3. A startup warning + login recommendation for AM2 presence exists (commit `ef1177e`).
4. **Known LEGITIMATE `SpellCastEvent.Pre` vetoers in the user's pack** (full jar bytecode
   scan, 2026-07-17 — the fix must NOT break these):
   | Vetoer | Condition | How to re-check it ourselves |
   |---|---|---|
   | EBW `WizardryEventHandler.onSpellCastPreEvent` | `!spell.isEnabled(SpellProperties.Context.NPCS)` | call `spell.isEnabled(Context.NPCS)` directly |
   | ASC `ASEventHandler` (Charm of Spell Suppression) | spell tier ordinal > 1 AND any player with active `ASItems.charm_suppression_orb` within distSq < 20 of the caster | replicate: scan `world.playerEntities`, `ItemArtefact.isArtefactActive(p, ASItems.charm_suppression_orb)`, `caster.getDistanceSq(p) < 20` — guard the whole check with `Loader.isModLoaded("ancientspellcraft")` and put ASC class refs in a separate helper method so classloading stays safe |
   | ASC `IClassSpell.onSpellCastPreEvent` | `spell instanceof com.windanesz.ancientspellcraft.spell.IClassSpell` cast by a non-class NPC | `spell instanceof IClassSpell` (same classload guard) |
   | ASC `ASPotions` | caster has `magical_exhaustion` amplifier >= 2 | check the potion on the caster (classload guard) |
   | AM2 `EBWizardryCompatHandler` | caster has AM2 `silence`; else burnout/mana gate | silence: `caster.isPotionActive(...)` via registry lookup `ForgeRegistries.POTIONS.getValue(new ResourceLocation("arsmagica2","silence"))` (verify the exact registry name from the AM2 jar's assets/lang before trusting it); the burnout gate is the FALSE POSITIVE we bypass |
   | ArcaneApprentices | only for its own `EntityWizardInitiate` casters | n/a (never our entities) |
   | insanetweaks `SpellRestrictionEventHandler` | our own rules | runs in-process anyway; see note in Task 1 step 3 |
5. Decompiled sources live in `notes/decompiled_mods/` (ebwizardry_source, srp_sourcecode,
   ancientspellcraft_source, srpextra_sourcecode, and more). Some are `.class`-only — use
   `javap -p -c <file> | tr -d '\000'` like previous sessions did.
6. User's live instance (deploy target):
   `C:\Users\spege\AppData\Roaming\.sklauncher\instances\bright-moles-grin\` — after a
   successful build copy `build/libs/insanetweaks-1.0.26.jar` into its `mods/` folder
   (overwrite; a `.bak-2026-07-16` of the pre-rework jar already sits there).

---

## Task 1: AM2 veto workaround (config-gated whitelist re-check)

**Decision (made by the controller — implement as specified):** we cannot attribute a Pre
cancel to a specific mod at runtime, so instead of honoring the cancel blindly, our two NPC
cast pipelines get a **second-opinion check**: when Pre comes back cancelled AND the
workaround is enabled, we re-evaluate the KNOWN legitimate veto conditions ourselves
(table above). If any legit condition holds → honor the cancel as today. If none holds →
the cancel is attributed to AM2's burnout false-positive (or an equivalent blanket gate)
and the cast **proceeds anyway**. Summon deletion gets un-done by a LOWEST-priority
`EntityJoinWorldEvent` listener.

Rejected alternatives (do not implement): reflection-unregistering AM2's handler (kills
AM2's player-side EBW mana integration, too blunt); preventing AM2 capability attachment
(fragile, Forge 1.12 AttachCapabilitiesEvent gives no reliable removal path).

### Step 1 — config

`src/main/java/com/spege/insanetweaks/config/categories/InteractionsCategory.java`, append:

```java
    @Config.Comment({
            "Second-opinion check for NPC spell casts vetoed via SpellCastEvent.Pre.",
            "Some mods (notably Ars Magica 2's EB Wizardry compat) blanket-cancel NPC casts",
            "for reasons that cannot apply to this mod's casters (AM2 burnout/mana). When ON,",
            "a vetoed cast by the Sim Wizard or Sentinel is re-checked against the KNOWN",
            "legitimate veto conditions (EB per-spell NPC disable, ASC suppression charm,",
            "ASC class spells, exhaustion/silence debuffs); if none applies, the cast proceeds.",
            "AUTO default: enabled only when Ars Magica 2 is installed." })
    @Config.Name("NPC Cast Veto Second Opinion")
    public NpcVetoSecondOpinion npcCastVetoSecondOpinion = NpcVetoSecondOpinion.AUTO;

    public enum NpcVetoSecondOpinion { AUTO, ON, OFF }
```

### Step 2 — the veto arbiter (new util)

Create `src/main/java/com/spege/insanetweaks/util/NpcCastVetoArbiter.java`:

- `public static boolean shouldOverrideVeto(EntityLiving caster, Spell spell)` — returns
  true when the cancelled cast should proceed anyway. Logic:
  1. resolve the config tri-state: OFF → false; AUTO → only continue when
     `Loader.isModLoaded("arsmagica2")`; ON → continue always;
  2. return false (honor the veto) if ANY legit condition from the facts-table matches —
     implement each as its own small private method; every method touching ASC/AM2 classes
     must be behind `Loader.isModLoaded(...)` and kept in separate helper methods (the
     established classloading-safety pattern; see `SrpInfestationHelper` for reference);
  3. otherwise log (INFO, throttle not needed — vetoes are already rate-limited by cast
     cooldowns): `"[InsaneTweaks] Overriding NPC cast veto for {} by {} — no known legitimate veto condition matched"`
     and return true.
- Keep it dependency-light: EBW check via `spell.isEnabled(SpellProperties.Context.NPCS)`;
  ASC checks via reflection-free direct references inside `Loader.isModLoaded`-guarded
  helper methods (ASC is a compile-time dependency already — see `EntitySentinel` imports).
  AM2 is NOT a compile-time dependency: its silence-potion check must use the Forge potion
  registry by ResourceLocation, never AM2 classes.

### Step 3 — wire into both cast pipelines

`EntityAISimWizardCombat.fireCommittedCast` (the `if (MinecraftForge.EVENT_BUS.post(...))`
block) and `EntityAISentinelCombat.tickCasting` (same pattern): when the event returns
cancelled, call the arbiter; if it says override, fall through to `spell.cast(...)` instead
of returning. Keep the existing veto log line in the honored-veto branch and change its
wording to include whether the second opinion upheld or overrode it.

IMPORTANT ordering note: our own `SpellRestrictionEventHandler` can only cancel
insanetweaks-spells cast by vanilla EBW wizards — never by our entities — so it can't
collide with the override. Do NOT add it to the arbiter's whitelist.

### Step 4 — summon un-deletion

New handler `src/main/java/com/spege/insanetweaks/events/SummonVetoGuardHandler.java`,
registered in `InsaneTweaksMod.init` right after the AM2 warning block, gated by the same
resolved tri-state (extract the resolution into `NpcCastVetoArbiter.isActive()`):

- `@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)` on
  `EntityJoinWorldEvent`;
- conditions: event is cancelled; entity `instanceof ISummonedCreature`; its
  `getCaster()` is `EntitySimWizard` or `EntitySentinel`;
- action: `event.getEntity().isDead = false; event.setCanceled(false);` + debug-gated log.
  (`isDead` is a public field in 1.12 — this legally revives the just-`setDead()` entity
  before it ever ticked.)

### Step 5 — verify & deploy

1. `./gradlew build` green; commit (`feat: second-opinion override for vetoed NPC casts (AM2 workaround)`).
2. Deploy the jar to the user instance (path in facts #6).
3. Tell the user (in your final report) to test with AM2 REINSTALLED: sim wizard and
   sentinel should now cast normally despite AM2; the suppression-orb charm must still
   silence ADVANCED+ spells when worn near the sentinel; summons must persist.
4. Update `notes/sim_wizard_v1.md` (append a short v4.2 section) and the memory file
   `am2-blocks-npc-ebw-casts.md` (the "no code-side workaround" sentence is now outdated —
   rewrite it to describe the second-opinion arbiter).

---

## Task 2: targeted base-mod analysis → `notes/mod_analysis_2026-07.md`

Produce ONE markdown file with the following sections. This is an extraction job: read the
decompiled sources (and `javap` the `.class`-only ones), quote exact class/method names and
the actual conditions — no speculation, no paraphrase without a code citation.

1. **Version audit.** For each of: EB Wizardry, SRParasites, Ancient Spellcraft, SRPextra —
   compare the version in `libs/` (and `build.gradle` for EBW's CurseMaven pin) against the
   decompiled copy in `notes/decompiled_mods/` and against the user pack
   (`C:\Users\spege\AppData\Roaming\.sklauncher\instances\bright-moles-grin\mods\`).
   Flag every mismatch (we already know ASC's decompile is older than the pack's 1.8.2 —
   quantify it). Output: a table (mod / libs ver / decompile ver / pack ver / mismatch?).
2. **SRP internals we depend on.** Document with code citations: the parasite status/points
   system (`getParasiteStatus` semantics — enumerate every value the SRP code assigns and
   where), `EntityAINearestAttackableTargetStatus` gates (already known: status!=0 bail +
   1-in-10 chance — cite lines), COTH/`srpcothimmunity` lifecycle, `canAttackClass` string
   check (our `MixinEntityParasiteBase` targets it — confirm the SRG name still matches),
   and `SRPSaveData.getEvolutionPhase` usage.
3. **EBW NPC casting contract.** Which `Spell` subclasses override the
   `cast(World, EntityLiving, ...)` overload (list them), what `SpellProperties.Context.NPCS`
   controls and where it is loaded from (jar assets / config folder / per-world data —
   cite `SpellProperties.init` and `loadWorldSpecificSpellProperties`), and the artefact
   `enabled` flag semantics (`ItemArtefact.isArtefactActive`).
4. **ASC surface we still touch.** After the sentinel rework we still use:
   `EntityAIBlockWithShield`, `IShieldUser`, `ICustomCooldown` (see `EntitySentinel`
   imports). Document their contracts in ASC **1.8.2 from the pack jar** (javap if needed,
   the old decompile may lie), plus the two ASC veto paths from the facts-table with exact
   conditions (needed to keep the Task 1 arbiter honest if ASC updates).
5. **Cross-mod `SpellCastEvent.Pre` vetoer registry.** Reproduce the facts-table above as a
   maintained reference, adding any vetoer you find in the decompiled sources that the
   original pack-jar scan could have missed (grep all of `notes/decompiled_mods` for
   `SpellCastEvent`).
6. **Upstream check (optional, only if network access available).** Note the newest
   released versions of EBW / SRP / ASC and whether changelogs mention NPC casting,
   spell properties, or parasite status changes.

Commit the file (`docs: targeted base-mod analysis 2026-07`). If a finding contradicts
anything in this handoff or in `notes/agent_notes.md`, flag it prominently at the top of
the analysis file instead of silently correcting either document.

---

## Session context for the executor (read-only background)

- This session shipped: Zhonya default-off; thrall STAY anchor; Porter=sort-only; HUSBANDRY
  mode + 2026-07-16 work-site rework (HOME=depot, 3 remembered sites, periodic cull);
  Cursed Ring parity; infernal dust drops; sim wizard v4/v4.1 (single combat task + own
  targeting); sentinel combat task + container GUI + stance/priorities/utility. All specs in
  `docs/superpowers/specs/2026-07-1*.md`, commits `f60d9a7..ef1177e`.
- Open user-side verification: none blocking — user confirmed thrall, sim wizard and
  sentinel all behave correctly without AM2.
- Debug flags are ON in the user instance config; leave them ON until the AM2 fix is
  verified, then suggest the user turn them off.
