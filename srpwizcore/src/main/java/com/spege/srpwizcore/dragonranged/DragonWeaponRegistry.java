package com.spege.srpwizcore.dragonranged;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Which ranged weapons carry which element. Built once from registry names, so a missing mod
 * simply contributes no entries — srpwizcore keeps no hard dependency on Spartan Fire or
 * Spartan Dragonsteel.
 *
 * <p>15 items: Spartan Fire's longbow and crossbow in three elements times two materials
 * (dragonbone, dragonsteel), plus Ice and Fire's three dragonbone bows.
 */
public final class DragonWeaponRegistry {

    private static final Map<Item, DragonElement> WEAPONS = new HashMap<Item, DragonElement>();

    private static final Set<Item> PREFERS_DRAGON_AMMO = new HashSet<Item>();

    private DragonWeaponRegistry() {
    }

    /** Call once, after every mod has registered its items (postInit). */
    public static void build() {
        WEAPONS.clear();
        PREFERS_DRAGON_AMMO.clear();
        String[] materials = new String[] { "dragonbone", "dragonsteel" };
        String[] kinds = new String[] { "longbow", "crossbow" };
        for (int m = 0; m < materials.length; m++) {
            for (int k = 0; k < kinds.length; k++) {
                String prefix = "spartanfire:" + kinds[k] + "_";
                String suffix = "_" + materials[m];
                put(prefix + "fire" + suffix, DragonElement.FIRE);
                put(prefix + "ice" + suffix, DragonElement.ICE);
                put(prefix + "lightning" + suffix, DragonElement.LIGHTNING);
            }
        }
        // Spartan Fire looks for dragon ammunition only when the weapon is one of its four
        // DRAGONBONE materials; the dragonsteel tier falls through to vanilla ammo. These six are
        // what the findAmmo mixins have to cover.
        for (int k = 0; k < kinds.length; k++) {
            String prefix = "spartanfire:" + kinds[k] + "_";
            addAmmoPreference(prefix + "fire_dragonsteel");
            addAmmoPreference(prefix + "ice_dragonsteel");
            addAmmoPreference(prefix + "lightning_dragonsteel");
        }
        put("iceandfire:dragonbone_bow_fire", DragonElement.FIRE);
        put("iceandfire:dragonbone_bow_ice", DragonElement.ICE);
        put("iceandfire:dragonbone_bow_lightning", DragonElement.LIGHTNING);
        // Derived, not hardcoded: this line is the only signal a human gets that the feature
        // loaded, so adding a material must not silently turn it into a lie. The trailing +3 is
        // Ice and Fire's three dragonbone bows above.
        int expected = materials.length * kinds.length * 3 + 3;
        SrpWizCore.LOGGER.info("[srpwizcore] dragon ranged: {} of {} elemental weapons resolved,"
                        + " {} prefer dragon ammunition",
                Integer.valueOf(WEAPONS.size()), Integer.valueOf(expected),
                Integer.valueOf(PREFERS_DRAGON_AMMO.size()));
    }

    private static void put(String id, DragonElement element) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item != null) {
            WEAPONS.put(item, element);
        }
    }

    private static void addAmmoPreference(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item != null) {
            PREFERS_DRAGON_AMMO.add(item);
        }
    }

    /**
     * @return true when this weapon should look for dragon ammunition before ordinary ammunition.
     *         Only the dragonsteel tier needs it — Spartan Fire does this itself for dragonbone.
     */
    public static boolean prefersDragonAmmo(Item item) {
        return item != null && PREFERS_DRAGON_AMMO.contains(item);
    }

    /** @return the element of the weapon in this stack, or null if it is not an elemental one. */
    public static DragonElement forStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return WEAPONS.get(stack.getItem());
    }
}
