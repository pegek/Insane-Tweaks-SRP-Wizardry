package com.spege.tombtweaks.feature.cooldown;

import com.spege.tombtweaks.core.Cooldown;
import com.spege.tombtweaks.core.CooldownRules;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.PlayerData;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Odczyt i zapis cooldownu jednej ksiegi. Zegar scienny, ta sama skala co Tombstone.
 *
 * <p>Kluczem jest sam Item ksiegi, nie stack: ksiega zuzywa sie przy udanym uzyciu,
 * a pusty stack w 1.20.1 raportuje swoj item jako minecraft:air - cooldown kluczowany
 * po stacku po prostu by nie wystartowal.
 */
public final class BookCooldownService {

    private static final String PREFIX = "tombtweaks_cooldown_";

    private BookCooldownService() {
    }

    private static CooldownRules rules() {
        return CooldownRules.parse(Config.INSTANCE.cooldownBooks.get());
    }

    /** @return ile ms zostalo, 0 gdy wolne albo gdy feature jest wylaczony */
    public static long remaining(Player player, Item book) {
        if (!Config.INSTANCE.cooldownEnabled.get()) {
            return 0L;
        }
        String id = StackViews.idOf(book);
        long length = rules().millisFor(id);
        if (length <= 0L) {
            return 0L;
        }
        CompoundTag persisted = PlayerData.persisted(player);
        return Cooldown.remaining(persisted.getLong(PREFIX + id), length, System.currentTimeMillis());
    }

    public static void start(Player player, Item book) {
        if (!Config.INSTANCE.cooldownEnabled.get()) {
            return;
        }
        String id = StackViews.idOf(book);
        if (rules().millisFor(id) <= 0L) {
            return;
        }
        PlayerData.persisted(player).putLong(PREFIX + id, System.currentTimeMillis());
    }

    public static void tellRemaining(Player player, long remainingMillis) {
        long seconds = (remainingMillis + 999L) / 1000L;
        player.displayClientMessage(
                Component.translatable("message.tombtweaks.book_cooldown", seconds), true);
    }
}
