package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Zapisuje rozklad ekwipunku w ostatnim momencie, w ktorym jest jeszcze prawda.
 *
 * <p>{@code Player.die()} oproznia ekwipunek zaraz po tym evencie, a wszystko, co dociera
 * do {@code LivingDropsEvent} - gdzie Tombstone buduje grob - jest juz plaska kupka bez
 * indeksow. Wlasny listener {@code LivingDeathEvent} Tombstone'a ma priorytet LOWEST,
 * wiec na HIGHEST zawsze widzimy ekwipunek nietkniety.
 *
 * <p>Priorytet HIGHEST takze po to, zeby handler anulujacy smierc nie wszedl przed nami.
 * Snapshot dla smierci, ktora sie nie wydarzyla, kosztuje jeden nieuzyty wpis, ktory
 * magazyn przycina.
 */
@Mod.EventBusSubscriber(modid = TombTweaks.MODID)
public final class SnapshotCaptureHandler {

    private static final int MAX_PENDING = 5;
    private static final long MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L; // 30 dni

    private SnapshotCaptureHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        long now = System.currentTimeMillis();
        SlotSnapshot snapshot = new SlotSnapshot(now);
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.items.size(); i++) {
            record(snapshot, i, inventory.items.get(i));
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            record(snapshot, SlotSnapshot.ARMOR_BASE + i, inventory.armor.get(i));
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            record(snapshot, SlotSnapshot.OFFHAND_SLOT + i, inventory.offhand.get(i));
        }

        if (snapshot.isEmpty()) {
            return;
        }

        SnapshotStore store = SnapshotCodec.load(player);
        store.add(snapshot);
        store.prune(now, MAX_PENDING, MAX_AGE_MILLIS);
        SnapshotCodec.save(player, store);

        if (Config.INSTANCE.restoreDebugLogging.get()) {
            TombTweaks.LOGGER.info("[TombTweaks] recorded {} seats for {} at {}",
                    snapshot.entries().size(), player.getGameProfile().getName(), now);
        }
    }

    private static void record(SlotSnapshot snapshot, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        snapshot.add(slot, StackViews.keyOf(stack), stack.getCount());
    }
}
