package com.spege.srpwizcore.dormant;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.api.DormantWaystoneRegistry;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.config.categories.DormantWaystonesCategory;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Channelled two-way waystone travel between {@code dimSurface} and {@code dimTarget}.
 *
 * <p>Right-clicking the waystone while holding the configured key item starts a
 * {@code channelTicks}-tick channel; ANY damage taken during the channel cancels it.
 * On completion:
 * <ul>
 *   <li><b>ENTER</b> ({@code dimSurface}): first use searches a safe landing pocket in
 *       {@code dimTarget}, places a return waystone at the landing floor, registers it AND links
 *       the pair (native fix: GS only linked, so {@code nearest()} never saw target-dim anchors).
 *       Later uses reuse the stored return pos.</li>
 *   <li><b>RETURN</b> ({@code dimTarget}): back to the exact linked surface waystone; if the pair
 *       is gone, escape-hatch to the nearest surface waystone, else world spawn -- the player must
 *       never be stranded.</li>
 * </ul>
 *
 * <p>Cooldown uses wall-clock ms, NOT getTotalWorldTime() -- that one is per-dimension and a
 * cross-dim comparison goes negative (learned the hard way in the GS prototype).
 */
public class DormantTeleportHandler {

    private static final long COOLDOWN_MS = 1500L;

    private static class Pending {
        final int dim;
        final BlockPos waystone;
        int ticksLeft;
        Pending(int dim, BlockPos waystone, int ticksLeft) {
            this.dim = dim; this.waystone = waystone; this.ticksLeft = ticksLeft;
        }
    }

    private final Map<UUID, Pending> pending = new HashMap<UUID, Pending>();
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();

    private static DormantWaystonesCategory cfg() { return SrpWizCoreConfig.dormantWaystones; }

