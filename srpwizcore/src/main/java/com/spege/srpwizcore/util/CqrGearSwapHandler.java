package com.spege.srpwizcore.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.config.categories.CqrIntegrationCategory;

import com.oblivioussp.spartanweaponry.api.IWeaponPropertyContainer;
import com.oblivioussp.spartanweaponry.api.WeaponProperties;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import team.cqr.cqrepoured.entity.bases.AbstractEntityCQR;

/**
 * Native port of the proven {@code groovy/postInit/cqr_gear.groovy} v2 (instance pack,
 * 2026-07-31): CQR dungeon mobs trade their vanilla sword/axe/bow for a weighted Spartan
 * Weaponry weapon of the same material tier. Called from {@code MixinCqrSpawnerGearSwap}
 * on {@code TileEntitySpawner.spawnEntityFromNBT} RETURN — i.e. ONLY on CQR dungeon spawns,
 * unlike the Groovy prototype which paid a listener on every {@code EntityJoinWorldEvent}.
 *
 * <p>Design rules (user decisions 2026-07-31): bosses are never touched (they spawn via
 * {@code TileEntityBoss}, outside this hook; the package check below is belt and braces for
 * anything exotic), CQR custom weapons are never touched (only items present in the
 * {@code VANILLA} map match), a matched vanilla weapon is swapped ALWAYS by default.
 * Enchantments ride along via NBT copy. The persistent {@code itweaks_cqr_gear} tag is kept
 * for compatibility with entities already tagged by the Groovy prototype in existing saves.
 *
 * <p>Spartan items are resolved by registry name through a null-tolerant cache — materials
 * disabled in Spartan's own config simply fall back — so this class carries no compile
 * dependency on Spartan Weaponry.
 */
public final class CqrGearSwapHandler {

    private static final String TAG = "itweaks_cqr_gear";

    /** Melee types that exist in every wood..diamond material (sword-derived). */
    private static final String[] SWORD_TYPES = {
            "dagger", "longsword", "katana", "scythe", "saber", "rapier",
            "greatsword", "spear", "halberd", "pike", "glaive", "lance"};
    /** Melee types that exist in every wood..diamond material (axe-derived). */
    private static final String[] AXE_TYPES = {"battleaxe", "warhammer", "hammer", "mace"};

    /** Same-tier exotic materials (flavour sprinkle, chance = gearSwapExoticPct). */
    private static final Map<String, String[]> EXOTIC = new HashMap<>();
    /** Per-entity weapon pools (weights via repetition); key = entity registry path. */
    private static final Map<String, String[]> ENTITY_POOLS = new HashMap<>();
    /** Vanilla weapon -> {category, material}; ONLY these items are ever swapped. */
    private static final Map<String, String[]> VANILLA = new HashMap<>();

    private static final String[] BOW_NAMES = {
            "longbow_wood", "longbow_leather", "longbow_copper", "longbow_iron",
            "longbow_silver", "longbow_steel", "longbow_diamond"};
    private static final int[] BOW_WEIGHTS = {35, 25, 10, 15, 5, 5, 5};

    private static final String[] CROSSBOW_NAMES = {
            "crossbow_wood", "crossbow_leather", "crossbow_copper", "crossbow_iron",
            "crossbow_silver", "crossbow_steel", "crossbow_diamond"};
    private static final int[] CROSSBOW_WEIGHTS = {30, 20, 10, 20, 5, 10, 5};

