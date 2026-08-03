package com.spege.srpwizcore.dormant;

import com.spege.srpwizcore.api.DormantWaystoneRegistry;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.config.categories.DormantWaystonesCategory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Key-item ("Dormant Eye") additions: a server-side particle trail toward the nearest waystone
 * while the key item is HELD, and tooltip lines — including the exact XYZ of the nearest
 * waystone once the player is within {@code tooltipCoordsRange} blocks of it.
 *
 * <p>Coords reach the client via the key item's stack NBT (subtag {@value #NBT_KEY}), written
 * server-side only-on-change every {@value #INTERVAL} ticks; stack NBT auto-syncs, so no custom
 * network channel is needed. The tag is removed when out of range so the line disappears.
 */
public class DormantEyeHandler {

    static final String NBT_KEY = "SrpwizWaystone";
    private static final int INTERVAL = 10;         // ticks between updates per player
    private static final double TRAIL_REACH = 12.0D;
    private static final double TRAIL_SPACING = 0.6D;
    private static final double ARRIVE_DIST = 2.5D;

    /** Trail palette: dust = exact-RGB redstone (count=0 trick), smoke = the dark/black stop. */
    private static final double[][] PALETTE = {
            {0.00D, 0.00D, 0.55D},   // dark blue
            {0.00D, 0.90D, 1.00D},   // cyan
            {1.00D, 1.00D, 1.00D},   // white
            {0.35D, 0.00D, 0.55D},   // dark purple
            {-1.0D, 0.0D, 0.0D},     // sentinel: SMOKE_LARGE (redstone can't render true black)
    };

    private static DormantWaystonesCategory cfg() { return SrpWizCoreConfig.dormantWaystones; }

    // --- server: trail + NBT coords -----------------------------------------

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        if (p.ticksExisted % INTERVAL != 0) {
            return;
        }
        BlockPos target = DormantWaystoneRegistry.nearest(p.world, p.getPosition());
        updateCoordsNbt(p, target);
        if (target != null && cfg().trailEnabled && holdingKey(p)) {
            drawTrail(p, target);
        }
    }

    private static boolean holdingKey(EntityPlayer p) {
        return DormantTeleportHandler.isKeyItem(p.getHeldItemMainhand())
                || DormantTeleportHandler.isKeyItem(p.getHeldItemOffhand());
    }

    /**
     * Writes (or clears) the nearest-waystone coords on EVERY key-item stack in the player's
     * inventory, only when the value actually changed — NBT churn re-syncs the stack, so keep
     * writes minimal.
     */
    private static void updateCoordsNbt(EntityPlayerMP p, BlockPos target) {
        boolean inRange = target != null && cfg().tooltipCoordsRange > 0.0D
                && p.getPosition().distanceSq(target) <=
                        cfg().tooltipCoordsRange * cfg().tooltipCoordsRange;
        for (int i = -1; i < p.inventory.mainInventory.size(); i++) {
            ItemStack s = i < 0 ? p.getHeldItemOffhand() : p.inventory.mainInventory.get(i);
            if (!DormantTeleportHandler.isKeyItem(s)) {
                continue;
            }
            NBTTagCompound tag = s.getTagCompound();
            if (inRange) {
                if (tag != null && tag.hasKey(NBT_KEY)) {
                    NBTTagCompound c = tag.getCompoundTag(NBT_KEY);
                    if (c.getInteger("x") == target.getX() && c.getInteger("y") == target.getY()
                            && c.getInteger("z") == target.getZ()) {
                        continue; // unchanged
                    }
                }
                if (tag == null) {
                    tag = new NBTTagCompound();
                    s.setTagCompound(tag);
                }
                NBTTagCompound c = new NBTTagCompound();
                c.setInteger("x", target.getX());
                c.setInteger("y", target.getY());
                c.setInteger("z", target.getZ());
                tag.setTag(NBT_KEY, c);
            } else if (tag != null && tag.hasKey(NBT_KEY)) {
                tag.removeTag(NBT_KEY);
                if (tag.hasNoTags()) {
                    s.setTagCompound(null); // leave the stack exactly as it was
                }
            }
        }
    }

    private static void drawTrail(EntityPlayerMP p, BlockPos target) {
        WorldServer ws = (WorldServer) p.world;
        double px = p.posX, py = p.posY + 1.0D, pz = p.posZ;
        double tx = target.getX() + 0.5D, ty = target.getY() + 1.0D, tz = target.getZ() + 0.5D;
        double dx = tx - px, dy = ty - py, dz = tz - pz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < ARRIVE_DIST) {
            ws.spawnParticle(EnumParticleTypes.PORTAL, true, tx, ty, tz, 12, 0.4D, 0.6D, 0.4D, 0.0D);
            return;
        }
        double nx = dx / dist, ny = dy / dist, nz = dz / dist;
        double reach = Math.min(dist, TRAIL_REACH);
        int dots = (int) (reach / TRAIL_SPACING);
        for (int i = 1; i <= dots; i++) {
            double d = i * TRAIL_SPACING;
            spawnDot(ws, PALETTE[(i - 1) % PALETTE.length],
                    px + nx * d, py + ny * d, pz + nz * d);
        }
    }

    /** One coloured trail dot: 3-particle dust cluster (server can't scale), or big smoke. */
    private static void spawnDot(WorldServer ws, double[] c, double x, double y, double z) {
        if (c[0] >= 0.0D) {
            ws.spawnParticle(EnumParticleTypes.REDSTONE, true, x,          y,          z,          0, c[0], c[1], c[2], 1.0D);
            ws.spawnParticle(EnumParticleTypes.REDSTONE, true, x + 0.07D,  y + 0.02D,  z - 0.05D,  0, c[0], c[1], c[2], 1.0D);
            ws.spawnParticle(EnumParticleTypes.REDSTONE, true, x - 0.06D,  y - 0.02D,  z + 0.06D,  0, c[0], c[1], c[2], 1.0D);
        } else {
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // --- client: tooltip ------------------------------------------------------
    // 🚨 The tooltip half lives in DormantEyeTooltipHandler and must stay there. It used to be a
    // second @SubscribeEvent in this class, which broke the dedicated server: EventBus.register()
    // reflects over EVERY method of the class it is handed, so registering this handler resolved
    // ItemTooltipEvent — and through it net.minecraft.client.util.ITooltipFlag, which does not
    // exist server-side. The event never firing on a server does not help; the load happens at
    // registration.
}
