package com.spege.insanetweaks.config;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.categories.ArcaneSunderingCategory;
import com.spege.insanetweaks.config.categories.AutoLockPickerCategory;
import com.spege.insanetweaks.config.categories.BaubleFruitsCategory;
import com.spege.insanetweaks.config.categories.ChargeJumpCategory;
import com.spege.insanetweaks.config.categories.ClientCategory;
import com.spege.insanetweaks.config.categories.EnchantmentsCategory;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.config.categories.GearCategory;
import com.spege.insanetweaks.config.categories.PropertyBooksCategory;
import com.spege.insanetweaks.config.categories.InteractionsCategory;
import com.spege.insanetweaks.config.categories.ModulesCategory;
import com.spege.insanetweaks.config.categories.SanctuaryCategory;
import com.spege.insanetweaks.config.categories.ScarredFleshCategory;
import com.spege.insanetweaks.config.categories.SanctuaryCostCategory;
import com.spege.insanetweaks.config.categories.ThrallCategory;
import com.spege.insanetweaks.config.categories.TombstoneCategory;
import com.spege.insanetweaks.config.categories.TraitsCategory;
import com.spege.insanetweaks.config.categories.TweaksCategory;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = InsaneTweaksMod.MODID, name = "insanetweaks", category = "")
public class ModConfig {

    @Config.Name("modules")
    @Config.LangKey("config.insanetweaks.category.modules")
    @Config.Comment("Toggle main systems and cross-mod integrations.")
    public static final ModulesCategory modules = new ModulesCategory();

    @Config.Name("tweaks")
    @Config.LangKey("config.insanetweaks.category.tweaks")
    @Config.Comment("Specific bugfixes and mechanic alterations.")
    public static final TweaksCategory tweaks = new TweaksCategory();

    @Config.Name("baubleFruits")
    @Config.LangKey("config.insanetweaks.category.baubleFruits")
    @Config.Comment("Bauble Fruits: consumption cap, tooltip counter, and the corrupted seed/sapling/fruit loop.")
    public static final BaubleFruitsCategory baubleFruits = new BaubleFruitsCategory();

    @Config.Name("gear")
    @Config.LangKey("config.insanetweaks.category.gear")
    @Config.Comment("The Living/Sentient gear line: Spellblades, Aegis shields, armour sets, wands, their advanced properties, and which of them are obtainable.")
    public static final GearCategory gear = new GearCategory();

    @Config.Name("interactions")
    @Config.LangKey("config.insanetweaks.category.interactions")
    @Config.Comment("Master switches for cross-mod interactions that depend on optional mods.")
    public static final InteractionsCategory interactions = new InteractionsCategory();

    @Config.Name("traits")
    @Config.LangKey("config.insanetweaks.category.traits")
    @Config.Comment("SP costs, parent trees and requirements for custom Reskillable traits.")
    public static final TraitsCategory traits = new TraitsCategory();

    @Config.Name("tombstone")
    @Config.LangKey("config.insanetweaks.category.tombstone")
    @Config.Comment("Bugfixes and mechanic alterations for Corail Tombstone.")
    public static final TombstoneCategory tombstone = new TombstoneCategory();

    @Config.Name("thrall")
    @Config.LangKey("config.insanetweaks.category.thrall")
    @Config.Comment("The Thrall companion entity: slots, work modes, AI tunables.")
    public static final ThrallCategory thrall = new ThrallCategory();

    @Config.Name("entities")
    @Config.LangKey("config.insanetweaks.category.entities")
    @Config.Comment("Custom entities added by the mod.")
    public static final EntitiesCategory entities = new EntitiesCategory();

    @Config.Name("client")
    @Config.LangKey("config.insanetweaks.category.client")
    @Config.Comment("Visual toggles and debugging tools.")
    public static final ClientCategory client = new ClientCategory();

    @Config.Name("sanctuary")
    @Config.LangKey("config.insanetweaks.category.sanctuary")
    @Config.Comment("Sanctuary Dome tunables (radius tiers, fuel, cleanse, dimension blacklist). Master toggle is modules.enableSanctuary.")
    public static final SanctuaryCategory sanctuary = new SanctuaryCategory();

    @Config.Name("sanctuaryCost")
    @Config.LangKey("config.insanetweaks.category.sanctuaryCost")
    @Config.Comment("Sanctuary 'Cost of Power': presence tax, mana-fuel upkeep, drain escalation, upgrade slots.")
    public static final SanctuaryCostCategory sanctuaryCost = new SanctuaryCostCategory();

    @Config.Name("enchantments")
    @Config.LangKey("config.insanetweaks.category.enchantments")
    @Config.Comment("Tunables for the mod's custom enchantments (Sentient Codex, Swift Picking, Mmmm).")
    public static final EnchantmentsCategory enchantments = new EnchantmentsCategory();

    @Config.Name("chargeJump")
    @Config.LangKey("config.insanetweaks.category.chargeJump")
    @Config.Comment("Coiled Spring trait: charge time, leap power and fall-damage waiver.")
    public static final ChargeJumpCategory chargeJump = new ChargeJumpCategory();

    @Config.Name("scarredFlesh")
    @Config.LangKey("config.insanetweaks.category.scarredFlesh")
    @Config.Comment("Scarred Flesh trait: the total parasite-affliction level budget.")
    public static final ScarredFleshCategory scarredFlesh = new ScarredFleshCategory();

    @Config.Name("propertyBooks")
    @Config.LangKey("config.insanetweaks.category.propertyBooks")
    @Config.Comment("Property Books: anvil cost and the Grip recovery fallback. Master toggle is modules.enablePropertyBooks.")
    public static final PropertyBooksCategory propertyBooks = new PropertyBooksCategory();

    @Config.Name("arcaneSundering")
    @Config.LangKey("config.insanetweaks.category.arcaneSundering")
    @Config.Comment("Arcane Sundering: the true-damage and physical-to-magic shares taken out of each melee hit.")
    public static final ArcaneSunderingCategory arcaneSundering = new ArcaneSunderingCategory();

    @Config.Name("autoLockPicker")
    @Config.LangKey("config.insanetweaks.category.autoLockPicker")
    @Config.Comment("Auto Lock Picker (Locks integration): channel time, durability cost, Complexity/Sturdy/Shocking handling. Master toggle is modules.enableAutoLockPicker.")
    public static final AutoLockPickerCategory autoLockPicker = new AutoLockPickerCategory();

    @Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(InsaneTweaksMod.MODID)) {
                ConfigManager.sync(InsaneTweaksMod.MODID, Config.Type.INSTANCE);
                // The sim_wizard spell-role map is derived once per spell and cached; drop it so
                // edits to entities.assimilated_wizard.spells.spellRoles apply without a restart.
                com.spege.insanetweaks.entities.ai.SpellRoleResolver.invalidateCache();
                com.spege.insanetweaks.util.SrpWizardryAssimilationHelper.invalidateCache();
                // Tombstone's effect whitelist is parsed from registry names once and cached; drop
                // it so edits to tombstone.effectpools apply without a restart.
                com.spege.insanetweaks.tombstone.effects.EffectPoolRegistry.invalidate();
            }
        }
    }
}
