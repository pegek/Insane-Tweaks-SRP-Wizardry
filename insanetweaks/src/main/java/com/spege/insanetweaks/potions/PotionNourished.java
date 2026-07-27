package com.spege.insanetweaks.potions;

import javax.annotation.Nonnull;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.FoodStats;

/**
 * Nourished - applied by the Mmmm enchantment when a player finishes eating enchanted food.
 * Native port of UniqueEnchantments' own SaturationPotion.
 *
 * <p>Every 4 ticks it tops the player's saturation back up without letting the hunger bar itself
 * grow: {@code addStats(1, amplifier)} raises saturation by {@code 2 * amplifier} (clamped to the
 * food level by vanilla) and would also add one food point, so the food level is snapshotted and
 * written straight back. The practical effect is that hunger stops draining for the duration -
 * vanilla only decrements the food level once saturation has hit zero.
 *
 * <p>Vanilla's own {@code minecraft:saturation} is not a substitute: it is an instant effect that
 * feeds the player once. This one has to be duration-based.
 *
 * <p>NOTE on the API: {@link FoodStats#setFoodLevel(int)} is plain server-safe API in 1.12.2, but
 * its neighbour {@code setFoodSaturationLevel(float)} is {@code @SideOnly(Side.CLIENT)} and would
 * vanish on a dedicated server - hence the addStats/restore dance rather than writing saturation
 * directly.
 *
 * <p>No status icon: {@code hasStatusIcon()} is left at the vanilla default, which is false while
 * no sprite index is set, so Forge draws the effect's name and timer with an empty icon slot rather
 * than indexing into the vanilla sprite sheet. The other potions in this mod ship a PNG; this one
 * deliberately does not need one.
 */
public class PotionNourished extends Potion {

    /** Same colour as vanilla saturation (and as the upstream effect), for the swirl particles. */
    private static final int COLOUR = 0xF82423;

    /** How often the top-up pulses, in ticks. Matches upstream. */
    private static final long PULSE_INTERVAL = 4L;

    public PotionNourished() {
        super(false, COLOUR);
        this.setPotionName("potion.insanetweaks.nourished");
        this.setBeneficial();
    }

    @Override
    public boolean isInstant() {
        return false;
    }

    /**
     * True every tick; the actual 4-tick cadence is enforced in {@link #performEffect} against world
     * time rather than the effect's remaining duration. Upstream does the same, and it matters: a
     * duration-based modulo would drift between effects applied on different ticks.
     */
    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Override
    public void performEffect(@Nonnull EntityLivingBase entity, int amplifier) {
        if (!(entity instanceof EntityPlayer)) {
            return;
        }
        if (entity.world.getTotalWorldTime() % PULSE_INTERVAL != 0L) {
            return;
        }
        FoodStats stats = ((EntityPlayer) entity).getFoodStats();
        int foodBefore = stats.getFoodLevel();
        stats.addStats(1, amplifier);
        stats.setFoodLevel(foodBefore);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
