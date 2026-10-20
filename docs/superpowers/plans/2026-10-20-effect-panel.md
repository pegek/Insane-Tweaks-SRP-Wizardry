# Effect Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hovering an effect tile in any inventory screen replaces it, in place, with the effect's description (or a hint saying where to write one), and Just Enough Effects' broken popup is switched off.

**Architecture:** Three Minecraft-free classes (descriptions-file parser, lookup chain, tile geometry) carry all the decisions and are unit-tested on a plain JVM. A client-only layer wraps them: a resource-reload listener that reads `config/srpwizcore/effect_descriptions.lang`, a `DrawScreenEvent.Post` renderer, a JEE unregister call, and a client command. All of it lives in srpwizcore, package `com.spege.srpwizcore.effectpanel`; zero mixins.

**Tech Stack:** Java 8, Forge 1.12.2-14.23.5.2860 (ForgeGradle 3, MCP `snapshot_20171003`), JUnit 4.12 for the pure classes.

**Spec:** `docs/superpowers/specs/2026-10-20-effect-panel-design.md` (commit `74d556f`). Read it before any task; section numbers below (§N) refer to it.

## Global Constraints

- Owner mod **srpwizcore** only. No change to any other mod except the content version bump in Task 7.
- Client-only feature. Nothing reachable from a dedicated server may load a client class (CLAUDE.md "Side safety"). Every event handler class is `@Mod.EventBusSubscriber(modid = SrpWizCore.MODID, value = Side.CLIENT)`, like `dormant/DormantEyeTooltipHandler`.
- No compile dependency on JEE, EBW or HEI: name their classes only as strings.
- Java 8 source; no lambdas needed but allowed outside mixins (there are none here); `-Xlint:all` stays clean of new warnings.
- Descriptions file: `config/srpwizcore/effect_descriptions.lang`, lines `<registry name>=<text>`, `#` comments, UTF-8, text used verbatim (no `String.format`), empty text = missing (§4a).
- Lookup order exactly: file → JEE `<name>.<N>.desc` (N = amplifier..4) → `description.<name>` → `bestiary.effect.<path>.desc` only for namespace `srparasites` (§4).
- Panel: tile width 140, same left/top edge as the tile, font scale from config (default 0.6), text colour `0x404040`, duration line last in `0x7F7F7F` and only when `potion.shouldRenderInvText(effect)` (§2, §10).
- Config category `effectPanel` must be a category object on the `category = ""` root (§8).
- All player-facing text English; panel strings in srpwizcore `en_us.lang` (which uses CRLF line endings — keep them).

## Review Focus

- **Amplifier outside 0–4** (`/effect … 255` stores a byte, so it arrives as -1; mods use 5+): JEE walk must clamp to 0..4, never index past it. Pinned in Task 2.
- **Effects that `shouldRender` = false mixed with visible ones**: vanilla spaces tiles by the *total* effect count but positions them by *drawn* index; a mismatch puts the hover on the wrong tile. Pinned in Task 3.
- **A hand-edited descriptions file**: CRLF, a UTF-8 BOM from Notepad, `=` inside the text, the same key twice after a paste. Must parse, last occurrence wins, never throw. Pinned in Task 1.
- **Panel near the bottom of the screen** (many effects, long text, small window): must move up to stay visible, never above 0. Pinned in Task 3.
- **Descriptions file missing, deleted or unreadable between reloads**: panel keeps working on an empty map, one WARN, no crash. Manual check in Task 4.

---

### Task 1: Test setup + descriptions-file parser

