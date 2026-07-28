package com.spege.insanetweaks.api;

import com.spege.insanetweaks.util.PropertyDescriptions;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * The advanced-property registry: named traits an item can carry, shown under "Properties:" in its
 * tooltip.
 *
 * <p>A property can reach a stack three different ways, and {@code AdvPropertyResolver} is what
 * unions them: from the {@code Item} class via {@link ITweaksPropertyHolder}, from the stack's own
 * NBT via {@code AdvPropertyStorage} (Property Books), or from an enchantment (Sentient Codex
 * confers Ashen Legacy). Consumers should always ask the resolver rather than testing the interface
 * directly, or they will silently miss the last two.
 */
public class AdvPropertyRegistry {

    /**
     * Decides whether a property may be applied to a given stack. Used by the Property Book anvil
     * recipe; properties an item declares from its own class are not filtered by this.
     *
     * <p>A named interface rather than a lambda so implementations can live together in
     * {@code util.PropertyApplicability} and carry their own javadoc - the reasoning behind
     * "what counts as a weapon" is worth more than the two lines it takes to express.
     */
    public interface Applicability {
        boolean test(ItemStack stack);
    }

    /** Accepts anything. The default for properties that are not book-grantable. */
    public static final Applicability ANY = new Applicability() {
        @Override
        public boolean test(ItemStack stack) {
            return true;
        }
    };

    public static class Property {
        public final String id;
        public final String displayName;
        public final TextFormatting color;
        /** Whether a Property Book exists that grants this. */
        public final boolean bookGrantable;
        /** What such a book may be applied to. Ignored when {@link #bookGrantable} is false. */
        public final Applicability applicability;

        public Property(String id, String displayName, TextFormatting color) {
            this(id, displayName, color, false, ANY);
        }

        public Property(String id, String displayName, TextFormatting color, boolean bookGrantable,
                Applicability applicability) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.bookGrantable = bookGrantable;
            this.applicability = applicability == null ? ANY : applicability;
        }

        /** @return true when a Property Book for this may be applied to {@code stack}. */
        public boolean canApplyTo(ItemStack stack) {
            return this.bookGrantable && stack != null && !stack.isEmpty()
                    && this.applicability.test(stack);
        }

        public void addTooltipLines(List<String> tooltip, boolean isShiftPressed) {
            tooltip.add(this.color + "- " + this.displayName);
            if (isShiftPressed) {
                String desc = PropertyDescriptions.getDescription(this.id);
                if (desc != null) {
                    tooltip.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " + desc);
                }
            }
        }
    }

    /** Insertion-ordered so Property Books appear in JEI in a stable, declared order. */
    private static final Map<String, Property> REGISTRY = new LinkedHashMap<>();

    public static final String ASHEN_LEGACY = "ashen_legacy";
    public static final String ARMOR_LAST_STAND = "armor_last_stand";
    public static final String ETHEREAL_SHELL = "ethereal_shell";
    public static final String ETHEREAL_SHELL_AWAKENED = "ethereal_shell_awakened";
    public static final String GRIP = "grip";

    static {
        // Book-grantable. Ashen Legacy hardens the dropped item against fire/lava/explosions
        // (IndestructibleDropHandler -> EntityItemIndestructible), so it is restricted to gear
        // rather than letting a stack of dirt become indestructible.
        register(new Property(ASHEN_LEGACY, "Ashen Legacy", TextFormatting.GOLD,
                true, com.spege.insanetweaks.util.PropertyApplicability.GEAR));
        // Deliberately displayed as "Fleshbound", in DARK_RED, with Fleshbound's own description:
        // this IS that mechanic, granted per stack instead of earned by a Sentient Spellblade, and
        // showing it under a second name would read as a second, different effect. The registry id
        // stays "grip" because it is written into stack NBT and into quest commands - renaming it
        // would orphan every book and every grant already in a world.
        register(new Property(GRIP, "Fleshbound", TextFormatting.DARK_RED,
                true, com.spege.insanetweaks.util.PropertyApplicability.WEAPON));

        // Display-only: these are granted by the armour classes themselves and have no book.
        register(new Property(ARMOR_LAST_STAND, "Grave Defiance", TextFormatting.GOLD));
        register(new Property(ETHEREAL_SHELL, "Veil of Stasis", TextFormatting.DARK_GRAY));
        register(new Property(ETHEREAL_SHELL_AWAKENED, "Awakened Veil of Stasis",
                TextFormatting.LIGHT_PURPLE));
    }

    /** Adds a property. Re-registering an id replaces the previous entry. */
    public static void register(Property property) {
        if (property != null && property.id != null) {
            REGISTRY.put(property.id, property);
        }
    }

    public static Property getProperty(String id) {
        return REGISTRY.get(id);
    }

    /** Every registered property, in declaration order. */
    public static Collection<Property> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
