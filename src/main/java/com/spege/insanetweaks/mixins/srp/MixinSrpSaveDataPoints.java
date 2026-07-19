package com.spege.insanetweaks.mixins.srp;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.ISrpSaveDataDirectPoints;

import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Fix B - honor the per-dimension starting POINTS token in SRP's
 * "Evolution Phases Dimension Starting Phase List" (format {@code dim;phase;points}).
 *
 * <p>On 1.10.7 the world-data init in {@code SRPSaveData.createData(World,MapStorage,int)} (called
 * once by {@code get()} on first creation) applies each dim's configured points by calling
 * {@code setTotalKills(dim, value, /*canChangePhase*&#47;false, ...)}.
 * The diagnostic build proved that call returns {@code accepted=false} and never persists the
 * value: every configured dimension keeps {@code Default Points Start} (-300). For dim 111
 * (configured 600M, phase 8) the log showed the 600M setTotalKills rejected, the dim left at -300,
 * and then the first runtime point tick normalized -300 -&gt; 0 and degraded the phase 8 -&gt; 7.
 *
 * <p>We {@link Redirect} the two {@code setTotalKills} calls inside {@code createData} and, when the
 * fix is enabled, write SRP's own computed {@code value} straight into the dimension's points slot
 * (last call wins, which is the configured points token). {@code createData} runs once per world, so
 * this fires once. Phase -2 ("frozen") dimensions are unaffected - {@code createData} never calls
 * {@code setTotalKills} for them, and their points are cosmetic since such dimensions neither gain
 * nor lose. The runtime {@code checkPhase} caller is a different method and is not touched. Gated on
 * {@link ModConfig#srpCompat}.fixStartingPoints.
 *
 * <p>{@code setTotalKills} is SRP's own method (not MCP-mapped), matched with {@code remap = false};
 * {@code markDirty} is MC's ({@code func_76185_a}) and reobf-maps automatically.
 */
@Mixin(value = SRPSaveData.class, remap = false)
public abstract class MixinSrpSaveDataPoints implements ISrpSaveDataDirectPoints {

    @Shadow(remap = false)
    private ArrayList dimEPid;

    @Shadow(remap = false)
    private ArrayList dimEPtotalKills;

    @Redirect(
            method = "createData",
            at = @At(value = "INVOKE",
                    target = "Lcom/dhanantry/scapeandrunparasites/world/SRPSaveData;"
                            + "setTotalKills(IIZLnet/minecraft/world/World;ZI)Z"),
            remap = false)
    private static boolean insanetweaks$applyStartingPoints(SRPSaveData self, int dim, int value,
            boolean canChangePhase, World world, boolean flag, int code) {
        if (!ModConfig.srpCompat.fixStartingPoints) {
            return self.setTotalKills(dim, value, canChangePhase, world, flag, code);
        }
        ((ISrpSaveDataDirectPoints) self).insanetweaks$setDimPointsDirect(dim, value);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void insanetweaks$setDimPointsDirect(int dim, int points) {
        if (this.dimEPid == null || this.dimEPtotalKills == null) {
            return;
        }
        for (int i = 0; i < this.dimEPid.size(); i++) {
            Object idObj = this.dimEPid.get(i);
            if (idObj instanceof Integer && ((Integer) idObj).intValue() == dim) {
                this.dimEPtotalKills.set(i, Integer.valueOf(points));
                ((WorldSavedData) (Object) this).markDirty();
                return;
            }
        }
    }
}