**Files:**
- Modify: `srpwizcore/build.gradle` (repositories, dependencies, `test` block)
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/DescriptionFile.java`
- Test: `srpwizcore/src/test/java/com/spege/srpwizcore/effectpanel/DescriptionFileTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class DescriptionFile {
      public static final class Parsed {
          public final Map<String, String> entries;   // registry name -> text; non-empty texts only; LinkedHashMap
          public final Set<String> presentKeys;        // every key that has a line, empty text included
          public final List<Integer> malformedLines;   // 1-based line numbers
      }
      public static final class Missing {
          public Missing(String key, String displayName, String modName);
          public final String key, displayName, modName;
      }
      public static Parsed parse(List<String> lines);
      public static List<String> header();
      public static List<String> linesToAppend(Set<String> presentKeys, List<Missing> missing, String sectionLabel);
  }
  ```

- [ ] **Step 1: Add JUnit to srpwizcore**, copying content's wiring: `mavenCentral()` in `repositories` (with content's one-line comment why), `testImplementation 'junit:junit:4.12'` in `dependencies`, and `test { testLogging { events 'passed', 'skipped', 'failed' } }`.

- [ ] **Step 2: Write the failing tests** in `DescriptionFileTest`:

```java
@Test public void parsesEntriesCommentsAndBlanks() {
    DescriptionFile.Parsed p = DescriptionFile.parse(Arrays.asList(
        "# comment", "", "minecraft:speed=Walk faster.", "srparasites:bleed=",
        "broken line", "=no key", "a:b=x=y", " minecraft:speed = Faster still. "));
    assertEquals("Faster still.", p.entries.get("minecraft:speed"));   // last occurrence wins, trimmed
    assertEquals("x=y", p.entries.get("a:b"));                         // split on the FIRST '='
    assertFalse(p.entries.containsKey("srparasites:bleed"));           // empty text = missing
    assertTrue(p.presentKeys.containsAll(Arrays.asList("minecraft:speed", "srparasites:bleed", "a:b")));
    assertEquals(Arrays.asList(5, 6), p.malformedLines);
}
@Test public void stripsUtf8Bom() {
    assertEquals("Lucky.", DescriptionFile.parse(Collections.singletonList("\uFEFFminecraft:luck=Lucky.")).entries.get("minecraft:luck"));
}
@Test public void headerIsOnlyComments() {
    for (String line : DescriptionFile.header()) assertTrue(line, line.startsWith("#"));
    assertTrue(DescriptionFile.parse(DescriptionFile.header()).presentKeys.isEmpty());
}
@Test public void appendsOnlyKeysWithNoLineYet() {
    Set<String> present = new HashSet<>(Arrays.asList("minecraft:speed", "srparasites:bleed"));
    List<String> out = DescriptionFile.linesToAppend(present, Arrays.asList(
        new DescriptionFile.Missing("srparasites:bleed", "Bleeding", "Scape and Run: Parasites"),
        new DescriptionFile.Missing("minecraft:luck", "Luck", "Minecraft")), "L");
    assertEquals(Arrays.asList("", "# --- L ---", "# Luck — Minecraft", "minecraft:luck="), out);
}
@Test public void appendsNothingWhenNothingIsNew() {
    assertTrue(DescriptionFile.linesToAppend(Collections.singleton("minecraft:luck"),
        Collections.singletonList(new DescriptionFile.Missing("minecraft:luck", "Luck", "Minecraft")), "L").isEmpty());
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :srpwizcore:test`
Expected: compilation FAIL, `DescriptionFile` not found.

- [ ] **Step 4: Implement `DescriptionFile`.** Malformed = a non-blank, non-`#` line with no `=` or an empty key after trimming. Key and text are trimmed. `header()` returns exactly:

```
# Effect descriptions for the srpwizcore effect panel.
# One line per effect:  <registry name>=<description>   e.g.  minecraft:speed=Makes you walk faster.
# Lines starting with # are comments. A line with nothing after '=' counts as not described yet.
# The text is used exactly as written: a % is a plain %, and § formatting codes work.
# Authoring loop: run /effectdesc missing in game, fill in the blank lines it appends here, press F3+T.
# This file wins over every other source of effect descriptions (JEE, potiondescriptions, SRP bestiary).
```

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew :srpwizcore:test`
Expected: `DescriptionFileTest` 5 PASSED, BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add srpwizcore/build.gradle srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/DescriptionFile.java srpwizcore/src/test/java/com/spege/srpwizcore/effectpanel/DescriptionFileTest.java
git commit -m "feat(srpwizcore): effect descriptions file parser + JUnit setup"
```

---

### Task 2: Description lookup chain

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/DescriptionChain.java`
- Test: `srpwizcore/src/test/java/com/spege/srpwizcore/effectpanel/DescriptionChainTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class DescriptionChain {
      public enum Rule { FILE, JEE, POTION_DESCRIPTIONS, SRP_BESTIARY, NONE }
      public interface LangView { boolean has(String key); String format(String key); }
      public static final class Result { public final Rule rule; public final String text; } // text null for NONE
      public static Result resolve(String registryName, String translationName, int amplifier,
                                   Map<String, String> file, LangView lang);
      public static String fileLine(String registryName);  // registryName + "="
  }
  ```
  `registryName` is `ns:path`; the class splits it itself.

- [ ] **Step 1: Write the failing tests**, using a `LangView` backed by a `Map<String,String>` (`has` = `containsKey`, `format` = `get`):

```java
@Test public void fileWinsOverEverything() {
    Result r = resolve("minecraft:speed", "effect.moveSpeed", 0, map("minecraft:speed", "File text"),
                       lang("effect.moveSpeed.4.desc", "jee", "description.effect.moveSpeed", "pd"));
    assertEquals(Rule.FILE, r.rule); assertEquals("File text", r.text);
}
@Test public void jeeWalksFromAmplifierUpToFour() {
    LangView l = lang("effect.x.1.desc", "one", "effect.x.4.desc", "any");
    assertEquals("one", resolve("m:x", "effect.x", 0, empty(), l).text);
    assertEquals("one", resolve("m:x", "effect.x", 1, empty(), l).text);
    assertEquals("any", resolve("m:x", "effect.x", 2, empty(), l).text);
    assertEquals("any", resolve("m:x", "effect.x", 7, empty(), l).text);   // start clamps to 4
    assertEquals("one", resolve("m:x", "effect.x", -1, empty(), l).text);  // byte amplifier 255 -> -1, start clamps to 0
    assertEquals(Rule.JEE, resolve("m:x", "effect.x", 0, empty(), l).rule);
}
@Test public void jeeBeatsPotionDescriptions() {
    assertEquals(Rule.JEE, resolve("minecraft:speed", "effect.moveSpeed", 0, empty(),
        lang("effect.moveSpeed.4.desc", "jee", "description.effect.moveSpeed", "pd")).rule);
}
@Test public void potionDescriptionsFormat() {
    Result r = resolve("minecraft:speed", "effect.moveSpeed", 0, empty(), lang("description.effect.moveSpeed", "pd"));
    assertEquals(Rule.POTION_DESCRIPTIONS, r.rule); assertEquals("pd", r.text);
}
@Test public void srpBestiaryOnlyForSrparasitesNamespace() {
    LangView l = lang("bestiary.effect.bleed.desc", "B");
    Result r = resolve("srparasites:bleed", "mob_effect.srparasites:bleed", 0, empty(), l);
    assertEquals(Rule.SRP_BESTIARY, r.rule); assertEquals("B", r.text);
    assertEquals(Rule.NONE, resolve("othermod:bleed", "potion.othermod.bleed", 0, empty(), l).rule);
}
@Test public void noneWhenNothingMatches() {
    Result r = resolve("m:x", "effect.x", 0, empty(), lang());
    assertEquals(Rule.NONE, r.rule); assertNull(r.text);
}
@Test public void fileLineIsTheExactLineToAdd() {
    assertEquals("minecraft:speed=", DescriptionChain.fileLine("minecraft:speed"));
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :srpwizcore:test`, compilation FAIL on `DescriptionChain`.

- [ ] **Step 3: Implement `DescriptionChain`.** Rules 2–4 return `lang.format(key)`; rule 1 returns the file text as-is. A `registryName` without `:` is treated as namespace `minecraft`.

- [ ] **Step 4: Run to verify they pass** — `./gradlew :srpwizcore:test`, all `DescriptionChainTest` PASSED.

- [ ] **Step 5: Commit** — `feat(srpwizcore): effect description lookup chain`.

---

### Task 3: Effect tile geometry

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/EffectTileLayout.java`
- Test: `srpwizcore/src/test/java/com/spege/srpwizcore/effectpanel/EffectTileLayoutTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class EffectTileLayout {
      public static final int TILE_W = 140, TILE_H = 32;
      public static int spacing(int totalEffects);                       // 33, or 132 / (n - 1) when n > 5
      public static int tileX(int guiLeft);                              // guiLeft - 124
      public static int tileY(int guiTop, int drawnIndex, int spacing);  // guiTop + drawnIndex * spacing
      public static int hovered(int mouseX, int mouseY, int guiLeft, int guiTop, int drawnCount, int spacing); // -1 = none
      public static int panelTop(int tileY, int panelHeight, int screenHeight);
  }
  ```
  Values are vanilla's, from `InventoryEffectRenderer.drawActivePotionEffects` (Forge 14.23.5.2860). Note the asymmetry the spec's §10 warns about: `spacing` takes the count of **all** active effects; positions take the index among **drawn** ones (`shouldRender` true).

- [ ] **Step 1: Write the failing tests:**

```java
@Test public void spacingMatchesVanilla() {
    assertEquals(33, spacing(1)); assertEquals(33, spacing(5)); assertEquals(26, spacing(6)); assertEquals(22, spacing(7));
}
@Test public void tilePosition() { assertEquals(76, tileX(200)); assertEquals(116, tileY(50, 2, 33)); }
@Test public void hoverHitsTheRightTileAndEdgesAreHalfOpen() {
    assertEquals(0, hovered(80, 50, 200, 50, 3, 33));
    assertEquals(1, hovered(80, 83, 200, 50, 3, 33));
    assertEquals(0, hovered(215, 60, 200, 50, 3, 33));   // x range [76, 216)
    assertEquals(-1, hovered(216, 60, 200, 50, 3, 33));
    assertEquals(-1, hovered(75, 60, 200, 50, 3, 33));
    assertEquals(-1, hovered(80, 49, 200, 50, 3, 33));
    assertEquals(2, hovered(80, 147, 200, 50, 3, 33));   // tile 2 spans [116, 148)
    assertEquals(-1, hovered(80, 148, 200, 50, 3, 33));
}
@Test public void overlappingTilesPickTheLastDrawn() {
    assertEquals(1, hovered(80, 50 + 22 + 5, 200, 50, 7, 22)); // inside both tile 0 and tile 1
}
@Test public void hiddenEffectsShrinkSpacingButNotPositions() {
    int s = spacing(7);                                        // 7 active, only 6 drawn
    assertEquals(5, hovered(80, 50 + 5 * s + 10, 200, 50, 6, s));
    assertEquals(-1, hovered(80, 50 + 5 * s + TILE_H, 200, 50, 6, s));
}
@Test public void panelStaysOnScreen() {
    assertEquals(100, panelTop(100, 50, 300));
    assertEquals(250, panelTop(280, 50, 300));
    assertEquals(0, panelTop(10, 400, 300));
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :srpwizcore:test`, FAIL on `EffectTileLayout`.

- [ ] **Step 3: Implement `EffectTileLayout`.** `hovered` walks from the last drawn tile to the first and returns the first rectangle `[x, x+TILE_W) × [y, y+TILE_H)` containing the mouse.

- [ ] **Step 4: Run to verify they pass** — all three test classes PASSED.

- [ ] **Step 5: Commit** — `feat(srpwizcore): effect tile geometry`.

---

### Task 4: Config category, descriptions loader, panel strings

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/config/categories/EffectPanelCategory.java`
- Modify: `srpwizcore/src/main/java/com/spege/srpwizcore/config/SrpWizCoreConfig.java` (new field after `devSpeed`)
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/EffectDescriptions.java`
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/EffectPanelClient.java`
- Modify: `srpwizcore/src/main/java/com/spege/srpwizcore/SrpWizCore.java` (`postInit`)
- Modify: `srpwizcore/src/main/resources/assets/srpwizcore/lang/en_us.lang`

**Interfaces:**
- Consumes: `DescriptionFile.parse`, `DescriptionFile.header`, `DescriptionChain.resolve`.
- Produces:
  ```java
  public class EffectPanelCategory {                     // @Config.Name("effectPanel") on the root
      public boolean enabled = true;                    // "Enabled", read live
      public double fontScale = 0.6;                    // "Font Scale", @RangeDouble(min = 0.3, max = 1.0), read live
      public boolean showMissingHint = true;            // "Show Missing Hint", read live
      public boolean suppressJeeRenderer = true;        // "Suppress JEE Renderer", @RequiresMcRestart
  }
  @SideOnly(Side.CLIENT)
  public final class EffectDescriptions implements IResourceManagerReloadListener {
      public static final EffectDescriptions INSTANCE;
      public static File file();                                        // <configDir>/srpwizcore/effect_descriptions.lang
      public static DescriptionFile.Parsed current();                   // never null; empty if unreadable
      public static DescriptionChain.Result describe(Potion potion, int amplifier); // cached per potion and clamped start level 0..4
  }
  @SideOnly(Side.CLIENT)
  public final class EffectPanelClient { public static void init(); }  // called once from postInit on the client
  ```
  Lang keys added (exact): `srpwizcore.effectpanel.missing.title=No description yet.`, `srpwizcore.effectpanel.missing.where=Add to config/srpwizcore/effect_descriptions.lang:`.

- [ ] **Step 1: Add `EffectPanelCategory`** with the four fields above, comments in the `DragonRangedCategory` style ending "Read live, no restart needed. Default …" (or, for `suppressJeeRenderer`, "Applied once at game start - restart to change. Default ON."), and the root field `@Config.Name("effectPanel") @Config.Comment("Effect panel: hover an effect in the inventory to read what it does. Client-only.") public static final EffectPanelCategory effectPanel`.

- [ ] **Step 2: Implement `EffectDescriptions`.** `onResourceManagerReload`: create the file with `header()` if absent (create parent dirs), read it as UTF-8 lines, `parse`, log one WARN per malformed line (`[srpwizcore] effect_descriptions.lang line N ignored: no '=' or empty key`), swap the parsed result into a `volatile` field, clear the cache, log one INFO with the entry count. Any `IOException` → one WARN, keep an empty `Parsed`. `describe` builds the `LangView` from `net.minecraft.client.resources.I18n` (`hasKey`, `format`) and calls `DescriptionChain.resolve(potion.getRegistryName().toString(), potion.getName(), amplifier, current().entries, view)`.

- [ ] **Step 3: Implement `EffectPanelClient.init()`** — for now only: `((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(EffectDescriptions.INSTANCE)` (this also triggers the first read). Wire it from `SrpWizCore.postInit`: `if (event.getSide().isClient()) { com.spege.srpwizcore.effectpanel.EffectPanelClient.init(); }` with a one-line comment that the call is side-guarded because the target is client-only.

- [ ] **Step 4: Add the two lang lines** to srpwizcore `en_us.lang` with CRLF endings.

- [ ] **Step 5: Build** — `./gradlew :srpwizcore:build`. Expected: BUILD SUCCESSFUL, tests pass, no new `-Xlint` warnings in the new files.

- [ ] **Step 6: Verify in game** (copy the jar into DEv 1.2 `mods/`, removing the old srpwizcore jar). Expected: `config/srpwizcore/effect_descriptions.lang` exists with exactly the header; `latest.log` has the INFO line with `0` entries; `srpwizcore.cfg` has an `effectpanel` block with the four values. Add `bad line` to the file, F3+T → one WARN naming line 7. Delete the file, F3+T → recreated, no crash.

- [ ] **Step 7: Commit** — `feat(srpwizcore): effect panel config and descriptions loader`.

---

### Task 5: The hover panel + JEE suppression

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/EffectPanelRenderer.java`
- Modify: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/EffectPanelClient.java`

**Interfaces:**
- Consumes: `EffectTileLayout.*`, `EffectDescriptions.describe`, `DescriptionChain.fileLine`, `SrpWizCoreConfig.effectPanel`.
- Produces: `EffectPanelRenderer` — `@Mod.EventBusSubscriber(modid = SrpWizCore.MODID, value = Side.CLIENT)`, `@SubscribeEvent(priority = EventPriority.HIGH) public static void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event)`. Keep the text as a `List<String>` of lines built before drawing (§9).

- [ ] **Step 1: Implement the hover check.** Act only when `effectPanel.enabled`, the gui is an `InventoryEffectRenderer`, and its protected `hasActivePotionEffects` is true — read through `ReflectionHelper.findField(InventoryEffectRenderer.class, "hasActivePotionEffects", "field_147045_u")`, cached. All active effects → `Ordering.natural().sortedCopy(...)` → keep those with `potion.shouldRender(effect)`; `spacing(allActive.size())`; `hovered(...)` with `getGuiLeft()`/`getGuiTop()`.

- [ ] **Step 2: Build the lines.** `Result r = EffectDescriptions.describe(potion, amp)`. If `NONE`: when `showMissingHint`, lines = translated `missing.title`, translated `missing.where`, `DescriptionChain.fileLine(registryName)`; otherwise return (tile stays vanilla). Else wrap `r.text` with `fontRenderer.listFormattedStringToWidth(text, (int) ((TILE_W - 10) / fontScale))`. Append `Potion.getPotionDurationString(effect, 1.0F)` as the last line only when `potion.shouldRenderInvText(effect)`.

- [ ] **Step 3: Draw.** Height = `max(TILE_H, 10 + ceil(lines * 9 * fontScale))`; top = `panelTop(tileY, height, gui.height)`. `GlStateManager.disableDepth()`, `disableLighting()`, `color(1,1,1,1)`; restore depth and lighting afterwards. Normal effects: background from the vanilla tile texture (`new ResourceLocation("textures/gui/container/inventory.png")`, u 0, v 166, 140×32, drawn with `gui.drawTexturedModalRect`): top 4 rows, middle rows 4–27 repeated to fill, bottom 4 rows — so it looks exactly like the tile, only taller. EBW curses (cached `Class.forName("electroblob.wizardry.potion.Curse").isAssignableFrom(potion.getClass())`, `false` if EBW is absent): `Gui.drawRect` fill `0xFF3B1450`, 1-px highlight `0xFF7A4A9A` top/left, shadow `0xFF1E0A29` bottom/right, text `0xFFE8D8F0`. Text: `pushMatrix`, `translate(x + 5, top + 5, 0)`, `scale(fontScale, fontScale, 1)`, `fontRenderer.drawString(line, 0, i * 9, colour)` with `0x404040` (curse colour for curses) and `0x7F7F7F` for the duration line; `popMatrix`.

- [ ] **Step 4: Add JEE suppression to `EffectPanelClient.init()`**: when `effectPanel.suppressJeeRenderer` and `Loader.isModLoaded("effectinfo")`, `MinecraftForge.EVENT_BUS.unregister(Class.forName("com.pinball3d.effectinfo.Render"))`; INFO `[srpwizcore] Just Enough Effects' renderer unregistered - the effect panel replaces it.`; any `Throwable` → one WARN and continue (§6).

- [ ] **Step 5: Build** — `./gradlew :srpwizcore:build`, BUILD SUCCESSFUL.

- [ ] **Step 6: Verify in game** — spec §12 steps 1–7, 9 and 11 (vanilla Speed → JEE text, Nourished → content text, `srparasites:bleed` → bestiary text, undescribed effect → hint with `<ns>:<path>=`, six+ effects, recipe book open hides tiles and the panel, creative inventory, an EBW curse → purple panel without duration, `enabled=false` live, HEI bookmark overlap). Also check `cleanmix.log` is irrelevant (no mixins) and `latest.log` has the JEE INFO line.

- [ ] **Step 7: Commit** — `feat(srpwizcore): in-place effect description panel, replaces JEE's popup`.

---

### Task 6: `/effectdesc missing`

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/CommandEffectDesc.java`
- Modify: `srpwizcore/src/main/java/com/spege/srpwizcore/effectpanel/EffectPanelClient.java`
- Modify: `srpwizcore/src/main/resources/assets/srpwizcore/lang/en_us.lang`

**Interfaces:**
- Consumes: `EffectDescriptions.file/current/describe`, `DescriptionFile.linesToAppend`, `DescriptionFile.Missing`.
- Produces: `@SideOnly(Side.CLIENT) public final class CommandEffectDesc extends CommandBase` — name `effectdesc`, usage `/effectdesc missing`, permission level 0, `checkPermission` true. Registered in `EffectPanelClient.init()` via `ClientCommandHandler.instance.registerCommand(new CommandEffectDesc())`.
  Lang keys (exact): `srpwizcore.effectpanel.command.usage=/effectdesc missing`, `srpwizcore.effectpanel.command.appended=Appended %s blank line(s) to %s`, `srpwizcore.effectpanel.command.coverage=Described %s of %s effects - file %s, JEE %s, potiondescriptions %s, SRP bestiary %s`, `srpwizcore.effectpanel.command.failed=Could not write %s: %s`.

- [ ] **Step 1: Implement `execute`** for argument `missing` (anything else → `WrongUsageException` with the usage key). Walk `ForgeRegistries.POTIONS` sorted by registry name; for each, `describe(potion, 0)`; tally rules; collect `NONE` ones as `Missing(registryName, I18n.format(potion.getName()), modName)` where `modName` is `Loader.instance().getIndexedModList().get(ns).getName()`, `"Minecraft"` for `minecraft`, else the namespace. `linesToAppend(current().presentKeys, missing, "/effectdesc missing, " + yyyy-MM-dd HH:mm)`; append the lines (UTF-8, `System.lineSeparator()`) to `file()`; send `appended` and `coverage` to chat; on `IOException` send `failed`. Then trigger a reload of `EffectDescriptions` so `presentKeys` reflects the new lines without F3+T.

- [ ] **Step 2: Add the four lang lines** (CRLF).

- [ ] **Step 3: Build** — `./gradlew :srpwizcore:build`, BUILD SUCCESSFUL.

- [ ] **Step 4: Verify in game** — spec §12 step 8: run it, blank lines appear under one dated section, counts in chat; run again → `Appended 0`; fill one line, F3+T, hover → new text; a rule-1 line for `minecraft:speed` beats JEE.

- [ ] **Step 5: Commit** — `feat(srpwizcore): /effectdesc missing writes undescribed effects to the descriptions file`.

---

### Task 7: Ship

**Files:**
- Modify: `srpwizcore/build.gradle` (`version`), `srpwizcore/src/main/java/com/spege/srpwizcore/SrpWizCore.java` (`VERSION`)
- Modify: `insanetweaks/build.gradle` (`version`), `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (`VERSION`)
- Modify: `CLAUDE.md` (Build & Run → tests paragraph)

- [ ] **Step 1: Bump versions from whatever is current at this point** (another session works in this repo — re-read them, do not assume): srpwizcore next **minor** (expected 1.19.0 → 1.20.0) in both places; content next **patch** (expected 1.24.0 → 1.24.1) in both places, for the four description keys committed in `74d556f`.

- [ ] **Step 2: Update CLAUDE.md** — the tests paragraph now names three subprojects: add srpwizcore, its pure `effectpanel` classes (`DescriptionFile`, `DescriptionChain`, `EffectTileLayout`) and `./gradlew :srpwizcore:test`; fix "the other six mods" to "the other four". Add one line under srpwizcore's native systems: the effect panel, its config file `config/srpwizcore/effect_descriptions.lang`, and that it unregisters JEE's `Render` subscriber.

- [ ] **Step 3: Build** — `./gradlew :srpwizcore:build :insanetweaks:build`. Expected: BUILD SUCCESSFUL, all srpwizcore tests and content tests PASSED.

- [ ] **Step 4: Install** both jars into `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\`, removing the old jar of each modid; confirm exactly one jar per modid.

- [ ] **Step 5: Dedicated server check** (spec §12 step 10): start a dedicated server with the new srpwizcore (dev `./gradlew runServer` or the pack's server) → no `invalid side`, `NoClassDefFoundError` or `effectpanel` stack trace in its log.

- [ ] **Step 6: Commit** — `chore: srpwizcore 1.20.0, insanetweaks 1.24.1 - effect panel` (with the actual numbers).
