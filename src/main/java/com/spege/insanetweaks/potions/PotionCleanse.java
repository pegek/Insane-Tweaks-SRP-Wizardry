package com.spege.insanetweaks.potions;

import javax.annotation.Nonnull;
import com.spege.insanetweaks.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Cleanse  Ea duration-based protective effect applied by the Living/Sentient Armor Hardcap.
 *
 * Applied by hardcap for 40 ticks (2 seconds).
 * Behavior:
 *   - Every 10 ticks (0.5s): removes ALL active potion effects registered as non-beneficial.
 *   - Has no fixed duration of its own  Eduration is set by the applier (hardcap = 40t).
 *
 * Note: Damage immunity is handled SEPARATELY by ArmorEventHandler.onLivingAttack
 * via a dedicated NBT immunity window (TAG_IMMUNITY, 10 ticks = 0.5s).
 * PotionCleanse itself does NOT grant immunity.
 *
 * Inspired by PotionCore's PotionCure (Tmtravlr):
 *   Original uses an external helper system (PotionCoreHelper.cureEntities).
 *   This implementation is self-contained  Ethe logic lives in performEffect().
 */
public class PotionCleanse extends Potion {

    /** Path to the 18x18 icon PNG shown in HUD and inventory. */
    @SideOnly(Side.CLIENT)
    private static final ResourceLocation CLEANSE_TEXTURE =
            new ResourceLocation("insanetweaks", "textures/gui/potion/cleanse.png");

    /**
     * Built-in parasite-ecosystem effects removed by Cleanse (audit 2026-07-09,
     * SRParasites 1.10.7 + SRPExtra 1.10.7.5; SW:Parasites registers no own potions).
     * Shipped in code rather than as config defaults because Forge @Config values
     * already written to disk never pick up new defaults.
     * Deliberately EXCLUDED (protective/mechanic — never add): srparasites:repel,
     * srparasites:antimall, srparasites:rage, srparasites:jugg, srparasites:pivot,
     * srparasites:primitive/adapted/pure/crude/feral/nexus,
     * srparasites:distorted_enlightenment, srparasites:dod_smoke_trail.
     */
    private static final String[] BUILT_IN_CLEANSED_EFFECTS = {
            "srparasites:coth", "srparasites:fear", "srparasites:bleed",
            "srparasites:corrosive", "srparasites:viral", "srparasites:vomit",
            "srparasites:senses", "srparasites:prey", "srparasites:debar",
            "srparasites:needler", "srparasites:foster", "srparasites:link",
            "srparasites:parate", "srparasites:spotted", "srparasites:braining",
            "srparasites:novision", "srparasites:indeaf", "srparasites:overheating",
            "srparasites:conta", "srparasites:muscleout", "srparasites:effectpos",
            "srparasites:effectneg", "srparasites:the_sign",
            "srparasites:thornshade_thorns",
            "srpextra:stung", "srpextra:confused" };

    /** Resolved lazily on first cleanse pulse — registries are complete by then. */
    private static java.util.List<Potion> builtInResolved = null;

    private static java.util.List<Potion> getBuiltInCleansed() {
        if (builtInResolved == null) {
            builtInResolved = new java.util.ArrayList<>();
            for (String id : BUILT_IN_CLEANSED_EFFECTS) {
                Potion p = ForgeRegistries.POTIONS.getValue(new ResourceLocation(id));
                if (p != null) builtInResolved.add(p);
            }
        }
        return builtInResolved;
    }

    public PotionCleanse() {
        super(false, 0xAADDFF); // color: light icy-blue
        this.setPotionName("potion.insanetweaks.cleanse");
    }

    @Override
    public boolean isInstant() {
        return false; // duration-based
    }

