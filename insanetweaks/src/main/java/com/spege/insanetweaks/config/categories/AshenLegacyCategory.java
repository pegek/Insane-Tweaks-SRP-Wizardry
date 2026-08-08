package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the <b>Ashen Legacy</b> advanced property: a dropped item carrying it is replaced
 * with {@code EntityItemIndestructible} and lingers far longer than ordinary gear.
 *
 * <p>Every value here used to be a hardcoded constant - the lifespan in
 * {@code LegendaryDropHelper} and four immunity branches inside the entity - so a pack could turn
 * the property off entirely (via {@code gear.properties.ashenLegacy}) but could not soften it.
 *
 * <p>All fields are <b>read live</b>. The immunity flags are consulted on every damage source the
 * entity is handed, and the lifespan on every drop, so a change applies to items dropped from then
 * on. An {@code EntityItemIndestructible} that already exists keeps the {@code lifespan} and the
 * vanilla {@code isImmuneToFire} flag it was created with; the {@code attackEntityFrom} branches
 * still follow the config immediately.
 *
 * <p>Accessed as {@code ModConfig.ashenLegacy.*}. Whether the property exists at all is
 * {@code gear.properties.ashenLegacy}; who grants it is a Property Book, an item class, or
 * {@code enchantments.sentientCodex.conferAshenLegacy}.
 *
 * <p>Void / out-of-world removal is <b>not</b> covered by any of this and never was - it needs
 * explicit recovery logic rather than a damage-source veto.
 */
public class AshenLegacyCategory {

    @Config.Comment({
            "How long a dropped Ashen Legacy item survives before despawning, in ticks. Vanilla is",
            "6000 (5 minutes); 72000 is 60 minutes. Only ever raises an item's lifespan - a drop that",
            "already has a longer one keeps it. Read live."
    })
    @Config.Name("Drop Lifespan Ticks")
    @Config.RangeInt(min = 6000, max = 432000)
    public int dropLifespanTicks = 72000;

    @Config.Comment({
            "Ignore burning: both the standing-in-fire and the on-fire damage sources, plus the",
            "vanilla entity fire-immunity flag that stops the item catching light in the first place.",
            "Read live (the flag itself is fixed when the drop is created). Default true."
    })
    @Config.Name("Immune To Fire")
    public boolean immuneToFire = true;

    @Config.Comment({
            "Ignore lava damage. Note this is separate from 'Immune To Fire': with fire immunity off",
            "and this on, the item survives the lava but still burns on the way out. Read live.",
            "Default true."
    })
    @Config.Name("Immune To Lava")
    public boolean immuneToLava = true;

    @Config.Comment({
            "Ignore cactus damage. Read live. Default true."
    })
    @Config.Name("Immune To Cactus")
    public boolean immuneToCactus = true;

    @Config.Comment({
            "Ignore every explosion, including the creeper/TNT blast that destroys ordinary drops.",
            "This is the immunity the property was originally written for - vanilla EntityItem did",
            "not prove reliable against blasts in practice. Read live. Default true."
    })
    @Config.Name("Immune To Explosions")
    public boolean immuneToExplosions = true;
}
