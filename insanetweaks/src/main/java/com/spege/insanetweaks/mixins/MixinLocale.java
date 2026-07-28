package com.spege.insanetweaks.mixins;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;

@Mixin(Locale.class)
public class MixinLocale {

    /**
     * Enigmatic Legacy's own {@code en_us.lang} ships this key with a stray {@code %} followed by
     * a CJK character where a placeholder was meant to be. {@code Locale.formatMessage} runs every
     * value through {@code String.format}, so it throws and the Ring of the Seven Curses tooltip
     * renders as "Format error: ..." with the number stranded after the full stop.
     */
    private static final String EL_CURSED_RING_PAIN_KEY = "tooltip.enigmaticlegacy.cursedRing4_alt";

    /**
     * Replacement for the above. Enigmatic Legacy calls the key with <em>zero</em> arguments and
     * appends the value itself ({@code GOLD + painMultiplier * 100 + "%"}), so the repaired string
     * must carry no placeholder at all and must end open — the number always lands last, which is
     * why this is a rewording rather than a copy of EL's original sentence.
     */
    private static final String EL_CURSED_RING_PAIN_FIX =
            "§5- Damage you take from §6ANY§5 source is multiplied to ";

    // Shadow po nazwie SRG bezpośrednio (remap=false, zero aliasów) — Mixin 0.8.7 / CleanMix 0.6.6+
    // zabrania aliasowania pól non-private, a Locale.properties (field_135032_a) jest package-private.
    @Shadow(remap = false)
    Map<String, String> field_135032_a;

    @Inject(method = { "loadLocaleDataFiles", "func_135022_a" }, at = @At("RETURN"), remap = false)
    private void insanetweaks_overwriteGoldenOsmosis(IResourceManager resourceManager, List<String> languageList, CallbackInfo ci) {
        if (this.field_135032_a != null) {
            String[] nativeSkillsToOverwrite = new String[] {
                "golden_osmosis",
                "safe_port"
            };

            for (String skill : nativeSkillsToOverwrite) {
                String newName = this.field_135032_a.get("reskillable.unlock.compatskills." + skill);
                String newDesc = this.field_135032_a.get("reskillable.unlock.compatskills." + skill + ".desc");

                // Hard overwrite natywnych wpisów Reskillable w słowniku gry
                if (newName != null) {
                    this.field_135032_a.put("reskillable.unlock.reskillable." + skill, newName);
                }
                if (newDesc != null) {
                    this.field_135032_a.put("reskillable.unlock.reskillable." + skill + ".desc", newDesc);
                }
            }

            insanetweaks_repairEnigmaticCurseTooltip();
        }
    }

    /**
     * Repairs Enigmatic Legacy's malformed Ring of the Seven Curses line, in place, in the same
     * dictionary the Reskillable overwrite above uses. Shipping a competing lang file would not
     * work: whichever resource domain loads last wins, and that order is not ours to control.
     *
     * <p>The test is the exact condition the game itself hits — {@code Locale.formatMessage} calls
     * {@code String.format} on every value, so a value that throws with no arguments is precisely
     * a value that will render as "Format error:". Scoped to this one key on purpose: this repairs
     * a known typo, it is not licence to rewrite arbitrary strings from other mods. Absent
     * Enigmatic Legacy the key does not exist and nothing happens, so no mod check is needed.
     */
    private void insanetweaks_repairEnigmaticCurseTooltip() {
        String broken = this.field_135032_a.get(EL_CURSED_RING_PAIN_KEY);
        if (broken == null) {
            return;
        }

        try {
            String.format(broken);
            return; // Formats cleanly — either EL fixed it or a translation supplies a sane value.
        } catch (IllegalFormatException expected) {
            this.field_135032_a.put(EL_CURSED_RING_PAIN_KEY, EL_CURSED_RING_PAIN_FIX);
        }

        org.apache.logging.log4j.LogManager.getLogger("insanetweaks").info(
                "[InsaneTweaks] Repaired malformed Enigmatic Legacy lang key '{}' "
                        + "(it would have rendered as \"Format error:\" on the Ring of the Seven Curses).",
                EL_CURSED_RING_PAIN_KEY);
    }
}
