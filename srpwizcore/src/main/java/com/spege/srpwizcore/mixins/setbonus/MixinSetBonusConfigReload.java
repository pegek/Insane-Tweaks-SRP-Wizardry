package com.spege.srpwizcore.mixins.setbonus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraftforge.fml.client.event.ConfigChangedEvent;

/**
 * SetBonus crash fix: {@code ConcurrentModificationException} in
 * {@code ServerBonus.updateBonuses} while ticking a player (crash 2026-07-27 22:54, server thread).
 *
 * <p><b>Root cause.</b> SetBonus subscribes two config events and filters only one of them.
 * Verified with {@code javap -p -c} against {@code libs/SetBonus-1.12.2.040.jar}, which is the
 * exact build the pack ships:
 *
 * <ul>
 *   <li>{@code SetBonus.saveConfig(OnConfigChangedEvent)} opens with
 *       {@code getModID().equals("setbonus")} and returns early otherwise. Correct.</li>
 *   <li>{@code SetBonus.calcConfigs(PostConfigChangedEvent)} has <b>no such check</b> — its very
 *       first instruction is {@code invokestatic SetBonusData.setServerFromConfig()}. So pressing
 *       Done on <i>any</i> mod's config screen after changing something reloads SetBonus's server
 *       data.</li>
 * </ul>
 *
 * <p>That reload runs on the <b>client</b> thread and is structurally destructive:
 * {@code setServerFromConfig -> SERVER_DATA.setFromConfig() -> clear()} (which calls
 * {@code ServerBonus.dropAll()} and {@code bonuses.clear()}) followed by a {@code bonuses.add(...)}
 * per configured bonus. Meanwhile the <b>server</b> thread sits inside
 * {@code ServerBonus.updateBonuses}, iterating that same {@code LinkedHashSet<Bonus>} from
 * {@code TickEvent.PlayerTickEvent}. A {@code LinkedHashSet}'s iterator is
 * {@code LinkedHashMap$LinkedKeyIterator} — exactly the frame at the top of the crash report.
 *
 * <p><b>The fix.</b> Apply to {@code calcConfigs} the same modid filter its sibling
 * {@code saveConfig} already has. When the player edits SetBonus's own config the modid matches and
 * the method runs completely unchanged; every foreign mod's config screen stops triggering a
 * SetBonus reload it never asked for. {@code TooltipRenderer.update()} is skipped along with it,
 * which is right — a foreign mod's config change cannot have altered SetBonus's tooltip settings.
 *
 * <p><b>Scope.</b> This closes the trigger that actually fired, not the race itself. The two other
 * callers of {@code setServerFromConfig} are untouched: {@code Commands.subCommand}
 * ({@code /setbonus reload}) runs on the server thread and was never racy, while SetBonus's own
 * in-game config GUI ({@code SetBonusConfigGUI}) still reloads from the client thread and can still
 * lose this race. Sealing that needs a second mixin making {@code updateBonuses} iterate a copy, at
 * the cost of an allocation on a per-player-per-tick path.
 *
 * <p>String target — no SetBonus jar on this project's classpath, and none needed. No config gate:
 * the filter restores the mod's own evident intent and has no gameplay effect, so there is nothing
 * to tune. Client-only on purpose: {@code PostConfigChangedEvent} is fired by the config GUI, so it
 * cannot occur on a dedicated server and there is nothing to guard there.
 */
@Mixin(targets = "com.fantasticsource.setbonus.SetBonus", remap = false)
public class MixinSetBonusConfigReload {

    @Inject(method = "calcConfigs", at = @At("HEAD"), cancellable = true, remap = false)
    private static void srpwizcore$onlyReloadOwnConfig(ConfigChangedEvent.PostConfigChangedEvent event,
            CallbackInfo ci) {
        if (!"setbonus".equals(event.getModID())) {
            ci.cancel();
        }
    }
}
