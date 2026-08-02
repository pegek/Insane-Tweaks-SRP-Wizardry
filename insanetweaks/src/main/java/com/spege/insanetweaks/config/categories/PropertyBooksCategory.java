package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Property Books: the anvil route that grants an advanced property to one particular item.
 *
 * <p>Two properties ship with the system. <b>Ashen Legacy</b> hardens the dropped item against
 * fire, lava and explosions and stretches its despawn timer - enforced by
 * {@code IndestructibleDropHandler} via {@code EntityItemIndestructible}, and applicable to gear.
 * <b>Grip</b> is the property face of the existing Fleshbound mechanic: the weapon cannot be thrown
 * away, dragged out of the inventory or knocked out of your hands, and is severed with a regrow
 * cooldown when you die. Both are read through {@code AdvPropertyResolver}, so a book grant behaves
 * exactly like the same property declared by an item class.
 *
 * <p>Mint books with {@code /itweaks propertybook <id> [player]}, the same shape as
 * {@code /itweaks grantbook}, so an FTB Quests command reward can hand one out without any
 * hand-authored NBT.
 */
public class PropertyBooksCategory {

    @Config.Comment({"Experience levels the anvil charges to apply a Property Book. Read live."})
    @Config.Name("Anvil Level Cost")
    @Config.RangeInt(min = 0, max = 100)
    public int anvilLevelCost = 12;

    @Config.Comment({"Radius (blocks) searched for a Grip item's owner when it somehow entered the world as a",
            "dropped entity and carries neither a thrower nor a bound-owner tag. Last-resort fallback:",
            "the thrower and the owner UUID are both tried first. Read live."})
    @Config.Name("Grip Recovery Radius")
    @Config.RangeDouble(min = 0.0D, max = 32.0D)
    public double gripRecoveryRadius = 6.0D;

    @Config.Comment({"Let a book apply a property the item already has from another source (its own class, or",
            "a Sentient Codex conferring Ashen Legacy). Off by default: the grant would be invisible,",
            "since the property is already listed, so the anvil would just eat the book and the levels.",
            "Read live."})
    @Config.Name("Allow Redundant Grants")
    public boolean allowRedundantGrants = false;
}
