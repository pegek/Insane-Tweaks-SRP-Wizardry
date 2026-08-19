package com.spege.manacore.compat.tab;

import javax.annotation.Nullable;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.CostMath;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * All the logic behind Trinkets and Baubles' {@code MagicStats} takeover, kept out of the mixin
 * on purpose. {@code MixinMagicStats} is meant to stay a thin dispatcher - seven {@code @Inject}
 * methods that do nothing but call through to here - so that a future TaB update only ever
 * requires editing this one small file, never re-deriving the injection points.
 *
 * <p>Every value that crosses the boundary goes through {@link CostMath#toForeignUnits} /
 * {@link CostMath#fromForeignUnits}: TaB counts mana in {@code float}, at whatever scale its own
 * balance expects, while our pool is a {@code double} in ManaCore's own units. The conversion is
 * the only thing standing between "one unified pool" and "the HUD number silently doesn't match
 * what a spell actually costs".
 */
public final class TabManaAccess {

    private TabManaAccess() {
    }

    /** True when ManaCore should answer for this capability instance instead of TaB itself. */
    public static boolean handles(@Nullable EntityLivingBase owner) {
        return ManaCoreConfig.tab.enabled && owner instanceof EntityPlayer;
    }

    public static float getMana(EntityLivingBase owner) {
        return CostMath.toForeignUnits(ManaAPI.getMana((EntityPlayer) owner), ManaCoreConfig.tab.unitScale);
    }

    public static float getMaxMana(EntityLivingBase owner) {
        return CostMath.toForeignUnits(ManaAPI.getMaxMana((EntityPlayer) owner), ManaCoreConfig.tab.unitScale);
    }

    public static void setMana(EntityLivingBase owner, float value) {
        ManaAPI.setMana((EntityPlayer) owner, CostMath.fromForeignUnits(value, ManaCoreConfig.tab.unitScale));
    }

    public static void addMana(EntityLivingBase owner, float value) {
        ManaAPI.add((EntityPlayer) owner, CostMath.fromForeignUnits(value, ManaCoreConfig.tab.unitScale));
    }

    public static boolean spendMana(EntityLivingBase owner, float value) {
        return ManaAPI.spend((EntityPlayer) owner, CostMath.fromForeignUnits(value, ManaCoreConfig.tab.unitScale));
    }

    public static void refillMana(EntityLivingBase owner) {
        EntityPlayer player = (EntityPlayer) owner;
        ManaAPI.setMana(player, ManaAPI.getMaxMana(player));
    }

    public static boolean needMana(EntityLivingBase owner) {
        EntityPlayer player = (EntityPlayer) owner;
        return ManaAPI.getMana(player) < ManaAPI.getMaxMana(player);
    }
}
