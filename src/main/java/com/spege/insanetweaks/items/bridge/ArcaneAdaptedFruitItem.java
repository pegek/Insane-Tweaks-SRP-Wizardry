package com.spege.insanetweaks.items.bridge;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.util.ArcaneAdaptedFruitHelper;
import com.spege.insanetweaks.util.PlayerManaCompat;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("null")
public class ArcaneAdaptedFruitItem extends ItemFood {

    public ArcaneAdaptedFruitItem() {
        super(4, 0.6f, false);
        this.setAlwaysEdible();
        this.setMaxStackSize(1);
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "arcane_adapted_fruit"));
        this.setUnlocalizedName("arcane_adapted_fruit");
        this.setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (!PlayerManaCompat.canModifyMaxMana()) {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentString(
                        TextFormatting.GRAY + "The fruit remains dormant without player_mana."));
            }
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        if (ArcaneAdaptedFruitHelper.hasConsumedFruit(player)) {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "You have already consumed an Arcane Adapted Fruit."));
            }
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        double currentMaxMana = PlayerManaCompat.getMaxMana(player);
        double maxManaCap = PlayerManaCompat.getMaxManaCap();
        if (maxManaCap <= 0.0D) {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "The fruit cannot read your Soul MP cap in this world."));
            }
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        if (maxManaCap > 0.0D && currentMaxMana >= maxManaCap - 0.001D) {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentString(
                        TextFormatting.GRAY + "Your Soul MP has already reached its maximum cap."));
            }
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        return super.onItemRightClick(world, player, hand);
    }

    @Override
    public void onFoodEaten(@Nonnull ItemStack stack, @Nonnull World world, @Nonnull EntityPlayer player) {
        if (world.isRemote) {
            return;
        }

        if (!PlayerManaCompat.canModifyMaxMana() || ArcaneAdaptedFruitHelper.hasConsumedFruit(player)) {
            return;
        }

        double cap = PlayerManaCompat.getMaxManaCap();
        if (cap <= 0.0D) {
            player.sendMessage(new TextComponentString(
                    TextFormatting.RED + "The fruit cannot attune to your Soul MP cap."));
            return;
        }
        
        // Oblicz brakującą ilość many do capa u tego gracza.
        double currentMax = PlayerManaCompat.getMaxMana(player);
        double diff = cap - currentMax;
        
        if (diff <= 0) {
            player.sendMessage(new TextComponentString(
                    TextFormatting.RED + "You already possess unimaginable mana power."));
            return;
        }

        boolean applied = PlayerManaCompat.addMaxMana(player, diff);
        if (!applied) {
            player.sendMessage(new TextComponentString(
                    TextFormatting.RED + "The fruit rejects the ritual and fails to alter your mana pool."));
            return;
        }

        ArcaneAdaptedFruitHelper.markConsumedFruit(player);
        world.playSound(null, player.posX, player.posY, player.posZ,
                net.minecraft.init.SoundEvents.ENTITY_ILLAGER_PREPARE_MIRROR, SoundCategory.PLAYERS, 1.0f, 0.75f);
        player.sendMessage(new TextComponentString(
                TextFormatting.LIGHT_PURPLE + "Arcane Adaptation takes root: Your Soul MP has been maximized."));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "A parasite-born fruit steeped in stolen wizardry.");
        tooltip.add("");
        tooltip.add(TextFormatting.LIGHT_PURPLE + "Instantly maximizes your Soul MP to its absolute limits");
        tooltip.add(TextFormatting.DARK_GRAY + "for player_mana users.");
        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + "Arcane Adapted");
        tooltip.add(TextFormatting.GRAY + "Spells from outside Insane Tweaks cost "
                + (int) ArcaneAdaptedFruitHelper.FOREIGN_SPELL_COST_MULTIPLIER + "x mana!");
        tooltip.add(TextFormatting.GRAY + "Casting an InsaneTweaks spell grants "
                + (int) ArcaneAdaptedFruitHelper.MANA_REGEN_FRUIT_TOTAL_MULTIPLIER
                + "x total mana regeneration for 10 seconds.");
        tooltip.add(TextFormatting.DARK_GRAY + "This surge is temporary, not permanent.");
        tooltip.add("");
        tooltip.add(TextFormatting.DARK_GRAY + "One use per player.");
    }
}
