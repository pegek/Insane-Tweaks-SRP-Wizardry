package com.spege.insanetweaks.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Wczesne mixiny moda + deklaracja jara jako coremodu Forge'a.
 *
 * <p>DLACZEGO TO JEST TAKZE IFMLLoadingPlugin (2026-08-06):
 * Jar deklarowal w manifescie {@code MixinConfigs}, ale nie byl coremodem. Na CZYSTYM Forge,
 * gdzie serwisem mixinow jest MixinBooter (u nas: CleanMix Cleanrooma), konczylo sie to
 * crashem startu serwera - {@code DuplicateModsFoundException}, ten sam plik wykryty dwa razy:
 *
 * <pre>
 *   MixinBooterService.getMixinContainers():
 *       known = CoreModManager.getIgnoredMods() + getReparseableCoremods()
 *       if (!known.contains(jar.getName())) Launch.classLoader.addURL(jar)   // <- tu wpadalismy
 *
 *   Loader.identifyMods():
 *       discoverer.findClasspathMods(...)   // znajduje jar na classpath  (sciezka wzgledna)
 *       discoverer.findModDirMods(...)      // znajduje ten sam jar w mods/ (bezwzgledna)
 *       -> dwa ModContainery o tym samym modid -> wyjatek
 * </pre>
 *
 * <p>Lista pominiec w {@code findClasspathMods} to dokladnie {@code getDefaultLibraries() +
 * getIgnoredMods() + getReparseableCoremods()} - bez sprawdzania zawartosci {@code mods/}.
 * Czyli obie strony pytaja o to samo, a jar nie byl na zadnej z tych list, bo trafiaja tam
 * wylacznie coremody ({@code CoreModManager.discoverCoreMods}).
 *
 * <p>{@code FMLCorePlugin} + {@code FMLCorePluginContainsFMLMod} w manifescie kieruja jar do
 * {@code candidateModFiles} zamiast {@code ignoredModFiles}, wiec {@code @Mod} laduje sie dalej
 * normalnie, a nazwa jara jest na liscie, ktora sprawdzaja oba mechanizmy.
 *
 * <p>Ten sam uklad ma AncientSpellcraft ({@code ASLoadingPlugin}) i dziala.
 *
 * <p>UWAGA: klasa laduje sie PRZED transformacjami - nie wolno tu odwolywac sie do klas
 * Minecrafta ani do reszty moda. Zadnego {@code TweakClass} w manifescie: Forge pomija wtedy
 * coremod ("declares both a TweakClass and FMLCorePlugin").
 */
@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class EarlyMixinBooter implements IEarlyMixinLoader, IFMLLoadingPlugin {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.insanetweaks.early.json");
    }

    // --- IFMLLoadingPlugin: sama deklaracja, zadnych transformacji ---------------

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
        // nic - nie potrzebujemy nazwy pliku ani flagi deobf
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
