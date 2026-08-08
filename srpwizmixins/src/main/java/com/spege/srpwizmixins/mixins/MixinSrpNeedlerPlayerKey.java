package com.spege.srpwizmixins.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.potion.PotionNeedler;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Fix - Needler's terminal stage does nothing to players.
 *
 * <p>{@code PotionNeedler.effectNeedler} runs once the effect's amplifier reaches
 * {@code needlerTerminal}. Its first two statements are, in order:
 *
 * <pre>
 * e.removePotionEffect(SRPPotions.DLER_E);           // (1) effect consumed
 * try { name = EntityList.getKey(e).toString(); }    // (2) NPE for a player
 * catch (Exception ex) { logger.log(ERROR, "Problem with needler and an entity", ex); return; }
 * </pre>
 *
 * <p>{@link EntityList#getKey(Entity)} returns {@code null} for {@code EntityPlayer} - players
 * carry no entity registry name. SRP calls {@code toString()} on it unguarded, so on a player the
 * method always throws at (2) and returns from the catch block. The effect has already been
 * consumed at (1), so the whole terminal sequence - the immunity-list check, the re-application at
 * a lower amplifier, the {@code maxHealth * needlerDamage} hit, the explosion and the totem-of-undying
 * handling - is dead code for players. The stack trace repeats once per triggering player tick.
 *
 * <p>That SRP ships a separate {@code Needler Maximum Damage Player} option is direct evidence the
 * mechanic is meant to reach players; the NPE makes that option unreachable.
 *
 * <p>Measured 2026-08-08 with a matched control on the same world, same config, same amplifier 7:
 * a villager (max health 54.42) took exactly 21.77 = 40% of its maximum, while the player took 0 and
 * logged the NPE. The only difference between the two targets was the null registry name.
 *
 * <p>We {@link Redirect} that single {@code getKey} call and substitute {@code minecraft:player}
 * when it comes back {@code null} <em>and</em> the target really is a player. Any other entity with
 * no registry name keeps SRP's original behaviour, error line included - a nameless non-player is a
 * genuine anomaly and should stay visible.
 *
 * <p>The {@code minecraft:player} fallback is deliberate rather than an arbitrary placeholder: the
 * name flows straight into SRP's {@code checkName(name, needlerImmuneList, needlerImmuneListWhite)},
 * so writing {@code minecraft:player} into {@code Needler Immune Mob List} turns the effect off for
 * players again, from config, without touching this fix.
 *
 * <p>🚨 This fix makes the game harder. With {@code Needler Damage = 0.4} a player at the terminal
 * stage loses 40% of maximum health (capped by {@code Needler Maximum Damage Player}) and, at zero
 * health, dies unless a totem of undying is in hand. That damage simply was not happening before.
 * The explosion is {@code createExplosion(entity, x, y, z, 0.0F, false)} - strength zero, so it
 * breaks no blocks. Gated on {@code srpCompat.fixNeedlerOnPlayers}, default OFF.
 *
 * <p>Target names are production (SRG) with {@code remap = false}, matching the shipped SRP jar;
 * {@code effectNeedler} is SRP's own name and is never remapped.
 */
@Mixin(value = PotionNeedler.class, remap = false)
public abstract class MixinSrpNeedlerPlayerKey {

    @Redirect(
            method = "effectNeedler",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityList;func_191301_a"
                            + "(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/ResourceLocation;"),
            remap = false)
    private ResourceLocation insanetweaks$needlerKeyFallback(Entity entity) {
        ResourceLocation key = EntityList.getKey(entity);
        if (key == null
                && SrpWizMixinsConfig.srpCompat.fixNeedlerOnPlayers
                && entity instanceof EntityPlayer) {
            return new ResourceLocation("minecraft", "player");
        }
        return key;
    }
}
