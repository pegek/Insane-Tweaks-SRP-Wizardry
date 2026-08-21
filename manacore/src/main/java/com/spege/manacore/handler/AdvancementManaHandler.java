package com.spege.manacore.handler;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.BonusTable;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

/**
 * Grants permanent maximum mana for completed advancements.
 *
 * <p>🚨 <b>The bonus is RECALCULATED from the player's advancement state, never accumulated.</b>
 * {@code ManaAPI.addGrantedMax} looks like the natural home for this - its own javadoc even names
 * achievements - and using it would be wrong. {@code grantedMax} is a single shared number for
 * one-way awards, and advancement state is neither: it can be revoked, and the config value
 * attached to it can change. Banking the reward into that shared number would mean
 * {@code /advancement revoke} leaves the mana granted forever (nothing records which part of the
 * sum came from which advancement), a lowered config value never takes effect, and re-earning a
 * revoked advancement doubles the bonus.
 *
 * <p>Recalculating instead gives all three behaviours for free, and needs no new persistent state
 * of its own: the source of truth is already on disk, in the player's advancement file.
 *
 * <p>Recalculated on four events. The three that are not {@code AdvancementEvent} look redundant
 * and are not: {@code MAX_MANA} modifiers do NOT survive death (vanilla builds a fresh player
 * entity on respawn and copies no attribute map onto it), so without the respawn hook the whole
 * bonus would vanish on the first death - the same trap that made {@code /mana setmax} reset
 * before 2026-08-19. Login is additionally where a changed config first takes effect. All four
 * are rare events; nothing here runs per tick.
 */
@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class AdvancementManaHandler {

    /**
     * Identifies the advancement bonus modifier. Like every attribute modifier id in this mod it
     * must stay constant: vanilla serialises modifiers into the player's NBT, so changing it
     * would orphan the old one in existing worlds instead of replacing it.
     */
    private static final UUID ADVANCEMENT_MODIFIER_ID =
            UUID.fromString("3d8e5c17-9b42-4f60-a7d3-6e1c0b95f284");
    private static final String ADVANCEMENT_MODIFIER_NAME = "manacore.advancements";

    private AdvancementManaHandler() {
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent event) {
        Advancement advancement = event.getAdvancement();
        recalculate(event.getEntityPlayer(),
                advancement == null ? null : advancement.getId().toString(), false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        // The one recalculation that reports malformed config entries. Doing it on every
        // recalculation would repeat the same warnings on each advancement earned; doing it once
        // per login is bounded and lands at the moment a player would go looking for it. The
        // trade-off is that a config edited mid-session shows its warnings only after a relog.
        recalculate(event.player, null, true);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent event) {
        recalculate(event.player, null, false);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerChangedDimensionEvent event) {
        recalculate(event.player, null, false);
    }

    /**
     * Recomputes the bonus from scratch and installs it as a single modifier.
     *
     * @param earnedId      id of the advancement just completed, or null when this is a plain
     *                      refresh. Only used to decide whether to announce - the sum itself is
     *                      always rebuilt from the player's full advancement state, never from
     *                      this one entry.
     * @param logRejections whether to warn about malformed config entries
     */
    private static void recalculate(@Nullable EntityPlayer player, @Nullable String earnedId,
            boolean logRejections) {
        if (!(player instanceof EntityPlayerMP) || player.world.isRemote) {
            return;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }

        BonusTable.Table table = BonusTable.parse(ManaCoreConfig.advancements.bonuses);
        if (logRejections) {
            for (String rejection : table.rejected()) {
                ManaCoreMod.LOGGER.warn("[ManaCore] Ignoring malformed advancement bonus entry: {}", rejection);
            }
        }

        // Read before and after rather than tracking per-player state: the difference is what the
        // player actually gained, which is not the table value whenever the cap trims the total.
        double before = ManaAttributes.getPersistentMaxMana(serverPlayer);

        double total = ManaCoreConfig.advancements.enabled ? sumCompleted(server, serverPlayer, table) : 0.0D;
        ManaAttributes.applyMaxModifier(serverPlayer, ADVANCEMENT_MODIFIER_ID, ADVANCEMENT_MODIFIER_NAME,
                BonusTable.clampTotal(total, ManaCoreConfig.advancements.cap), 0);

        // Announce only for an advancement this mod actually pays for, or every vanilla
        // advancement earned would be a candidate for a message.
        double gained = ManaAttributes.getPersistentMaxMana(serverPlayer) - before;
        if (earnedId != null && table.bonuses().containsKey(earnedId)
                && gained > 0.0D && ManaCoreConfig.advancements.announce) {
            serverPlayer.sendStatusMessage(new TextComponentTranslation(
                    "manacore.message.advancement_bonus", Integer.valueOf((int) Math.round(gained))), true);
        }
    }

    /** Sums the table entries for advancements this player has completed. */
    private static double sumCompleted(MinecraftServer server, EntityPlayerMP player,
            BonusTable.Table table) {
        double total = 0.0D;
        for (Map.Entry<String, Double> entry : table.bonuses().entrySet()) {
            Advancement advancement =
                    server.getAdvancementManager().getAdvancement(new ResourceLocation(entry.getKey()));
            if (advancement == null) {
                // DEBUG, not WARN: a pack may legitimately keep entries for optional mods, and a
                // warning per missing id on every login would be noise. A MALFORMED entry is
                // still a warning - that distinction is deliberate.
                ManaCoreMod.LOGGER.debug("[ManaCore] Advancement bonus entry {} matches no known advancement.",
                        entry.getKey());
                continue;
            }
            AdvancementProgress progress = player.getAdvancements().getProgress(advancement);
            if (progress != null && progress.isDone()) {
                total += entry.getValue().doubleValue();
            }
        }
        return total;
    }

}
