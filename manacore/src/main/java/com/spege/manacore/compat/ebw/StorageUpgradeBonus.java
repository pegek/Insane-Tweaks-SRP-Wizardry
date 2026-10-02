package com.spege.manacore.compat.ebw;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The EBW storage upgrade, re-purposed for a mana pool: while a wand is held, each level raises
 * the player's maximum by {@code ebw.storageBonusPerLevel}, and gaining that bonus adds
 * {@code ebw.storageFillFraction} of it to current mana at once - otherwise picking the wand up
 * would only raise an empty ceiling.
 *
 * <p>Upstream the upgrade enlarged the wand's own store, which no longer pays for anything.
 * SpellBundle already rewrites its description to "Increases the player's mana capacity while
 * holding the wand", so this makes the game do what the pack's tooltip was promising.
 *
 * <p>This is the first consumer of the dynamic {@code bonusMana} attribute: the modifier is
 * unsaved and gone after death or relog, and simply re-applied here on the next check. The
 * installed modifier is itself the record of the previous bonus, so no state is kept for it.
 *
 * <p>🚨 The refill is the one exploitable part. Putting the wand away confiscates the overflow
 * above the lowered cap, but taking it out again would refill once more - free mana on a loop. A
 * per-player wall-clock cooldown ({@code ebw.storageFillCooldownSeconds}) closes that; wall clock
 * because world time is per dimension. The map is keyed by UUID so it survives death and relog
 * within one server session, both of which would otherwise count as a fresh pick-up - which is
 * also why entries are never removed on logout. One {@code Long} per player seen this session.
 */
public class StorageUpgradeBonus {

    private static final UUID MODIFIER_ID = UUID.fromString("9a4e2c71-5b38-4d0f-8e16-c3f7a2d95b04");
    private static final String MODIFIER_NAME = "manacore.storage_upgrade";

    /** Checked four times a second: soon enough to feel immediate, without reading NBT each tick. */
    private static final int CHECK_INTERVAL_TICKS = 5;

    private final Map<UUID, Long> lastFillMillis = new HashMap<UUID, Long>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world.isRemote || player.ticksExisted % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        double previous = ManaAttributes.getBonusModifierAmount(player, MODIFIER_ID);
        double desired = ManaCoreConfig.ebw.enabled
                ? storageLevel(player) * ManaCoreConfig.ebw.storageBonusPerLevel
                : 0.0D;
        if (desired == previous) {
            return;
        }

        ManaAPI.addBonusModifier(player, MODIFIER_ID, MODIFIER_NAME, desired, 0);
        if (desired > previous) {
            refill(player, desired - previous);
        }
    }

    private void refill(EntityPlayer player, double gained) {
        double fraction = ManaCoreConfig.ebw.storageFillFraction;
        if (fraction <= 0.0D) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastFillMillis.get(player.getUniqueID());
        long cooldown = ManaCoreConfig.ebw.storageFillCooldownSeconds * 1000L;
        if (last != null && now - last.longValue() < cooldown) {
            return;
        }
        lastFillMillis.put(player.getUniqueID(), Long.valueOf(now));
        ManaAPI.add(player, gained * fraction);
    }

    /** Highest storage level across both hands, not the sum - one wand per hand is not two upgrades. */
    private static int storageLevel(EntityPlayer player) {
        return Math.max(levelOf(player.getHeldItemMainhand()), levelOf(player.getHeldItemOffhand()));
    }

    private static int levelOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
            return 0;
        }
        return WandHelper.getUpgradeLevel(stack, WizardryItems.storage_upgrade);
    }
}
