package com.spege.reskilltweaks.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * The set of SRParasites / SRPExtra status effects that are genuinely <b>hostile to the
 * player wearing them</b>.
 *
 * <p>This is deliberately NOT the same list as {@code PotionCleanse.BUILT_IN_CLEANSED_EFFECTS}.
 * Cleanse strips every parasite-ecosystem effect off the player, including a few that
 * actually <i>benefit</i> the host. Anything that resists or shortens an incoming effect
 * must not touch those, or the feature would be sabotaging the player:
 *
 * <ul>
 *   <li>{@code srparasites:the_sign} — "Parasites will ignore their presence while the sign
 *       persists." A strong buff.</li>
 *   <li>{@code srparasites:parate} — raises the host's max health, armor and attack damage.</li>
 *   <li>{@code srparasites:muscleout} — greatly increases the host's outgoing damage.</li>
 *   <li>{@code srparasites:senses} — heightened perception / follow range; neutral on a player.</li>
 *   <li>{@code srparasites:debar} — suppresses <i>parasite</i> loot drops; inert on a player.</li>
 * </ul>
 *
 * <p>Classifications taken from SRP's own bestiary strings
 * ({@code assets/srparasites/lang/en_us.lang}, {@code bestiary.effect.*.desc}),
 * SRParasites 1.10.7 + SRPExtra 1.10.7.5.
 *
 * <p>Effects already excluded upstream by Cleanse as protective/mechanic
 * ({@code repel}, {@code antimall}, {@code rage}, {@code jugg}, {@code pivot}, {@code nexus},
 * the tier markers, {@code distorted_enlightenment}) are likewise absent here.
 */
public final class SrpEffectRegistry {

    private SrpEffectRegistry() {
    }

    /**
     * Hostile-to-host parasite effects. Shipped in code rather than as config defaults
     * because Forge {@code @Config} values already written to disk never pick up new
     * defaults — same reasoning as {@code PotionCleanse}. Extend at runtime through
     * {@code scarredFlesh.additionalHostileEffects}.
     */
    private static final String[] BUILT_IN_HOSTILE_EFFECTS = {
            // Conversion
            "srparasites:coth",
            // Damage over time
            "srparasites:bleed", "srparasites:corrosive", "srparasites:viral",
            "srparasites:conta", "srparasites:overheating", "srparasites:vomit",
            "srparasites:needler", "srparasites:thornshade_thorns",
            // Control loss
            "srparasites:fear", "srparasites:indeaf", "srparasites:novision",
            "srparasites:braining",
            // Marking / targeting
            "srparasites:prey", "srparasites:spotted", "srparasites:foster",
            "srparasites:link",
            // Effect manipulation
            "srparasites:effectpos", "srparasites:effectneg",
            // SRPExtra
            "srpextra:stung", "srpextra:confused" };

    /** Resolved on first use — registries are complete long before any effect is applied. */
    private static Set<Potion> hostileResolved;
    /** The extra ids the resolved set was built from, so a config edit re-resolves. */
    private static String[] resolvedExtras;

    /**
     * @param additional extra effect ids from config, may be null or empty
     * @return the hostile effect set; never null, possibly empty when SRP is absent
     */
    public static Set<Potion> getHostileEffects(String[] additional) {
        String[] extras = additional == null ? new String[0] : additional;
        if (hostileResolved == null || !java.util.Arrays.equals(extras, resolvedExtras)) {
            Set<Potion> resolved = new HashSet<>();
            for (String id : BUILT_IN_HOSTILE_EFFECTS) {
                addResolved(resolved, id);
            }
            for (String id : extras) {
                addResolved(resolved, id);
            }
            hostileResolved = resolved;
            resolvedExtras = extras.clone();
        }
        return hostileResolved;
    }

    private static void addResolved(Set<Potion> target, String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        Potion potion = ForgeRegistries.POTIONS.getValue(new ResourceLocation(id.trim()));
        if (potion != null) {
            target.add(potion);
        }
    }

    /** How many distinct hostile parasite effects are currently active on this entity. */
    public static int countActive(EntityLivingBase entity, Set<Potion> hostile) {
        if (hostile.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Potion potion : hostile) {
            if (entity.isPotionActive(potion)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Total <b>displayed level</b> of the hostile parasite effects currently on this entity —
     * that is, the sum of {@code amplifier + 1}, so Viral VII counts as 7 rather than 6. This is
     * the unit Scarred Flesh budgets in, chosen because it is the number the player actually sees
     * on their status bar.
     *
     * @param exclude effect to leave out of the sum, or {@code null} for none. Callers evaluating
     *        an incoming effect pass its own potion here: refreshing an affliction that is already
     *        active must be measured against the <i>others</i>, otherwise the effect competes with
     *        itself and every refresh gets throttled.
     */
    public static int sumActiveLevels(EntityLivingBase entity, Set<Potion> hostile, Potion exclude) {
        if (hostile.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Potion potion : hostile) {
            if (potion == exclude) {
                continue;
            }
            PotionEffect active = entity.getActivePotionEffect(potion);
            if (active != null) {
                total += active.getAmplifier() + 1;
            }
        }
        return total;
    }

    /** Read-only view of the built-in ids, for diagnostics and the {@code /itweaks} command. */
    public static List<String> getBuiltInIds() {
        return Collections.unmodifiableList(new ArrayList<>(java.util.Arrays.asList(BUILT_IN_HOSTILE_EFFECTS)));
    }
}
