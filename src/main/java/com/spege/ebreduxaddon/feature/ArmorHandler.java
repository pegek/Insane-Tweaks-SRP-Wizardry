package com.spege.ebreduxaddon.feature;

import com.spege.ebreduxaddon.core.LastStand;
import com.spege.ebreduxaddon.core.Progress;
import com.spege.ebreduxaddon.feature.effect.CleansingEffect;
import com.spege.ebreduxaddon.feature.item.AddonArmorItem;
import com.spege.ebreduxaddon.platform.ArmorProgress;
import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Zbroja Grafted/Sentient: postep z pochlonietych obrazen, ewolucja czesci i Last Stand.
 * Wszystko tylko dla graczy i tylko po stronie serwera.
 */
public final class ArmorHandler {

    static final String TAG_LAST_STAND = "ebreduxaddon:last_stand";
    static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorHandler() {
    }

    /** Liczby noszonych czesci: [grafted, sentient]. */
    public static int[] countPieces(LivingEntity entity) {
        int grafted = 0;
        int sentient = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof AddonArmorItem item) {
                if (item.isSentient()) {
                    sentient++;
                } else {
                    grafted++;
                }
            }
        }
        return new int[] {grafted, sentient};
    }

    /** Rozdziela obrazenia po noszonych czesciach Grafted. Publiczne dla GameTestow. */
    public static void absorb(Player player, float damage) {
        List<ItemStack> grafted = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof AddonArmorItem item && !item.isSentient()) {
                grafted.add(stack);
            }
        }
        double share = Progress.share(damage, grafted.size());
        if (share > 0) {
            grafted.forEach(stack -> ArmorProgress.add(stack, share));
        }
    }

    /** Ewoluuje czesci, ktore przekroczyly prog. Zwraca ich liczbe. */
    public static int evolveReady(Player player) {
        long evolveAt = Config.INSTANCE.armorEvolveAt.get();
        int evolved = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof AddonArmorItem item) || item.isSentient()
                    || !Progress.shouldEvolve((long) ArmorProgress.get(stack), evolveAt)) {
                continue;
            }
            ItemStack next = new ItemStack(ModItems.sentientFor(item.getType()));
            next.setTag(stack.getTag() == null ? null : stack.getTag().copy());
            player.setItemSlot(slot, next);
            evolved++;
        }
        if (evolved > 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE,
                    SoundSource.PLAYERS, 1.0f, 0.6f);
            player.displayClientMessage(Component.translatable("message.ebreduxaddon.armor_evolved"), true);
        }
        return evolved;
    }

    /** Last Stand. Zwraca, czy smierc zostala anulowana. Publiczne dla GameTestow. */
    public static boolean tryLastStand(Player player, DamageSource source) {
        int[] pieces = countPieces(player);
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(TAG_LAST_STAND);
        if (!LastStand.fires(Config.INSTANCE.lastStandEnabled.get(), source.is(DamageTypeTags.BYPASSES_INVULNERABILITY),
                pieces[0] + pieces[1] == 4, now, last, Config.INSTANCE.lastStandCooldownSeconds.get() * 20L)) {
            return false;
        }
        // Zapis 0 znaczy "nigdy", wiec tick 0 zapisujemy jako 1.
        player.getPersistentData().putLong(TAG_LAST_STAND, Math.max(1L, now));
        player.setHealth(Config.INSTANCE.lastStandHealth.get().floatValue());
        CleansingEffect.cleanse(player);
        int cleanse = Config.INSTANCE.lastStandCleanseTicks.get();
        if (cleanse > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.CLEANSING.get(), cleanse, 0, false, false));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    public static boolean immune(Player player) {
        long last = player.getPersistentData().getLong(TAG_LAST_STAND);
        return LastStand.immune(player.level().getGameTime(), last, Config.INSTANCE.lastStandImmunityTicks.get());
    }

    /** Nasluch na szynie Forge. Nazwy unikalne w modzie - patrz WandBonusHandler.WandForgeEvents. */
    public static final class ArmorForgeEvents {

        @SubscribeEvent
        public void absorbIntoGrafted(LivingDamageEvent event) {
            if (event.getEntity() instanceof Player player && !player.level().isClientSide && event.getAmount() > 0) {
                absorb(player, event.getAmount());
            }
        }

        /** HIGHEST jak na 1.12.2: przed innymi modami, ktore moglyby juz rozdac lup z gracza. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void lastStand(LivingDeathEvent event) {
            if (event.getEntity() instanceof Player player && !player.level().isClientSide
                    && tryLastStand(player, event.getSource())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void lastStandImmunity(LivingAttackEvent event) {
            if (event.getEntity() instanceof Player player && !player.level().isClientSide
                    && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && immune(player)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void evolveArmour(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide && event.player.tickCount % 10 == 0) {
                evolveReady(event.player);
            }
        }
    }

}
