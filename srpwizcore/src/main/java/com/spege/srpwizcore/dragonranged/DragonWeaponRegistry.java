package com.spege.srpwizcore.dragonranged;

import java.util.HashMap;
import java.util.Map;

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

    private DragonWeaponRegistry() {
    }

    /** Call once, after every mod has registered its items (postInit). */
    public static void build() {
        WEAPONS.clear();
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
        put("iceandfire:dragonbone_bow_fire", DragonElement.FIRE);
        put("iceandfire:dragonbone_bow_ice", DragonElement.ICE);
        put("iceandfire:dragonbone_bow_lightning", DragonElement.LIGHTNING);
        SrpWizCore.LOGGER.info("[srpwizcore] dragon ranged: {} of 15 elemental weapons resolved",
                Integer.valueOf(WEAPONS.size()));
    }

    private static void put(String id, DragonElement element) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item != null) {
            WEAPONS.put(item, element);
        }
    }

    /** @return the element of the weapon in this stack, or null if it is not an elemental one. */
    public static DragonElement forStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return WEAPONS.get(stack.getItem());
    }
}
