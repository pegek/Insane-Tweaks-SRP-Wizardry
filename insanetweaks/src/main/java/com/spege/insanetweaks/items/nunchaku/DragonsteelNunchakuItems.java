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
     * Czy w ogóle TWORZYMY tę broń — jedyna brama rejestracji.
     *
     * <p>Master-switch {@code modules.enableDragonsteelNunchaku} sprawdzamy PIERWSZY, żeby przy
     * wyłączonym module nie płacić nawet za dwa {@code Loader.isModLoaded}. Bezpieczne bez obu
     * modów: czyta config i rejestr Forge'a, nie dotyka żadnej obcej klasy.
     *
     * <p>To pytanie jest rozłączne z {@link #enabled()} — tam chodzi o DOSTĘPNOŚĆ już istniejącej
     * broni, tutaj o jej istnienie. Nie sklejaj ich z powrotem w jeden warunek.
     */
    public static boolean shouldRegister() {
        return com.spege.insanetweaks.config.ModConfig.modules.enableDragonsteelNunchaku
                && available();
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

    /**
     * Czy broń jest DOSTĘPNA (recepta + zakładka kreatywna) wg {@code gear.availability}.
     *
     * <p>Master-switcha z {@code modules} tu NIE ma i nie ma go dodawać — o rejestracji decyduje
     * wyłącznie {@link #shouldRegister()}. Przy wyłączonym module {@link #items()} zwraca pustą
     * tablicę, więc {@code applyGearAvailability()} nie ma czego ukrywać i odpowiedź tej metody
     * przestaje mieć znaczenie sama z siebie. Sklejenie obu flag z powrotem w AND to był dubel:
     * dwa przełączniki, jeden identyczny skutek.
     *
     * <p>Bezpieczne do wołania bez obu modów: dotyka wyłącznie configu.
     */
    public static boolean enabled() {
        return com.spege.insanetweaks.config.ModConfig.gear.availability.dragonsteelNunchaku;
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
     * <p>Zakładki kreatywnej NIE ustawiamy — konstruktor {@code ItemCustomWeapon} kładzie broń
     * w {@code CreativeTabs.COMBAT}, tam gdzie wszystkie pozostałe bronie Better Survival, i tak
     * ma zostać. Jedyne, co tę zakładkę rusza, to {@code ModItems.applyGearAvailability()}, które
     * zeruje ją broniom wyłączonym w configu.
     */
    public static void register(IForgeRegistry<Item> registry) {
        items = new Item[] {
            new ItemDragonsteelNunchaku(ModItems.dragonsteel_fire_tools),
            new ItemDragonsteelNunchaku(ModItems.dragonsteel_ice_tools),
            new ItemDragonsteelNunchaku(ModItems.dragonsteel_lightning_tools),
        };
        for (Item item : items) {
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
