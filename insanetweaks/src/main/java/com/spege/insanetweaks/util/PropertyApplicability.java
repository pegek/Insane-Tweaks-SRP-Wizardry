package com.spege.insanetweaks.util;

import com.spege.insanetweaks.api.AdvPropertyRegistry.Applicability;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

/**
 * The rules deciding what a Property Book may be applied to.
 *
 * <p>Kept out of {@code AdvPropertyRegistry} so the reasoning has room to live next to the code,
 * and so a future property can pick a rule by name instead of restating it.
 */
public final class PropertyApplicability {

    private PropertyApplicability() {
    }

    /**
     * Tools, weapons, armour, shields and bows - anything a player equips and keeps.
     *
     * <p>The {@code isDamageable} arm is what catches modded gear that extends none of the vanilla
     * item classes (Spartan Weaponry, EB Wizardry wands, this mod's own spellblades). Durability is
     * a good proxy for "a single, meaningful item" as opposed to a resource stack, which is the line
     * worth drawing: Ashen Legacy makes a dropped item survive fire, lava and explosions
     * indefinitely, and that is a sensible reward on a hard-won weapon and an exploit on a stack of
     * ore.
     */
    public static final Applicability GEAR = new Applicability() {
        @Override
        public boolean test(ItemStack stack) {
            net.minecraft.item.Item item = stack.getItem();
            return item instanceof ItemTool || item instanceof ItemSword || item instanceof ItemArmor
                    || item instanceof ItemShield || item instanceof ItemBow || item.isDamageable();
        }
    };

    /**
     * Anything you fight with.
     *
     * <p>The attribute test is the load-bearing one: rather than trying to enumerate every melee
     * item class in the pack, it asks whether the item actually grants attack damage in the main
     * hand, which is exactly what "is a weapon" means mechanically. That covers Spartan Weaponry's
     * whole roster and this mod's spellblades without naming any of them, and correctly excludes a
     * pickaxe's mining-only profile from counting purely because it is a tool.
     *
     * <p>Deliberately broad for now. The intended direction is to narrow Grip to
     * {@code living_*}/{@code sentient_*} weapons by registry name, and to gate it behind a
     * kill/damage threshold; both are changes to this one predicate.
     */
    public static final Applicability WEAPON = new Applicability() {
        @Override
        public boolean test(ItemStack stack) {
            if (stack.getItem() instanceof ItemSword) {
                return true;
            }
            try {
                return stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND)
                        .containsKey(SharedMonsterAttributes.ATTACK_DAMAGE.getName());
            } catch (Exception ex) {
                // A third-party item can throw while building its modifier map; a book that
                // refuses to apply is a far better outcome than an anvil that crashes the GUI.
                return false;
            }
        }
    };
}
