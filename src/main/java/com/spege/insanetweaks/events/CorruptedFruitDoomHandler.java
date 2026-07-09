package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.entity.monster.deterrent.nexus.EntityVenkrolSV;
import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The price of eating a Corrupted Fruit (see CorruptedFruitItem).
 *
 * While the doom window (NBT world-time deadline) is open:
 *   - the player is rooted (motion zeroed every tick),
 *   - blindness + nausea are RE-APPLIED every tick, so no cleanse/milk can help.
 * When the deadline passes:
 *   - a Beckon Stage V (EntityVenkrolSV) rises at the player's position,
 *   - the player dies via DamageSource.OUT_OF_WORLD + Float.MAX_VALUE, which
 *     bypasses the Totem of Undying AND our armor hardcap (both early-return
 *     on canHarmInCreative() sources).
 *
 * Registered only when SRP is present (direct EntityVenkrolSV import).
 */
public class CorruptedFruitDoomHandler {

    /** NBT (entityData): world-time at which the player dies. */
    public static final String TAG_DOOM_AT = "CorruptedFruitDoomAt";

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        if (player.world.isRemote) return;

        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(TAG_DOOM_AT)) return;

        long doomAt = data.getLong(TAG_DOOM_AT);
        long now = player.world.getTotalWorldTime();

        if (now < doomAt) {
            // Root + unremovable dread.
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
            player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0, false, false));
            player.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 60, 0, false, false));
            return;
        }

        data.removeTag(TAG_DOOM_AT);

        EntityVenkrolSV beckon = new EntityVenkrolSV(player.world);
        beckon.setPosition(player.posX, player.posY, player.posZ);
        player.world.spawnEntity(beckon);

        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] Corrupted Fruit claimed {} — Beckon Stage V rises at {},{},{}",
                player.getName(), (int) player.posX, (int) player.posY, (int) player.posZ);

        player.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
    }
}
