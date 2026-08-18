package com.spege.manacore.attr;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;
import com.spege.manacore.config.ManaCoreConfig;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaAttributes {

    /**
     * setShouldWatch(true) makes Forge sync the attribute value to the client on its own.
     * Because of that, we only ever sync `current` ourselves (see Task 6).
     */
    public static final IAttribute MAX_MANA = new RangedAttribute(
            (IAttribute) null, "manacore.maxMana", 100.0D, 0.0D, 1.0E7D)
            .setDescription("Max Mana")
            .setShouldWatch(true);

    /**
     * The progression modifier is identified SOLELY by this UUID - vanilla serializes the
     * AttributeMap together with its modifiers into the player's NBT, so changing this constant
     * in a future version of the mod would not remove the old modifier from existing worlds: it
     * would become orphaned, keep contributing to the max mana total, and the new code would add
     * a second modifier next to it with a fresh UUID. The result is a permanently doubled
     * progression bonus, not fixable without manually editing the player's save data. Do not
     * change this value.
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
        }
    }

    /**
     * Vanilla serializes the attribute base into the player's NBT and restores it on load, so
     * the value set in EntityConstructing gets overwritten by the save data from before the
     * config change. Without this handler, a change to `baseMaxMana` would NEVER reach an
     * existing character - only a brand new one. We set the base unconditionally, after loading.
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntity();
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        if (instance != null) {
            instance.setBaseValue(ManaCoreConfig.pool.baseMaxMana);
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
     * Recomputes the progression modifier from the value stored in the capability.
     * Safe to call from either side - does nothing on the client side.
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
