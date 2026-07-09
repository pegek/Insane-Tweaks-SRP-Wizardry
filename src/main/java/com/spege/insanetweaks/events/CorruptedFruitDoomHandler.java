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
 *   - a Beckon Stage V (EntityVenkrolSV) rises at the position where the fruit
 *     was EATEN (stored in NBT). The bargain is absolute — logging out only
 *     delays it, and the Beckon rises where the deed was done, not wherever
 *     the player happens to relog.
 *   - the player dies via DamageSource.OUT_OF_WORLD + Float.MAX_VALUE, which
 *     bypasses the Totem of Undying AND our armor hardcap (both early-return
 *     on canHarmInCreative() sources).
 *
 * Registered only when SRP is present (direct EntityVenkrolSV import).
 */
public class CorruptedFruitDoomHandler {

    /** NBT (entityData): world-time at which the player dies. */
    public static final String TAG_DOOM_AT = "CorruptedFruitDoomAt";
    /** NBT (entityData): position where the fruit was eaten — the Beckon rises HERE. */
    public static final String TAG_DOOM_X = "CorruptedFruitDoomX";
    public static final String TAG_DOOM_Y = "CorruptedFruitDoomY";
    public static final String TAG_DOOM_Z = "CorruptedFruitDoomZ";

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

        // Spawn at the STORED eat position (fallback: current position for legacy tags).
        double spawnX = data.hasKey(TAG_DOOM_X) ? data.getDouble(TAG_DOOM_X) : player.posX;
        double spawnY = data.hasKey(TAG_DOOM_Y) ? data.getDouble(TAG_DOOM_Y) : player.posY;
        double spawnZ = data.hasKey(TAG_DOOM_Z) ? data.getDouble(TAG_DOOM_Z) : player.posZ;

        data.removeTag(TAG_DOOM_AT);
        data.removeTag(TAG_DOOM_X);
        data.removeTag(TAG_DOOM_Y);
        data.removeTag(TAG_DOOM_Z);

        EntityVenkrolSV beckon = new EntityVenkrolSV(player.world);
        beckon.setPosition(spawnX, spawnY, spawnZ);
        player.world.spawnEntity(beckon);

        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] Corrupted Fruit claimed {} — Beckon Stage V rises at {},{},{}",
                player.getName(), (int) spawnX, (int) spawnY, (int) spawnZ);

        player.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
    }
}
