package com.spege.srpwizcore.util;

import java.util.List;
import java.util.Random;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.config.categories.CqrIntegrationCategory;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Boss-room mechanics for CQR dungeons (user request 2026-08-01), pure Forge events — no
 * mixins, no tick cost, no stored region data:
 *
 * <ul>
 * <li><b>Chest lock</b> — {@code RightClickBlock} on any {@code TileEntityLockableLoot} is
 * cancelled while a LIVING CQR boss (class in {@code team.cqr...entity.boss.*} — the same
 * marker the gear swap uses; no isBoss() exists in CQR bytecode) is within
 * {@code bossChestLockRadius} blocks. The "boss within 50" proximity test IS the lock — a
 * chest with no boss nearby was never a boss-room chest.</li>
 * <li><b>Bonus loot</b> — on the boss's {@code LivingDeathEvent}, every loot container in
 * the same radius rolls {@code bossLootBookPct} for one enchanted book from the configured
 * pool (Supreme/Advanced SoManyEnchantments picks), level I–II clamped to the enchant's
 * max. One roll per chest ever, marked in the tile's ForgeData
 * ({@code srpwizcore_bossloot_done}) so overlapping boss rooms don't re-roll.</li>
 * </ul>
 *
 * <p>Alternative considered and parked: EBW's Arcane Lock NBT (visual particles + own
 * enforcement) — its lock is owner-UUID-based and would outlive the boss unless cleaned up
 * on every death path, so v1 uses the self-contained proximity check instead.
 */
public class CqrBossRoomHandler {

    private static final String LOOT_DONE_TAG = "srpwizcore_bossloot_done";

    public static long locksEnforced;
    public static long booksInjected;

    @SubscribeEvent
    public void onContainerClick(PlayerInteractEvent.RightClickBlock event) {
        try {
            CqrIntegrationCategory cfg = SrpWizCoreConfig.cqrIntegration;
            if (!cfg.bossChestLockEnabled) {
                return;
            }
            World world = event.getWorld();
            TileEntity te = world.getTileEntity(event.getPos());
            if (!(te instanceof TileEntityLockableLoot)) {
                return;
            }
            EntityLivingBase boss = findLivingBoss(world, event.getPos(), cfg.bossChestLockRadius);
            if (boss == null) {
                return;
            }
            event.setCanceled(true);
            if (!world.isRemote) {
                locksEnforced++;
                event.getEntityPlayer().sendStatusMessage(new TextComponentString(
                        "§5The chest is sealed while the dungeon's master lives..."), true);
            }
        } catch (Throwable t) {
            SrpWizCore.LOGGER.error("[srpwizcore] boss chest lock failed: {}", t.toString());
        }
    }

