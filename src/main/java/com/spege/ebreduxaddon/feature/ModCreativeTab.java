package com.spege.ebreduxaddon.feature;

import com.spege.ebreduxaddon.EbreduxAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Zakladka kreatywna: rozdzki i zbroje. Zaklecia pokazuje Redux w swoich zakladkach ksiag i zwojow. */
public final class ModCreativeTab {

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EbreduxAddon.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ebreduxaddon"))
            .icon(() -> new ItemStack(ModItems.SYMBIOTIC_WAND.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.SYMBIOTIC_WAND.get());
                out.accept(ModItems.SENTIENT_WAND.get());
                out.accept(ModItems.GRAFTED_HELMET.get());
                out.accept(ModItems.GRAFTED_CHESTPLATE.get());
                out.accept(ModItems.GRAFTED_LEGGINGS.get());
                out.accept(ModItems.GRAFTED_BOOTS.get());
                out.accept(ModItems.SENTIENT_HELMET.get());
                out.accept(ModItems.SENTIENT_CHESTPLATE.get());
                out.accept(ModItems.SENTIENT_LEGGINGS.get());
                out.accept(ModItems.SENTIENT_BOOTS.get());
            })
            .build());

    private ModCreativeTab() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
