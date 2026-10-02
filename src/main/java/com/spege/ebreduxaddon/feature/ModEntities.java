package com.spege.ebreduxaddon.feature;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.entity.PurifyingWaveEntity;
import com.spege.ebreduxaddon.feature.entity.SpineEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EbreduxAddon.MODID);

    /** Zasieg sledzenia i interwal jak pociski Redux (EBEntities.MagicType.PROJECTILE: 64, 10). */
    public static final RegistryObject<EntityType<SpineEntity>> SPINE = ENTITIES.register("spine",
            () -> EntityType.Builder.<SpineEntity>of(SpineEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(10)
                    .build(EbreduxAddon.id("spine").toString()));

    public static final RegistryObject<EntityType<PurifyingWaveEntity>> PURIFYING_WAVE = ENTITIES.register("purifying_wave",
            () -> EntityType.Builder.<PurifyingWaveEntity>of(PurifyingWaveEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).fireImmune().noSummon().clientTrackingRange(64).updateInterval(20)
                    .build(EbreduxAddon.id("purifying_wave").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
