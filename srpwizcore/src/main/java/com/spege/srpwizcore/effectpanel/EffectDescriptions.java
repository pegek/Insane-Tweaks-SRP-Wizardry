package com.spege.srpwizcore.effectpanel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The client half of the effect descriptions: reads {@code config/srpwizcore/effect_descriptions.lang},
 * re-reads it on every resource reload (F3+T, language change), and answers "what describes this
 * effect?" through {@link DescriptionChain}, caching the answer until the next reload.
 *
 * <p>Registered as a reload listener from {@link EffectPanelClient#init()}; registering one makes the
 * resource manager call it straight away, which is also the first read.
 *
 * <p>The file is hand-edited, so nothing here may stop the panel: a missing file is recreated with
 * its header, an unreadable one leaves an empty map behind and one WARN, and a malformed line costs
 * that line only. Bytes are decoded leniently - a file saved by Notepad in the system code page
 * loses an accented letter, not the whole file.
 *
 * <p>Every reload re-reads the file, whatever resource types it covers: F3+T reloads them all, and
 * the file is too small for the selective variant's filtering to be worth anything.
 *
 * <p>Client-only ({@code ISelectiveResourceReloadListener}, {@code I18n}); only ever loaded from
 * {@code EffectPanelClient}, which {@code SrpWizCore.postInit} reaches behind a side check.
 */
@SideOnly(Side.CLIENT)
public final class EffectDescriptions implements ISelectiveResourceReloadListener {

    public static final EffectDescriptions INSTANCE = new EffectDescriptions();

    private static final DescriptionFile.Parsed EMPTY =
            DescriptionFile.parse(Collections.<String>emptyList());

    /** Swapped whole on reload, so a reader never sees a half-parsed file. */
    private static volatile DescriptionFile.Parsed current = EMPTY;

    /** One answer per potion and JEE start level 0..4. Render thread only. */
    private static final Map<Potion, DescriptionChain.Result[]> CACHE = new HashMap<>();

    private static final DescriptionChain.LangView LANG = new DescriptionChain.LangView() {
        @Override
        public boolean has(String key) {
            return I18n.hasKey(key);
        }

        @Override
        public String format(String key) {
            return I18n.format(key);
        }
    };

    private EffectDescriptions() {
    }

    /** {@code <config dir>/srpwizcore/effect_descriptions.lang}. */
    public static File file() {
        return new File(new File(Loader.instance().getConfigDir(), "srpwizcore"), "effect_descriptions.lang");
    }

    /** The file as last read; never null, empty when the file could not be read. */
    public static DescriptionFile.Parsed current() {
        return current;
    }

    public static DescriptionChain.Result describe(Potion potion, int amplifier) {
        int level = Math.max(0, Math.min(4, amplifier));
        DescriptionChain.Result[] row = CACHE.get(potion);
        if (row == null) {
            row = new DescriptionChain.Result[5];
            CACHE.put(potion, row);
        }
        if (row[level] == null) {
            ResourceLocation name = potion.getRegistryName();
            row[level] = DescriptionChain.resolve(name == null ? "" : name.toString(), potion.getName(),
                    level, current.entries, LANG);
        }
        return row[level];
    }

    /** Re-reads the file now, without a full resource reload - used after the dump writes to it. */
    public static void reload() {
        INSTANCE.load();
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager, Predicate<IResourceType> resourcePredicate) {
        load();
    }

    private void load() {
        File f = file();
        try {
            if (!f.exists()) {
                File dir = f.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("could not create " + dir);
                }
                Files.write(f.toPath(), DescriptionFile.header(), StandardCharsets.UTF_8);
            }
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            DescriptionFile.Parsed parsed = DescriptionFile.parse(Arrays.asList(text.split("\r?\n")));
            for (Integer line : parsed.malformedLines) {
                SrpWizCore.LOGGER.warn("[srpwizcore] effect_descriptions.lang line {} ignored: no '=' or empty key", line);
            }
            current = parsed;
            SrpWizCore.LOGGER.info("[srpwizcore] Effect panel: {} description(s) loaded from {}",
                    parsed.entries.size(), f);
        } catch (IOException | RuntimeException e) {
            current = EMPTY;
            SrpWizCore.LOGGER.warn("[srpwizcore] Could not read {} ({}) - the effect panel carries on without it",
                    f, e.toString());
        }
        CACHE.clear();
    }
}