    static {
        EXOTIC.put("stone", new String[] {"copper", "tin"});
        EXOTIC.put("iron", new String[] {"bronze", "silver", "steel"});

        ENTITY_POOLS.put("minotaur", new String[] {
                "battleaxe", "battleaxe", "battleaxe", "halberd", "halberd", "warhammer", "greatsword"});
        ENTITY_POOLS.put("ogre", new String[] {"hammer", "hammer", "warhammer", "mace", "greatsword"});
        ENTITY_POOLS.put("orc", new String[] {"battleaxe", "mace", "saber", "longsword"});
        ENTITY_POOLS.put("boarman", new String[] {"spear", "spear", "pike", "halberd", "glaive"});
        ENTITY_POOLS.put("dwarf", new String[] {"battleaxe", "warhammer", "hammer", "hammer", "mace"});
        ENTITY_POOLS.put("pirate", new String[] {"saber", "saber", "rapier", "dagger"});
        ENTITY_POOLS.put("triton", new String[] {"spear", "pike", "pike", "lance", "glaive"});
        ENTITY_POOLS.put("goblin", new String[] {"dagger", "dagger", "saber"});
        ENTITY_POOLS.put("gremlin", new String[] {"dagger", "dagger"});
        ENTITY_POOLS.put("skeleton", new String[] {"dagger", "saber", "scythe", "scythe", "longsword"});
        ENTITY_POOLS.put("mummy", new String[] {"scythe", "scythe", "katana", "saber"});

        VANILLA.put("minecraft:wooden_sword", new String[] {"sword", "wood"});
        VANILLA.put("minecraft:wooden_axe", new String[] {"axe", "wood"});
        VANILLA.put("minecraft:stone_sword", new String[] {"sword", "stone"});
        VANILLA.put("minecraft:stone_axe", new String[] {"axe", "stone"});
        VANILLA.put("minecraft:iron_sword", new String[] {"sword", "iron"});
        VANILLA.put("minecraft:iron_axe", new String[] {"axe", "iron"});
        VANILLA.put("minecraft:golden_sword", new String[] {"sword", "gold"});
        VANILLA.put("minecraft:golden_axe", new String[] {"axe", "gold"});
        VANILLA.put("minecraft:diamond_sword", new String[] {"sword", "diamond"});
        VANILLA.put("minecraft:diamond_axe", new String[] {"axe", "diamond"});
    }

    /** Registry-name resolution cache; null values are cached too (disabled SW materials). */
    private static final Map<String, Item> ITEM_CACHE = new HashMap<>();

    /** EBW presence, checked once — gates every reference to CqrWandSwapHelper so its
     * Electroblob imports are never classloaded in a pack without wizardry. */
    private static final boolean EBW_LOADED =
            net.minecraftforge.fml.common.Loader.isModLoaded("ebwizardry");

    public static long seen;
    public static long swapped;
    public static long noMatch;
    public static long shieldsDropped;

    private CqrGearSwapHandler() {
    }

