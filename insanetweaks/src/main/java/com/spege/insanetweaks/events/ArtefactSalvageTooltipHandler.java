package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Writes the Imbuement Altar salvage ability into the tooltip of whichever artefact grants it.
 *
 * <p>The ability is ours, not Electroblob's, so nothing in EB's own description mentions it. Without
 * this line the mechanic is undiscoverable: a player holding the Charm of Silk Touch has no way to
 * learn that it is the difference between destroying an altar and keeping it, and the failure mode -
 * mining the altar and watching it vanish - is exactly the one that costs them the block.
 *
 * <p>Driven by the same config list as {@link ImbuementAltarSalvageHandler}, so swapping the artefact
 * moves the tooltip with it and the two can never disagree.
 *
 * <p>🚨 Class-level {@code @SideOnly(Side.CLIENT)}: instantiating this on a dedicated server is fatal
 * at load, so its registration site in {@code InsaneTweaksMod.init()} must stay inside the
 * {@code event.getSide() == Side.CLIENT} block. The two travel together.
 */
@SideOnly(Side.CLIENT)
public class ArtefactSalvageTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!ModConfig.tweaks.imbuementAltarBreakable) {
            return; // the altar is still unbreakable, so there is nothing to salvage
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null) {
            return;
        }

        String registryName = id.toString();
        String[] configured = ModConfig.tweaks.imbuementAltarSalvageArtefacts;
        for (int i = 0; i < configured.length; i++) {
            String candidate = configured[i];
            if (candidate != null && registryName.equals(candidate.trim())) {
                event.getToolTip().add(I18n.format("tooltip.insanetweaks.altar_salvage"));
                return;
            }
        }
    }
}
