package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.ModConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class GraveDecayHandler {

    private final Map<TileEntity, Long> lastDecayTick = new WeakHashMap<>();

    private Class<?> graveClass = null;
    private Method getInventoryMethod = null;
    private Field countTicksField = null;
    private Method getOwnerNameMethod = null;
    private boolean reflectionReady = false;

    private boolean setupReflection(TileEntity te) {
        if (reflectionReady) return true;
        try {
            graveClass = te.getClass();
            getInventoryMethod = graveClass.getMethod("getInventory");
            countTicksField = graveClass.getSuperclass().getField("countTicks");
            getOwnerNameMethod = graveClass.getMethod("getOwnerName");
            reflectionReady = true;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }

        if (!ModConfig.tombstone.enableTombstoneTweaks || !ModConfig.tombstone.enableGraveItemDecay) {
            return;
        }

        long worldTime = event.world.getTotalWorldTime();

        // Check only once every 40 ticks (2 seconds) for performance
        if (worldTime % 40 != 0) {
            return;
        }

        List<TileEntity> tiles = new ArrayList<>(event.world.loadedTileEntityList);

        for (TileEntity te : tiles) {
            if (!te.getClass().getName().equals("ovh.corail.tombstone.tileentity.TileEntityPlayerGrave")) {
                continue;
            }

            if (!setupReflection(te)) {
                continue;
            }

            try {
                int countTicks = countTicksField.getInt(te);
                if (countTicks < ModConfig.tombstone.graveDecayStartTicks) {
                    continue;
                }

                long lastDecay = lastDecayTick.getOrDefault(te, 0L);
                if (worldTime - lastDecay < ModConfig.tombstone.graveDecayIntervalTicks) {
                    continue;
                }

                decayRandomItemFromGrave(te, event.world);
                lastDecayTick.put(te, worldTime);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void decayRandomItemFromGrave(TileEntity grave, World world) {
        try {
            IItemHandler inv = (IItemHandler) getInventoryMethod.invoke(grave);
            if (inv == null) return;

            List<Integer> validSlots = new ArrayList<>();
            for (int i = 0; i < inv.getSlots(); i++) {
                if (!inv.getStackInSlot(i).isEmpty()) {
                    validSlots.add(i);
                }
            }

            if (validSlots.isEmpty()) {
                return; // Grave is empty
            }

            int selectedSlot = validSlots.get(world.rand.nextInt(validSlots.size()));
            ItemStack toDrop = inv.extractItem(selectedSlot, inv.getStackInSlot(selectedSlot).getCount(), false);

            if (!toDrop.isEmpty()) {
                BlockPos pos = grave.getPos();
                InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, toDrop);

                String ownerName = (String) getOwnerNameMethod.invoke(grave);
                saveDecayToHistory(grave, world.getMinecraftServer(), toDrop, ownerName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveDecayToHistory(TileEntity grave, MinecraftServer server, ItemStack decayedStack, String ownerName) {
        if (server == null || ownerName == null || ownerName.isEmpty()) return;

        EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(ownerName);
        if (player == null) return; 

        NBTTagCompound playerData = player.getEntityData();
        NBTTagCompound persistentData = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);

        NBTTagList decayHistory = persistentData.getTagList("GraveDecayHistory", 10);
        
        String currentTime = new SimpleDateFormat("dd.MM:HH:mm").format(new Date());
        String gravePosStr = grave.getPos().getX() + "," + grave.getPos().getY() + "," + grave.getPos().getZ() + ",dim:" + player.dimension;
        
        NBTTagCompound latestEntry = null;
        if (decayHistory.tagCount() > 0) {
            latestEntry = decayHistory.getCompoundTagAt(decayHistory.tagCount() - 1);
            if (!latestEntry.getString("GravePos").equals(gravePosStr)) {
                latestEntry = null; // different grave
            }
        }

        if (latestEntry == null) {
            latestEntry = new NBTTagCompound();
            latestEntry.setString("Time", currentTime);
            latestEntry.setString("GravePos", gravePosStr);
            latestEntry.setTag("Items", new NBTTagList());
            decayHistory.appendTag(latestEntry);
        } else {
            latestEntry.setString("Time", currentTime);
        }

        NBTTagList itemsList = latestEntry.getTagList("Items", 10);
        itemsList.appendTag(decayedStack.writeToNBT(new NBTTagCompound()));

        int maxHistory = ModConfig.tombstone.graveDecayMaxHistory;
        while (decayHistory.tagCount() > maxHistory && decayHistory.tagCount() > 0) {
            decayHistory.removeTag(0);
        }

        if (!playerData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            playerData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistentData);
        }
    }
}
