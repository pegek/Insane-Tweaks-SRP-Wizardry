package com.spege.insanetweaks.events;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.registry.WizardryBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Gives Electroblob's Imbuement Altar a break/reclaim path.
 *
 * <p>EB builds the altar with {@code setBlockUnbreakable()}: hardness -1, i.e. bedrock in survival.
 * That was coherent while the block only ever generated in library ruins and was meant to be
 * restored where it stood. It stopped being coherent once Ancient Spellcraft added a crafting recipe
 * for it ({@code assets/ancientspellcraft/recipes/imbuement_altar.json}) - the altar became something
 * you pay for and then can never move.
 *
 * <p>Two separate things happen here, and they are deliberately not the same switch:
 * <ul>
 * <li>{@link #applyBlockProperties()} makes the block minable. Blast resistance is left at EB's
 * 6000000 on purpose - a ritual fixture a creeper can delete is worse than one you cannot move.</li>
 * <li>{@link #onHarvestDrops} decides whether mining it gives the item back. By default it does not.
 * Only a player wearing one of the configured EB artefacts reclaims the altar; everyone else
 * destroys it. That keeps world-generated altars from becoming a farm while still letting a geared
 * player undo a misplacement.</li>
 * </ul>
 *
 * <p>🚨 Setting hardness alone would NOT have produced "minable with a pickaxe". The altar's material
 * is {@code ROCK} and EB never calls {@code setHarvestLevel}, so its harvest tool is null; a pickaxe
 * would swing at bare-hand speed and {@code canHarvestBlock} would be false, which means the block
 * breaks and drops nothing no matter what this class does. The harvest level is what puts the drop
 * decision in our hands in the first place - which is why the "no drop" default is enforced here
 * rather than left to that accident.
 *
 * <p>The item sitting ON the altar is not our business: EB's own {@code breakBlock} spawns it before
 * any of this runs, and it is not part of {@code getDrops}, so it always comes back.
 */
public class ImbuementAltarSalvageHandler {

    /** Resolved form of {@code tweaks.imbuementAltarSalvageArtefacts}. Rebuilt when the array changes. */
    private static List<Item> cachedArtefacts = null;

    /** The array instance the cache was built from. ConfigManager.sync hands out a NEW array. */
    private static String[] cachedSource = null;

    /**
     * Turns the altar into an ordinary minable block. Call once from FML init - after EB's
     * {@code RegistryEvent.Register<Block>}, which is guaranteed, and before any world exists.
     *
     * <p>Hardness is not cached in {@code IBlockState} (the state cache holds opacity, light and face
     * shape, not hardness), so a plain setter is enough and no mixin is involved. Both setters are
     * public on this Forge build.
     */
    public static void applyBlockProperties() {
        if (!ModConfig.tweaks.imbuementAltarBreakable) {
            return;
        }

        Block altar = WizardryBlocks.imbuement_altar;
        if (altar == null) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Imbuement Altar: EB's block is missing from the "
                    + "registry, so it stays unbreakable. Nothing else to do.");
            return;
        }

        float hardness = (float) ModConfig.tweaks.imbuementAltarHardness;
        int level = ModConfig.tweaks.imbuementAltarHarvestLevel;

        // setHardness only RAISES resistance (when resistance < hardness * 5), and EB's is 6000000,
        // so the altar stays blast-proof without us naming resistance at all.
        altar.setHardness(hardness);
        altar.setHarvestLevel("pickaxe", level);

        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] Imbuement Altar is now minable (hardness {}, pickaxe level {}). "
                        + "It drops itself only for a player wearing one of: {}.",
                Float.valueOf(hardness), Integer.valueOf(level),
                Arrays.toString(ModConfig.tweaks.imbuementAltarSalvageArtefacts));
    }

    /**
     * Strips the altar's own ItemBlock from the drop list unless the harvester is wearing a salvage
     * artefact. Anything else in the list (another mod's addition, or the fortune-scaled extras of a
     * future EB version) is left alone - this removes one specific stack, it does not clear drops.
     *
     * <p>{@code getHarvester()} is null for explosions, pistons and command-driven breaks, which is
     * exactly the set of cases that should never yield an altar.
     */
    @SubscribeEvent
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        Block altar = WizardryBlocks.imbuement_altar;
        if (altar == null || event.getState().getBlock() != altar) {
            return;
        }

        EntityPlayer harvester = event.getHarvester();
        if (harvester != null && hasSalvageArtefact(harvester)) {
            return; // reclaimed: leave the default drop in place
        }

        Item altarItem = Item.getItemFromBlock(altar);
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (!stack.isEmpty() && stack.getItem() == altarItem) {
                it.remove();
            }
        }
    }

    private static boolean hasSalvageArtefact(EntityPlayer player) {
        List<Item> artefacts = resolveArtefacts();
        for (int i = 0; i < artefacts.size(); i++) {
            // isArtefactActive() throws IllegalArgumentException on a non-artefact, which is why
            // resolveArtefacts() filters by instanceof rather than trusting the config string.
            if (ItemArtefact.isArtefactActive(player, artefacts.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Config strings to items, cached. The list is read live, so the cache is keyed on the array
     * INSTANCE: {@code ConfigManager.sync} replaces the field with a freshly parsed array, so a
     * reference mismatch is a reliable "the player edited this" signal without comparing contents.
     */
    private static List<Item> resolveArtefacts() {
        String[] configured = ModConfig.tweaks.imbuementAltarSalvageArtefacts;
        if (cachedArtefacts != null && cachedSource == configured) {
            return cachedArtefacts;
        }

        List<Item> resolved = new ArrayList<>();
        for (int i = 0; i < configured.length; i++) {
            String id = configured[i];
            if (id == null || id.trim().isEmpty()) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id.trim()));
            if (item == null) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Imbuement Altar: salvage artefact '{}' is not "
                        + "a registered item - ignored.", id);
                continue;
            }
            if (!(item instanceof ItemArtefact)) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Imbuement Altar: salvage artefact '{}' exists but "
                        + "is not an EB Wizardry artefact - ignored. Only rings, amulets and charms can be "
                        + "checked this way.", id);
                continue;
            }
            resolved.add(item);
        }

        cachedArtefacts = resolved;
        cachedSource = configured;
        return resolved;
    }
}