    /** RETURN hook body for {@code TileEntitySpawner.spawnEntityFromNBT}. Null-tolerant. */
    public static void process(Entity e) {
        try {
            CqrIntegrationCategory cfg = SrpWizCoreConfig.cqrIntegration;
            if (!cfg.gearSwapEnabled) {
                return;
            }
            if (!(e instanceof EntityLiving) || e.world == null || e.world.isRemote) {
                return;
            }
            String cls = e.getClass().getName();
            if (!cls.startsWith("team.cqr.")) {
                return;
            }
            // Covers dedicated boss classes AND promoted (hasBossBar NBT) bosses — a promoted
            // gremlin slipped through the pure package check on 2026-08-01.
            if (CqrBossCheck.isCqrBoss(e)) {
                return;
            }
            EntityLiving living = (EntityLiving) e;
            NBTTagCompound data = living.getEntityData();
            if (data.getBoolean(TAG)) {
                return;
            }
            data.setBoolean(TAG, true);
            seen++;

            // Flat extra-potion roll for EVERY dungeon mob (user request 2026-08-01),
            // independent of whether any weapon gets swapped below.
            if (cfg.extraPotionPct > 0 && living instanceof AbstractEntityCQR
                    && living.world.rand.nextInt(100) < cfg.extraPotionPct) {
                AbstractEntityCQR cqr = (AbstractEntityCQR) living;
                cqr.setHealingPotions(cqr.getHealingPotions() + 1);
            }

            ItemStack held = living.getHeldItemMainhand();
            if (held.isEmpty() || held.getItem().getRegistryName() == null) {
                noMatch++;
                return;
            }
            String regName = held.getItem().getRegistryName().toString();
            Random rand = living.world.rand;

            // CQR staffs go through their own EBW-wand map (plan 2026-08-01); the
            // no-touching-CQR-customs rule still holds for everything else CQR-made.
            if (regName.startsWith("cqrepoured:staff")) {
                if (!EBW_LOADED || !cfg.staffSwapEnabled
                        || rand.nextInt(100) >= cfg.gearSwapPct) {
                    noMatch++;
                    return;
                }
                ItemStack wand = CqrWandSwapHelper.trySwapStaff(living, regName, rand, cfg);
                if (wand == null) {
                    // staff_gun and unresolvable pools land here — staff stays.
                    noMatch++;
                    return;
                }
                living.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, wand);
                swapped++;
                return;
            }

            Item newItem;
            if ("minecraft:bow".equals(regName)) {
                if (rand.nextInt(100) < cfg.gearSwapCrossbowPct) {
                    newItem = pickWeighted(rand, CROSSBOW_NAMES, CROSSBOW_WEIGHTS);
                } else {
                    newItem = pickWeighted(rand, BOW_NAMES, BOW_WEIGHTS);
                }
            } else {
                String[] catMat = VANILLA.get(regName);
                if (catMat == null) {
                    // CQR custom weapons and modded items — never touched by design.
                    noMatch++;
                    return;
                }
                newItem = pickMelee(rand, catMat[0], catMat[1], poolFor(living), cfg.gearSwapExoticPct);
            }
            if (newItem == null) {
                noMatch++;
                return;
            }
            if (rand.nextInt(100) >= cfg.gearSwapPct) {
                return;
            }

            ItemStack newStack = new ItemStack(newItem);
            if (held.getTagCompound() != null) {
                newStack.setTagCompound(held.getTagCompound().copy());
            }
            if (rand.nextInt(100) < cfg.gearSwapEnchantPct) {
                EnchantmentHelper.addRandomEnchantment(rand, newStack, 5 + rand.nextInt(11), false);
            }
            living.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, newStack);
            swapped++;
            // Offhand BEFORE the two-handed roll: a freshly granted shield must still be
            // subject to the shield-loss penalty.
            handleOffhandSwap(living, rand, cfg);
            handleTwoHandedShieldRoll(living, newItem, rand, cfg.twoHandedShieldLossPct,
                    cfg.debugLogging);
            if (cfg.debugLogging || swapped == 1L || swapped % 50L == 0L) {
                SrpWizCore.LOGGER.info(
                        "[srpwizcore] cqr gear swap: {} {} -> {} (swapped={} seen={} noMatch={})",
                        cls.substring(cls.lastIndexOf('.') + 1), regName,
                        newItem.getRegistryName(), Long.valueOf(swapped), Long.valueOf(seen),
                        Long.valueOf(noMatch));
            }
        } catch (Throwable t) {
            // Never let a cosmetic swap break a dungeon spawn.
            SrpWizCore.LOGGER.error("[srpwizcore] cqr gear swap failed: {}", t.toString());
        }
    }

    /**
     * Offhand consistency pass (user feedback 2026-08-01: pirates with a Spartan rapier
     * still held a vanilla diamond axe in the offhand — CQR dual-wield structure NBT). Only
     * items from the {@code VANILLA} map are touched: {@code offhandShieldPct} chance for a
     * plain shield, otherwise a Spartan weapon from the same per-entity pool as the
     * mainhand ("second rapier" for pirates). Enchantments carry over like on the mainhand.
     */
    private static void handleOffhandSwap(EntityLiving living, Random rand,
            CqrIntegrationCategory cfg) {
        if (!cfg.offhandSwapEnabled) {
            return;
        }
        ItemStack offhand = living.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);
        if (offhand.isEmpty() || offhand.getItem().getRegistryName() == null) {
            return;
        }
        String[] catMat = VANILLA.get(offhand.getItem().getRegistryName().toString());
        if (catMat == null) {
            return;
        }
        Item newItem;
        if (rand.nextInt(100) < cfg.offhandShieldPct) {
            newItem = Item.getByNameOrId("minecraft:shield");
        } else if (isTwoHanded(living.getHeldItemMainhand().getItem())) {
            // A two-handed mainhand never gets a second weapon (user feedback 2026-08-01:
            // walker with pike + halberd); the vanilla offhand weapon is traded for a
            // healing potion instead.
            newItem = null;
        } else {
            // The per-entity pools contain two-handed types (pike, halberd...) which are
            // fine for the mainhand but never valid as a second weapon — reroll a few
            // times, then fall back to the potion trade.
            newItem = null;
            for (int i = 0; i < 4; i++) {
                Item candidate = pickMelee(rand, catMat[0], catMat[1], poolFor(living),
                        cfg.gearSwapExoticPct);
                if (candidate != null && !isTwoHanded(candidate)) {
                    newItem = candidate;
                    break;
                }
            }
        }
        if (newItem == null) {
            living.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
            if (living instanceof AbstractEntityCQR) {
                AbstractEntityCQR cqr = (AbstractEntityCQR) living;
                cqr.setHealingPotions(cqr.getHealingPotions() + 1);
            }
            if (cfg.debugLogging) {
                SrpWizCore.LOGGER.info(
                        "[srpwizcore] cqr gear swap (offhand): {} -> +1 healing potion "
                                + "(two-handed rule)",
                        offhand.getItem().getRegistryName());
            }
            return;
        }
        ItemStack newStack = new ItemStack(newItem);
        if (offhand.getTagCompound() != null) {
            newStack.setTagCompound(offhand.getTagCompound().copy());
        }
        living.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, newStack);
        if (cfg.debugLogging) {
            SrpWizCore.LOGGER.info("[srpwizcore] cqr gear swap (offhand): {} -> {}",
                    offhand.getItem().getRegistryName(), newItem.getRegistryName());
        }
    }

    /**
     * Off-hand penalty for two-handed weapons (user request 2026-08-01): if the swapped
     * weapon carries Spartan's {@code two_handed} OR {@code versatile} property (versatile =
     * hand-and-a-half, e.g. longsword — counted per user intent) and the mob holds a shield
     * (vanilla {@code ItemShield}; CQR's {@code ItemShieldCQR} extends it), roll
     * {@code twoHandedShieldLossPct}: on loss the shield is removed and the mob gets +1 CQR
     * healing potion via {@code setHealingPotions} — the counter its drinking AI actually
     * uses, not a cosmetic off-hand item.
     */
    private static void handleTwoHandedShieldRoll(EntityLiving living, Item newItem,
            Random rand, int lossPct, boolean debug) {
        if (lossPct <= 0 || !isTwoHanded(newItem)) {
            return;
        }
        ItemStack offhand = living.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);
        if (offhand.isEmpty() || !(offhand.getItem() instanceof ItemShield)) {
            return;
        }
        if (rand.nextInt(100) >= lossPct) {
            return;
        }
        living.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (living instanceof AbstractEntityCQR) {
            AbstractEntityCQR cqr = (AbstractEntityCQR) living;
            cqr.setHealingPotions(cqr.getHealingPotions() + 1);
        }
        shieldsDropped++;
        if (debug || shieldsDropped == 1L) {
            SrpWizCore.LOGGER.info(
                    "[srpwizcore] cqr gear swap: two-handed {} -> shield dropped, +1 healing "
                            + "potion (total drops: {})",
                    newItem.getRegistryName(), Long.valueOf(shieldsDropped));
        }
    }

    /**
     * Spartan {@code two_handed} OR {@code versatile} (hand-and-a-half, e.g. longsword —
     * counted as two-handed per user intent 2026-08-01).
     */
    private static boolean isTwoHanded(Item item) {
        if (!(item instanceof IWeaponPropertyContainer)) {
            return false;
        }
        IWeaponPropertyContainer<?> weapon = (IWeaponPropertyContainer<?>) item;
        return weapon.getFirstWeaponPropertyWithType(
                WeaponProperties.PROPERTY_TYPE_TWO_HANDED) != null
                || weapon.getFirstWeaponPropertyWithType(
                        WeaponProperties.PROPERTY_TYPE_VERSATILE) != null;
    }

    private static String[] poolFor(EntityLiving living) {
        ResourceLocation key = EntityList.getKey(living);
        return key == null ? null : ENTITY_POOLS.get(key.getResourcePath());
    }

    private static Item pickMelee(Random rand, String category, String baseMaterial,
            String[] pool, int exoticPct) {
        String[] types = pool != null ? pool : ("axe".equals(category) ? AXE_TYPES : SWORD_TYPES);
        String material = baseMaterial;
        String[] exotic = EXOTIC.get(baseMaterial);
        if (exotic != null && rand.nextInt(100) < exoticPct) {
            material = exotic[rand.nextInt(exotic.length)];
        }
        // Up to 4 tries in case the rolled variant is disabled in Spartan's config; after the
        // first miss fall back to the base material (exotics are the usual absentees).
        for (int i = 0; i < 4; i++) {
            Item item = resolve(types[rand.nextInt(types.length)] + "_" + material);
            if (item != null) {
                return item;
            }
            material = baseMaterial;
        }
        return null;
    }

    private static Item pickWeighted(Random rand, String[] names, int[] weights) {
        int roll = rand.nextInt(100);
        int acc = 0;
        for (int i = 0; i < names.length; i++) {
            acc += weights[i];
            if (roll < acc) {
                Item item = resolve(names[i]);
                if (item != null) {
                    return item;
                }
                // Rolled variant disabled — fall through to the fallback below.
                break;
            }
        }
        return resolve(names[0]);
    }

    private static Item resolve(String path) {
        String full = "spartanweaponry:" + path;
        synchronized (ITEM_CACHE) {
            if (ITEM_CACHE.containsKey(full)) {
                return ITEM_CACHE.get(full);
            }
            Item item = Item.getByNameOrId(full);
            ITEM_CACHE.put(full, item);
            return item;
        }
    }
}
