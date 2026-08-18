package com.spege.manacore.attr;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaAttributes {

    /**
     * setShouldWatch(true) sprawia, ze Forge sam synchronizuje wartosc atrybutu do klienta.
     * Dzieki temu sami synchronizujemy WYLACZNIE `current` (patrz Task 6).
     */
    public static final IAttribute MAX_MANA = new RangedAttribute(
            (IAttribute) null, "manacore.maxMana", 100.0D, 0.0D, 1.0E7D)
            .setDescription("Max Mana")
            .setShouldWatch(true);

    /** Stale UUID modyfikatora progresji - musi byc stabilne miedzy sesjami. */
    private static final UUID PROGRESSION_MODIFIER_ID =
            UUID.fromString("6b7a1d54-3f6c-4a0e-9a1a-2f9c5b8e7d10");
    private static final String PROGRESSION_MODIFIER_NAME = "manacore.progression";

    private ManaAttributes() {
    }

    @SubscribeEvent
    public static void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (!(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityLivingBase living = (EntityLivingBase) event.getEntity();
        if (living.getAttributeMap().getAttributeInstance(MAX_MANA) == null) {
            living.getAttributeMap().registerAttribute(MAX_MANA);
        }
    }

    public static double getMaxMana(@Nullable EntityPlayer player) {
        if (player == null) {
            return 0.0D;
        }
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        return instance == null ? 0.0D : instance.getAttributeValue();
    }

    /** Przelicza modyfikator progresji na podstawie zapisanej w capability wartosci. */
    public static void refreshProgressionModifier(@Nullable EntityPlayer player) {
        if (player == null) {
            return;
        }
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        IManaPool pool = ManaCapabilities.get(player);
        if (instance == null || pool == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(PROGRESSION_MODIFIER_ID);
        if (existing != null) {
            instance.removeModifier(existing);
        }

        double bonus = pool.getProgressionBonus();
        if (bonus > 0.0D) {
            instance.applyModifier(new AttributeModifier(
                    PROGRESSION_MODIFIER_ID, PROGRESSION_MODIFIER_NAME, bonus, 0));
        }
    }
}
