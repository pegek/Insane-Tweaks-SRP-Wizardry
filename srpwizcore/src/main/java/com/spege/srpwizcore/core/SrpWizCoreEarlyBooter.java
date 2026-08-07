package com.spege.srpwizcore.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

/**
 * Declares this jar as a Forge coremod. That is the whole job — it registers one early mixin
 * config and no transformers at all.
 *
 * <h3>Why a mod with no coremod work has to say it is one (2026-08-07)</h3>
 *
 * <p>The jar declared {@code MixinConfigs} in its manifest but was not a coremod. On <b>plain
 * Forge</b>, where the mixin service is MixinBooter rather than Cleanroom's CleanMix, that
 * combination fails the start outright with {@code DuplicateModsFoundException} — the same file
 * found twice, once on the classpath and once in {@code mods/}:
 *
 * <pre>
 *   MixinBooterService.getMixinContainers():
 *       known = CoreModManager.getIgnoredMods() + getReparseableCoremods()
 *       if (!known.contains(jar.getName())) Launch.classLoader.addURL(jar)   // &lt;- we landed here
 *
 *   Loader.identifyMods():
 *       discoverer.findClasspathMods(...)   // finds the jar on the classpath (relative path)
 *       discoverer.findModDirMods(...)      // finds the same jar in mods/  (absolute path)
 *       -> two ModContainers with one modid -> exception
 * </pre>
 *
 * <p>The skip list {@code findClasspathMods} consults is exactly {@code getDefaultLibraries() +
 * getIgnoredMods() + getReparseableCoremods()}, and {@code CoreModManager.discoverCoreMods} puts
 * <em>only coremods</em> on those last two. So both sides ask the same question, and a jar that
 * declares mixin configs without declaring itself a coremod is on neither list.
 *
 * <p>{@code FMLCorePlugin} + {@code FMLCorePluginContainsFMLMod} in the manifest route the jar into
 * {@code candidateModFiles} instead of {@code ignoredModFiles}, so the {@code @Mod} class still
 * loads normally and the file name is on the list both mechanisms check.
 *
 * <p>Diagnosed and verified in Insane Tweaks 1.12.0 (commit {@code e7718ca}), which had the identical
 * shape; that commit's closing note is what this class settles for srpwizcore. AncientSpellcraft
 * ({@code ASLoadingPlugin}) has run the same arrangement for years.
 *
 * <p>🚨 {@code MixinConfigs} stays in the manifest. {@code mixins.srpwizcore.intcache.json} is
 * registered by that attribute and nowhere else — dropping it would switch the IntCache guard off
 * in silence.
 *
 * <p>🚨 This class loads <b>before</b> transformation. Nothing here may touch a Minecraft class or
 * anything else in this mod. And no {@code TweakClass} beside {@code FMLCorePlugin}: Forge skips a
 * coremod that declares both.
 */
@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class SrpWizCoreEarlyBooter implements IEarlyMixinLoader, IFMLLoadingPlugin {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.srpwizcore.early.json");
    }

    // --- IFMLLoadingPlugin: the declaration only, no transformers ----------------

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // nothing - we need neither the file name nor the deobf flag
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