    static boolean isKeyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation rl = stack.getItem().getRegistryName();
        return rl != null && rl.toString().equals(cfg().keyItem);
    }

    private static boolean holdingKeyItem(EntityPlayer p) {
        return isKeyItem(p.getHeldItemMainhand()) || isKeyItem(p.getHeldItemOffhand());
    }

    private static void reply(EntityPlayer p, String text) {
        p.sendMessage(new TextComponentString(text));
    }

    // --- interaction: start the channel -------------------------------------

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer p = event.getEntityPlayer();
        if (p == null || p.world.isRemote || !(p instanceof EntityPlayerMP)) {
            return;
        }
        if (event.getHand() != EnumHand.MAIN_HAND) {
            return; // fire once
        }
        BlockPos pos = event.getPos();
        if (p.world.getBlockState(pos).getBlock() != DormantBlocks.DORMANT_WAYSTONE) {
            return;
        }
        event.setCanceled(true); // our block -- own the interaction

        UUID id = p.getUniqueID();
        if (pending.containsKey(id)) {
            return; // already channelling -- ignore extra clicks
        }
        long now = System.currentTimeMillis();
        Long last = cooldown.get(id);
        if (last != null && now - last.longValue() < COOLDOWN_MS) {
            return;
        }
        if (cfg().requireKeyItem && !holdingKeyItem(p)) {
            reply(p, "§8The waystone is silent. It answers only to the §5Dormant Eye§8 held in hand.");
            return;
        }
        int dim = p.dimension;
        if (dim != cfg().dimSurface && dim != cfg().dimTarget) {
            reply(p, "§8The waystone is inert here.");
            return;
        }
        cooldown.put(id, Long.valueOf(now));
        int ticks = cfg().channelTicks;
        if (ticks <= 0) {
            execute((EntityPlayerMP) p, dim, pos);
            return;
        }
        pending.put(id, new Pending(dim, pos, ticks));
        reply(p, "§5The waystone stirs... §8(hold still — taking damage breaks the channel)");
    }

    // --- damage cancels the channel ------------------------------------------

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP) || event.getAmount() <= 0.0F) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.getEntityLiving();
        if (pending.remove(p.getUniqueID()) != null) {
            reply(p, "§cPain shatters the channel. The waystone falls silent.");
        }
    }

    // --- channel countdown ----------------------------------------------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(e.getKey());
            Pending pd = e.getValue();
            if (p == null || p.isDead || p.dimension != pd.dim) {
                it.remove();
                continue;
            }
            pd.ticksLeft--;
            if (pd.ticksLeft % 5 == 0) {
                ((WorldServer) p.world).spawnParticle(EnumParticleTypes.PORTAL, true,
                        p.posX, p.posY + 1.0D, p.posZ, 8, 0.4D, 0.8D, 0.4D, 0.0D);
            }
            if (pd.ticksLeft > 0) {
                continue;
            }
            it.remove();
            // re-validate: the waystone may have been broken during the channel
            if (p.world.getBlockState(pd.waystone).getBlock() != DormantBlocks.DORMANT_WAYSTONE) {
                reply(p, "§cThe waystone is gone. The channel collapses.");
                continue;
            }
            execute(p, pd.dim, pd.waystone);
        }
    }

    // --- teleport execution (port of the GS prototype) ------------------------

    private void execute(EntityPlayerMP p, int dim, BlockPos pos) {
        if (dim == cfg().dimSurface) {
            enter(p, pos);
        } else {
            back(p, pos);
        }
    }

    private void enter(EntityPlayerMP p, BlockPos surfacePos) {
        BlockPos stored = DormantWaystoneRegistry.getReturnPos(surfacePos);
        if (stored != null) {
            reply(p, "§5The world folds inward. You descend to the Underneath...");
            teleportTo(p, cfg().dimTarget, stored.up());
            SrpWizCore.LOGGER.info("[srpwizcore] dormant: {} ENTER (reuse) {} -> d{} {}",
                    p.getName(), surfacePos, cfg().dimTarget, stored);
            return;
        }
        reply(p, "§5The world folds inward. You descend to the Underneath...");
        BlockPos landed = enterWithPocketSearch(p);      // p is now in dimTarget
        BlockPos floor = landed.down();
        if (DormantBlocks.DORMANT_WAYSTONE != null) {
            p.world.setBlockState(floor, DormantBlocks.DORMANT_WAYSTONE.getDefaultState());
        }
        DormantWaystoneRegistry.registerWaystone(p.world, floor); // native fix (see class javadoc)
        DormantWaystoneRegistry.linkPair(surfacePos, floor);
        SrpWizCore.LOGGER.info("[srpwizcore] dormant: {} ENTER {} -> d{} return {}",
                p.getName(), surfacePos, cfg().dimTarget, floor);
    }

    private void back(EntityPlayerMP p, BlockPos targetPos) {
        MinecraftServer server = p.world.getMinecraftServer();
        WorldServer surface = server.getWorld(cfg().dimSurface);
        BlockPos owPos = DormantWaystoneRegistry.getOverworldPos(targetPos);
        BlockPos dest;
        if (owPos != null) {
            dest = owPos.up();
            reply(p, "§5The Underneath releases you. You rise to the surface...");
        } else {
            // escape hatch: unpaired anchor OR the linked waystone was destroyed -- the player
            // must NEVER be stranded. Nearest surface waystone, else world spawn.
            BlockPos near = DormantWaystoneRegistry.nearest(surface,
                    new BlockPos((int) Math.floor(p.posX), 64, (int) Math.floor(p.posZ)));
            if (near != null) {
                dest = near.up();
                reply(p, "§5The waystone wrenches you upward to another gate...");
            } else {
                BlockPos sp = surface.getSpawnPoint();
                BlockPos top = surface.getHeight(new BlockPos(sp.getX(), 0, sp.getZ()));
                dest = new BlockPos(sp.getX(), Math.max(top.getY(), 64) + 1, sp.getZ());
                reply(p, "§5The waystone casts you back toward the world's heart...");
            }
        }
        teleportTo(p, cfg().dimSurface, dest);
        SrpWizCore.LOGGER.info("[srpwizcore] dormant: {} RETURN d{}{} -> d{} {} (paired={})",
                p.getName(), cfg().dimTarget, targetPos, cfg().dimSurface, dest, owPos != null);
    }

    /** Teleport to a KNOWN feet position in a target dim (no search). */
    private static void teleportTo(EntityPlayerMP p, int dim, final BlockPos feet) {
        if (p.dimension == dim) {
            p.connection.setPlayerLocation(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                    p.rotationYaw, p.rotationPitch);
            return;
        }
        p.changeDimension(dim, new ITeleporter() {
            @Override
            public void placeEntity(World world, net.minecraft.entity.Entity entity, float yaw) {
                if (entity instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) entity).connection.setPlayerLocation(
                            feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                            entity.rotationYaw, entity.rotationPitch);
                } else {
                    entity.setPositionAndUpdate(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                }
            }
        });
    }

    /** Enter dimTarget with a safe-pocket column search; returns the landing feet pos. */
    private static BlockPos enterWithPocketSearch(EntityPlayerMP p) {
        final BlockPos[] holder = new BlockPos[1];
        p.changeDimension(cfg().dimTarget, new ITeleporter() {
            @Override
            public void placeEntity(World world, net.minecraft.entity.Entity entity, float yaw) {
                BlockPos landed = findSafePocket(world,
                        (int) Math.floor(entity.posX), (int) Math.floor(entity.posZ));
                holder[0] = landed;
                if (entity instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) entity).connection.setPlayerLocation(
                            landed.getX() + 0.5D, landed.getY(), landed.getZ() + 0.5D,
                            entity.rotationYaw, entity.rotationPitch);
                } else {
                    entity.setPositionAndUpdate(landed.getX() + 0.5D, landed.getY(), landed.getZ() + 0.5D);
                }
            }
        });
        return holder[0] != null ? holder[0] : p.getPosition();
    }

    /**
     * Cheap single-column search for a safe standing spot (cave dims): highest floor with two
     * passable, non-lava blocks above it. Returns feet pos; falls back to scanTop.
     */
    private static BlockPos findSafePocket(World world, int x, int z) {
        for (int y = cfg().scanTop; y >= cfg().scanBottom; y--) {
            IBlockState floor = world.getBlockState(new BlockPos(x, y, z));
            if (!floor.getMaterial().isSolid() || floor.getMaterial() == Material.LAVA) {
                continue;
            }
            IBlockState a1 = world.getBlockState(new BlockPos(x, y + 1, z));
            IBlockState a2 = world.getBlockState(new BlockPos(x, y + 2, z));
            boolean clear1 = !a1.getMaterial().isSolid() && a1.getMaterial() != Material.LAVA;
            boolean clear2 = !a2.getMaterial().isSolid() && a2.getMaterial() != Material.LAVA;
            if (clear1 && clear2) {
                return new BlockPos(x, y + 1, z);
            }
        }
        return new BlockPos(x, cfg().scanTop, z);
    }
}
