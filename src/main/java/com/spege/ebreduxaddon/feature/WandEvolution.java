package com.spege.ebreduxaddon.feature;

import com.binaris.wizardry.api.content.item.IManaItem;
import com.spege.ebreduxaddon.core.Progress;
import com.spege.ebreduxaddon.feature.item.SymbioticWandItem;
import com.spege.ebreduxaddon.platform.Config;
import com.spege.ebreduxaddon.platform.WandProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Ewolucja Symbiotic -&gt; Sentient: podmiana przedmiotu z kopia calego NBT (zaklecia, ulepszenia,
 * cooldowny, progres Redux, nasz licznik), jak w Redux applyTierUpgrade. Mana jest przenoszona
 * wprost, bo przechowywana jest jako uszkodzenie, a Sentient ma inna pojemnosc.
 *
 * <p>Wolane tylko, gdy gracz nie uzywa przedmiotu: podmiana w trakcie kanalu zerwalaby go.
 */
public final class WandEvolution {

    private WandEvolution() {
    }

    /** Zwraca, czy ewolucja nastapila. */
    public static boolean tryEvolve(Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (!(wand.getItem() instanceof SymbioticWandItem old)
                || !Progress.shouldEvolve(WandProgress.get(wand), Config.INSTANCE.wandEvolveAt.get())) {
            return false;
        }
        ItemStack evolved = new ItemStack(ModItems.SENTIENT_WAND.get());
        evolved.setTag(wand.getTag() == null ? null : wand.getTag().copy());
        ((IManaItem) evolved.getItem()).setMana(evolved, old.getMana(wand));
        player.setItemInHand(hand, evolved);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 0.6f, 1.6f);
        player.displayClientMessage(Component.translatable("message.ebreduxaddon.wand_evolved"), true);
        return true;
    }
}
