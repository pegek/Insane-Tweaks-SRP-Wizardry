package com.spege.manacore.compat.ebw;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Redirects Electroblob's Wizardry's passive mana sources - the {@code condenser} and
 * {@code siphon} wand upgrades, and the {@code ring_condensing} / {@code amulet_arcane_defence} /
 * {@code ring_siphoning} artefacts - into the player's unified mana pool. Both upgrades used to regenerate/refill the wand's own
 * mana, which stopped mattering once {@link com.spege.manacore.mixins.ebw.MixinItemWand} cut the
 * wand's mana gates out of the cast path. The third mana upgrade, {@code storage}, is NOT handled
 * here - it is already remapped to a spell-cost refund in {@link EbwSpellCostHandler#applyRefund}.
 *
 * <p>🚨 <b>Both hands are read, but the higher upgrade level wins, not the sum.</b> A player
 * dual-wielding two {@code condenser}-upgraded wands gets the passive regen of the better wand,
 * not double it - carrying a second wand purely as a passive-income battery would otherwise be a
 * strictly dominant strategy with no gameplay cost. The same reasoning applies to {@code siphon}:
 * EBW itself only ever fires one cast (and therefore one payout) per kill regardless of what is
 * held, so summing upgrade levels across hands would give kill-mana a bonus that casting itself
 * never grants. {@link #getUpgradeLevel} implements this with {@code Math.max}, not addition, for
 * both upgrades.
 *
 * <p>🚨 <b>{@code ManaCoreConfig.ebw.enabled} is re-checked here even though it carries
 * {@code @Config.RequiresMcRestart}.</b> The restart annotation means the flag cannot flip this
 * class's registration on or off without a relaunch, so the check below is redundant with the
 * registration guard in {@code ManaCoreMod.init} for that flag specifically. It is kept anyway to
 * match {@link EbwSpellCostHandler#applies}, which checks the very same flag on every event: the
 * two EBW bridge classes should look the same to whoever reads them next, and a single boolean
 * field read costs nothing on a path that already does far more expensive work (an NBT read via
 * {@link WandHelper#getUpgradeLevel}) a few lines later.
 */
public class EbwManaSourceBridge {

    /** Regen is accrued once a second, not every tick - counting upgrades means reading NBT. */
    private static final int CONDENSER_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ManaCoreConfig.ebw.enabled) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world.isRemote) {
            return;
        }
        // Cheapest possible check comes last-but-one, BEFORE the NBT-reading upgrade lookup below:
        // everything above this line is a field read or an enum/reference comparison.
        if (player.ticksExisted % CONDENSER_INTERVAL_TICKS != 0) {
            return;
        }

        double perSecond = getUpgradeLevel(player, WizardryItems.condenser_upgrade)
                * ManaCoreConfig.ebw.condenserRegenPerLevel;

        // Both artefacts recharged ITEM mana in EBW - ring_condensing every wand on the hotbar,
        // amulet_arcane_defence every worn piece of wizard armour - so both became silently
        // pointless once spells stopped being paid for out of items. Same fix as the condenser
        // upgrade above: pay the player instead. Their contributions ADD to the upgrade's and to
        // each other's, unlike the two hands in getUpgradeLevel, because these are three distinct
        // pieces of equipment a player had to acquire separately rather than one item carried
        // twice.
        if (ItemArtefact.isArtefactActive(player, WizardryItems.ring_condensing)) {
            perSecond += ManaCoreConfig.ebw.ringCondensingRegenPerSecond;
        }
        if (ItemArtefact.isArtefactActive(player, WizardryItems.amulet_arcane_defence)) {
            perSecond += ManaCoreConfig.ebw.amuletArcaneDefenceRegenPerSecond;
        }

        if (perSecond > 0.0D) {
            ManaAPI.add(player, perSecond);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!ManaCoreConfig.ebw.enabled) {
            return;
        }
        if (event.getEntity().world.isRemote) {
            return;
        }
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();

        int level = getUpgradeLevel(player, WizardryItems.siphon_upgrade);
        if (level <= 0) {
            return;
        }
        double gain = level * ManaCoreConfig.ebw.siphonManaPerLevel;
        // EBW multiplies siphoned mana by exactly 1.3 for this ring (confirmed as `fmul 1.3f` in
        // WizardryEventHandler.onLivingDeathEvent), which is where the config default comes from.
        // Applied only to the siphon payout, never to the condenser regen - the ring boosts what
        // is siphoned from a kill, and EBW keeps those two apart too.
        if (ItemArtefact.isArtefactActive(player, WizardryItems.ring_siphoning)) {
            gain *= ManaCoreConfig.ebw.ringSiphoningMultiplier;
        }
        if (gain > 0.0D) {
            ManaAPI.add(player, gain);
        }
    }

    /**
     * Highest upgrade level held across both hands, NOT the sum - see class javadoc for why
     * dual-wielding two upgraded wands must not double the payout.
     */
    private int getUpgradeLevel(EntityPlayer player, Item upgrade) {
        int mainHand = levelOf(player.getHeldItem(EnumHand.MAIN_HAND), upgrade);
        int offHand = levelOf(player.getHeldItem(EnumHand.OFF_HAND), upgrade);
        return Math.max(mainHand, offHand);
    }

    private int levelOf(ItemStack stack, Item upgrade) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
            return 0;
        }
        return WandHelper.getUpgradeLevel(stack, upgrade);
    }
}
