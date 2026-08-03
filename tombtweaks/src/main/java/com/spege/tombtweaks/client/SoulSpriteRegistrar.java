package com.spege.tombtweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.relauncher.Side;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.TombTweaksConfig;

/**
 * Puts two of Tombstone's particle textures back on the block atlas, so the particles that use them
 * stop drawing as nothing.
 *
 * <p>Tombstone stitches its particle sprites in {@code TombstoneParticleSprites.onTextureStitch},
 * but only three of them — {@code particles/fake_fog}, {@code particles/ghost}, {@code particles/bone}.
 * Two particle classes never moved to that registry and still resolve their texture by raw name:
 *
 * <ul>
 *   <li>{@code ParticleGraveSoul} → {@code tombstone:items/soul} — the blue/pink orb marking a
 *       decorative grave that holds a soul</li>
 *   <li>{@code ParticleShield} → {@code tombstone:items/pray_of_protection}</li>
 * </ul>
 *
 * <p>Neither texture belongs to a real item any more. Each is named by exactly one file in the jar —
 * {@code models/item/advancement_5.json} and {@code advancement_9.json} — and no item by those names
 * is registered, so {@code ModelBakery} never loads those models and never registers their textures.
 * {@code TextureMap.getAtlasSprite} then hands back the atlas's "missingno" sprite instead.
 *
 * <p>Worse, {@code ParticleGraveSoul} caches it in a {@code static final} field, resolved once on
 * first class load — so there is no second chance and no per-particle workaround. Registering the
 * sprite before the atlas is stitched is the only place to fix it.
 *
 * <p>Client only: {@code TextureStitchEvent} lives in {@code net.minecraftforge.client.event} and
 * the whole class would be unloadable on a dedicated server. {@code @Mod.EventBusSubscriber} with an
 * explicit side is safe here — FML checks the side before it calls {@code Class.forName}.
 */
@Mod.EventBusSubscriber(modid = TombstoneTweaks.MODID, value = Side.CLIENT)
public final class SoulSpriteRegistrar {

    private static final String[] MISSING_SPRITES = {
        "items/soul",
        "items/pray_of_protection"
    };

    private SoulSpriteRegistrar() {}

    @SubscribeEvent
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks) return;
        if (!TombTweaksConfig.tombstone.fixMissingParticleSprites) return;

        // Only the block atlas: it is the one ParticleGraveSoul reads, and the one whose sprites are
        // addressed by the "modid:path" names those particles use.
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) return;

        for (String path : MISSING_SPRITES) {
            event.getMap().registerSprite(new ResourceLocation("tombstone", path));
        }
        TombstoneTweaks.LOGGER.info("[TombstoneTweaks] Registered {} Tombstone particle sprites the mod itself leaves off the block atlas.",
                Integer.valueOf(MISSING_SPRITES.length));
    }

    /**
     * Reports what the sprites actually resolved to once the atlas is built.
     *
     * <p>Deliberately unconditional on the fix flag: with the fix off this prints {@code missingno}
     * and is the evidence that the sprite really was absent, which is otherwise invisible from a log.
     */
    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks) return;
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) return;

        for (String path : MISSING_SPRITES) {
            TextureAtlasSprite sprite = event.getMap().getAtlasSprite("tombstone:" + path);
            String icon = sprite == null ? "<null>" : sprite.getIconName();
            boolean resolved = ("tombstone:" + path).equals(icon);
            TombstoneTweaks.LOGGER.info("[TombstoneTweaks] Atlas lookup tombstone:{} -> {} ({})",
                    path, icon, resolved ? "OK" : "MISSING, that particle will draw nothing");
        }
    }
}
