package com.spege.reskilltweaks.skills;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import static com.spege.reskilltweaks.ReskillTweaks.LOGGER;

/**
 * Teaches Reskillable's native <b>Effect Twist</b> trait about PotionCore and Electroblob's
 * Wizardry.
 *
 * <h3>What the trait does</h3>
 * {@code TraitEffectTwist.onHurt}, dispatched by {@code PlayerData.hurt()} so the victim is always
 * a player: if the attacker is an {@code EntityLivingBase} <i>and</i> an {@code IMob}, it rolls
 * {@code world.rand.nextBoolean()} (50%), collects the victim's active effects that appear as a KEY
 * in its {@code badPotions} map, picks one at random, and applies the mapped counterpart to the
 * <b>attacker</b> for {@code 80 + rand(60)} ticks at amplifier 0.
 *
 * <p>Stock the map holds six vanilla pairs (speed, haste, strength, regeneration, night vision,
 * glowing), so in a modded pack the trait almost never has anything to mirror.
 *
 * <h3>Why reflection</h3>
 * {@code badPotions} is a private instance field on the trait singleton with no getter, setter,
 * registry or config - Reskillable exposes no extension point for it whatsoever. The singleton
 * itself is public though: it lives in {@code ReskillableRegistries.UNLOCKABLES}, and the field is
 * a plain mutable {@link java.util.HashMap}, so a one-off {@code put()} at postInit is enough. This
 * costs nothing at runtime (the trait reads the map, it never rebuilds it), touches no save data,
 * and survives a config reload. Reskillable is not obfuscated, so the field name is stable.
 *
 * <h3>Deliberately NOT changed</h3>
 * The 50% roll, the 4-7 second duration, the hardcoded amplifier 0 and the {@code IMob} gate all
 * live in the method body and would need a mixin on {@code onHurt}. The {@code IMob} gate is the
 * one with real gameplay weight: {@code EntityDragonBase extends EntityTameable} and does NOT
 * implement {@code IMob}, so Ice and Fire dragons can never be twisted. SRP parasites
 * ({@code EntityParasiteBase extends EntityMob}) and CQR mobs ({@code AbstractEntityCQR implements
 * IMob}) are both covered.
 *
 * <p>Note on tuning: the trait mirrors ONE randomly chosen matching buff. Every pair added here
 * makes the trait fire more often but makes any particular mirror less likely, so this list is
 * curated rather than exhaustive.
 */
public final class EffectTwistPairs {

    private EffectTwistPairs() {
    }

    private static final String TRAIT_ID = "reskillable:effect_twist";
    private static final String FIELD_NAME = "badPotions";

    /**
     * Flat {buff, debuff} pairs. Both ids are resolved from the potion registry and the pair is
     * skipped unless BOTH resolve - a null value here would be fatal, because the trait feeds
     * {@code badPotions.get(...)} straight into a {@code PotionEffect} constructor.
     */
    private static final String[][] PAIRS = {
            // --- PotionCore. Its effects come in designed opposites, which is exactly the shape
            // --- this trait wants. Verified against PotionCore 1.9: registry names below are the
            // --- strings its potion classes pass to PotionCorePotion(name, isBad, colour).
            { "potioncore:iron_skin", "potioncore:broken_armor" },
            { "potioncore:diamond_skin", "potioncore:vulnerable" },
            { "potioncore:magic_shield", "potioncore:broken_magic_shield" },
            { "potioncore:magic_focus", "potioncore:magic_inhibition" },
            { "potioncore:bless", "potioncore:curse" },
            { "potioncore:step_up", "potioncore:weight" },
            { "potioncore:climb", "potioncore:weight" },
            { "potioncore:flight", "potioncore:weight" },
            { "potioncore:slow_fall", "potioncore:launch" },
            { "potioncore:antidote", "potioncore:potion_sickness" },
            { "potioncore:archery", "potioncore:klutz" },
            { "potioncore:repair", "potioncore:rust" },

            // --- Electroblob's Wizardry. Picked for the battlemage identity: the buffs a caster
            // --- actually keeps up get mirrored as the counter-school debuff.
            { "ebwizardry:ironflesh", "ebwizardry:curse_of_enfeeblement" },
            { "ebwizardry:oakflesh", "ebwizardry:curse_of_enfeeblement" },
            { "ebwizardry:diamondflesh", "ebwizardry:decay" },
            { "ebwizardry:empowerment", "ebwizardry:curse_of_enfeeblement" },
            { "ebwizardry:font_of_mana", "ebwizardry:arcane_jammer" },
            { "ebwizardry:ice_shroud", "ebwizardry:frost" },
            { "ebwizardry:frost_step", "ebwizardry:frost" },
            { "ebwizardry:static_aura", "ebwizardry:paralysis" },
            { "ebwizardry:transience", "ebwizardry:containment" },
            { "ebwizardry:sixth_sense", "ebwizardry:mind_trick" },
            { "ebwizardry:ward", "ebwizardry:mark_of_sacrifice" } };

    /**
     * Called from postInit - late enough that every mod's potions and Reskillable's unlockables are
     * all registered. Every failure path is silent-but-logged: this is a nice-to-have on a native
     * trait, never a reason to break loading.
     */
    @SuppressWarnings("unchecked")
    public static void install() {
        if (!Loader.isModLoaded("reskillable")) {
            return;
        }
        if (!com.spege.reskilltweaks.config.ReskillTweaksConfig.modules.enableSkillsModule) {
            return;
        }

        try {
            Object trait = codersafterdark.reskillable.api.ReskillableRegistries.UNLOCKABLES
                    .getValue(new ResourceLocation(TRAIT_ID));
            if (trait == null) {
                LOGGER.info("[ReskillTweaks] Effect Twist not present, skipping pair injection.");
                return;
            }

            Field field = trait.getClass().getDeclaredField(FIELD_NAME);
            field.setAccessible(true);
            Object raw = field.get(trait);
            if (!(raw instanceof Map)) {
                LOGGER.warn("[ReskillTweaks] Effect Twist's " + FIELD_NAME
                        + " is not a Map - Reskillable changed, skipping pair injection.");
                return;
            }
            Map<Potion, Potion> badPotions = (Map<Potion, Potion>) raw;

            int added = 0;
            int skipped = 0;
            for (String[] pair : PAIRS) {
                Potion buff = ForgeRegistries.POTIONS.getValue(new ResourceLocation(pair[0]));
                Potion debuff = ForgeRegistries.POTIONS.getValue(new ResourceLocation(pair[1]));
                if (buff == null || debuff == null) {
                    skipped++; // mod absent, or it renamed the effect - both are fine
                    continue;
                }
                badPotions.put(buff, debuff);
                added++;
            }

            LOGGER.info("[ReskillTweaks] Effect Twist: added " + added + " effect pairs ("
                    + skipped + " skipped, mod absent), map now holds " + badPotions.size() + ".");
        } catch (NoSuchFieldException e) {
            LOGGER.warn("[ReskillTweaks] Effect Twist has no '" + FIELD_NAME
                    + "' field - Reskillable changed. Trait keeps its 6 vanilla pairs.");
        } catch (Exception e) {
            LOGGER.warn("[ReskillTweaks] Could not extend Effect Twist: " + e);
        }
    }
}
