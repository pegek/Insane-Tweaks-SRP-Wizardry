package com.spege.ebreduxaddon.gametest;

import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.ArmorHandler;
import com.spege.ebreduxaddon.feature.ModEffects;
import com.spege.ebreduxaddon.feature.ModItems;
import com.spege.ebreduxaddon.feature.WandBonusHandler;
import com.spege.ebreduxaddon.platform.ArmorProgress;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.spege.ebreduxaddon.gametest.CleanseGameTests.EMPTY;
import static com.spege.ebreduxaddon.gametest.CleanseGameTests.casterAt;

/** Zaklada domyslny config: -3% / -5% kosztu, +3% potency, evolveAt 1500, Last Stand 3 HP / 10 t / 90 s. */
@GameTestHolder(EbreduxAddon.MODID)
@PrefixGameTestTemplate(false)
public final class ArmorGameTests {

    private ArmorGameTests() {
    }

    private static Player fullGrafted(GameTestHelper helper) {
        Player player = casterAt(helper, 1.5, 2, 1.5, 0f, 0f);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.GRAFTED_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.GRAFTED_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.GRAFTED_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.GRAFTED_BOOTS.get()));
        return player;
    }

    @GameTest(template = EMPTY)
    public static void armorBonusCountsPieces(GameTestHelper helper) {
        Player player = fullGrafted(helper);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SENTIENT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SENTIENT_CHESTPLATE.get()));
        SpellModifiers modifiers = new SpellModifiers();
        helper.assertTrue(WandBonusHandler.applyBonus(player, ItemStack.EMPTY, modifiers), "armour bonus not applied");
        float cost = modifiers.getFactor(SpellModifiers.COST);
        float potency = modifiers.getFactor(SpellModifiers.POTENCY);
        helper.assertTrue(Math.abs(cost - 0.84) < 1e-4, "cost " + cost);
        helper.assertTrue(Math.abs(potency - 1.06) < 1e-4, "potency " + potency);
        helper.assertFalse(WandBonusHandler.applyBonus(player, ItemStack.EMPTY, modifiers), "armour bonus applied twice");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void damageIsSplitAndPiecesEvolveAlone(GameTestHelper helper) {
        Player player = casterAt(helper, 1.5, 2, 1.5, 0f, 0f);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.GRAFTED_HELMET.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.GRAFTED_BOOTS.get()));
        ArmorHandler.absorb(player, 10f);
        double helmet = ArmorProgress.get(player.getItemBySlot(EquipmentSlot.HEAD));
        helper.assertTrue(Math.abs(helmet - 5.0) < 1e-6, "helmet share " + helmet);

        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        boots.setHoverName(net.minecraft.network.chat.Component.literal("Lucky Boots"));
        ArmorProgress.add(boots, 1500);
        helper.assertTrue(ArmorHandler.evolveReady(player) == 1, "exactly the boots should evolve");
        ItemStack evolved = player.getItemBySlot(EquipmentSlot.FEET);
        helper.assertTrue(evolved.getItem() == ModItems.SENTIENT_BOOTS.get(), "boots became " + evolved.getItem());
        helper.assertTrue(evolved.getHoverName().getString().equals("Lucky Boots"), "custom name lost");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).getItem() == ModItems.GRAFTED_HELMET.get(), "helmet evolved too early");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lastStandSavesOnceThenCoolsDown(GameTestHelper helper) {
        Player player = fullGrafted(helper);
        player.setHealth(1f);
        helper.assertTrue(ArmorHandler.tryLastStand(player, player.damageSources().generic()), "Last Stand did not fire");
        helper.assertTrue(player.getHealth() == 3f, "health after Last Stand " + player.getHealth());
        helper.assertTrue(player.hasEffect(ModEffects.CLEANSING.get()), "no Cleansing after Last Stand");
        helper.assertTrue(ArmorHandler.immune(player), "no immunity window after Last Stand");
        helper.assertFalse(ArmorHandler.tryLastStand(player, player.damageSources().generic()), "fired again during cooldown");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lastStandIgnoresVoidAndPartialSets(GameTestHelper helper) {
        Player player = fullGrafted(helper);
        helper.assertFalse(ArmorHandler.tryLastStand(player, player.damageSources().fellOutOfWorld()), "stopped the void");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertFalse(ArmorHandler.tryLastStand(player, player.damageSources().generic()), "fired with three pieces");
        helper.succeed();
    }
}
