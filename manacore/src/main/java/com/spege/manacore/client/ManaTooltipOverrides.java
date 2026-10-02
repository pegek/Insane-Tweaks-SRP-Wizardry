package com.spege.manacore.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.Wizardry;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Rewrites the descriptions of items whose function ManaCore changed, so the tooltip describes
 * what the item now does rather than what it did with wand mana.
 *
 * <p>Done on the tooltip, not by shipping lang entries under the other mods' domains: which of
 * several jars' {@code lang/en_us.lang} wins for one key depends on resource-pack order, and
 * SpellBundle already ships its own {@code ebwizardry} lang overriding {@code storage_upgrade}.
 *
 * <p>Both EBW and Ancient Spellcraft add an item's description through
 * {@code Wizardry.proxy.addMultiLineDescription(tooltip, "item.<id>.desc")}, which may wrap it
 * across several lines. So the original is rebuilt with the same call and located as a run of
 * lines, then replaced by ours, built the same way. If the run is not found - another mod changed
 * the text, or the item does not show its description in this context - the tooltip is left as
 * it is rather than guessed at.
 */
@SideOnly(Side.CLIENT)
public class ManaTooltipOverrides {

    private static final String[] MANA_RESERVES = {
            "ancientspellcraft:ring_mana_lesser",
            "ancientspellcraft:ring_mana_greater",
            "ancientspellcraft:charm_majestic_mana"
    };
    private static final String HUNGER_CASTING = "ebwizardry:charm_hunger_casting";
    private static final String STORAGE_UPGRADE = "ebwizardry:storage_upgrade";

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (!ManaCoreConfig.ebw.enabled) {
            return;
        }
        Item item = event.getItemStack().getItem();
        ResourceLocation name = item.getRegistryName();
        if (name == null) {
            return;
        }
        String id = name.toString();

        if (ManaCoreConfig.ebw.fuelEnabled && isReserve(id)) {
            replace(event.getToolTip(), id, "manacore.desc.mana_reserve",
                    format(ManaCoreConfig.ebw.fuelArtefactManaPerPoolMana));
        } else if (HUNGER_CASTING.equals(id)) {
            // Rewritten even with fuel off: the upstream trigger is blinded either way, so the
            // original text would describe something that cannot happen.
            replace(event.getToolTip(), id, ManaCoreConfig.ebw.fuelEnabled
                    ? "manacore.desc.charm_hunger_casting" : "manacore.desc.disabled",
                    format(ManaCoreConfig.ebw.hungerManaPerPoint));
        } else if (STORAGE_UPGRADE.equals(id) && ManaCoreConfig.ebw.storageBonusPerLevel > 0.0D) {
            replace(event.getToolTip(), id, "manacore.desc.storage_upgrade",
                    format(ManaCoreConfig.ebw.storageBonusPerLevel),
                    format(ManaCoreConfig.ebw.storageFillFraction * 100.0D),
                    Integer.valueOf(ManaCoreConfig.ebw.storageFillCooldownSeconds));
        }
    }

    private static boolean isReserve(String id) {
        for (String reserve : MANA_RESERVES) {
            if (reserve.equals(id)) {
                return true;
            }
        }
        // A pack may list further reserves in the config; describe those too.
        for (String reserve : ManaCoreConfig.ebw.fuelArtefacts) {
            if (reserve.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void replace(List<String> tooltip, String id, String key, Object... args) {
        List<String> original = new ArrayList<String>();
        Wizardry.proxy.addMultiLineDescription(original, "item." + id + ".desc");
        if (original.isEmpty()) {
            return;
        }
        int at = Collections.indexOfSubList(tooltip, original);
        if (at < 0) {
            return;
        }
        List<String> ours = new ArrayList<String>();
        Wizardry.proxy.addMultiLineDescription(ours, key, args);
        for (int i = 0; i < original.size(); i++) {
            tooltip.remove(at);
        }
        tooltip.addAll(at, ours);
    }

    /** 5.0 as "5", 0.25 as "0.25" - config values read better without a trailing ".0". */
    private static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E9D) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
