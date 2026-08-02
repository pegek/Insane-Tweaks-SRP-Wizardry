package com.spege.srpwizcore.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.categories.CqrIntegrationCategory;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.WandHelper;

import net.minecraft.entity.EntityLiving;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import team.cqr.cqrepoured.entity.bases.AbstractEntityCQR;

/**
 * CQR staff → EBW wand swap (plan {@code notes/cqr_ebw_wand_swap_plan_2026-08-01.md}).
 * ALL code touching Electroblob's Wizardry classes lives in this one class —
 * {@code CqrGearSwapHandler} calls it only behind a {@code Loader.isModLoaded} check, so a
 * pack without EBW never classloads it.
 *
 * <p>Zero mixins involved: CQR's own {@code integration.ebwizardry.EntityAICastSpell}
 * (task ~11 in every AbstractEntityCQR when wizardry is loaded) reads spells straight from
 * the HELD wand's NBT ({@code WandHelper.getSpells}), randomly cycles castable ones
 * (IntArrays.shuffle + selectSpell — verified in bytecode), handles continuous spells,
 * per-wand cooldowns and NPC cast packets. We only have to hand the mob a wand with spells
 * bound — which doubles as meaningful loot: the player gets exactly the wand the mob cast
 * with.
 *
 * <p>Healers ({@code staff_healing}) additionally lose their default healing potions
 * (user decision 2026-08-01) — heal_ally IS their sustain now.
 */
public final class CqrWandSwapHelper {

    private static final class Loadout {
        final String wandSuffix;
        final String guaranteed;
        final String[] pool;

        Loadout(String wandSuffix, String guaranteed, String... pool) {
            this.wandSuffix = wandSuffix;
            this.guaranteed = guaranteed;
            this.pool = pool;
        }
    }

    /** CQR staff registry name -> wand element + spell pool. staff_gun is deliberately
     * ABSENT — it is a firearm working through IRangedWeapon and stays untouched. */
    private static final Map<String, Loadout> STAFF_MAP = new HashMap<>();

    static {
        STAFF_MAP.put("cqrepoured:staff_healing", new Loadout("healing_wand", "heal_ally",
                "oakflesh", "invigorating_presence", "agility", "shield"));
        STAFF_MAP.put("cqrepoured:staff_fire", new Loadout("fire_wand", null,
                "fireball", "firebolt", "flame_ray", "firebomb"));
        STAFF_MAP.put("cqrepoured:staff_thunder", new Loadout("lightning_wand", null,
                "arc", "thunderbolt", "homing_spark", "spark_bomb"));
        // decay removed from BOTH necromancy pools (user decision 2026-08-01: lingering
        // ground damage on necromancers is off the table).
        STAFF_MAP.put("cqrepoured:staff_poison", new Loadout("necromancy_wand", null,
                "poison", "poison_bomb", "snare"));
        STAFF_MAP.put("cqrepoured:staff_vampiric", new Loadout("necromancy_wand", null,
                "life_drain", "wither", "darkness_orb"));
        STAFF_MAP.put("cqrepoured:staff_wind", new Loadout("sorcery_wand", null,
                "whirlwind", "tornado", "force_orb", "blink"));
        STAFF_MAP.put("cqrepoured:staff_spider", new Loadout("earth_wand", null,
                "spider_swarm", "snare", "poison", "cobwebs"));
        STAFF_MAP.put("cqrepoured:staff", new Loadout("wand", null,
                "magic_missile", "force_arrow", "dart", "snare"));
    }

    public static long wandsHanded;

    private CqrWandSwapHelper() {
    }

    /**
     * Builds the replacement wand for the given staff, or {@code null} when the staff has
     * no mapping (staff_gun), the wand item is missing, or no pool spell resolves. Also
     * applies the healer potion-zeroing side effect on success.
     */
    public static ItemStack trySwapStaff(EntityLiving living, String staffRegName,
            Random rand, CqrIntegrationCategory cfg) {
        Loadout loadout = STAFF_MAP.get(staffRegName);
        if (loadout == null) {
            return null;
        }
        Item wandItem = resolveWand(cfg.staffWandTier, loadout.wandSuffix);
        if (wandItem == null) {
            return null;
        }
        List<Spell> spells = pickSpells(loadout, rand, Math.max(1, cfg.staffSpellsPerMob));
        if (spells.isEmpty()) {
            return null;
        }
        ItemStack wand = new ItemStack(wandItem);
        WandHelper.setSpells(wand, spells.toArray(new Spell[0]));

        if ("cqrepoured:staff_healing".equals(staffRegName)
                && living instanceof AbstractEntityCQR) {
            // heal_ally replaces the potion sustain entirely (user decision 2026-08-01).
            ((AbstractEntityCQR) living).setHealingPotions(0);
        }
        wandsHanded++;
        if (cfg.debugLogging || wandsHanded == 1L) {
            StringBuilder names = new StringBuilder();
            for (Spell s : spells) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(s.getRegistryName());
            }
            SrpWizCore.LOGGER.info("[srpwizcore] cqr wand swap #{}: {} -> {} [{}]",
                    Long.valueOf(wandsHanded), staffRegName, wandItem.getRegistryName(),
                    names);
        }
        return wand;
    }

    private static Item resolveWand(String tierCfg, String suffix) {
        String tier = tierCfg == null ? "apprentice" : tierCfg.toLowerCase().trim();
        if (!tier.equals("novice") && !tier.equals("apprentice") && !tier.equals("advanced")
                && !tier.equals("master")) {
            tier = "apprentice";
        }
        // The novice element-less wand is called magic_wand, not novice_wand.
        String id = (tier.equals("novice") && suffix.equals("wand"))
                ? "ebwizardry:magic_wand"
                : "ebwizardry:" + tier + "_" + suffix;
        Item item = Item.getByNameOrId(id);
        if (item == null) {
            SrpWizCore.LOGGER.warn("[srpwizcore] cqr wand swap: wand '{}' not found", id);
        }
        return item;
    }

    private static List<Spell> pickSpells(Loadout loadout, Random rand, int count) {
        List<Spell> result = new ArrayList<>(count);
        if (loadout.guaranteed != null) {
            addSpell(result, loadout.guaranteed);
        }
        // Fisher-Yates over a pool copy, then take until the loadout is full.
        String[] pool = loadout.pool.clone();
        for (int i = pool.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            String tmp = pool[i];
            pool[i] = pool[j];
            pool[j] = tmp;
        }
        for (int i = 0; i < pool.length && result.size() < count; i++) {
            addSpell(result, pool[i]);
        }
        return result;
    }

    private static void addSpell(List<Spell> result, String name) {
        Spell spell = Spell.get(name);
        if (spell == null) {
            SrpWizCore.LOGGER.warn("[srpwizcore] cqr wand swap: unknown spell '{}'", name);
            return;
        }
        if (!result.contains(spell)) {
            result.add(spell);
        }
    }
}
