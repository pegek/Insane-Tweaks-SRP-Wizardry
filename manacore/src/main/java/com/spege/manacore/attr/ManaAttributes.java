package com.spege.manacore.attr;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;

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

    /**
     * Modyfikator progresji jest identyfikowany WYLACZNIE po tym UUID - wanilla serializuje
     * AttributeMap razem z modyfikatorami do NBT gracza, wiec zmiana tej stalej w przyszlej
     * wersji moda nie usunie starego modyfikatora z istniejacych swiatow: zostanie osierocony,
     * dalej doliczy sie do maksimum many, a nowy kod dolozy obok niego drugi modyfikator ze
     * swiezym UUID. Efekt to trwale podwojony bonus progresji, nie do naprawienia bez recznej
     * ingerencji w zapis gracza. Nie zmieniac tej wartosci.
     */
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
        EntityPlayer player = (EntityPlayer) event.getEntity();
        if (player.getAttributeMap().getAttributeInstance(MAX_MANA) == null) {
            player.getAttributeMap().registerAttribute(MAX_MANA);
            player.getEntityAttribute(MAX_MANA)
                    .setBaseValue(com.spege.manacore.config.ManaCoreConfig.pool.baseMaxMana);
        }
    }

    public static double getMaxMana(@Nullable EntityPlayer player) {
        if (player == null) {
            return 0.0D;
        }
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        return instance == null ? 0.0D : instance.getAttributeValue();
    }

    /**
     * Przelicza modyfikator progresji na podstawie zapisanej w capability wartosci.
     * Bezpieczne do wywolania z dowolnej strony - po stronie klienta nic nie robi.
     */
    public static void refreshProgressionModifier(@Nullable EntityPlayer player) {
        if (player == null) {
            return;
        }
        if (player.world.isRemote) {
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
