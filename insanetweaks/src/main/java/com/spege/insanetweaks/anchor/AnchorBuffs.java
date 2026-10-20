package com.spege.insanetweaks.anchor;

import java.util.UUID;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

/**
 * Stat bonuses for mobs released from a Petrified Anchor: more health for all of them, more speed
 * for sim wizards and battlemages. A stand-in until their AI is balanced, so it is switchable as a
 * whole ({@code Release Buffs}) and per value (0 disables that part). Fixed UUIDs: a mob released
 * twice, or reloaded, never stacks them.
 */
public final class AnchorBuffs {

    private static final UUID HEALTH_ID = UUID.fromString("5b8a2f1e-0d1c-4e43-9a0a-71c1e3d0a101");
    private static final UUID SPEED_ID = UUID.fromString("5b8a2f1e-0d1c-4e43-9a0a-71c1e3d0a102");

    private AnchorBuffs() {
    }

    public static void apply(EntityLiving mob) {
        EntitiesCategory.PetrifiedAnchor cfg = ModConfig.entities.petrifiedAnchor;
        if (!cfg.releaseBuffs) {
            return;
        }
        boolean changed = add(mob.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH), HEALTH_ID,
                "Petrified Anchor health", cfg.releaseHealthBonus);
        if (mob instanceof EntitySimWizard) {
            add(mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED), SPEED_ID, "Petrified Anchor speed",
                    cfg.releaseSpeedBonus);
        }
        if (changed) {
            mob.setHealth(mob.getMaxHealth());
        }
    }

    private static boolean add(IAttributeInstance attribute, UUID id, String name, double bonus) {
        if (attribute == null || bonus <= 0.0D || attribute.getModifier(id) != null) {
            return false;
        }
        attribute.applyModifier(new AttributeModifier(id, name, bonus, 1));
        return true;
    }
}
