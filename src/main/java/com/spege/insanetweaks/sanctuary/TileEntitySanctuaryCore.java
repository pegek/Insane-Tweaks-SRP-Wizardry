package com.spege.insanetweaks.sanctuary;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class TileEntitySanctuaryCore extends TileEntity implements ITickable {

    public static final int SLOT_FUEL = 0;
    public static final int UPGRADE_SLOTS = 4; // slots 1..4

    private final ItemStackHandler inventory = new ItemStackHandler(1 + UPGRADE_SLOTS) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };

    private int tier;            // 0 = inactive (no/incomplete pyramid)
    private int effectiveRadius; // computed from tier + upgrades
    private boolean cleanseEnabled;
    private boolean cleanseStalled; // true when cleanse wants to run but fuel == 0
    private int fuelStored;      // remaining cleanse-conversions from consumed fuel items
    private boolean initialized; // first-tick default for cleanseEnabled
    private int statusCode = SanctuaryStatus.NO_PYRAMID.ordinal(); // SanctuaryStatus ordinal

    // last display snapshot pushed to clients (server-side), for change detection
    private int sentTier = -1, sentRadius = -1, sentStatus = -1;
    private boolean sentCleanse, sentStalled, snapshotInit;

    public ItemStackHandler getInventory() { return inventory; }
    public int getTier() { return tier; }
    public int getEffectiveRadius() { return effectiveRadius; }
    public boolean isCleanseEnabled() { return cleanseEnabled; }
    public boolean isCleanseStalled() { return cleanseStalled; }
    public void setCleanseEnabled(boolean v) { this.cleanseEnabled = v; markDirty(); }
    public int getStatusCode() { return statusCode; }
    public SanctuaryStatus getStatus() { return SanctuaryStatus.byId(statusCode); }

    // setters used by tick logic (Task 5/9)
    void setTier(int t) { this.tier = t; }
    void setEffectiveRadius(int r) { this.effectiveRadius = r; }
    void setCleanseStalled(boolean v) { this.cleanseStalled = v; }
    public int getFuelStored() { return fuelStored; }
    void setFuelStored(int v) { this.fuelStored = v; }
    boolean isInitialized() { return initialized; }
    void markInitialized() { this.initialized = true; }

    private int pyramidTickCounter;

    private boolean isPyramidBlock(net.minecraft.block.state.IBlockState state) {
        net.minecraft.util.ResourceLocation rn = state.getBlock().getRegistryName();
        if (rn == null) { return false; }
        String s = rn.toString();
        for (String allowed : com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidBlocks) {
            if (allowed.equalsIgnoreCase(s)) { return true; }
        }
        return false;
    }

    /** Beacon-style: count complete pyramid layers directly below the core. Returns 0..4.
     *  Layer L is the (2L+1)x(2L+1) square of blocks at y = coreY - L. Layers must be
     *  contiguous from the top: the first incomplete layer stops the count. */
    private int scanPyramidTier() {
        int tiers = 0;
        for (int layer = 1; layer <= 4; layer++) {
            int y = pos.getY() - layer;
            boolean complete = true;
            for (int dx = -layer; dx <= layer && complete; dx++) {
                for (int dz = -layer; dz <= layer; dz++) {
                    net.minecraft.util.math.BlockPos p =
                            new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    if (!isPyramidBlock(world.getBlockState(p))) {
                        complete = false;
                        break;
                    }
                }
            }
            if (complete) { tiers = layer; } else { break; }
        }
        return tiers;
    }

    private int countUpgradeRadiusItems() {
        int n = 0;
        for (int slot = 1; slot <= UPGRADE_SLOTS; slot++) {
            if (!getInventory().getStackInSlot(slot).isEmpty()) { n++; }
        }
        return n;
    }

    private void revalidateAndSync() {
        int newTier = scanPyramidTier();
        int radius = 0;
        if (newTier >= 1) {
            int[] radii = com.spege.insanetweaks.config.ModConfig.sanctuary.tierRadii;
            if (radii.length == 0) {
                radius = 0;
            } else {
                int base = radii[Math.min(newTier, radii.length) - 1];
                radius = base + countUpgradeRadiusItems() * com.spege.insanetweaks.config.ModConfig.sanctuary.upgradeRadiusBonus;
                radius = Math.min(radius, 256);
            }
        }
        int oldTier = tier;
        setTier(newTier);
        setEffectiveRadius(radius);
        statusCode = (newTier >= 1) ? SanctuaryStatus.ACTIVE.ordinal()
                                    : SanctuaryStatus.NO_PYRAMID.ordinal();
        SanctuaryWorldData.get(world).setRegion(pos, radius); // radius<=0 removes
        markDirty();
        if (com.spege.insanetweaks.config.ModConfig.sanctuary.debugLogging && newTier != oldTier) {
            com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] Sanctuary @ (" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + ") dim" + world.provider.getDimension() + ": tier=" + newTier + " radius=" + radius
                + " status=" + SanctuaryStatus.byId(statusCode)
                + " cleanse=" + CleanseState.of(newTier, cleanseEnabled, cleanseStalled));
        }
    }

    public void onRemovedFromWorld() {
        if (world != null && !world.isRemote) {
            SanctuaryWorldData.get(world).removeRegion(pos);
        }
    }

    @Override
    public void update() {
        if (world == null) { return; }
        if (world.isRemote) { clientParticleTick(); return; }
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary) { return; }
        if (com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper.isDimensionBlacklisted(world)) {
            if (getTier() != 0 || statusCode != SanctuaryStatus.DIM_BLACKLISTED.ordinal()) {
                setTier(0); setEffectiveRadius(0);
                statusCode = SanctuaryStatus.DIM_BLACKLISTED.ordinal();
                SanctuaryWorldData.get(world).removeRegion(pos);
                markDirty();
            }
            syncDisplayIfChanged();
            return;
        }
        if (!isInitialized()) {
            setCleanseEnabled(com.spege.insanetweaks.config.ModConfig.sanctuary.cleanseEnabledByDefault);
            markInitialized();
        }
        if (++pyramidTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidRevalidateInterval) {
            pyramidTickCounter = 0;
            revalidateAndSync();
        }
        runCleanse();
        syncDisplayIfChanged();
    }

    /** Server-side: if any display field changed since the last push, send a block update. */
    private void syncDisplayIfChanged() {
        boolean changed = !snapshotInit
                || tier != sentTier || effectiveRadius != sentRadius || statusCode != sentStatus
                || cleanseEnabled != sentCleanse || cleanseStalled != sentStalled;
        if (!changed) { return; }

        if (snapshotInit) { // don't announce on first load
            boolean wasActive = sentTier >= 1;
            boolean nowActive = tier >= 1;
            if (!wasActive && nowActive) { announce("msg.insanetweaks.sanctuary.activated", tier, effectiveRadius); }
            else if (wasActive && !nowActive) { announce("msg.insanetweaks.sanctuary.deactivated"); }
            if (!sentStalled && cleanseStalled) { announce("msg.insanetweaks.sanctuary.stalled"); }
            else if (sentStalled && !cleanseStalled && tier >= 1 && cleanseEnabled) { announce("msg.insanetweaks.sanctuary.resumed"); }
        }

        sentTier = tier; sentRadius = effectiveRadius; sentStatus = statusCode;
        sentCleanse = cleanseEnabled; sentStalled = cleanseStalled; snapshotInit = true;
        net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, st, st, 3);
    }

    public void sendStatusTo(net.minecraft.entity.player.EntityPlayer player) {
        CleanseState cs = CleanseState.of(tier, cleanseEnabled, cleanseStalled);
        player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                "msg.insanetweaks.sanctuary.status", tier, effectiveRadius,
                (tier >= 1 ? "ON" : "OFF"), cs.name(), fuelStored));
    }

    private void announce(String key, Object... args) {
        if (world == null || world.isRemote) { return; }
        net.minecraft.util.text.TextComponentTranslation msg =
                new net.minecraft.util.text.TextComponentTranslation(key, args);
        double r = Math.max(effectiveRadius, 8);
        for (net.minecraft.entity.player.EntityPlayer p : world.playerEntities) {
            double dx = p.posX - (pos.getX() + 0.5);
            double dz = p.posZ - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= r * r) { p.sendMessage(msg); }
        }
    }

    private int cleanseCursor; // rolling index over the cylinder volume

    private boolean consumeFuelUnit() {
        if (fuelStored > 0) { fuelStored--; markDirty(); return true; }
        net.minecraft.item.ItemStack stack = inventory.getStackInSlot(SLOT_FUEL);
        if (stack.isEmpty()) { return false; }
        net.minecraft.util.ResourceLocation rn = stack.getItem().getRegistryName();
        if (rn == null) { return false; }
        int value = fuelValueFor(rn.toString());
        if (value <= 0) { return false; }
        stack.shrink(1);
        inventory.setStackInSlot(SLOT_FUEL, stack);
        fuelStored = value - 1; // consume one conversion now
        markDirty();
        return true;
    }

    private static int fuelValueFor(String registryName) {
        for (String line : com.spege.insanetweaks.config.ModConfig.sanctuary.fuelItems) {
            int eq = line.indexOf('=');
            if (eq <= 0) { continue; }
            if (line.substring(0, eq).trim().equalsIgnoreCase(registryName)) {
                try { return Integer.parseInt(line.substring(eq + 1).trim()); } catch (NumberFormatException ex) { return 0; }
            }
        }
        return 0;
    }

    private void runCleanse() {
        if (!cleanseEnabled || tier < 1 || effectiveRadius <= 0) { cleanseStalled = false; return; }
        int r = effectiveRadius;
        int diameter = r * 2 + 1;
        int height = world.getHeight(); // full column
        long total = (long) diameter * diameter * height;
        if (total <= 0) { cleanseStalled = false; return; }
        int scanBudget = com.spege.insanetweaks.config.ModConfig.sanctuary.cleanseScanPerTick;
        int convertBudget = com.spege.insanetweaks.config.ModConfig.sanctuary.cleanseBlocksPerTick;
        int converted = 0;
        for (int i = 0; i < scanBudget && converted < convertBudget; i++) {
            int idx = (int) (((long) cleanseCursor) % total);
            cleanseCursor = (int) (((long) cleanseCursor + 1) % total);
            int y = idx / (diameter * diameter);
            int rem = idx % (diameter * diameter);
            int dx = (rem / diameter) - r;
            int dz = (rem % diameter) - r;
            if ((long) dx * dx + (long) dz * dz > (long) r * r) { continue; }
            net.minecraft.util.math.BlockPos p = new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
            if (!world.isBlockLoaded(p)) { continue; }
            if (!isInfestedQuick(p)) { continue; }
            // cleanseStalled is latched: it stays set until fuel is next consumed, so the status
            // line and transition messages don't flicker on ticks whose scan misses infested blocks.
            if (!consumeFuelUnit()) { cleanseStalled = true; return; }
            cleanseStalled = false;
            if (com.spege.insanetweaks.sanctuary.SanctuaryCleanseHelper.tryCleanse(world, p)) { converted++; }
        }
    }

    private boolean isInfestedQuick(net.minecraft.util.math.BlockPos p) {
        if (!world.isBlockLoaded(p)) { return false; }
        return com.spege.insanetweaks.util.SrpPurificationHelper.isSrpInfested(world.getBlockState(p));
    }

    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private int particleTimer;

    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private void clientParticleTick() {
        if (!com.spege.insanetweaks.config.ModConfig.sanctuary.particleBorder) { return; }
        if (tier < 1 || effectiveRadius <= 0) { return; }
        // EBW's SPHERE ("dome") particle is anchored at the core, so it is NOT distance-culled the way
        // a ring of boundary particles would be at large radii, and it draws the whole protection shell.
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("ebwizardry")) { return; }
        // Re-emit a little before the previous sphere fades (time 25 vs interval 20) so it never blinks.
        if (++particleTimer < 20) { return; }
        particleTimer = 0;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        electroblob.wizardry.util.ParticleBuilder
                .create(electroblob.wizardry.util.ParticleBuilder.Type.SPHERE)
                .pos(cx, cy, cz)
                .scale((float) effectiveRadius)
                .clr(0.55F, 0.75F, 1.0F)
                .time(25)
                .spawn(world);
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        inventory.deserializeNBT(c.getCompoundTag("inv"));
        tier = c.getInteger("tier");
        effectiveRadius = c.getInteger("radius");
        cleanseEnabled = c.getBoolean("cleanse");
        cleanseStalled = c.getBoolean("stalled");
        fuelStored = c.getInteger("fuel");
        initialized = c.getBoolean("init");
        if (c.hasKey("status")) { statusCode = c.getInteger("status"); }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        c.setTag("inv", inventory.serializeNBT());
        c.setInteger("tier", tier);
        c.setInteger("radius", effectiveRadius);
        c.setBoolean("cleanse", cleanseEnabled);
        c.setBoolean("stalled", cleanseStalled);
        c.setInteger("fuel", fuelStored);
        c.setBoolean("init", initialized);
        c.setInteger("status", statusCode);
        return c;
    }

    /** Compact tag of just the client-display fields (no inventory). */
    private NBTTagCompound writeDisplayTag(NBTTagCompound c) {
        c.setInteger("tier", tier);
        c.setInteger("radius", effectiveRadius);
        c.setBoolean("cleanse", cleanseEnabled);
        c.setBoolean("stalled", cleanseStalled);
        c.setInteger("status", statusCode);
        return c;
    }

    private void readDisplayTag(NBTTagCompound c) {
        tier = c.getInteger("tier");
        effectiveRadius = c.getInteger("radius");
        cleanseEnabled = c.getBoolean("cleanse");
        cleanseStalled = c.getBoolean("stalled");
        statusCode = c.getInteger("status");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeDisplayTag(super.getUpdateTag()); // super adds id + x/y/z
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, writeDisplayTag(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity pkt) {
        readDisplayTag(pkt.getNbtCompound());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readDisplayTag(tag); // do NOT call super (would readFromNBT and clear inventory client-side)
    }

    @Override
    public boolean hasCapability(Capability<?> cap, net.minecraft.util.EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, net.minecraft.util.EnumFacing side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) inventory;
        }
        return super.getCapability(cap, side);
    }
}
