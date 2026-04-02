package com.spege.insanetweaks.skills;

import codersafterdark.reskillable.api.unlockable.Trait;
import codersafterdark.reskillable.base.ExperienceHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;

@SuppressWarnings("null")
public class TraitGoldenOsmosisBuffed extends Trait {

    public TraitGoldenOsmosisBuffed() {
        super(new ResourceLocation("compatskills", "golden_osmosis"), 3, 2, new ResourceLocation("reskillable", "magic"),
                8, "reskillable:magic|40", "reskillable:mining|10", "reskillable:gathering|10", "reskillable:attack|10");
        SkillsModule.TRAITS.add(this);
    }

    @Override
    public void onPlayerTick(PlayerTickEvent event) {
        if (!event.player.world.isRemote) {
            tryRepair(event.player, event.player.getHeldItemMainhand());
            tryRepair(event.player, event.player.getHeldItemOffhand());
            for (ItemStack stack : event.player.inventory.armorInventory) {
                tryRepair(event.player, stack);
            }
        }
    }

    private void tryRepair(EntityPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            int damage = stack.getItemDamage();
            if (damage > 2) {
                Item item = stack.getItem();
                if (item.isRepairable() && item.getIsRepairable(stack, new ItemStack(Items.GOLD_INGOT))) {
                    if (ExperienceHelper.getPlayerXP(player) > 0) {
                        ExperienceHelper.drainPlayerXP(player, 1);
                        stack.setItemDamage(damage - 3);
                    }
                }
            }
        }
    }
}
