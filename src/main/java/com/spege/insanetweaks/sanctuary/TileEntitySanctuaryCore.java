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
    private java.util.UUID ownerId;   // player who placed this ritual Nexus (null for creative / unowned)
    private String ownerName = "";    // cached owner name for the GUI nameplate

    // "Cost of Power" (see SanctuaryCostCategory): fuel upkeep + empty-tank drain escalation.
    private boolean unfueled;   // true once an upkeep charge went unpaid; drives Layer B/C drain
    private boolean sentStarving; // owner-warning latch for the unfueled transition
    private int upkeepCounter;   // ticks toward the next upkeep charge
    private int drainCounter;    // ticks toward the next drain pulse
    private int biomeResetCounter; // ticks toward the next native biome reset (R1)
    private boolean registered;  // whether this TE is currently in SanctuaryRegistry

    /** Life-essence tithe drained from the owner when no wand mana is available inside. */
    public static final net.minecraft.util.DamageSource SANCTUARY_TITHE =
            new net.minecraft.util.DamageSource("sanctuary_tithe").setDamageBypassesArmor().setMagicDamage();

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
    public java.util.UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }

    /** Bind this ritual Nexus to its placing player (owner nameplate + per-player limit). */
    public void setOwner(java.util.UUID id, String name) {
        this.ownerId = id;
        this.ownerName = name == null ? "" : name;
        markDirty();
    }
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

    // --- Upgrade slots: each of the 4 slots is bound to one specific item (see SanctuaryCostCategory). ---
    // Slot 1 = U1 (efficiency), 2 = U2 (owner HP relief), 3 = U3 (+radius), 4 = U4 (ascension).

    private boolean slotHas(int slot, String regName, int meta, int count) {
        net.minecraft.item.ItemStack s = inventory.getStackInSlot(slot);
        if (s.isEmpty() || s.getCount() < count) { return false; }
        net.minecraft.util.ResourceLocation rn = s.getItem().getRegistryName();
        if (rn == null || regName == null || !rn.toString().equals(regName)) { return false; }
        if (meta >= 0 && s.getMetadata() != meta) { return false; }
        return true;
    }

    public boolean u1Active() {
        return slotHas(1, com.spege.insanetweaks.config.ModConfig.sanctuaryCost.upgradeItemU1, -1, 1);
    }
    public boolean u2Active() {
        com.spege.insanetweaks.config.categories.SanctuaryCostCategory c =
                com.spege.insanetweaks.config.ModConfig.sanctuaryCost;
        return slotHas(2, c.upgradeItemU2, c.u2Meta, c.u2Count);
    }
    public boolean u3Active() {
        return slotHas(3, com.spege.insanetweaks.config.ModConfig.sanctuaryCost.upgradeItemU3, -1, 1);
    }
    /** Ascension: the U4 item AND all three lower upgrades present. */
    public boolean u4Active() {
        return slotHas(4, com.spege.insanetweaks.config.ModConfig.sanctuaryCost.upgradeItemU4, -1, 1)
                && u1Active() && u2Active() && u3Active();
    }

    /** True when this Sanctuary imposes no presence tax (creative, U4 ascension, or cost disabled). */
    public boolean penaltiesSuppressed() {
        return creativeRadius > 0
                || !com.spege.insanetweaks.config.ModConfig.sanctuaryCost.enableCost
                || u4Active();
    }

    public boolean isOwner(net.minecraft.entity.player.EntityPlayer player) {
        return ownerId != null && player != null && ownerId.equals(player.getUniqueID());
    }

    /** Radius bonus (blocks) from the +radius upgrades: U3 and U4 each grant Upgrade Radius Bonus. */
    private int upgradeRadiusBonus() {
        int bonus = com.spege.insanetweaks.config.ModConfig.sanctuary.upgradeRadiusBonus;
        int total = 0;
        if (u3Active()) { total += bonus; }
        if (u4Active()) { total += bonus; }
        return total;
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
                radius = base + upgradeRadiusBonus();
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

    /** Ticks the Nexus channels a completed lure ring before consuming it (40 = 2s). */
    private static final int RITUAL_DURATION_TICKS = 40;

    /** Detect a completed demanded ring, channel it for RITUAL_DURATION_TICKS, then consume + advance. */
    private void tickRitual() {
        if (progress >= 6) { ritualTicks = 0; return; }        // fully built
        if (!lureRingComplete(progress)) { ritualTicks = 0; return; } // no/incorrect ring -> idle
        if (ritualTicks <= 0) {
            ritualTicks = RITUAL_DURATION_TICKS;
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
            if (ownerId != null) {
                SanctuaryOwnerData.get(world).remove(ownerId, world.provider.getDimension(), pos);
            }
        }
        deregister();
    }

    /** Drop out of the loaded-active index (removal, chunk unload, or TE invalidation). */
    private void deregister() {
        if (registered) {
            com.spege.insanetweaks.sanctuary.SanctuaryRegistry.unregister(this);
            registered = false;
        }
    }

    @Override
    public void invalidate() {
        deregister();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        deregister();
        super.onChunkUnload();
    }

    @Override
    public void update() {
        if (world == null) { return; }
        if (world.isRemote) { clientPulseTick(); return; } // dome = TESR; locator ping = clientPulseTick
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
            if (ownerId != null) { // self-heal the global owner registry for a loaded owned Nexus
                SanctuaryOwnerData.get(world).add(ownerId, world.provider.getDimension(), pos);
            }
        }
        if (creativeRadius <= 0) { tickRitual(); }
        if (++revalidateTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.revalidateInterval) {
            revalidateTickCounter = 0;
            revalidateAndSync();
            logParasitesInZoneDebug();
        }
        syncRegistry();
        tickCost();
        tickBiomeReset();
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

    /**
     * R1: periodically reset parasite biomes to natural within the dome, via SRP's own throttled
     * BiomeUpdateQueue (killBiome). This kills the biome-driven spread at the root without the
     * cleanse tug-of-war. Radius is capped (config) to stay within loaded chunks near the Nexus —
     * a large radius would probe unloaded chunks. Self-limiting: once a biome is natural again it
     * is not re-enqueued.
     */
    private void tickBiomeReset() {
        if (!com.spege.insanetweaks.config.ModConfig.sanctuary.nativeBiomeReset || tier < 1) { return; }
        if (!com.spege.insanetweaks.util.SrpNativePurifyHelper.isAvailable()) { return; }
        int interval = com.spege.insanetweaks.config.ModConfig.sanctuary.biomeResetIntervalTicks;
        if (++biomeResetCounter < interval) { return; }
        biomeResetCounter = 0;
        // Cover the WHOLE dome (capped at 256): on a large dome the parasite biome outside a small
        // radius keeps spreading and re-infesting faster than the block cleanse can revert it, so
        // the biome reset must reach the dome edge to stop the spread at its root. killBiome uses
        // World.getBiome (no chunk force-gen) and only rewrites already-infested columns, so a
        // dome-sized radius is safe within the loaded area around an active Nexus.
        int radius = Math.min(effectiveRadius, 256);
        if (radius > 0) {
            com.spege.insanetweaks.util.SrpNativePurifyHelper.killBiome(world, pos, radius);
        }
    }

    /** Keep this TE's membership in the loaded-active index in sync with its tier. */
    private void syncRegistry() {
        boolean shouldBe = tier >= 1;
        if (shouldBe && !registered) {
            com.spege.insanetweaks.sanctuary.SanctuaryRegistry.register(this);
            registered = true;
        } else if (!shouldBe && registered) {
            com.spege.insanetweaks.sanctuary.SanctuaryRegistry.unregister(this);
            registered = false;
        }
    }

    /**
     * The "Cost of Power" tick: burn mana-fuel for upkeep, and once the tank runs dry, escalate to
     * draining wand mana from casters inside (Layer B) and, if none is available, the owner's HP
     * (Layer C). A fully-ascended (U4) Sanctuary pays nothing. Server-side only.
     */
    private void tickCost() {
        com.spege.insanetweaks.config.categories.SanctuaryCostCategory c =
                com.spege.insanetweaks.config.ModConfig.sanctuaryCost;
        if (!c.enableCost || tier < 1) { unfueled = false; sentStarving = false; return; }
        if (creativeRadius > 0) { unfueled = false; sentStarving = false; return; } // creative: always free
        if (u4Active()) { unfueled = false; sentStarving = false; return; } // ascended: free forever

        // Upkeep: spend fuel every interval (U1 doubles the interval -> half the burn rate).
        int interval = u1Active() ? c.upkeepIntervalTicks * 2 : c.upkeepIntervalTicks;
        if (++upkeepCounter >= interval) {
            upkeepCounter = 0;
            boolean paid = true;
            for (int i = 0; i < c.upkeepCost; i++) {
                if (!consumeFuelUnit()) { paid = false; break; }
            }
            unfueled = !paid;
        }

        if (unfueled) {
            if (!sentStarving) { warnOwner("msg.insanetweaks.sanctuary.starving"); sentStarving = true; }
            if (++drainCounter >= c.drainIntervalTicks) {
                drainCounter = 0;
                drainPulse(c);
            }
        } else {
            sentStarving = false;
        }
    }

    /** One drain pulse: mana from every wand-carrying player inside, else the owner's HP. */
    private void drainPulse(com.spege.insanetweaks.config.categories.SanctuaryCostCategory c) {
        double mult = u1Active() ? 0.5D : 1.0D;
        int manaPer = (int) Math.round(c.manaDrainAmount * mult);
        long drained = 0;
        double r = effectiveRadius;
        double rr = r * r;
        for (int i = 0; i < world.playerEntities.size(); i++) {
            net.minecraft.entity.player.EntityPlayer p = world.playerEntities.get(i);
            double dx = p.posX - (pos.getX() + 0.5D);
            double dz = p.posZ - (pos.getZ() + 0.5D);
            if (dx * dx + dz * dz > rr) { continue; }
            drained += drainWandMana(p, manaPer);
        }
        if (drained <= 0L) { // no wand mana anywhere inside -> tithe the owner's life essence
            net.minecraft.entity.player.EntityPlayer owner = resolveOwner();
            if (owner != null) {
                float dmg = (float) (c.ownerHpDrain * mult);
                if (dmg > 0.0F) { owner.attackEntityFrom(SANCTUARY_TITHE, dmg); }
            }
        }
    }

    /** Drain up to {@code amount} mana from the player's EBW wands (main inventory + offhand). */
    private static int drainWandMana(net.minecraft.entity.player.EntityPlayer p, int amount) {
        if (amount <= 0) { return 0; }
        int remaining = amount;
        int total = 0;
        total += drainFromList(p.inventory.mainInventory, remaining - total, p);
        total += drainFromList(p.inventory.offHandInventory, amount - total, p);
        return total;
    }

    private static int drainFromList(java.util.List<net.minecraft.item.ItemStack> stacks, int budget,
            net.minecraft.entity.EntityLivingBase wielder) {
        if (budget <= 0) { return 0; }
        int drained = 0;
        for (int i = 0; i < stacks.size() && drained < budget; i++) {
            net.minecraft.item.ItemStack s = stacks.get(i);
            if (s.isEmpty() || !(s.getItem() instanceof electroblob.wizardry.item.IManaStoringItem)) { continue; }
            electroblob.wizardry.item.IManaStoringItem wand = (electroblob.wizardry.item.IManaStoringItem) s.getItem();
            int mana = wand.getMana(s);
            if (mana <= 0) { continue; }
            int take = Math.min(mana, budget - drained);
            wand.consumeMana(s, take, wielder);
            drained += take;
        }
        return drained;
    }

    /** The owning player if online (any dimension), else null. */
    private net.minecraft.entity.player.EntityPlayer resolveOwner() {
        if (ownerId == null) { return null; }
        net.minecraft.server.MinecraftServer server = world.getMinecraftServer();
        if (server == null) { return null; }
        return server.getPlayerList().getPlayerByUUID(ownerId);
    }

    /** Send a chat line to the online owner (used for the starving warning). */
    private void warnOwner(String key) {
        net.minecraft.entity.player.EntityPlayer owner = resolveOwner();
        if (owner != null) {
            owner.sendMessage(new net.minecraft.util.text.TextComponentTranslation(key));
        }
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
            // Cleanse is no longer fuel-gated (fuel now powers the Sanctuary's upkeep, not cleanse).
            // It runs freely while the Sanctuary is active; the mana tank is spent by tickCost().
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
        net.minecraft.block.state.IBlockState st = world.getBlockState(p);
        if (com.spege.insanetweaks.util.SrpPurificationHelper.isSrpInfested(st)) { return true; }
        // R2: also treat blocks SRP's own map recognises (broader/more accurate than our heuristic).
        return com.spege.insanetweaks.config.ModConfig.sanctuary.nativeBlockPurify
                && com.spege.insanetweaks.util.SrpNativePurifyHelper.isAvailable()
                && com.spege.insanetweaks.util.SrpNativePurifyHelper.isSrpPurifiable(st);
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

    /** Ticks between locator pings, and the ticks each ping expands over. */
    private static final int PULSE_INTERVAL = 30;
    private static final int PULSE_LIFETIME = 18;

    /**
     * Client-side: a small pulsing EBW SPHERE that expands from the block to {@code pulseRadius} and
     * fades, repeated every {@link #PULSE_INTERVAL} ticks while the sanctuary is ACTIVE - a locator
     * "ping" for the block itself (deliberately a few blocks, NOT the full dome). EBW is a hard
     * dependency, so ParticleBuilder is always available. Only ever called from the isRemote branch.
     */
    private void clientPulseTick() {
        if (!com.spege.insanetweaks.config.ModConfig.sanctuary.pulseLocator) { return; }
        if (getStatus() != SanctuaryStatus.ACTIVE) { return; }
        if (world.getTotalWorldTime() % PULSE_INTERVAL != 0L) { return; }
        electroblob.wizardry.util.ParticleBuilder.create(electroblob.wizardry.util.ParticleBuilder.Type.SPHERE)
                .pos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                .scale((float) com.spege.insanetweaks.config.ModConfig.sanctuary.pulseRadius)
                .clr(0.60F, 0.82F, 0.90F)
                .time(PULSE_LIFETIME)
                .spawn(world);
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
        ownerId = c.hasUniqueId("ownerId") ? c.getUniqueId("ownerId") : null;
        ownerName = c.getString("ownerName");
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
        if (ownerId != null) { c.setUniqueId("ownerId", ownerId); }
        c.setString("ownerName", ownerName == null ? "" : ownerName);
        return c;
    }

    /** Compact tag of just the client-display fields (no inventory). */
    private NBTTagCompound writeDisplayTag(NBTTagCompound c) {
        c.setInteger("tier", tier);
        c.setInteger("radius", effectiveRadius);
        c.setBoolean("cleanse", cleanseEnabled);
        c.setBoolean("stalled", cleanseStalled);
        c.setInteger("status", statusCode);
        c.setInteger("creativeRadius", creativeRadius);
        c.setString("ownerName", ownerName == null ? "" : ownerName);
        return c;
    }

    private void readDisplayTag(NBTTagCompound c) {
        tier = c.getInteger("tier");
        effectiveRadius = c.getInteger("radius");
        cleanseEnabled = c.getBoolean("cleanse");
        cleanseStalled = c.getBoolean("stalled");
        statusCode = c.getInteger("status");
        creativeRadius = c.getInteger("creativeRadius");
        ownerName = c.getString("ownerName");
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
