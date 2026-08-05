package com.spege.srpwizcore.whtcompat;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.api.WhtIFrames;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import baubles.api.BaublesApi;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Grants the Cross Necklace ({@code bountifulbaubles:amuletcross}) its advertised effect.
 *
 * <p>The item's whole implementation is {@code maxHurtResistantTime = 36} on equip. Under
 * WorseHurtTimer that field survives as an input in exactly one branch of
 * {@code Events.getHurtTime} — the one taken when the attacker holds no attack-speed weapon —
 * so the necklace helps against bare-handed mobs and nothing else. Moving the effect onto
 * {@link WhtIFrames} makes it apply on every path and stops it depending on an entity field that
 * is neither saved to NBT nor exclusively ours.
 *
 * <p>No compile dependency on Bountiful Baubles: the item is looked up by registry name once and
 * cached. Bountiful Baubles keeps writing 36 to the field; {@code MixinBhtEventsResistantTime}
 * makes WorseHurtTimer ignore it for players, so that write is a harmless dead store and the
 * bonus is not counted twice.
 */
public final class CrossNecklaceProvider implements WhtIFrames.Provider {

    private static final ResourceLocation AMULET_CROSS =
            new ResourceLocation("bountifulbaubles", "amuletcross");

    private final Item amuletCross;

    private CrossNecklaceProvider(Item amuletCross) {
        this.amuletCross = amuletCross;
    }

    /**
     * Registers the provider when both Bountiful Baubles and Baubles are present and the item
     * actually resolves. Call from {@code FMLInitializationEvent} — item registration is over by
     * then.
     */
    public static void registerIfPossible() {
        if (!Loader.isModLoaded("bountifulbaubles") || !Loader.isModLoaded("baubles")) {
            return;
        }
        final Item item = ForgeRegistries.ITEMS.getValue(AMULET_CROSS);
        if (item == null) {
            SrpWizCore.LOGGER.warn("[srpwizcore] whtCompat: {} not in the item registry, "
                    + "Cross Necklace multiplier disabled", AMULET_CROSS);
            return;
        }
        WhtIFrames.register("srpwizcore:cross_necklace", new CrossNecklaceProvider(item));
        SrpWizCore.LOGGER.info("[srpwizcore] whtCompat: Cross Necklace multiplier armed ({}x)",
                SrpWizCoreConfig.whtCompat.crossNecklaceMultiplier);
    }

    @Override
    public float multiplier(EntityLivingBase victim) {
        if (!(victim instanceof EntityPlayer)) {
            return 1.0F;
        }
        if (!BaublesApi.isBaubleEquipped(victim, this.amuletCross)) {
            return 1.0F;
        }
        return (float) SrpWizCoreConfig.whtCompat.crossNecklaceMultiplier;
    }
}
