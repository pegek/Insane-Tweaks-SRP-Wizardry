package com.spege.tombtweaks.wizardry;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory.WandSoulbindingConfig;

import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.util.WandHelper;
import ovh.corail.tombstone.api.capability.ISoulConsumer;

/**
 * Lets a grave's soul buy a wand upgrade.
 *
 * <p>Attached to wand stacks by {@link WandSoulbindAttacher}. Tombstone's
 * {@code BlockDecorativeGrave.onBlockActivated} looks for this capability on the <b>off-hand</b>
 * stack first, keeps it only when {@link #isUsingOffhandToEnchant()} is true, and then calls
 * {@link #canEnchant} followed by {@link #setEnchant} on the same stack.
 *
 * <p>🚨 {@code isUsingOffhandToEnchant()} returning {@code true} is load-bearing: it is the filter
 * on that branch. Return false and the wand is never consulted at all — and the main-hand branch
 * would then target the off-hand stack instead, which is the opposite of what is wanted.
 *
 * <p>The Ankh's own behaviour is untouched. It returns {@code PASS} at a soul-bearing grave, its
 * soul path is the perk respec, and its prayer lives on {@code onItemUseFinish} — three separate
 * branches of Tombstone's code, none of which this class enters.
 */
public class WandSoulbindConsumer implements ISoulConsumer {

    private static final String ANKH_NAME = "tombstone:ankh_of_prayer";

    private static WandSoulbindingConfig cfg() {
        return TombTweaksConfig.tombstone.wandSoulbinding;
    }

    /**
     * The configured upgrade, or null when the name is malformed or no mod registers it.
     *
     * <p>The colon check is not pedantry: {@code getByNameOrId} falls back to a legacy numeric item
     * id, so a config value of "42" would quietly resolve to an unrelated item instead of failing.
     */
    @Nullable
    private static Item upgradeItem() {
        String name = cfg().upgradeItem;
        if (name == null || name.indexOf(':') <= 0) {
            return null;
        }
        return Item.getByNameOrId(name);
    }

    /**
     * 🚨 Load-bearing, and deliberately more than a constant: this is the filter Tombstone applies
     * to the off-hand stack. Returning true commits the grave to the soul-consumer branch and
     * swallows the click, so anything that means "this feature is not operating" has to be answered
     * here rather than in {@link #canEnchant} — otherwise a disabled feature, or a missing upgrade
     * mod, would still take over the interaction instead of leaving it to Tombstone.
     *
     * <p>The Ankh requirement is deliberately NOT checked here. Holding a wand in the off hand at a
     * grave is a clear enough intent that telling the player "hold the Ankh" is more useful than
     * silently doing something else.
     */
    @Override
    public boolean isUsingOffhandToEnchant() {
        return TombTweaksConfig.tombstone.enableTombstoneTweaks
                && cfg().enabled
                && upgradeItem() != null;
    }

    /** The soul is the whole price. */
    @Override
    public int getKnowledge() {
        return 0;
    }

    @Override
    public boolean isEnchanted(ItemStack stack) {
        Item upgrade = upgradeItem();
        return upgrade != null && WandHelper.getUpgradeLevel(stack, upgrade) > 0;
    }

    /**
     * The grave calls this immediately before {@link #setEnchant} and abandons the whole
     * interaction when it is false, so a refusal here never costs the player a soul.
     */
    @Override
    public boolean canEnchant(World world, BlockPos pos, EntityPlayer player, ItemStack stack) {
        return refusal(player, stack) == null;
    }

    @Override
    public ConsumeResult setEnchant(World world, BlockPos pos, EntityPlayerMP player, ItemStack stack,
            int soulStrength) {
        String why = refusal(player, stack);
        if (why != null) {
            return refuse(player, stack, why);
        }

        Item upgrade = upgradeItem();
        int before = WandHelper.getUpgradeLevel(stack, upgrade);

        // Wizardry's own path, the one the arcane workbench uses: it checks Tier.upgradeLimit and
        // Constants.UPGRADE_STACK_LIMIT, and fires its special_upgrade advancement.
        ((ItemWand) stack.getItem()).applyUpgrade(player, stack, new ItemStack(upgrade));

        // 🚨 applyUpgrade returns the wand whether or not it applied anything - past the limit it
        // simply falls through to its return. The level is the only honest signal.
        if (WandHelper.getUpgradeLevel(stack, upgrade) <= before) {
            return refuse(player, stack, "This wand has no room for another upgrade.");
        }

        if (cfg().debugLogging) {
            TombstoneTweaks.LOGGER.info("[TombstoneTweaks] Soulbound {} for {} at {} (soul strength {}).",
                    stack.getItem().getRegistryName(), player.getName(), pos,
                    Integer.valueOf(soulStrength));
        }
        // Hand back the strength we were given: the grave compares it against the grave's own soul
        // to decide which advancement fires.
        return ConsumeResult.success(new TextComponentString("The soul binds itself to your wand."),
                soulStrength);
    }

    private static ConsumeResult refuse(EntityPlayer player, ItemStack stack, String why) {
        if (cfg().debugLogging) {
            TombstoneTweaks.LOGGER.info("[TombstoneTweaks] Wand soulbinding refused for {} ({}): {}",
                    player.getName(), stack.getItem().getRegistryName(), why);
        }
        return ConsumeResult.fail(new TextComponentString(why));
    }

    /** The reason this interaction cannot proceed, or null when it can. */
    @Nullable
    private static String refusal(EntityPlayer player, ItemStack stack) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks || !cfg().enabled) {
            return "Wand soulbinding is switched off.";
        }
        if (!(stack.getItem() instanceof ItemWand)) {
            return "Hold a wand in your off hand.";
        }
        Item upgrade = upgradeItem();
        if (upgrade == null) {
            return "No mod registers the upgrade \"" + cfg().upgradeItem + "\".";
        }
        if (cfg().requireAnkhOfPrayer) {
            ResourceLocation main = player.getHeldItemMainhand().getItem().getRegistryName();
            if (main == null || !ANKH_NAME.equals(main.toString())) {
                return "Hold the Ankh of Prayer in your main hand.";
            }
        }
        // Unreachable via the grave — Tombstone calls isEnchanted() first and short-circuits with
        // its own message. Kept for any other caller that drives the capability directly.
        if (WandHelper.getUpgradeLevel(stack, upgrade) > 0) {
            return "This wand is already bound to your soul.";
        }
        return null;
    }
}
