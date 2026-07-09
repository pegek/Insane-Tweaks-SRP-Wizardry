package com.spege.insanetweaks.baubles;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.events.ZhonyaStasisHandler;
import com.spege.insanetweaks.init.ModPotions;
import com.spege.insanetweaks.util.PlayerManaCompat;

import electroblob.wizardry.item.ItemArtefact;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Artefakt: Zhonyas Hourglass (REWORK 2026-07-09)
 *
 * Dawna funkcja (restoracja SRP entity) przeniesiona 1:1 do
 * ItemRestorationHourglassArtefact. Ten item ma NOWE działanie:
 *
 * AKTYWNE (PPM trzymając w ręce):
 *   1. Koszt: drenaż CAŁEJ aktualnej many (player_mana) + cooldown (config, domyślnie 3 h).
 *      Wymagane minimum many (config, domyślnie 100) — poniżej aktywacja odmawia
 *      i nie zużywa cooldownu.
 *   2. Gilded Stasis (config, domyślnie 3 s): pełna nieśmiertelność + full heal
 *      + Cleanse + root w miejscu + złoty tint modelu (ZhonyaStasisHandler /
 *      ZhonyaClientHandler egzekwują efekt — ten item tylko go nakłada).
 *   3. Aggro loss (config, domyślnie 5 s): wszystkie moby celujące w gracza
 *      tracą target, per-tick przez całe okno (pokrywa agresywny re-targeting SRP).
 */
@SuppressWarnings("null")
public class ItemZhonyasHourglassArtefact extends ItemArtefact {

    public ItemZhonyasHourglassArtefact() {
        super(EnumRarity.EPIC, Type.CHARM);
        this.setRegistryName("zhonyas_hourglass");
        this.setUnlocalizedName("insanetweaks.zhonyas_hourglass");
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        if (player.getCooldownTracker().hasCooldown(this)) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        // --- Koszt: wymaga player_mana i minimum aktualnej many ---
        if (!PlayerManaCompat.isAvailable()) {
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "[Zhonyas] The hourglass is inert without a mana soul (player_mana missing)."));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        double currentMana = PlayerManaCompat.getCurrentMana(player);
        if (currentMana < ModConfig.tweaks.zhonyaMinMana) {
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "[Zhonyas] Not enough mana ("
                + (int) currentMana + "/" + ModConfig.tweaks.zhonyaMinMana + ")."));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        // Płacimy: cała mana + cooldown.
        PlayerManaCompat.setCurrentMana(player, 0.0D);
        if (!player.isCreative()) {
            player.getCooldownTracker().setCooldown(this, ModConfig.tweaks.zhonyaCooldownTicks);
        }

        // --- Gilded Stasis ---
        int stasisTicks = ModConfig.tweaks.zhonyaStasisTicks;
        player.setHealth(player.getMaxHealth());
        player.addPotionEffect(new PotionEffect(ModPotions.GILDED_STASIS, stasisTicks, 0, false, false));
        player.addPotionEffect(new PotionEffect(ModPotions.CLEANSE, stasisTicks, 0, false, false));
        // True root: attribute modifier instead of SLOWNESS because our own CLEANSE
        // strips non-beneficial potions on its first pulse. MOVEMENT_SPEED is a synced
        // attribute, so it works on BOTH sides without packets.
        ZhonyaStasisHandler.applyRootModifier(player);
        // Negative jump boost makes getJumpUpwardsMotion() non-positive — jumping is disabled.
        player.addPotionEffect(new PotionEffect(net.minecraft.init.MobEffects.JUMP_BOOST, stasisTicks, -6, false, false));
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        // Freeze mid-air too: no gravity for the stasis window (flag tracked for cleanup).
        player.setNoGravity(true);
        player.getEntityData().setBoolean(ZhonyaStasisHandler.TAG_STASIS_NO_GRAVITY, true);

        // --- Aggro loss window (NBT timestamp; handler czyści per-tick) ---
        long until = world.getTotalWorldTime() + ModConfig.tweaks.zhonyaAggroLossTicks;
        player.getEntityData().setLong(ZhonyaStasisHandler.TAG_AGGRO_LOSS_UNTIL, until);
        ZhonyaStasisHandler.clearAggroAround(player);

        // --- Audio-wizualia ---
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0f, 0.6f);
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                    player.posX, player.posY + player.height * 0.5D, player.posZ, 60,
                    0.5D, 0.9D, 0.5D, 0.05D);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "Time stolen from the parasite hive.");
        tooltip.add("");
        tooltip.add(TextFormatting.GOLD + "Gilded Stasis");
        tooltip.add(TextFormatting.GRAY + "Right-click: freeze yourself in golden stasis");
        tooltip.add(TextFormatting.GRAY + "for a moment — invulnerable, fully healed,");
        tooltip.add(TextFormatting.GRAY + "cleansed, and forgotten by your enemies.");
        tooltip.add("");
        tooltip.add(TextFormatting.RED + "Cost: ALL of your current mana.");
        tooltip.add(TextFormatting.RED + "Long cooldown.");
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
