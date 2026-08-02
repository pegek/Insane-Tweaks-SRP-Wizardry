package com.spege.srpwizcore.util;

import java.lang.reflect.Field;

import com.oblivioussp.spartanweaponry.api.WeaponProperties;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty;
import com.oblivioussp.spartanweaponry.item.ItemSwordBase;
import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import team.cqr.cqrepoured.entity.bases.AbstractEntityCQR;

/**
 * Reach-aware melee AI for CQR mobs holding Spartan weapons (2026-08-01, srpwizcore 1.8.6).
 *
 * <p>CQR's melee gate is {@code AbstractEntityCQR.getAttackReach} — width math + a bonus
 * that exists ONLY for CQR's own {@code ItemSpearBase}. Spartan encodes reach as the weapon
 * property {@code reach} whose magnitude is the PLAYER'S TOTAL reach in blocks (REACH_1 = 6,
 * REACH_2 = 7 vs the 5.0 vanilla baseline), so the mob-side bonus here is
 * {@code (magnitude - 5) * reachFactor}. Attack SPEED needs no code at all: AbstractEntityCQR
 * registers ATTACK_SPEED and defers to the held item's own modifier when it has one, so a
 * Spartan rapier already swings ~4x faster than a halberd on these mobs natively.
 *
 * <p>Called from the two cqrspartan mixins ({@code MixinCqrAttackReach} on the reach getter,
 * {@code MixinCqrAttackStandoff} on {@code EntityAIAttack.updatePath}); both flags read live.
 */
public final class CqrReachHelper {

    /** Player baseline the Spartan {@code reach} magnitude is measured against. */
    private static final double PLAYER_BASE_REACH = 5.0D;

    /** {@code AbstractCQREntityAI.entity} — protected, reached once via reflection. */
    private static Field aiEntityField;
    private static boolean aiEntityFieldBroken;

    private CqrReachHelper() {
    }

    /** Extra melee reach (blocks) granted by the mainhand Spartan weapon; 0 if none. */
    public static double reachBonus(EntityLivingBase mob) {
        if (!SrpWizCoreConfig.cqrIntegration.reachAiEnabled) {
            return 0.0D;
        }
        Item item = mob.getHeldItemMainhand().getItem();
        if (!(item instanceof ItemSwordBase)) {
            return 0.0D;
        }
        WeaponProperty reach = ((ItemSwordBase) item)
                .getFirstWeaponPropertyWithType(WeaponProperties.PROPERTY_TYPE_REACH);
        if (reach == null) {
            return 0.0D;
        }
        double extra = reach.getMagnitude() - PLAYER_BASE_REACH;
        if (extra <= 0.0D) {
            return 0.0D;
        }
        return extra * SrpWizCoreConfig.cqrIntegration.reachFactor;
    }

    /**
     * Standoff check for {@code EntityAIAttack.updatePath}: a mob whose weapon grants extra
     * reach and whose target is already comfortably inside that reach stops advancing (path
     * cleared) instead of pressing into hugging distance — it keeps jabbing from its reach
     * advantage. Returns true when the caller should cancel the vanilla path update.
     */
    public static boolean shouldStandoff(Object aiTask, EntityLivingBase target) {
        if (!SrpWizCoreConfig.cqrIntegration.reachStandoff || target == null) {
            return false;
        }
        AbstractEntityCQR mob = aiEntity(aiTask);
        if (mob == null || reachBonus(mob) <= 0.0D) {
            return false;
        }
        // 0.5-block margin inside the real reach so the attack gate stays satisfied while
        // the target shuffles; once the target leaves it, updatePath runs again normally.
        if (!mob.isInReach(target, mob.getAttackReach(target) - 0.5D)) {
            return false;
        }
        mob.getNavigator().clearPath();
        return true;
    }

    private static AbstractEntityCQR aiEntity(Object aiTask) {
        if (aiEntityFieldBroken) {
            return null;
        }
        try {
            if (aiEntityField == null) {
                aiEntityField = Class
                        .forName("team.cqr.cqrepoured.entity.ai.AbstractCQREntityAI")
                        .getDeclaredField("entity");
                aiEntityField.setAccessible(true);
            }
            Object entity = aiEntityField.get(aiTask);
            return entity instanceof AbstractEntityCQR ? (AbstractEntityCQR) entity : null;
        } catch (Throwable t) {
            aiEntityFieldBroken = true;
            SrpWizCore.LOGGER.warn(
                    "[srpwizcore] reach standoff disabled - AbstractCQREntityAI.entity not reachable",
                    t);
            return null;
        }
    }
}