    @SubscribeEvent
    public void onBossDeath(LivingDeathEvent event) {
        try {
            EntityLivingBase dead = event.getEntityLiving();
            if (dead == null || dead.world == null || dead.world.isRemote) {
                return;
            }
            if (!CqrBossCheck.isCqrBoss(dead)) {
                return;
            }
            String cls = dead.getClass().getName();
            CqrIntegrationCategory cfg = SrpWizCoreConfig.cqrIntegration;
            int radius = cfg.bossChestLockRadius;
            World world = dead.world;
            BlockPos center = dead.getPosition();
            int chests = 0;
            int books = 0;
            // One-shot sweep on boss death; loadedTileEntityList is not structurally modified
            // by filling or writing slots.
            double eyeY = dead.posY + dead.getEyeHeight();
            for (TileEntity te : world.loadedTileEntityList) {
                if (!(te instanceof TileEntityLockableLoot)
                        || te.getPos().distanceSq(center) > (double) (radius * radius)) {
                    continue;
                }
                // Boss-room only: visible from the death spot, or close enough (12) that a
                // pillar between them still plausibly means the same room.
                if (te.getPos().distanceSq(center) > 144.0D
                        && !hasLineOfSight(world, te.getPos(), dead.posX, eyeY, dead.posZ)) {
                    continue;
                }
                chests++;
                if (cfg.bossLootBookPct <= 0) {
                    continue;
                }
                if (te.getTileData().getBoolean(LOOT_DONE_TAG)) {
                    continue;
                }
                te.getTileData().setBoolean(LOOT_DONE_TAG, true);
                if (world.rand.nextInt(100) >= cfg.bossLootBookPct) {
                    continue;
                }
                if (injectBook((TileEntityLockableLoot) te, world.rand, cfg.bossLootEnchants)) {
                    books++;
                    booksInjected++;
                }
            }
            List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class,
                    new AxisAlignedBB(center).grow(radius));
            for (EntityPlayer p : players) {
                p.sendStatusMessage(new TextComponentString(
                        "§dThe master is slain - the seal on the dungeon's chests is broken."),
                        true);
            }
            SrpWizCore.LOGGER.info(
                    "[srpwizcore] cqr boss death: {} at {} - {} chests unsealed, {} bonus books",
                    cls.substring(cls.lastIndexOf('.') + 1), center, Integer.valueOf(chests),
                    Integer.valueOf(books));
        } catch (Throwable t) {
            SrpWizCore.LOGGER.error("[srpwizcore] boss loot sweep failed: {}", t.toString());
        }
    }

    private static EntityLivingBase findLivingBoss(World world, BlockPos pos, int radius) {
        List<EntityLiving> candidates = world.getEntitiesWithinAABB(EntityLiving.class,
                new AxisAlignedBB(pos).grow(radius));
        for (EntityLiving e : candidates) {
            if (e.isEntityAlive() && CqrBossCheck.isCqrBoss(e)
                    && hasLineOfSight(world, pos, e.posX, e.posY + e.getEyeHeight(), e.posZ)) {
                return e;
            }
        }
        return null;
    }

    /**
     * "Same room" test (user feedback 2026-08-01: a pure radius check spilled the lock onto
     * corridor chests a floor away): the chest counts as boss-room only when the boss is
     * VISIBLE from just above the chest — any wall or floor between them breaks the seal.
     * Block-only raytrace, runs solely on right-click/death events.
     */
    private static boolean hasLineOfSight(World world, BlockPos chestPos,
            double x, double y, double z) {
        net.minecraft.util.math.Vec3d from = new net.minecraft.util.math.Vec3d(
                chestPos.getX() + 0.5D, chestPos.getY() + 1.1D, chestPos.getZ() + 0.5D);
        net.minecraft.util.math.Vec3d to = new net.minecraft.util.math.Vec3d(x, y, z);
        return world.rayTraceBlocks(from, to, false, true, false) == null;
    }

    /** Puts one random pool book into the first empty slot; triggers pending loot first. */
    private static boolean injectBook(TileEntityLockableLoot chest, Random rand, String[] pool) {
        if (pool == null || pool.length == 0) {
            return false;
        }
        Enchantment ench = null;
        // A few draws in case the config names an enchant that is not registered.
        for (int i = 0; i < 4 && ench == null; i++) {
            String id = pool[rand.nextInt(pool.length)];
            ench = Enchantment.REGISTRY.getObject(new ResourceLocation(id));
            if (ench == null) {
                SrpWizCore.LOGGER.warn(
                        "[srpwizcore] boss loot: unknown enchant id '{}' in pool", id);
            }
        }
        if (ench == null) {
            return false;
        }
        int level = Math.min(1 + rand.nextInt(2), ench.getMaxLevel());
        ItemStack book = ItemEnchantedBook.getEnchantedItemStack(new EnchantmentData(ench, level));
        // Generates any pending loot table with a null player before we look at the slots.
        chest.fillWithLoot(null);
        for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
            if (chest.getStackInSlot(slot).isEmpty()) {
                chest.setInventorySlotContents(slot, book);
                chest.markDirty();
                return true;
            }
        }
        return false;
    }
}
