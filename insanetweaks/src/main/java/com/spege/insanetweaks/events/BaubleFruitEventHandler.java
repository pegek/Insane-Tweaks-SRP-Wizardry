package com.spege.insanetweaks.events;

import com.spege.insanetweaks.init.ModItems;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

import java.util.UUID;

/**
 * Persistent fallback handler for Bauble Fruits running under original
 * Baubles (v1.5.x) which has no slot-expansion API.
 *
 * Because vanilla Forge attribute modifiers are NOT saved automatically,
 * this handler re-applies them every time the player logs in or respawns.
 *
 * A single shared NBT counter "BaubleFruitLegacyBonus" (Int) is incremented
 * once per fruit type consumed in legacy mode. All fruit types contribute
 * +1 Luck each, tracked by a single modifier with fixed UUID.
 *
 * This handler is registered by InsaneTweaksMod.init() ONLY when
 * any version of Baubles is present (isModLoaded("baubles") == true).
 */
public class BaubleFruitEventHandler {

    /**
     * Fixed UUID for the legacy Bauble Fruit luck modifier.
     * All fruit types share this UUID so they combine into a single modifier.
     */
    public static final UUID LEGACY_MODIFIER_UUID =
            UUID.fromString("4a3d9f12-c8e1-4f2b-9a7d-0e3b6c1d8f4a");

    /**
     * Shared NBT counter key — stored in PERSISTED_NBT_TAG.
     * Value = total number of Bauble Fruits consumed in legacy mode.
     */
    public static final String LEGACY_BONUS_TAG = "BaubleFruitLegacyBonus";

    private static final String MODIFIER_NAME = "InsaneTweaks Bauble Fruit (Legacy)";

    // =========================================================================

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // Chat warning when running with original Baubles instead of BaublesEX
        if (!ModItems.isBaublesExPresent()) {
            event.player.sendMessage(new TextComponentString(
                    "\u00a7e[InsaneTweaks] \u00a77Bauble Fruits: \u00a7cOriginal Baubles detected. "
                    + "\u00a7bBaublesEX \u00a77is recommended for full slot expansion. "
                    + "Currently in \u00a7eLegacy Mode \u00a77(\u00a7a+1 Luck\u00a77 per fruit)."));
        }
        applyLegacyBonuses(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Attributes reset on death — reapply from NBT
        if (!event.isEndConquered()) {
            applyLegacyBonuses(event.player);
        }
    }

    /**
     * Reads {@link #LEGACY_BONUS_TAG} from the player's persistent NBT and
     * ensures the luck modifier matches that count.
     * Idempotent — safe to call multiple times.
     */
    @SuppressWarnings("null")
    public static void applyLegacyBonuses(EntityPlayer player) {
        NBTTagCompound persistent = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        int bonusCount = persistent.getInteger(LEGACY_BONUS_TAG);
        if (bonusCount <= 0) return;

        IAttributeInstance luck = player.getEntityAttribute(
                net.minecraft.entity.SharedMonsterAttributes.LUCK);
        if (luck == null) return;

        // Remove stale modifier first to prevent stacking on reload
        AttributeModifier existing = luck.getModifier(LEGACY_MODIFIER_UUID);
        if (existing != null) luck.removeModifier(existing);

        luck.applyModifier(
                new AttributeModifier(LEGACY_MODIFIER_UUID, MODIFIER_NAME,
                        bonusCount * 1.0, 0));
    }

    /**
     * Grants a legacy bonus to the player (+1 Luck via NBT counter increment).
     * Called from {@link com.spege.insanetweaks.items.fruit.BaseBaubleFruitItem}
     * when original Baubles (v<=1.5) is detected.
     *
     * @param player     The server-side EntityPlayerMP.
     * @param persistent The player's persistent NBT compound (non-null, mutable).
     */
    @SuppressWarnings("null")
    public static void grantLegacyBonus(EntityPlayer player, NBTTagCompound persistent) {
        int current = persistent.getInteger(LEGACY_BONUS_TAG);
        persistent.setInteger(LEGACY_BONUS_TAG, current + 1);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);

        // Apply immediately — don't wait for next login
        applyLegacyBonuses(player);

        player.sendMessage(new TextComponentString(
                "\u00a7d[Bauble Fruit] \u00a7eLegacy Baubles detected. "
                + "\u00a77Slot expansion unavailable without \u00a7bBaublesEX\u00a77. "
                + "Granted \u00a7a+1 Luck\u00a77 as fallback. "
                + "\u00a78(Switch to BaublesEX for real slot bonuses.)"));
    }
}
