package com.spege.insanetweaks.entities;

import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Manages the 3 persistent thrall slots per player.
 * State is stored in {@code player.getEntityData()} under key "InsaneTweaksThralls".
 *
 * <p>Each slot entry (NBTTagCompound) contains:
 * <ul>
 *   <li>"Slot"          – int 1-3</li>
 *   <li>"ThrallUUID"    – string UUID (empty = slot unused)</li>
 *   <li>"ThrallInventory" – NBTTagList backup</li>
 *   <li>"HomePos"       – string "x,y,z" (empty = not set)</li>
 *   <li>"Mode"          – int ordinal of ThrallMode</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class ThrallSlotManager {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallSlots");
    private static final String NBT_KEY   = "InsaneTweaksThralls";
    /** Live cap from ModConfig.thrall.general.maxSlotsPerPlayer. Read each access so changes apply without restart. */
    public static int maxSlots() {
        return com.spege.insanetweaks.config.ModConfig.thrall.general.maxSlotsPerPlayer;
    }

    private ThrallSlotManager() {}

    // -------------------------------------------------------------------------
    // Read helpers
    // -------------------------------------------------------------------------

    /** Returns the slot compound list from player data, creating it if absent. */
    private static NBTTagList getSlotList(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(NBT_KEY, Constants.NBT.TAG_LIST)) {
            data.setTag(NBT_KEY, new NBTTagList());
        }
        return data.getTagList(NBT_KEY, Constants.NBT.TAG_COMPOUND);
    }

    /** Returns the compound for a specific slot number (1-3), or null. */
    @Nullable
    private static NBTTagCompound getSlotEntry(EntityPlayer player, int slot) {
        NBTTagList list = getSlotList(player);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.getInteger("Slot") == slot) return entry;
        }
        return null;
    }

    /** Returns or creates a compound for the given slot number. */
    private static NBTTagCompound getOrCreateSlotEntry(EntityPlayer player, int slot) {
        NBTTagList list = getSlotList(player);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.getInteger("Slot") == slot) return entry;
        }
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Slot", slot);
        entry.setString("ThrallUUID", "");
        list.appendTag(entry);
        player.getEntityData().setTag(NBT_KEY, list);
        return entry;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the first free slot number (1..maxSlots()), or -1 if all are occupied.
     */
    public static int findFreeSlot(EntityPlayer player) {
        for (int slot = 1; slot <= maxSlots(); slot++) {
            NBTTagCompound entry = getSlotEntry(player, slot);
            if (entry == null || entry.getString("ThrallUUID").isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Returns the slot number for the given thrall UUID, or -1 if not found.
     */
    public static int getSlotForUUID(EntityPlayer player, UUID uuid) {
        NBTTagList list = getSlotList(player);
        String uuidStr = uuid.toString();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (uuidStr.equals(entry.getString("ThrallUUID"))) {
                return entry.getInteger("Slot");
            }
        }
        return -1;
    }

    /**
     * Saves the thrall's current state (inventory, home, mode) into the player's slot data.
     * Also assigns the slot number to the thrall if not already set.
     */
    public static void saveSlot(EntityPlayer player, EntityThrallMinion thrall) {
        int slot = thrall.getThrallSlot();
        if (slot < 1 || slot > maxSlots()) return;

        NBTTagCompound entry = getOrCreateSlotEntry(player, slot);
        entry.setString("ThrallUUID", thrall.getUniqueID().toString());
        entry.setTag("ThrallInventory", thrall.getThrallInventory().writeToNBT());
        entry.setInteger("Mode", thrall.getMode().ordinal());

        BlockPos home = thrall.getHomePoint();
        entry.setString("HomePos", home == null ? "" :
                home.getX() + "," + home.getY() + "," + home.getZ());

        player.getEntityData().setTag(NBT_KEY, getSlotList(player));
        LOG.debug("[ThrallSlots] Saved slot {} for player {} (UUID={})",
                slot, player.getName(), thrall.getUniqueID());
    }

    /**
     * Clears the UUID from a slot on dismiss (keeps inventory backup for re-summon).
     */
    public static void clearSlotUUID(EntityPlayer player, int slot) {
        NBTTagCompound entry = getSlotEntry(player, slot);
        if (entry != null) {
            entry.setString("ThrallUUID", "");
            LOG.info("[ThrallSlots] Cleared UUID for slot {} (player {})", slot, player.getName());
        }
    }

    /**
     * Assigns a new thrall entity to the given slot, restoring backed-up inventory/home/mode.
     * Call this right after spawning a new thrall for an empty (or re-summoned) slot.
     */
    public static void assignToSlot(EntityPlayer player, EntityThrallMinion thrall, int slot) {
        NBTTagCompound entry = getOrCreateSlotEntry(player, slot);
        entry.setString("ThrallUUID", thrall.getUniqueID().toString());

        // Restore backed-up inventory if present
        if (entry.hasKey("ThrallInventory")) {
            thrall.getThrallInventory().readFromNBT(
                    entry.getTagList("ThrallInventory", Constants.NBT.TAG_COMPOUND));
            LOG.info("[ThrallSlots] Restored inventory for slot {} thrall", slot);
        }

        // Restore home point
        String homeStr = entry.getString("HomePos");
        if (!homeStr.isEmpty()) {
            try {
                String[] parts = homeStr.split(",");
                thrall.setHomePoint(new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])));
            } catch (Exception ignored) {}
        }

        // Restore mode
        if (entry.hasKey("Mode")) {
            ThrallMode[] modes = ThrallMode.values();
            int ordinal = entry.getInteger("Mode");
            if (ordinal >= 0 && ordinal < modes.length) {
                thrall.setMode(modes[ordinal]);
            }
        }

        thrall.setThrallSlot(slot);
        player.getEntityData().setTag(NBT_KEY, getSlotList(player));
        LOG.info("[ThrallSlots] Assigned thrall {} to slot {} for player {}",
                thrall.getUniqueID(), slot, player.getName());
    }

    /**
     * Tries to recall (teleport) the live thrall for the given slot to the player.
     * If the thrall entity is not loaded/alive, spawns a new one with restored inventory.
     * If the slot is free, spawns a new thrall and assigns it.
     *
     * @return true if recall/spawn succeeded, false if all slots full and no recall possible.
     */
    public static boolean recallOrSpawn(EntityPlayerMP player, World world) {
        // Find a slot to work with
        int slot = -1;

        // First try: recall any existing live thrall
        NBTTagList list = getSlotList(player);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.getInteger("Slot") > maxSlots()) continue; // Ignore legacy slots
            
            String uuidStr = entry.getString("ThrallUUID");
            if (uuidStr.isEmpty()) continue;

            try {
                UUID uuid = UUID.fromString(uuidStr);
                EntityThrallMinion thrall = findThrallByUUID(world, uuid);
                if (thrall != null && thrall.isEntityAlive()) {
                    // Recall: teleport to player
                    slot = entry.getInteger("Slot");
                    double angle = Math.toRadians(player.rotationYaw + 180.0);
                    double tx = player.posX + Math.sin(angle) * 2.0;
                    double ty = player.posY;
                    double tz = player.posZ + Math.cos(angle) * 2.0;
                    thrall.setPositionAndUpdate(tx, ty, tz);
                    thrall.getNavigator().clearPath();
                    com.spege.insanetweaks.util.SpellCastFeedback.srpBurst(world,
                            tx, ty + 1.0D, tz,
                            com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle.FLASH,
                            0x781414, 2, 0.4F, 0.5F, 0.01F);
                    com.spege.insanetweaks.util.SpellCastFeedback.srpBurst(world,
                            tx, ty + 0.2D, tz,
                            com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle.GCLOUD,
                            0x503232, 4, 0.4F, 0.2F, 0.01F);
                    thrall.smartDeposit(new net.minecraft.util.math.BlockPos(thrall));
                    // Give the player 10 seconds to issue a new order; if none comes, the thrall resumes its prior task.
                    thrall.pauseAfterResummon(200);
                    LOG.info("[ThrallSlots] Recalled slot {} thrall to player {}", slot, player.getName());
                    return true;
                } else {
                    // Entity dead/unloaded — re-spawn with backup
                    slot = entry.getInteger("Slot");
                    EntityThrallMinion thrall2 = spawnThrall(player, world);
                    assignToSlot(player, thrall2, slot);
                    // Same grace period for a re-spawn — assignToSlot may restore a work mode that
                    // the player no longer wants resumed at the new (player-relative) position.
                    thrall2.pauseAfterResummon(200);
                    LOG.info("[ThrallSlots] Re-spawned slot {} thrall for player {}", slot, player.getName());
                    return true;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // No existing thrall found — try free slot
        slot = findFreeSlot(player);
        if (slot == -1) return false; // all slots truly occupied

        EntityThrallMinion thrall = spawnThrall(player, world);
        assignToSlot(player, thrall, slot);
        return true;
    }

    /** Spawns a fresh thrall near the player and adds it to the world. */
    private static EntityThrallMinion spawnThrall(EntityPlayerMP player, World world) {
        EntityThrallMinion thrall = new EntityThrallMinion(world);
        thrall.setOwnerUUID(player.getUniqueID());
        thrall.setMode(ThrallMode.FOLLOW);
        thrall.setStatusText("Following");

        double dx = (world.rand.nextDouble() - 0.5) * 2.0;
        double dz = (world.rand.nextDouble() - 0.5) * 2.0;
        thrall.setLocationAndAngles(
                player.posX + dx, player.posY, player.posZ + dz,
                player.rotationYaw, 0.0F);
        world.spawnEntity(thrall);

        com.spege.insanetweaks.util.SpellCastFeedback.srpBurst(world,
                thrall.posX, thrall.posY + 1.0D, thrall.posZ,
                com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle.FLASH,
                0x781414, 2, 0.4F, 0.5F, 0.01F);
        com.spege.insanetweaks.util.SpellCastFeedback.srpBurst(world,
                thrall.posX, thrall.posY + 0.2D, thrall.posZ,
                com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle.GCLOUD,
                0x503232, 4, 0.4F, 0.2F, 0.01F);
        return thrall;
    }

    /** Searches loaded entities in the world for a thrall with the given UUID. */
    @Nullable
    private static EntityThrallMinion findThrallByUUID(World world, UUID uuid) {
        for (EntityThrallMinion t : world.getEntities(EntityThrallMinion.class, e -> e != null)) {
            if (uuid.equals(t.getUniqueID())) return t;
        }
        return null;
    }
}
