package com.spege.insanetweaks.events;

import com.spege.insanetweaks.init.ModPotions;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Egzekwuje Gilded Stasis (Zhonya rework):
 *  - nieśmiertelność: cancel LivingAttackEvent gdy efekt aktywny,
 *  - root: zerowanie ruchu co tick (obie strony — client też, żeby input nie szarpał),
 *  - blokada ataku i interakcji podczas stasis,
 *  - aggro loss: przez okno TAG_AGGRO_LOSS_UNTIL czyści (co 5 ticków) target każdego
 *    moba celującego w gracza (pokrywa agresywny re-targeting AI SRParasites).
 */
public class ZhonyaStasisHandler {

    /** NBT (entityData): world-time do którego trwa okno utraty aggro. */
    public static final String TAG_AGGRO_LOSS_UNTIL = "ZhonyaAggroLossUntil";

    /** NBT (entityData): flaga "wyłączyliśmy grawitację na czas stasis" — do sprzątnięcia po efekcie. */
    public static final String TAG_STASIS_NO_GRAVITY = "ZhonyaStasisNoGravity";

    /** Promień czyszczenia aggro wokół gracza. */
    private static final double AGGRO_CLEAR_RADIUS = 24.0D;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("null") // ModPotions.GILDED_STASIS is guaranteed non-null at runtime
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        if (event.getEntityLiving().isPotionActive(ModPotions.GILDED_STASIS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntityPlayer().isPotionActive(ModPotions.GILDED_STASIS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && event.getEntityPlayer().isPotionActive(ModPotions.GILDED_STASIS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;

        // Root backstop — obie strony (główny root to SLOWNESS/JUMP_BOOST/noGravity
        // nałożone przy aktywacji; tu tylko dobijamy resztki pędu).
        if (player.isPotionActive(ModPotions.GILDED_STASIS)) {
            player.motionX = 0.0D;
            player.motionY = 0.0D;
            player.motionZ = 0.0D;

            // Golden aura particles — armor layers ignore the GL tint, so armored
            // players still read the stasis. (REDSTONE interprets speed args as RGB.)
            if (player.world.isRemote && player.ticksExisted % 2 == 0) {
                player.world.spawnParticle(net.minecraft.util.EnumParticleTypes.REDSTONE,
                        player.posX + (player.world.rand.nextDouble() - 0.5D) * player.width * 1.5D,
                        player.posY + player.world.rand.nextDouble() * player.height,
                        player.posZ + (player.world.rand.nextDouble() - 0.5D) * player.width * 1.5D,
                        1.0D, 0.84D, 0.1D);
            }
        }

        if (player.world.isRemote) return;

        NBTTagCompound data = player.getEntityData();

        // Gravity cleanup: restore gravity when the effect expires, dies, or is otherwise lost.
        if (!player.isPotionActive(ModPotions.GILDED_STASIS) && data.getBoolean(TAG_STASIS_NO_GRAVITY)) {
            player.setNoGravity(false);
            data.removeTag(TAG_STASIS_NO_GRAVITY);
        }

        // Aggro loss window.
        if (data.hasKey(TAG_AGGRO_LOSS_UNTIL)) {
            if (player.world.getTotalWorldTime() <= data.getLong(TAG_AGGRO_LOSS_UNTIL)) {
                // Throttled to every 5 ticks: target re-acquisition slower than 5t doesn't
                // matter, and a 24-block AABB scan every tick during a parasite horde is
                // exactly the worst moment for it.
                if (player.ticksExisted % 5 == 0) {
                    clearAggroAround(player);
                }
            } else {
                data.removeTag(TAG_AGGRO_LOSS_UNTIL);
            }
        }
    }

    /** Zdejmuje gracza z celownika wszystkich mobów w promieniu. */
    public static void clearAggroAround(EntityPlayer player) {
        AxisAlignedBB box = player.getEntityBoundingBox().grow(AGGRO_CLEAR_RADIUS);
        for (EntityLiving mob : player.world.getEntitiesWithinAABB(EntityLiving.class, box)) {
            if (mob.getAttackTarget() == player) {
                mob.setAttackTarget(null);
            }
            if (mob.getRevengeTarget() == player) {
                mob.setRevengeTarget(null);
            }
        }
    }
}
