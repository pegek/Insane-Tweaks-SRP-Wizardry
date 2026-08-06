package com.spege.insanetweaks.client;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.ChargeJumpCategory;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketChargeJump;
import com.spege.insanetweaks.api.TraitGate;
import com.spege.insanetweaks.events.ChargeJumpHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side half of the Coiled Spring trait ({@code compatskills:coiled_spring}).
 *
 * <p>Hold <b>Sneak + Jump</b> on the ground to coil, release to launch. At the default
 * multiplier a full charge reaches roughly 10 blocks against vanilla's 1.25.
 *
 * <p>Everything lives on the client because this reads raw key state and because
 * {@code EntityLivingBase.jump()} only runs client-side for a player — the server derives
 * vertical motion from movement packets. The one thing the client cannot do is waive fall
 * damage, which is computed server-side; that is what {@link PacketChargeJump} is for.
 *
 * <h3>Two ordering traps this works around</h3>
 * <ol>
 *   <li><b>The first jump escapes.</b> The world tick (and therefore {@code jump()}) runs
 *       before {@code ClientTickEvent.END}, so on the very first tick the charge counter is
 *       still zero. Suppression therefore keys off <i>live key state</i>, not off the
 *       counter, and is armed from tick one.</li>
 *   <li><b>{@code jumpTicks} adds 0–9 ticks of jitter.</b> {@code jump()} sets a 10-tick
 *       cooldown, so waiting for the next {@code jump()} to apply the leap would delay it by
 *       up to half a second, inconsistently. The leap is applied directly in the tick handler
 *       instead. {@link #launchGuardTicks} then covers the case where {@code jump()} still
 *       fires a tick later and overwrites {@code motionY} — the multiplier is simply
 *       re-applied to vanilla's fresh value, so exactly one leap of the right size happens on
 *       either path.</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public class ChargeJumpClientHandler {


    /** Vanilla {@code EntityLivingBase.getJumpUpwardsMotion()}. */
    private static final float BASE_JUMP_MOTION = 0.42F;

    /**
     * Downward motion used to cancel a jump while coiling. It must be NEGATIVE, not zero.
     *
     * <p>{@code Entity.move} decides grounding with
     * {@code collidedVertically = d3 != y; onGround = collidedVertically && d3 < 0.0D;}
     * where {@code d3} is the requested motionY. Requesting zero produces no vertical
     * collision and fails the {@code d3 < 0} test, so {@code onGround} flips to false on
     * exactly the tick {@code jump()} fires — which is every 10 ticks, because {@code jump()}
     * sets {@code jumpTicks = 10}. That silently broke the charge condition and dumped the
     * meter roughly a third of the way up. One tick of gravity keeps the player genuinely
     * grounded instead.
     */
    private static final double COILED_MOTION_Y = -0.08D;

    /** Ticks of tolerance for a one-off onGround blip (stairs, slabs, block edges). */
    private static final int GROUND_GRACE_TICKS = 3;

    /** Ticks of Sneak + Jump held together on the ground. */
    private int charge;
    /** Charge fraction of the leap currently being launched. */
    private float launchCharge;
    /** Ticks during which a late {@code jump()} may still clobber the launch motion. */
    private int launchGuardTicks;
    /** Counts down from {@link #GROUND_GRACE_TICKS} after the last genuinely grounded tick. */
    private int groundGrace;

    // -------------------------------------------------------------------------
    // Charge accumulation and launch
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.isGamePaused()) {
            return;
        }

        if (!TraitGate.has(player, TraitGate.COILED_SPRING)) {
            reset();
            return;
        }

        if (this.launchGuardTicks > 0) {
            this.launchGuardTicks--;
        }

        if (player.onGround) {
            this.groundGrace = GROUND_GRACE_TICKS;
        } else if (this.groundGrace > 0) {
            this.groundGrace--;
        }
        boolean grounded = player.onGround || this.groundGrace > 0;

        ChargeJumpCategory cfg = ModConfig.chargeJump;
        boolean coiling = mc.gameSettings.keyBindSneak.isKeyDown()
                && mc.gameSettings.keyBindJump.isKeyDown()
                && grounded
                && !player.isInWater()
                && !player.capabilities.isFlying;

        if (coiling) {
            if (this.charge < cfg.maxChargeTicks) {
                this.charge++;
            }
            return;
        }

        if (this.charge <= 0) {
            return;
        }

        float fraction = this.charge / (float) cfg.maxChargeTicks;
        this.charge = 0;

        if (fraction < cfg.minChargeToLaunch || !grounded) {
            return;
        }
        launch(player, fraction);
    }

    private void launch(EntityPlayerSP player, float fraction) {
        player.motionY = jumpMotion(player) * multiplierFor(fraction);
        player.isAirBorne = true;

        this.launchCharge = fraction;
        this.launchGuardTicks = 3;
        // Spend the grace immediately, or the airborne ticks right after launch would still
        // count as grounded and allow a mid-air recharge.
        this.groundGrace = 0;

        // Arm locally so single-player prediction matches, then tell the server, which owns
        // the real fall-damage calculation.
        ChargeJumpHandler.armFallProtection(player, fraction);
        InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketChargeJump(fraction));
    }

    private void reset() {
        this.charge = 0;
        this.launchCharge = 0.0F;
        this.launchGuardTicks = 0;
        this.groundGrace = 0;
    }

    private static double multiplierFor(float fraction) {
        return 1.0D + (ModConfig.chargeJump.maxJumpMultiplier - 1.0D) * fraction;
    }

    /** Mirrors vanilla {@code jump()}: base upwards motion plus any Jump Boost. */
    private static double jumpMotion(EntityPlayerSP player) {
        double motion = BASE_JUMP_MOTION;
        PotionEffect boost = player.getActivePotionEffect(MobEffects.JUMP_BOOST);
        if (boost != null) {
            motion += (boost.getAmplifier() + 1) * 0.1F;
        }
        return motion;
    }

    // -------------------------------------------------------------------------
    // Jump suppression
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onLivingJump(LivingEvent.LivingJumpEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntity() != player) {
            return;
        }
        if (!TraitGate.has(player, TraitGate.COILED_SPRING)) {
            return;
        }

        // A jump() that lands right after the launch would reset motionY to the vanilla
        // value; re-apply the multiplier to it rather than letting it swallow the leap.
        if (this.launchGuardTicks > 0) {
            this.launchGuardTicks = 0;
            player.motionY *= multiplierFor(this.launchCharge);
            return;
        }

        // Coiling: keyed off live key state so this is armed on the very first tick, before
        // the charge counter has been incremented. See COILED_MOTION_Y for why this must not
        // be zero.
        if (mc.gameSettings.keyBindSneak.isKeyDown() && mc.gameSettings.keyBindJump.isKeyDown()) {
            player.motionY = COILED_MOTION_Y;
        }
    }

    // -------------------------------------------------------------------------
    // HUD
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        // No trait check here: charge only ever leaves zero past the gate in onClientTick, which
        // also resets it the moment the trait goes away.
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL
                || !ModConfig.chargeJump.showChargeBar
                || this.charge <= 0) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.gameSettings.hideGUI) {
            return;
        }

        float progress = this.charge / (float) ModConfig.chargeJump.maxChargeTicks;
        ScaledResolution res = new ScaledResolution(mc);
        int top = res.getScaledHeight() - ChargeBarRenderer.BAR_BOTTOM_OFFSET;

        // Stack above the lock-picker bar so both can be readable at once.
        if (mc.player.isHandActive()) {
            top -= ChargeBarRenderer.BAR_STACK_STEP;
        }

        ChargeBarRenderer.draw(ChargeBarRenderer.centeredLeft(res.getScaledWidth()), top, progress,
                ChargeBarRenderer.COLOR_FILL_CHARGE);
    }
}
