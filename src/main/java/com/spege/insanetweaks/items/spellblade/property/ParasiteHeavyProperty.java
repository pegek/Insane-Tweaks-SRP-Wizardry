package com.spege.insanetweaks.items.spellblade.property;

import java.util.UUID;

import com.oblivioussp.spartanweaponry.api.IWeaponPropertyContainer;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponPropertyWithCallback;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Local re-implementation of swparasites {@code HeavyWeaponProperty}, so InsaneTweaks
 * spellblades no longer compile-depend on swparasites (SW: Parasites).
 *
 * Preserves the one piece of real functionality the original gave our blades: a heavy weapon
 * slows the wielder's attack speed while held. The swparasites version also drove SW: Parasites'
 * OWN weapon evolution via a {@code srpkills} NBT counter and an {@code EvolutionHandler}
 * lookup - that path is intentionally dropped here because InsaneTweaks spellblades evolve
 * through their own {@code SentientKills} NBT + {@code SpellbladeHitHandler}, and the swparasites
 * EvolutionHandler never knew about our items anyway (so it was dead code for us).
 *
 * The property {@code type} stays {@code "heavy"} and quality {@code NEGATIVE} so the existing
 * {@code SpellbladeTooltipHandler} renders the same red "Heavy II" line as before.
 */
public class ParasiteHeavyProperty extends WeaponPropertyWithCallback {

    /** Attack-speed multiplier penalty (operation 2). -0.5 == 50% slower, matches swparasites HEAVY_2. */
    private static final double LEVEL_2_SLOWNESS = 0.5D;
    private static final double LEVEL_1_SLOWNESS = 0.33D;

    // Stable, project-owned UUIDs (NOT the swparasites ones) so the modifier is unique to us.
    private static final UUID SLOW_UUID_I = UUID.fromString("a1b2c3d4-0001-4dee-9f00-1100bbccddee");
    private static final UUID SLOW_UUID_II = UUID.fromString("a1b2c3d4-0002-4dee-9f00-1100bbccddee");

    private final boolean level2;
    private AttributeModifier modifier;

    public ParasiteHeavyProperty(boolean level2) {
        super("heavy", "insanetweaks", level2 ? 2 : 1, 0.0f);
        this.level2 = level2;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public WeaponProperty.PropertyQuality getQuality() {
        return WeaponProperty.PropertyQuality.NEGATIVE;
    }

    private AttributeModifier getModifier() {
        if (this.modifier == null) {
            this.modifier = new AttributeModifier(
                    this.level2 ? SLOW_UUID_II : SLOW_UUID_I,
                    "insanetweaks_heavy_weapon_property",
                    -(this.level2 ? LEVEL_2_SLOWNESS : LEVEL_1_SLOWNESS),
                    2);
        }
        return this.modifier;
    }

    /**
     * Applies/removes the attack-speed penalty depending on whether the entity is currently
     * holding a weapon that carries THIS property. Mirrors the swparasites event logic.
     */
    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (event.getEntityLiving() == null) {
            return;
        }
        ItemStack held = event.getEntityLiving().getHeldItemMainhand();
        boolean shouldSlow = false;

        if (!held.isEmpty()) {
            Item item = held.getItem();
            if (item instanceof IWeaponPropertyContainer) {
                IWeaponPropertyContainer<?> container = (IWeaponPropertyContainer<?>) item;
                for (WeaponProperty prop : container.getAllWeaponProperties()) {
                    if (prop == this) {
                        shouldSlow = true;
                        break;
                    }
                }
            }
        }

        IAttributeInstance attr = event.getEntityLiving().getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        AttributeModifier mod = this.getModifier();
        boolean present = attr.getModifier(mod.getID()) != null;

        if (shouldSlow && !present) {
            attr.applyModifier(mod);
        } else if (!shouldSlow && present) {
            attr.removeModifier(mod);
        }
    }
}
