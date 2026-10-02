package com.spege.tombtweaks.feature.decay;

import com.spege.tombtweaks.core.DecaySchedule;
import com.spege.tombtweaks.core.DecayVictim;
import com.spege.tombtweaks.core.ProtectionRules;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave;

import java.util.ArrayList;
import java.util.List;

/** Jeden tick jednego grobu. Wszystkie decyzje podejmuje core; tu jest tylko wykonanie. */
public final class GraveDecayService {

    private GraveDecayService() {
    }

    public static void tick(Level level, BlockPos pos, BlockEntityPlayerGrave grave) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.decayEnabled.get()) {
            return;
        }
        if (!DecaySchedule.shouldDecay(grave.countTicks,
                                       Config.INSTANCE.decayStartTicks.get(),
                                       Config.INSTANCE.decayIntervalTicks.get())) {
            return;
        }

        IItemHandler inventory = grave.getInventory();
        if (inventory == null) {
            return;
        }

        // ProtectionRules.of przyjmuje List<?> i sam pomija null oraz nie-Stringi,
        // wiec wartosci z configu ida wprost, bez rzutowania.
        ProtectionRules rules = ProtectionRules.of(
                Config.INSTANCE.decayProtectedItems.get(),
                Config.INSTANCE.decayProtectedItemPrefixes.get(),
                Config.INSTANCE.decayProtectedEnchantments.get(),
                Config.INSTANCE.decayProtectedNbtStrings.get());

        List<Integer> unprotected = new ArrayList<>();
        List<Integer> guarded = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (rules.isProtected(StackViews.view(stack))) {
                guarded.add(slot);
            } else {
                unprotected.add(slot);
            }
        }

        int victim = DecayVictim.pick(unprotected, guarded,
                Config.INSTANCE.decayProtectedNeverDecay.get(),
                bound -> level.random.nextInt(bound));
        if (victim == DecayVictim.NOTHING) {
            return;
        }

        ItemStack lost = inventory.extractItem(victim, inventory.getStackInSlot(victim).getCount(), false);
        if (lost.isEmpty()) {
            return;
        }
        // Inwentarz grobu to gole ItemStackHandler bez onContentsChanged - samo wyjecie NIE
        // oznacza block entity jako zmienionego. Bez tego chunk moze sie zapisac ze starym
        // stanem grobu, a wyrzucony przedmiot (encja, zapisywana osobno) przetrwa restart:
        // po wczytaniu jest i na ziemi, i z powrotem w grobie. Duplikacja.
        grave.setChanged();

        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, lost);
        DecayHistory.record(level, pos, grave.getOwnerId(), lost);
    }
}