    /**
     * Fire performEffect every 10 ticks (0.5 seconds).
     * Mirrors the pattern used by vanilla Regen/Poison (e.g. duration % (50 >> amp) == 0).
     */
    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    /**
     * On each trigger (every 10t):
     *   Pass 1   removes all effects where isBeneficial() == false.
     *   Pass 1.5 removes the built-in parasite-ecosystem effect list (BUILT_IN_CLEANSED_EFFECTS),
     *             covering harmful SRParasites/SRPExtra effects that are incorrectly registered as
     *             neutral/beneficial (e.g. srparasites:novision) and thus bypass Pass 1.
     *   Pass 2   removes effects listed in ModConfig.tweaks.cleanseAdditionalEffects, a user-editable
     *             extension point for any other mod's negative effect not covered by Pass 1 or 1.5.
     * Uses a copy in Pass 1 to avoid ConcurrentModificationException.
     */
    @Override
    public void performEffect(@Nonnull EntityLivingBase entity, int amplifier) {
        // Pass 1: standard non-beneficial removal
        for (Potion potion : new java.util.ArrayList<>(entity.getActivePotionMap().keySet())) {
            if (!potion.isBeneficial()) {
                entity.removePotionEffect(potion);
            }
        }

        // Pass 1.5: built-in parasite-ecosystem effects (see BUILT_IN_CLEANSED_EFFECTS).
        for (Potion builtIn : getBuiltInCleansed()) {
            if (entity.isPotionActive(builtIn)) {
                entity.removePotionEffect(builtIn);
            }
        }

        // Pass 2: config-driven additional removal
        for (String effectId : ModConfig.tweaks.cleanseAdditionalEffects) {
            if (effectId == null || effectId.isEmpty()) continue;
            Potion extra = ForgeRegistries.POTIONS.getValue(new ResourceLocation(effectId));
            if (extra != null && entity.isPotionActive(extra)) {
                entity.removePotionEffect(extra);
            }
        }
    }

    @Override
    public boolean isBeneficial() {
        return true; // shows with blue border in HUD
    }

    /**
     * MUST return false so Forge skips the default Vanilla sprite-sheet rendering
     * and instead delegates to our renderHUDEffect() / renderInventoryEffect().
     * Without this, Forge assumes the icon lives in icons.png and never calls our methods.
     */
    @Override
    public boolean hasStatusIcon() {
        return false;
    }

    /**
     * Renders the Cleanse icon in the HUD (top-right effect list).
     *
     * The x,y coordinates point to the TOP-LEFT of the background frame (24x24 px).
     * To center our 18x18 icon within that frame, we offset by +3 on both axes  E     * matching the same +3,+3 offset that vanilla uses for all its potion icons.
     *
     * When ModConfig.client.hideCleanseHudEffect is true, this method returns immediately
     * without drawing anything (icon slot stays but is invisible).
     */
    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("null") // bindTexture param lacks @Nonnull annotation in Forge 1.12.2 API
    public void renderHUDEffect(int x, int y, @Nonnull PotionEffect effect, @Nonnull Minecraft mc, float alpha) {
        if (ModConfig.client.hideCleanseHudEffect) return;
        mc.getTextureManager().bindTexture(CLEANSE_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        // +3 offset centers 18x18 icon inside the 24x24 background frame
        drawFullTexture(x + 3, y + 3, 18, 18);
    }

    /**
     * Renders the Cleanse icon in the inventory screen (active effects panel).
     * Same +3,+3 centering as the HUD variant.
     * Returns immediately when hideCleanseHudEffect is true.
     */
    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("null") // bindTexture param lacks @Nonnull annotation in Forge 1.12.2 API
    public void renderInventoryEffect(int x, int y, @Nonnull PotionEffect effect, @Nonnull Minecraft mc) {
        if (ModConfig.client.hideCleanseHudEffect) return;
        mc.getTextureManager().bindTexture(CLEANSE_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawFullTexture(x + 3, y + 3, 18, 18);
    }

    /**
     * Controls whether this effect appears in the HUD effect list at all.
     * When false, Forge skips rendering the entire slot (background + icon + timer).
     * Available in Forge 1.12.2 via the patched Potion API.
     */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean shouldRenderHUD(@Nonnull PotionEffect effect) {
        return !ModConfig.client.hideCleanseHudEffect;
    }

    /**
     * Controls whether this effect appears in the inventory effect panel.
     * Mirrors shouldRenderHUD so both views are toggled by the same config flag.
     */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean shouldRenderInvText(@Nonnull PotionEffect effect) {
        return !ModConfig.client.hideCleanseHudEffect;
    }

    /**
     * Draws a textured quad at (x, y) using full UV coordinates (0.0 ↁE1.0).
     * This correctly renders any standalone PNG regardless of its pixel dimensions,
     * unlike drawTexturedModalRect which only works with 256x256 sprite-sheets.
     */
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("null") // DefaultVertexFormats fields lack @Nonnull in Forge 1.12.2 API
    private static void drawFullTexture(int x, int y, int w, int h) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x,     y + h, 0).tex(0.0, 1.0).endVertex();
        buf.pos(x + w, y + h, 0).tex(1.0, 1.0).endVertex();
        buf.pos(x + w, y,     0).tex(1.0, 0.0).endVertex();
        buf.pos(x,     y,     0).tex(0.0, 0.0).endVertex();
        tess.draw();
    }
}
