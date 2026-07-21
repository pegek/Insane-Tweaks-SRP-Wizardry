package com.spege.insanetweaks.sanctuary;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
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
    private int progress;       // 0..6 consumed lure offerings; permanent. Tier derived from this.
    private int ritualTicks;    // transient: >0 while channeling a completed lure ring
    private int creativeRadius; // 0 = normal ritual mode; >0 = creative forced (tier 4 at this radius)

    // last display snapshot pushed to clients (server-side), for change detection
    private int sentTier = -1, sentRadius = -1, sentStatus = -1;
    private boolean sentCleanse, sentStalled, snapshotInit;

    public ItemStackHandler getInventory() { return inventory; }
    public int getTier() { return tier; }
    public int getProgress() { return progress; }
    public int getCreativeRadius() { return creativeRadius; }

    /** Creative-only: force this Nexus active at a fixed radius (16..256), bypassing the ritual. */
    public void setCreativeRadius(int r) {
        this.creativeRadius = r <= 0 ? 0 : Math.max(16, Math.min(256, r));
        markDirty();
        if (world != null && !world.isRemote) {
            revalidateAndSync();
        }
    }
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

    private int revalidateTickCounter;

    private static final String[] LURE_KEYS = { "one", "two", "three", "four", "five", "six" };

    /** SRP block-name lang key for the lure meta the given progress (0..5) demands, so chat shows the
     *  exact in-game lure name + colour (e.g. "Lure (Weakened)"). Clamped to the 6-lure range. */
    public static String lureNameKey(int progress) {
        int i = Math.max(0, Math.min(LURE_KEYS.length - 1, progress));
        return "tile.srparasites.evolutionlure_" + LURE_KEYS[i] + ".name";
    }

    /** Progress (0..6 consumed offerings) -> tier. T1 at 2 offerings, T2 at 4, T3 at 5, T4 at 6. */
    private static int tierFromProgress(int p) {
        if (p >= 6) { return 4; }
        if (p >= 5) { return 3; }
        if (p >= 4) { return 2; }
        if (p >= 2) { return 1; }
        return 0;
    }

    private int countUpgradeRadiusItems() {
        int n = 0;
        for (int slot = 1; slot <= UPGRADE_SLOTS; slot++) {
            if (!getInventory().getStackInSlot(slot).isEmpty()) { n++; }
        }
        return n;
    }

    private void revalidateAndSync() {
        if (creativeRadius > 0) {
            setTier(4);
            setEffectiveRadius(Math.min(256, creativeRadius));
            statusCode = SanctuaryStatus.ACTIVE.ordinal();
            SanctuaryWorldData.get(world).setRegion(pos, effectiveRadius);
            markDirty();
            return;
        }
        int newTier = tierFromProgress(progress);
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

    /** True when all 24 cells of the flat 5x5 ring at the Nexus's Y level are the demanded lure meta. */
    private boolean lureRingComplete(int meta) {
        String lureId = com.spege.insanetweaks.config.ModConfig.sanctuary.lureBlockId;
        int y = pos.getY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) { continue; } // Nexus occupies the center
                net.minecraft.util.math.BlockPos p =
                        new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                net.minecraft.block.state.IBlockState st = world.getBlockState(p);
                net.minecraft.util.ResourceLocation rn = st.getBlock().getRegistryName();
                if (rn == null || !rn.toString().equals(lureId)) { return false; }
                if (st.getBlock().getMetaFromState(st) != meta) { return false; }
            }
        }
        return true;
    }

    /** Set the 24 ring cells to air (the offering is consumed). */
    private void consumeRing() {
        int y = pos.getY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) { continue; }
                world.setBlockToAir(new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz));
            }
        }
    }

    /** Ambient channel particles while a ritual is winding down. */
    private void spawnRitualFx() {
        if (world instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) world).spawnParticle(
                    net.minecraft.util.EnumParticleTypes.SPELL_WITCH,
                    pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                    6, 1.6D, 0.3D, 1.6D, 0.02D);
        }
    }

    /** Detect a completed demanded ring, channel it for ritualDurationTicks, then consume + advance. */
    private void tickRitual() {
        if (progress >= 6) { ritualTicks = 0; return; }        // fully built
        if (!lureRingComplete(progress)) { ritualTicks = 0; return; } // no/incorrect ring -> idle
        if (ritualTicks <= 0) {
            ritualTicks = Math.max(1, com.spege.insanetweaks.config.ModConfig.sanctuary.ritualDurationTicks);
        }
        spawnRitualFx();
        if (--ritualTicks <= 0) {
            consumeRing();
            progress++;
            onProgressAdvanced();
        }
    }

    /** Called right after progress increments: refresh tier/region, play FX, notify the nearest player. */
    private void onProgressAdvanced() {
        int oldTier = tier;
        revalidateAndSync(); // tier/radius/region update immediately from the new progress

        if (world instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) world).spawnParticle(
                    net.minecraft.util.EnumParticleTypes.SPELL_MOB,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    40, 1.2D, 0.6D, 1.2D, 0.1D);
        }
        world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_END_PORTAL_SPAWN,
                net.minecraft.util.SoundCategory.BLOCKS, 0.6F, 1.4F);

        net.minecraft.entity.player.EntityPlayer p = world.getClosestPlayer(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 64.0D, false);
        if (p != null) {
            if (tier > oldTier) {
                p.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "msg.insanetweaks.sanctuary.tierup", tier));
            }
            if (progress >= 6) {
                p.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "msg.insanetweaks.sanctuary.whole"));
            } else {
                p.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "msg.insanetweaks.sanctuary.demand",
                        new net.minecraft.util.text.TextComponentTranslation(lureNameKey(progress))));
            }
        }
    }

    /** Debug-only: counts parasites in the purge cylinder and logs a summary. Runs ONLY when
     *  debugLogging is on, so the AABB scan has no cost in normal play. */
    private void logParasitesInZoneDebug() {
        if (!com.spege.insanetweaks.config.ModConfig.sanctuary.debugLogging || tier < 1) {
            return;
        }
        int cap = Math.min(effectiveRadius, com.spege.insanetweaks.config.ModConfig.sanctuary.purgeFireRadiusCap);
        net.minecraft.util.math.AxisAlignedBB box = new net.minecraft.util.math.AxisAlignedBB(
                pos.getX() - cap, 0, pos.getZ() - cap,
                pos.getX() + cap + 1, world.getHeight(), pos.getZ() + cap + 1);
        java.util.List<net.minecraft.entity.EntityLivingBase> parasites =
                world.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class, box,
                        new com.google.common.base.Predicate<net.minecraft.entity.EntityLivingBase>() {
                            @Override
                            public boolean apply(net.minecraft.entity.EntityLivingBase ent) {
                                return com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper.isSrpParasite(ent);
                            }
                        });
        com.spege.insanetweaks.sanctuary.SanctuaryDebug.log(world.getTotalWorldTime(), "in-zone",
                "parasitesInZone=" + parasites.size() + " tier=" + tier + " r=" + effectiveRadius
                + " purgeFire=" + (com.spege.insanetweaks.config.ModConfig.sanctuary.enablePurgeFire ? "on" : "off"));
    }

    public void onRemovedFromWorld() {
        if (world != null && !world.isRemote) {
            SanctuaryWorldData.get(world).removeRegion(pos);
        }
    }

    @Override
    public void update() {
        if (world == null) { return; }
        if (world.isRemote) { return; } // dome is drawn by RenderSanctuaryDome (TESR), no client tick work
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
            revalidateAndSync(); // establish tier/radius/region from stored progress on load
        }
        if (creativeRadius <= 0) { tickRitual(); }
        if (++revalidateTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.revalidateInterval) {
            revalidateTickCounter = 0;
            revalidateAndSync();
            logParasitesInZoneDebug();
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
            net.minecraft.util.ResourceLocation cleansedId = world.getBlockState(p).getBlock().getRegistryName();
            if (com.spege.insanetweaks.sanctuary.SanctuaryCleanseHelper.tryCleanse(world, p)) {
                converted++;
                com.spege.insanetweaks.sanctuary.SanctuaryDebug.log(world.getTotalWorldTime(), "cleansed",
                        "@(" + p.getX() + "," + p.getY() + "," + p.getZ() + ") " + cleansedId);
            }
        }
    }

    private boolean isInfestedQuick(net.minecraft.util.math.BlockPos p) {
        if (!world.isBlockLoaded(p)) { return false; }
        return com.spege.insanetweaks.util.SrpPurificationHelper.isSrpInfested(world.getBlockState(p));
    }

    /** Render the dome even when the core block itself is off-screen / far behind the camera. */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    /** Keep the dome renderer alive while the viewer is anywhere near the sphere (radius up to ~128). */
    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0D; // 256 blocks; per-frame distance cull in RenderSanctuaryDome does the fine gating
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        inventory.deserializeNBT(c.getCompoundTag("inv"));
        tier = c.getInteger("tier");
        progress = c.getInteger("progress"); // 0 default: pre-ritual worlds reset to tier 0, rebuild via ritual
        creativeRadius = c.getInteger("creativeRadius");
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
        c.setInteger("progress", progress);
        c.setInteger("creativeRadius", creativeRadius);
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
