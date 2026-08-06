package com.spege.insanetweaks.items.nunchaku;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Living i Sentient Nunchaku, trzymane POZA {@code com.spege.insanetweaks.init.ModItems}.
 *
 * <p>Powód: pola w ModItems są static final i inicjalizują się przy pierwszym dotknięciu klasy.
 * Ta broń dziedziczy po klasie Better Survival, więc brak tego moda wywaliłby inicjalizację
 * ModItems NoClassDefFoundError i zabrał ze sobą cały mod. Tutaj klasa jest dotykana dopiero pod
 * strażą {@link #available()}.
 */
public final class ParasiteNunchakuItems {

    /**
     * 🚨 Typ MUSI zostać {@code Item[]}. Zawężenie do {@code ItemParasiteNunchaku[]} wstawia do
     * {@code <clinit>} instrukcję {@code anewarray} na klasie zależnej od Better Survival — a to
     * ładuje ją zachłannie i wysadza samo {@link #available()} NoClassDefFoundError, zanim zdąży
     * zwrócić false.
     */
    private static Item[] items = new Item[0];

    /** Osobno, bo ewolucja musi umieć wskazać konkretnie ten item. Też jako {@code Item}. */
    private static Item sentient = null;

    private ParasiteNunchakuItems() {
    }

    /**
     * Czy zależność jest obecna? Sprawdź TO, zanim dotkniesz czegokolwiek innego w tej klasie.
     *
     * <p>SRParasites NIE jest tu sprawdzane — jest {@code required-after} w {@code @Mod}, więc bez
     * niego nie wstaje cały mod.
     */
    public static boolean available() {
        return Loader.isModLoaded("mujmajnkraftsbettersurvival");
    }

    /** Master-switch ORAZ obecność moda. Jedyna brama rejestracji. */
    public static boolean shouldRegister() {
        return com.spege.insanetweaks.config.ModConfig.modules.enableLivingNunchaku && available();
    }

    /** Jednorazowo, bo {@link #shouldRegister()} jest pytane i przy itemach, i przy modelach. */
    private static boolean verdictLogged = false;

    /**
     * Mówi w logu, jak rozstrzygnęła się brama — <b>także wtedy, gdy odmówiła</b>.
     *
     * <p>🚨 To nie jest ozdobnik. Na cudzym serwerze bez Better Survival (2026-08-06) poleciało
     * {@code ClassNotFoundException: ItemParasiteNunchaku} spowodowane
     * {@code NoClassDefFoundError: ItemNunchaku}, a log nie zawierał ANI JEDNEJ linii o tej linii
     * broni — bo {@link #register} loguje wyłącznie sukces. Nie dało się więc odróżnić dwóch
     * zupełnie różnych diagnoz: „brama zadziałała, klasę załadowało coś innego" od „brama
     * przeciekła". Ramki stosu, które by to rozstrzygnęły, przepadły między
     * {@code Exception caught during firing event} a {@code Caused by}.
     *
     * <p>Ta linia rozstrzyga to bez stack trace'u. Nie dotyka {@code ItemParasiteNunchaku} — mówi
     * wyłącznie o flagach, więc jest bezpieczna dokładnie tam, gdzie jest potrzebna.
     */
    public static void logVerdict() {
        if (verdictLogged) {
            return;
        }
        verdictLogged = true;

        if (shouldRegister()) {
            return; // register() zaraz zaloguje sukces sam.
        }
        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] parasite nunchaku: NIE rejestruje (bettersurvival={}, "
                        + "modules.enableLivingNunchaku={}). Klasa ItemParasiteNunchaku nie powinna "
                        + "byc w ogole ladowana - jesli mimo to widzisz na nia NoClassDefFoundError, "
                        + "zaladowalo ja cos poza tym modem.",
                Boolean.valueOf(available()),
                Boolean.valueOf(com.spege.insanetweaks.config.ModConfig.modules.enableLivingNunchaku));
    }

    /**
     * Czy linia jest DOSTĘPNA wg {@code gear.availability} — recepta i zakładka kreatywna.
     *
     * <p>Master-switcha z {@code modules} tu NIE ma i nie ma go dodawać: o rejestracji decyduje
     * wyłącznie {@link #shouldRegister()}. Sklejenie obu flag w AND to był dubel naprawiony
     * w wersji 1.10.2 przy linii dragonsteelowej — nie powtarzaj go tutaj.
     */
    public static boolean enabled() {
        return com.spege.insanetweaks.config.ModConfig.gear.availability.livingNunchaku;
    }

    /** Wołalne ZAWSZE, także bez Better Survival — typ zwracany to {@code Item[]}. */
    public static Item[] items() {
        return items.clone();
    }

    /** Cel ewolucji. {@code null}, dopóki {@link #register} nie pobiegnie. */
    public static Item sentient() {
        return sentient;
    }

    /**
     * Buduje i rejestruje obie bronie.
     *
     * <p>Nazwy rejestrowe wychodzą w domenie Better Survival
     * ({@code mujmajnkraftsbettersurvival:itemparasitelivingnunchaku} i {@code ...sentient...}),
     * bo konstruktor {@code ItemNunchaku} ustawia je na sztywno z nazwy materiału, a Forge rzuca
     * IllegalStateException przy drugiej próbie. Świadoma decyzja z projektu, nie przeoczenie.
     *
     * <p>Zakładki kreatywnej nie ustawiamy — {@code ItemCustomWeapon} kładzie broń w
     * {@code CreativeTabs.COMBAT} razem z resztą broni Better Survival i tak ma zostać.
     */
    public static void register(IForgeRegistry<Item> registry) {
        Item living = new ItemParasiteNunchaku(ParasiteNunchakuMaterials.LIVING, ParasiteTier.LIVING);
        sentient = new ItemParasiteNunchaku(ParasiteNunchakuMaterials.SENTIENT, ParasiteTier.SENTIENT);
        items = new Item[] { living, sentient };
        for (Item item : items) {
            registry.register(item);
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] parasite nunchaku: registered {} weapons",
                Integer.valueOf(items.length));
    }

    /**
     * Modele. Nazwa rejestrowa jest w obcej domenie, więc NIE używamy jej wprost jako ścieżki —
     * pliki graficzne siedzą u nas. Ostatni człon zostaje ten sam, zmienia się domena.
     */
    public static void registerModels() {
        for (Item item : items) {
            ResourceLocation regName = item.getRegistryName();
            if (regName == null) {
                continue;
            }
            // getResourcePath(), NIE getPath() - to drugie pojawia sie dopiero w 1.13.
            String path = regName.getResourcePath();
            ModelResourceLocation base =
                    new ModelResourceLocation(InsaneTweaksMod.MODID + ":" + path, "inventory");
            ModelResourceLocation spinning = new ModelResourceLocation(
                    InsaneTweaksMod.MODID + ":" + path + "spinning", "inventory");
            // registerItemVariants PODMIENIA domyslny wariant wziety z nazwy rejestrowej, dzieki
            // czemu loader nigdy nie szuka modelu w domenie Better Survival.
            ModelLoader.registerItemVariants(item, base, spinning);
            ModelLoader.setCustomModelResourceLocation(item, 0, base);
        }
    }
}
