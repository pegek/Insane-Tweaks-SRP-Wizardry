package com.spege.srpwizmixins.mixins;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.world.SRPWorldData;
import com.dhanantry.scapeandrunparasites.world.SRPWorldEntitySpawner;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

/**
 * Fix - a dimension's meteor/origin trigger flag is saved from the wrong variable, so it is lost on
 * every reload.
 *
 * <p>{@code SRPWorldData} keeps one boolean per dimension, {@code dimMeteor}, reachable through
 * {@code getTriggerMet()}. It is computed once, in {@code SRPWorldData.create}, and it is where the
 * {@code Meteor Blacklisted Dimensions} decision actually lands:
 *
 * <pre>
 * boolean ok = true;
 * for (int dim : SRPConfigWorld.meteorBlacklistDims)
 *     if (dim == world.provider.getDimension()) { ok = false; break; }
 * instance.setTriggerMet(ok &amp;&amp; (SRPWorldEntitySpawner.triggerSPAWNING || SRPConfigWorld.meteorActive));
 * </pre>
 *
 * <p>The round trip through NBT does not preserve it. {@code writeToNBT} writes the <em>static</em>
 * {@code SRPWorldEntitySpawner.triggerSPAWNING} under the key {@code srpmeteor} and never reads
 * {@code this.dimMeteor} at all, while {@code readFromNBT} loads {@code srpmeteor} straight back
 * into {@code dimMeteor}:
 *
 * <pre>
 * writeToNBT: tag.setBoolean("srpmeteor", SRPWorldEntitySpawner.triggerSPAWNING);  // wrong variable
 * readFromNBT: if (tag.hasKey("srpmeteor")) this.dimMeteor = tag.getBoolean("srpmeteor");
 * </pre>
 *
 * <p>{@code triggerSPAWNING} is a client-side toggle on SRP's world-creation settings screen
 * (button id 12 in {@code GuiSRPWorldSettings}). It is static, it is never serialised, and a
 * full-jar scan of 1502 classes found exactly three writes to it: two {@code <clinit>} defaults of
 * {@code false} and that one button. So in any ordinary session it is {@code false} - and
 * {@code srpmeteor} is therefore written as {@code false} on every save.
 *
 * <p>Net effect: {@code getTriggerMet()} is correct only until the dimension's
 * {@code srparasites_data} is saved and reloaded once, and {@code false} forever after. Two systems
 * read it, and both fail open rather than closed:
 * <ul>
 *   <li><b>The meteor.</b> {@code SRPEventHandlerBus}' world tick requires {@code getTriggerMet()},
 *       so the meteor can only ever fall during the session that created the data. After the first
 *       relog it can never fall again, whatever the config says.</li>
 *   <li><b>The infestation anchors.</b> {@code SRPWorldParasiteSpawner.isPosWithinOrigin} ends with
 *       {@code if (!worldData.getTriggerMet()) return true;} - i.e. with the flag false, every
 *       position in the world counts as "inside an origin". The whole EIV / colony / parasite-biome
 *       gate switches off and parasites spawn anywhere, which is exactly what makes them appear to
 *       follow the player around.</li>
 * </ul>
 *
 * <p>We {@link Redirect} the single {@code GETSTATIC} in {@code writeToNBT} and hand it
 * {@code this.dimMeteor} instead, so the flag saves and restores as intended and
 * {@code Meteor Blacklisted Dimensions} keeps meaning something after a reload.
 *
 * <p>⚠️ This does not repair a world that has already been saved once - the damage is in the file,
 * and {@code create()} (the only code that computes the flag) does not run again for a dimension
 * that already has data. Existing saves keep {@code srpmeteor=false} and need a new world, or a
 * manual NBT edit, to get the intended behaviour.
 *
 * <p>No regression for someone who did press that world-creation button: with
 * {@code triggerSPAWNING} true, {@code create()} already set {@code dimMeteor} true, so the value
 * we write matches the old one.
 *
 * <p>Gated on {@code srpCompat.fixMeteorTriggerPersistence}; with the flag off the original static
 * is written and the save file is byte-identical to unmodified SRP.
 */
@Mixin(value = SRPWorldData.class, remap = false)
public abstract class MixinSrpMeteorTriggerPersist {

    @Shadow
    public abstract boolean getTriggerMet();

    @Redirect(
            method = "func_189551_b",
            at = @At(value = "FIELD",
                    target = "Lcom/dhanantry/scapeandrunparasites/world/SRPWorldEntitySpawner;"
                            + "triggerSPAWNING:Z",
                    opcode = Opcodes.GETSTATIC),
            remap = false)
    private boolean insanetweaks$saveOwnTriggerFlag() {
        if (!SrpWizMixinsConfig.srpCompat.fixMeteorTriggerPersistence) {
            return SRPWorldEntitySpawner.triggerSPAWNING;
        }
        return this.getTriggerMet();
    }
}
