package com.spege.insanetweaks.items.fruit;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityCorruptedSapling;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Corrupted Seed — planted (right-click on top of a block) it becomes a LIVING
 * EntityCorruptedSapling that parasites will attack. Tooltip doubles as the
 * growing manual, in the Living/Sentient gear flavor style.
 */
public class CorruptedSeedItem extends Item
        implements com.spege.insanetweaks.api.ITweaksPropertyHolder {

    public CorruptedSeedItem() {
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "corrupted_seed"));
        this.setUnlocalizedName("corrupted_seed");
        this.setCreativeTab(CreativeTabs.MATERIALS);
        this.setMaxStackSize(1);
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world,
            @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (facing != EnumFacing.UP) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        BlockPos above = pos.up();
        if (!world.isAirBlock(above)) {
            return EnumActionResult.FAIL;
        }

        // Every site rule (dimension, SRP phase, infestation, ground, roof, crowding, sanctuary),
        // then one active sapling per player. A refusal names the reason and keeps the seed.
        com.spege.insanetweaks.entities.sapling.SiteProblem problem =
                com.spege.insanetweaks.entities.sapling.SaplingSiteCheck.checkSite(world, above);
        if (problem == null && com.spege.insanetweaks.entities.sapling.SaplingSiteCheck
                .hasActiveSapling(player.getUniqueID())) {
            problem = com.spege.insanetweaks.entities.sapling.SiteProblem.ALREADY_ACTIVE;
        }
        if (problem != null) {
            player.sendStatusMessage(
                    new net.minecraft.util.text.TextComponentTranslation(problem.langKey()), true);
            return EnumActionResult.FAIL;
        }

        EntityCorruptedSapling sapling = new EntityCorruptedSapling(world);
        sapling.setPosition(above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D);
        sapling.setOwnerId(player.getUniqueID());
        world.spawnEntity(sapling);
        world.playSound(null, above, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.7f);

        player.getHeldItem(hand).shrink(1);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }

    /**
     * Three fragments went into this seed and a sapling has to survive a siege after it.
     * Same lava protection as the fruits it eventually becomes.
     */
    @Override
    public List<String> getActiveAdvProperties(ItemStack stack) {
        return java.util.Arrays.asList(com.spege.insanetweaks.api.AdvPropertyRegistry.ASHEN_LEGACY);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        com.spege.insanetweaks.config.categories.BaubleFruitsCategory cfg =
                com.spege.insanetweaks.config.ModConfig.baubleFruits;
        int minutes = Math.max(1, Math.round(new com.spege.insanetweaks.entities.sapling.SaplingDefensePlan(
                cfg.saplingLureSeconds, cfg.saplingWaveCount, cfg.saplingWaveIntervalSeconds).totalTicks() / 1200.0F));

        tooltip.add(TextFormatting.DARK_PURPLE + "It pulses. It listens. It waits for tainted soil.");
        tooltip.add("");
        tooltip.add(TextFormatting.GOLD + "How to grow:");
        tooltip.add(TextFormatting.GRAY + "1. Plant it on parasite-infested ground, in the open:");
        tooltip.add(TextFormatting.GRAY + "   nothing above it, room around it, no Sanctuary nearby.");
        tooltip.add(TextFormatting.GRAY + "2. Stay within " + cfg.saplingConditionRadius + " blocks wearing the "
                + TextFormatting.LIGHT_PURPLE + "Blessed Ring" + TextFormatting.GRAY + " or the");
        tooltip.add("   " + TextFormatting.DARK_RED + "Ring of the Seven Curses" + TextFormatting.GRAY + ".");
        tooltip.add(TextFormatting.GRAY + "3. The hive comes for it: " + TextFormatting.RED + "survive "
                + cfg.saplingWaveCount + " waves" + TextFormatting.GRAY + " (~" + minutes + " min).");
        tooltip.add(TextFormatting.GRAY + "4. It bears a " + TextFormatting.DARK_PURPLE
                + "Corrupted Fruit" + TextFormatting.GRAY + ".");
        tooltip.add("");
        tooltip.add(TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC
                + "If it dies, the seed is lost with it.");
        tooltip.add(TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC
                + "Sneak + right-click it with an empty hand to uproot it.");
    }
}
