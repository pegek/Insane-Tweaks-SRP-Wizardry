package com.spege.manacore.compat.tab;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bridges Trinkets and Baubles' consumable mana items into the player's unified pool: eating a
 * Mana Crystal grants permanent progression, and a Mana Reagent or Mana Candy restores current
 * mana.
 *
 * <p>🚨 Registered only for the three items that actually exist as their own registry entries.
 * TaB's jar ships four inventory models under {@code assets/xat/models/item/} -
 * {@code mana_candy}, {@code mana_candy2}, {@code mana_candy3}, {@code mana_candy4} - but there is
 * only ONE registered item behind them, {@code xat:mana_candy}: {@code Mana_Candy.registerModels()}
 * calls {@code ModelBakery.registerItemVariants} for all four models and picks between them client
 * -side with a custom {@code ItemMeshDefinition} keyed on the stack's remaining-count ratio (a
 * "freshness" look), not on item damage or a second registry name. {@code getRegistryName()} on
 * any Mana Candy stack always returns {@code xat:mana_candy}, confirmed against
 * {@code ModItems$foods} in the 0.33.3 jar, which constructs exactly one {@code Mana_Candy}
 * instance under that one name. A {@code ResourceLocation} for {@code mana_candy2/3/4} would
 * therefore never match any real stack - it would not throw, it just would never fire, exactly the
 * silent-failure trap this comment exists to head off. Do not add those three back without first
 * re-checking a newer TaB jar's {@code ModItems} the same way.
 */
public class TabManaItemHandler {

    private static final ResourceLocation MANA_CRYSTAL = new ResourceLocation("xat", "mana_crystal");
    private static final ResourceLocation MANA_REAGENT = new ResourceLocation("xat", "mana_reagent");
    private static final ResourceLocation MANA_CANDY = new ResourceLocation("xat", "mana_candy");

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!ManaCoreConfig.tab.enabled) {
            return;
        }
        EntityLivingBase entity = event.getEntityLiving();
        if (!(entity instanceof EntityPlayer) || entity.world.isRemote) {
            return;
        }
        EntityPlayer player = (EntityPlayer) entity;

        ItemStack stack = event.getItem();
        ResourceLocation registryName = stack.getItem().getRegistryName();
        if (registryName == null) {
            return;
        }

        if (MANA_CRYSTAL.equals(registryName)) {
            ManaAPI.addItemProgression(player, ManaCoreConfig.tab.manaCrystalMaxBonus);
        } else if (MANA_REAGENT.equals(registryName) || MANA_CANDY.equals(registryName)) {
            ManaAPI.add(player, ManaCoreConfig.tab.restoreItemAmount);
        }
    }
}
