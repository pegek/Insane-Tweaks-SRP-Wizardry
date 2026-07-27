package com.spege.insanetweaks.items.fruit;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.events.CorruptedFruitDoomHandler;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.BaubleFruitProgress;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Corrupted Fruit — the sapling's harvest. Two paths:
 *
 *  A) Purify at the Imbuement Altar (MixinTileEntityImbuementAltar) into a CHOSEN
 *     typed Bauble Fruit — the intended path.
 *  B) Eat it corrupted: immediately unlocks ONE RANDOM bauble slot (invokes the
 *     same BaseBaubleFruitItem.onFoodEaten logic — same one-per-type limits,
 *     no bypassing), then an unavoidable death sequence handled by
 *     CorruptedFruitDoomHandler: rooted, near-blind, and after the configured
 *     delay the player dies unconditionally and a Beckon Stage V rises.
 *
 * <p>With {@code corruptedFruitSmartRoll} on, path B draws only from slots the player can
 * still receive, so the bargain always pays out. With it off the roll spans all nine and can
 * land on an already-spent type — the death still happens, the gift does not.
 */
@SuppressWarnings("null")
public class CorruptedFruitItem extends ItemFood implements ITweaksPropertyHolder {

    public CorruptedFruitItem() {
        super(4, 0.6f, false);
        this.setAlwaysEdible();
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "corrupted_fruit"));
        this.setUnlocalizedName("corrupted_fruit");
        this.setCreativeTab(CreativeTabs.FOOD);
        this.setMaxStackSize(1);
    }

    /**
     * Refuses the bargain when the hive has nothing left to offer, rather than collecting the
     * price for nothing. Only meaningful under smart roll — without it the gamble is the point.
     */
    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn,
            @Nonnull EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        ItemStack held = playerIn.getHeldItem(handIn);

        if (com.spege.insanetweaks.config.ModConfig.baubleFruits.corruptedFruitSmartRoll
                && BaubleFruitProgress.getGrantableFruits(playerIn).isEmpty()) {
            if (!worldIn.isRemote) {
                playerIn.sendStatusMessage(
                        new TextComponentTranslation("msg.insanetweaks.corrupted_fruit.nothing_to_give"), true);
            }
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, held);
        }

        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    @Override
    public void onFoodEaten(@Nonnull ItemStack stack, @Nonnull World worldIn,
            @Nonnull EntityPlayer player) {
        if (worldIn.isRemote || !(player instanceof EntityPlayerMP)) {
            return;
        }

        // 1. Random slot unlock — exactly the same function as eating that fruit.
        BaseBaubleFruitItem chosen = pickFruit(worldIn, player);
        if (chosen == null) {
            // Smart roll is on and nothing is left to grant: the hive declines the bargain,
            // so the doom timer is never armed.
            player.sendStatusMessage(
                    new TextComponentTranslation("msg.insanetweaks.corrupted_fruit.nothing_to_give"), true);
            return;
        }
        chosen.onFoodEaten(new ItemStack(chosen), worldIn, player);

        // 2. The price. Doom window — CorruptedFruitDoomHandler takes it from here.
        // The eat position is stored too: the Beckon rises where the bargain was
        // struck, even if the player logs out and rejoins somewhere else.
        long doomAt = worldIn.getTotalWorldTime()
                + com.spege.insanetweaks.config.ModConfig.baubleFruits.corruptedFruitDoomTicks;
        net.minecraft.nbt.NBTTagCompound data = player.getEntityData();
        data.setLong(CorruptedFruitDoomHandler.TAG_DOOM_AT, doomAt);
        data.setDouble(CorruptedFruitDoomHandler.TAG_DOOM_X, player.posX);
        data.setDouble(CorruptedFruitDoomHandler.TAG_DOOM_Y, player.posY);
        data.setDouble(CorruptedFruitDoomHandler.TAG_DOOM_Z, player.posZ);
        player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 400, 0, false, false));
        player.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 400, 0, false, false));

        player.sendMessage(new TextComponentString(TextFormatting.DARK_RED + ""
                + TextFormatting.ITALIC + "The hive accepts your bargain."));
        worldIn.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.6f, 1.6f);
    }

    /** The fruit this bargain pays out, or null when smart roll finds nothing left to give. */
    @Nullable
    private BaseBaubleFruitItem pickFruit(World world, EntityPlayer player) {
        if (com.spege.insanetweaks.config.ModConfig.baubleFruits.corruptedFruitSmartRoll) {
            List<BaseBaubleFruitItem> available = BaubleFruitProgress.getGrantableFruits(player);
            if (available.isEmpty()) {
                return null;
            }
            return available.get(world.rand.nextInt(available.size()));
        }
        Item[] fruits = ModItems.getAllBaubleFruits();
        return (BaseBaubleFruitItem) fruits[world.rand.nextInt(fruits.length)];
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
     * Twenty minutes of sapling defence should not be undone by a misthrown stack — the typed
     * fruits this becomes are already lava-proof, so the harvest itself is too.
     */
    @Override
    public List<String> getActiveAdvProperties(ItemStack stack) {
        return Arrays.asList(AdvPropertyRegistry.ASHEN_LEGACY);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "Grown in tainted soil, under a blessed — or cursed — watch.");
        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + "Purify it at an Imbuement Altar to choose its gift.");
        tooltip.add("");
        tooltip.add(TextFormatting.RED + "Or eat it as it is — the hive grants a random gift");
        tooltip.add(TextFormatting.RED + "at once... and collects its price in full.");
        tooltip.add(TextFormatting.DARK_RED + "" + TextFormatting.ITALIC + "No blessing survives that bargain.");
    }
}
