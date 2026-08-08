package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;

import electroblob.wizardry.api.WizardryEnumHelper;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.spell.Spell;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

/**
 * Owns the Abomination element - the only place in this mod that touches Wizardry's Element enum.
 *
 * <p>The constant is appended to {@link Element} at runtime through Wizardry's own
 * {@code WizardryEnumHelper}, which wraps Forge's {@code EnumHelper.addEnum}. That has to happen
 * before anything snapshots {@code Element.values()} - notably {@code BlockCrystal}'s
 * {@code PropertyEnum.create}, which runs inside {@code RegistryEvent.Register<Block>}. Hence
 * {@link #init()} from the {@code @Mod} constructor.
 *
 * <p>If the reflective add ever fails, {@link #EXTENDED} goes false and {@link #ABOMINATION} becomes
 * {@link Element#MAGIC}. {@link #isAbomination(Spell)} then falls back to the registry-domain test
 * this mod used before the element existed, so degraded mode is exactly the old behaviour rather
 * than something new. A naive {@code getElement() == ABOMINATION} would, in that state, also match
 * Wizardry's own MAGIC spells and start blocking foreign magic on unadapted wands.
 */
public final class ModElements {

    /** The Abomination element, or {@link Element#MAGIC} if registration failed. */
    public static final Element ABOMINATION;

    /** True when the enum was really extended. False means every consumer must degrade. */
    public static final boolean EXTENDED;

    static {
        Element registered = null;
        try {
            registered = WizardryEnumHelper.addElement("ABOMINATION",
                    new Style().setColor(TextFormatting.RED),
                    "abomination",
                    InsaneTweaksMod.MODID);
        } catch (Throwable t) {
            InsaneTweaksMod.LOGGER.error("[InsaneTweaks] Could not add the Abomination element to "
                    + "Wizardry's Element enum. Falling back to MAGIC: spells keep working but show "
                    + "as 'None', and the casting gate reverts to a registry-domain check.", t);
        }

        EXTENDED = registered != null;
        ABOMINATION = EXTENDED ? registered : Element.MAGIC;

        if (EXTENDED) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Abomination element registered at ordinal {}. EXTENDED=true.",
                    Integer.valueOf(ABOMINATION.ordinal()));
        } else {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Abomination element NOT registered. EXTENDED=false.");
        }
    }

    private ModElements() {
    }

    /** No-op whose only job is to force this class's static initialiser at a chosen moment. */
    public static void init() {
    }

    /**
     * Whether the given spell belongs to this mod's magic.
     *
     * @param spell may be null
     */
    public static boolean isAbomination(Spell spell) {
        if (spell == null) {
            return false;
        }
        if (EXTENDED) {
            return spell.getElement() == ABOMINATION;
        }
        ResourceLocation id = spell.getRegistryName();
        return id != null && InsaneTweaksMod.MODID.equals(id.getResourceDomain());
    }
}
