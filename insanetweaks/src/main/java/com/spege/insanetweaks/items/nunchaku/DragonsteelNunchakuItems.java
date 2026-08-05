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
     * Buduje i rejestruje trzy bronie.
     *
     * <p>Nazwy rejestrowe wychodzą w domenie Better Survival
     * ({@code mujmajnkraftsbettersurvival:itemdragonsteelfirenunchaku} itd.), bo konstruktor
     * {@code ItemNunchaku} ustawia je na sztywno, a Forge rzuca IllegalStateException przy drugiej
     * próbie ustawienia nazwy. To świadoma decyzja z speca, nie przeoczenie — item i tak nie
     * istnieje bez Better Survival.
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
