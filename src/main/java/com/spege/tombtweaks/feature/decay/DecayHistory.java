package com.spege.tombtweaks.feature.decay;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.PlayerData;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Co grob juz stracil.
 *
 * <p>Wersja z 1.12.2 zapisywala historie przez wyszukanie gracza PO NAZWIE, wiec grob
 * gracza offline rozkladal sie bez sladu - dokladnie w scenariuszu, dla ktorego feature
 * powstal. Tutaj kluczem jest UUID; dla gracza offline wpis idzie na razie tylko do logu
 * (na poziomie DEBUG - przy domyslnym interwale to linia na minute na kazdy grob).
 *
 * <p>Wpis trzyma tylko id i ilosc, nie pelne NBT stacka: historia nie ma w v1 zadnego
 * konsumenta, a shulker box w kazdym wpisie potrafilby rozdac playerdata do megabajtow.
 */
public final class DecayHistory {

    private static final String KEY = "tombtweaks_decay_history";

    private DecayHistory() {
    }

    public static void record(Level level, BlockPos pos, UUID ownerId, ItemStack lost) {
        MinecraftServer server = level.getServer();
        if (server == null || ownerId == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            TombTweaks.LOGGER.debug("[TombTweaks] grave at {} lost {} x{} (owner {} offline)",
                    pos, StackViews.idOf(lost), lost.getCount(), ownerId);
            return;
        }

        CompoundTag persisted = PlayerData.persisted(owner);
        ListTag history = persisted.getList(KEY, Tag.TAG_COMPOUND);

        CompoundTag entry = new CompoundTag();
        entry.putLong("at", System.currentTimeMillis());
        entry.putString("pos", pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + "," + level.dimension().location());
        entry.putString("item", StackViews.idOf(lost));
        entry.putInt("count", lost.getCount());
        history.add(entry);

        int max = Config.INSTANCE.decayMaxHistory.get();
        while (history.size() > max) {
            history.remove(0);
        }
        persisted.put(KEY, history);
    }
}
