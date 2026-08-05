package com.spege.insanetweaks.items.nunchaku;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.registries.IForgeRegistry;

import rldragonsteel.core.ModItems;

/**
 * Trzy dragonsteelowe nunchaku, trzymane POZA {@code com.spege.insanetweaks.init.ModItems}.
 *
 * <p>Powód jest twardy: pola w ModItems są static final i inicjalizują się przy pierwszym
 * dotknięciu klasy. Ta broń dziedziczy po klasie Better Survival i sięga po materiały
 * RLDragonsteel — brak któregokolwiek moda wywaliłby inicjalizację ModItems NoClassDefFoundError
 * i zabrał ze sobą cały mod. Tutaj klasa jest dotykana dopiero pod strażą {@link #available()}.
 */
public final class DragonsteelNunchakuItems {

    /**
     * Nazwy rejestrowe są narzucone przez konstruktor ItemNunchaku — patrz {@link #register}.
     *
     * <p>Typ MUSI zostać {@code Item[]}. Zawężenie go do {@code ItemDragonsteelNunchaku[]} wygląda
     * niewinnie, ale wstawia do {@code <clinit>} instrukcję {@code anewarray} na klasie z
     * opcjonalnego moda — a to ładuje ją zachłannie i wysadza samo {@link #available()}
     * NoClassDefFoundError, zanim zdąży zwrócić false.
     */
    private static Item[] items = new Item[0];

    private DragonsteelNunchakuItems() {
    }

    /** Obie zależności obecne? Sprawdź TO, zanim dotkniesz czegokolwiek innego w tej klasie. */
    public static boolean available() {
        return Loader.isModLoaded("mujmajnkraftsbettersurvival") && Loader.isModLoaded("rldragonsteel");
    }

    /**
     * Zarejestrowane bronie — pusta tablica, dopóki {@link #register} nie pobiegnie.
     *
     * <p>Wołalne ZAWSZE, także bez obu modów: typ zwracany to {@code Item[]}, więc wywołanie nie
     * ładuje żadnej klasy z opcjonalnego moda. Dzięki temu {@code ModItems.applyGearAvailability()},
     * które biegnie bezwarunkowo, może po prostu przekazać wynik dalej.
     */
    public static Item[] items() {
        return items.clone();
    }

    /** Ikona zakładki kreatywnej: nunchaku ogniste. Null, dopóki bronie nie są zbudowane. */
    public static Item icon() {
        return items.length > 0 ? items[0] : null;
    }

    /**
     * Czy linia jest DOSTĘPNA wg configu — dwa przełączniki, oba muszą być włączone.
     *
     * <p>Jedno źródło prawdy dla dwóch decyzji: czy nadać broniom zakładkę kreatywną (tutaj,
     * w {@link #register}) i czy zdjąć im recepturę (w {@code ModItems.applyGearAvailability}).
     * Rozjechanie się tych dwóch miejsc dałoby albo pustą zakładkę w GUI, albo broń w kreatywce
     * bez receptury.
     *
     * <p>To jest pytanie o DOSTĘPNOŚĆ, nigdy o rejestrację — patrz komentarz przy
     * {@code ModItems.applyGearAvailability}. Bezpieczne do wołania bez obu modów: dotyka wyłącznie
     * configu.
     */
    public static boolean enabled() {
        return com.spege.insanetweaks.config.ModConfig.modules.enableDragonsteelNunchaku
                && com.spege.insanetweaks.config.ModConfig.gear.availability.dragonsteelNunchaku;
    }

    /**
     * Buduje i rejestruje trzy bronie.
     *
     * <p>Nazwy rejestrowe wychodzą w domenie Better Survival
     * ({@code mujmajnkraftsbettersurvival:itemdragonsteelfirenunchaku} itd.), bo konstruktor
     * {@code ItemNunchaku} ustawia je na sztywno, a Forge rzuca IllegalStateException przy drugiej
     * próbie ustawienia nazwy. To świadoma decyzja z speca, nie przeoczenie — item i tak nie
     * istnieje bez Better Survival.
     *
     * <p>Zakładkę kreatywną nadajemy TUTAJ, a nie w konstruktorze itemu, bo dopiero tu tablica
     * {@code items} jest wypełniona i zakładka ma z czego wziąć ikonę. Musi się to zdarzyć przed
     * {@code ModItems.applyGearAvailability()} — ono tę zakładkę zeruje dla broni wyłączonych
     * w configu i nie miałoby czego zerować, gdyby kolejność była odwrotna.
     *
     * <p>Przy wyłączonej linii zakładki nie nadajemy w ogóle, zamiast nadać ją i zaraz zdjąć:
     * konstruktor {@code CreativeTabs} dopisuje się do globalnej tablicy zakładek, więc samo
     * dotknięcie {@code ModCreativeTabs} zostawiłoby w GUI PUSTĄ zakładkę. A że master-switch
     * domyślnie stoi na OFF, byłby to stan domyślny paczki.
     */
    public static void register(IForgeRegistry<Item> registry) {
        items = new Item[] {
            new ItemDragonsteelNunchaku(ModItems.dragonsteel_fire_tools),
            new ItemDragonsteelNunchaku(ModItems.dragonsteel_ice_tools),
            new ItemDragonsteelNunchaku(ModItems.dragonsteel_lightning_tools),
        };
        boolean lineEnabled = enabled();
        for (Item item : items) {
            if (lineEnabled) {
                item.setCreativeTab(com.spege.insanetweaks.init.ModCreativeTabs.BETTER_SURVIVAL_WEAPONS);
            }
            registry.register(item);
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] dragonsteel nunchaku: registered {} weapons",
                Integer.valueOf(items.length));
    }

    /**
     * Modele. Nazwa rejestrowa jest w obcej domenie, więc NIE możemy użyć jej wprost jako ścieżki
     * modelu — pliki graficzne mają siedzieć u nas. Ostatni człon nazwy zostaje ten sam
     * (itemdragonsteelfirenunchaku), tylko domena idzie na insanetweaks.
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
            // ModelBakery i tak podąża za sekcją "overrides" (VanillaModelWrapper.getDependencies),
            // a setCustomModelResourceLocation samo woła registerItemVariants dla wariantu
            // bazowego. Zostawiamy jawne wywołanie jako pas i szelki: registerVariantNames
            // PODMIENIA domyślny wariant wzięty z nazwy rejestrowej, dzięki czemu loader nigdy nie
            // szuka modelu w domenie Better Survival i nie sypie "Exception loading model".
            ModelLoader.registerItemVariants(item, base, spinning);
            ModelLoader.setCustomModelResourceLocation(item, 0, base);
        }
    }
}
