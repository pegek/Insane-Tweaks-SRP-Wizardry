package com.spege.ebreduxaddon.compat.spore;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.ModCreativeTab;
import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Rejestracje zaleznie od Spore. Ladowane wylacznie przez SporeCompat.init. */
public final class SporeContent {

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EbreduxAddon.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EbreduxAddon.MODID);

    public static final RegistryObject<EntityType<InfectedWizardEntity>> INFECTED_WIZARD = ENTITIES.register("infected_wizard",
            () -> EntityType.Builder.<InfectedWizardEntity>of(InfectedWizardEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f).clientTrackingRange(8)
                    .build(EbreduxAddon.id("infected_wizard").toString()));

    /** Wymiary maga x MycomancerEntity.SCALE, zeby hitbox pasowal do powiekszonego modelu. */
    public static final RegistryObject<EntityType<MycomancerEntity>> MYCOMANCER = ENTITIES.register("mycomancer",
            () -> EntityType.Builder.<MycomancerEntity>of(MycomancerEntity::new, MobCategory.MONSTER)
                    .sized(0.6f * MycomancerEntity.SCALE, 1.95f * MycomancerEntity.SCALE).clientTrackingRange(8)
                    .build(EbreduxAddon.id("mycomancer").toString()));

    /** Kolory jajka: zielen grzybni i fiolet magii. */
    public static final RegistryObject<Item> INFECTED_WIZARD_SPAWN_EGG = ITEMS.register("infected_wizard_spawn_egg",
            () -> new ForgeSpawnEggItem(INFECTED_WIZARD, 0x4a5a2a, 0x7a3a9a, new Item.Properties()));

    public static final RegistryObject<Item> MYCOMANCER_SPAWN_EGG = ITEMS.register("mycomancer_spawn_egg",
            () -> new ForgeSpawnEggItem(MYCOMANCER, 0x2e3a1c, 0xb0a070, new Item.Properties()));

    private SporeContent() {
    }

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(SporeContent::attributes);
        modBus.addListener(SporeContent::tabContents);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) ->
                InfectedWizardEntity.resolveEvolutions(Config.INSTANCE.infectedEvolutions.get()));
        EbreduxAddon.LOGGER.info("[EbreduxAddon] Spore present: infected wizard and mycomancer enabled");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(INFECTED_WIZARD.get(), InfectedWizardEntity.createAttributes().build());
        event.put(MYCOMANCER.get(), MycomancerEntity.createAttributes().build());
    }

    private static void tabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModCreativeTab.MAIN.getKey()) {
            event.accept(INFECTED_WIZARD_SPAWN_EGG);
            event.accept(MYCOMANCER_SPAWN_EGG);
        }
    }
}
