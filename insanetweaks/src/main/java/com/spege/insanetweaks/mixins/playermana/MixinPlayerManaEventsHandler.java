package com.spege.insanetweaks.mixins.playermana;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.insanetweaks.util.ArcaneAdaptedFruitHelper;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zettasword.player_mana.Tales;
import zettasword.player_mana.api.Solver;
import zettasword.player_mana.cap.ISoul;
import zettasword.player_mana.cap.Mana;

@Mixin(targets = "zettasword.player_mana.events.EventsHandler", remap = false)
public abstract class MixinPlayerManaEventsHandler {

    @Inject(method = "onPlayerTick(Lnet/minecraftforge/fml/common/gameevent/TickEvent$PlayerTickEvent;)V", at = @At("RETURN"), remap = false)
    private static void insanetweaks$enhanceFruitRegen(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof EntityPlayerMP && event.player.ticksExisted > 5) {
            if (Solver.doEvery(event.player, Tales.mp.seconds_frequency)) {
                if (ArcaneAdaptedFruitHelper.hasConsumedFruit(event.player)
                        && ArcaneAdaptedFruitHelper.isFruitRegenActive(event.player)) {
                    ISoul soul = Mana.getSoul(event.player);
                    if (soul != null) {
                        double additional = Tales.mp.bonus_regen * (soul.getMaxMP() / Tales.mp.max);
                        double baseRegenAmount = Tales.mp.regeneration + additional;
                        double extraMultiplier = ArcaneAdaptedFruitHelper.MANA_REGEN_FRUIT_TOTAL_MULTIPLIER - 1.0D;
                        if (extraMultiplier > 0.0D) {
                            soul.addMana(event.player, baseRegenAmount * extraMultiplier);
                        }
                    }
                }
            }
        }
    }

}
