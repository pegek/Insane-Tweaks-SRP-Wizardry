package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;

import electroblob.wizardry.api.WizardryEnumHelper;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.spell.Spell;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;

/**
 * Owns the Abomination element - the only place in this mod that touches Wizardry's Element enum.
 *
 * <p>The constant is appended to {@link Element} at runtime through Wizardry's own
 * {@code WizardryEnumHelper}, which wraps Forge's {@code EnumHelper.addEnum}. That has to happen
 * before anything snapshots {@code Element.values()} - notably {@code BlockCrystal}'s
 * {@code PropertyEnum.create}, which runs inside {@code RegistryEvent.Register<Block>}. Hence
 * {@link #init()} from the {@code @Mod} constructor.
 *
 * <p>If the reflective add ever fails, {@link #EXTENDED} goes false and {@link #ABOMINATION} stays
 * null. {@link #isAbomination(Spell)} then falls back to the same registry-domain test this mod used
 * before the element existed. A naive {@code getElement() == ABOMINATION} would, in that state, also
 * match Wizardry's own MAGIC spells and start blocking foreign magic on unadapted wands - which is
 * exactly why the field is null instead of a fallback to {@link Element#MAGIC}: a non-null impostor
 * would be silently stored or dereferenced by later code, and this class has no way to stop that.
 *
 * <p>Reading {@code InsaneTweaksMod.LOGGER} from this class's static initialiser is safe only because
 * {@code InsaneTweaksMod}'s own static initialisers never reach back into {@code init/}. {@code MODID}
 * is a compile-time constant and gets folded in, so referencing it triggers nothing; {@code LOGGER} is
 * a real {@code getstatic} and is guaranteed assigned before this runs, because the JVM completes
 * {@code InsaneTweaksMod.<clinit>} before any code that reads {@code LOGGER} can execute. If a future
 * static field on {@code InsaneTweaksMod} ever touched this class transitively, that cycle would leave
 * {@code LOGGER} null here and surface as an {@code ExceptionInInitializerError} during mod
 * construction with no obvious link back to this file.
 */
public final class ModElements {

    /**
     * The Abomination element, or <b>null</b> when registration failed - see {@link #EXTENDED}.
     *
     * <p>Deliberately null rather than a fallback to {@link Element#MAGIC}: a non-null impostor would
     * be stored or dereferenced by later code and silently behave as Wizardry's own elementless
     * MAGIC, which reads as a content bug rather than as the degraded mode it is. Never dereference
     * this without checking {@link #EXTENDED}; for the common question, call
     * {@link #isAbomination(Spell)}, which is safe in both states.
     */
    @Nullable
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
                    + "Wizardry's Element enum. Spell JSONs still declare \"element\": \"abomination\"; "
                    + "MixinElementFromName maps that to MAGIC so the spells still load, but they will "
                    + "show as 'None' and the casting gate falls back to a registry-domain check. If "
                    + "that mixin also failed to apply, the fourteen spells will load with no "
                    + "properties at all - check for \"Parsing error loading spell property file\" "
                    + "in the log.", t);
        }

        EXTENDED = registered != null;
        ABOMINATION = registered;

        if (EXTENDED) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Abomination element registered at ordinal {}. EXTENDED=true.",
                    Integer.valueOf(registered.ordinal()));
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
     * Flash, particle and fade colours for an Abomination receptacle, in EBW's own
     * {@code {flash, particle, fade}} order. Deep red to match the element's {@code TextFormatting.RED}.
     */
    private static final int[] RECEPTACLE_COLOURS = { 0xD42A2A, 0xFF9090, 0x6E0B0B };

    /**
     * Adds Abomination to {@code BlockReceptacle.PARTICLE_COLOURS}.
     *
     * <p>Without this, {@code randomDisplayTick} NPEs on the client the moment Abomination dust
     * sits in a receptacle: it calls {@code PARTICLE_COLOURS.get(element)} and dereferences the
     * result unchecked.
     *
     * <p>No mixin is involved and none is needed. The field is {@code public static final}, but
     * only the <em>reference</em> is final - the map itself is a mutable {@code EnumMap}. The
     * ordering works out because {@code EnumMap} captures its key universe from
     * {@code Element.class.getEnumConstants()} at construction, Forge's {@code EnumHelper.addEnum}
     * clears that cache when it appends a constant, and we append from the {@code @Mod}
     * constructor - long before {@code BlockReceptacle.<clinit>} runs during block registration.
     *
     * <p>Call from the FML init phase. Calling it earlier would force
     * {@code BlockReceptacle.<clinit>} before EBW is ready.
     */
    public static void installReceptacleColours() {
        if (!EXTENDED) {
            return;
        }
        electroblob.wizardry.block.BlockReceptacle.PARTICLE_COLOURS.put(ABOMINATION, RECEPTACLE_COLOURS);
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Abomination receptacle particle colours installed.");
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
            // Narrow window: Spell.getElement() reports MAGIC until SpellProperties.init() has run
            // for that spell, so between class load and that call this branch answers false for our
            // own spells where the fallback below would answer true - and permanently for any spell
            // whose properties file fails to load, which EBW reports as an error of its own.
            // Deliberately not ORing in the domain test to cover it - that would permanently
            // re-couple this predicate to our registry domain, which is the coupling this whole
            // change exists to remove. The window is unreachable in practice: SpellProperties.init()
            // runs in EBW's FMLInitializationEvent, long before anything calls isAbomination.
            return spell.getElement() == ABOMINATION;
        }
        // The registry-domain test this mod used before the element existed.
        ResourceLocation id = spell.getRegistryName();
        return id != null && InsaneTweaksMod.MODID.equals(id.getResourceDomain());
    }
}
