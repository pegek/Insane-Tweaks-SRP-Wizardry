package com.spege.insanetweaks.enchant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.SentientCodexCategory;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Runtime behaviour of the {@link EnchantmentSentientCodex} enchantment. Registered on the Forge
 * event bus in {@code InsaneTweaksMod#init} under {@code modules.enableSentientCodex}.
 *
 * <h3>The Codex eats experience, not XP levels</h3>
 * 🚨 Growth is driven by the experience the holder earns <b>while the item is in their inventory</b>,
 * banked on the stack as {@code sentientcodex_fed}. The XP the player already had when they picked
 * the item up is worth nothing: a level-1000 player who has never fed a Codex starts it at zero
 * steps, exactly like anyone else.
 *
 * <p>That is measured as the positive delta of {@link EntityPlayer#experienceTotal} between ticks,
 * not by listening for XP orbs - the delta catches every source (mobs, furnaces, bottles o'
 * enchanting, commands, other mods) rather than the one vanilla fires an event for. Only rises
 * count, so spending levels at an anvil never withdraws from the bank.
 *
 * <p>Steps cost geometrically more as they go ({@code stepCostBase * stepCostGrowth^(n-1)}), which
 * is what makes early growth visible and later growth an investment. Each step adds +1 to every
 * boostable enchantment on the item, up to the per-enchantment ceiling
 * {@link SentientCodexPool#capFor}. Growth is monotonic: the step counter only rises, and the live
 * {@code "ench"} list is edited in place because the anvil lock freezes the enchantment set once the
 * Codex is applied.
 *
 * <h3>The two sides of the bargain</h3>
 * <ul>
 * <li><b>Starve it</b> and it turns on its holder: damage, exhaustion and debuffs escalating with
 *     how long it has gone unfed. It never takes levels back - the penalty falls on the player.</li>
 * <li><b>Feed it in battle</b> and it repairs itself, which is the role Mending would otherwise
 *     play. That is precisely why {@link SentientCodexPool#isIncompatible} refuses to let the two
 *     share an item.</li>
 * </ul>
 *
 * <p>Drop protection (no burn / lingers far longer) is NOT handled here: a Sentient Codex item
 * confers the "Ashen Legacy" property, so {@code LegendaryDropHelper.isLegendaryDropItem} routes it
 * through {@code EntityItemIndestructible} via the always-on {@code IndestructibleDropHandler}.
 * Gated by {@code ModConfig.enchantments.sentientCodex.conferAshenLegacy}.
 *
 * <p>Enchantment registration itself is NOT here (that is {@code ModEnchantments} on the MOD bus) -
 * this class is a plain Forge-bus handler instance.
 */
@SuppressWarnings("null")
public class SentientCodexHandler {

    /**
     * Last {@code experienceTotal} seen for a player, so the next tick can take the difference.
     *
     * <p>{@code WeakHashMap} for the same reason the nunchaku effect log uses one: the keys are live
     * entities and the entries must die with them. Transient by design - it is re-seeded from the
     * player's current total the first time they are seen, so relogging cannot bank a windfall.
     *
     * <p>Touched only from the server tick, hence no synchronisation.
     */
    private static final Map<EntityPlayer, Integer> LAST_XP_TOTAL = new WeakHashMap<>();

    /**
     * Experience left over from an uneven split, carried to the next interval.
     *
     * <p>Without this, splitting 3 XP between 4 items would silently discard all of it, and a player
     * earning slowly would never feed anything at all.
     */
    private static final Map<EntityPlayer, Integer> XP_REMAINDER = new WeakHashMap<>();

    // --- per-interval: bank XP, grow, starve (SERVER only) ---
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer p = e.player;
        if (p == null || p.world == null || p.world.isRemote) {
            return;
        }
        if (EnchantmentSentientCodex.INSTANCE == null) {
            return;
        }
        SentientCodexCategory cfg = ModConfig.enchantments.sentientCodex;
        if (p.ticksExisted % Math.max(1, cfg.tickInterval) != 0) {
            return;
        }

        int earned = consumeEarnedXp(p);

        if (!cfg.enabled) {
            // Frozen: stop feeding and stop growing. Already-granted levels stay baked in (we keep
            // no base snapshot to restore). To fully remove the enchant, unregister it via
            // modules.enableSentientCodex.
            return;
        }

        // "Active" is the whole inventory, not just the hands: an item riding in the pack is still
        // being carried, and it is what the starvation penalty is about.
        List<ItemStack> active = collectActive(p);
        if (active.isEmpty()) {
            // 🚨 Nothing carried means the experience is gone, and the leftover pot is emptied too.
            // Banking it would rebuild the exact behaviour this rework exists to remove: a player
            // who mined all afternoon could then pick up a Codex and have it grow instantly off XP
            // it was never there for. Experience only counts while the item is being carried.
            XP_REMAINDER.remove(p);
            return;
        }

        long now = p.world.getTotalWorldTime();
        int perItem = distribute(p, active.size(), earned);

        int worstSeverity = 0;
        for (ItemStack stack : active) {
            feed(stack, perItem, now, cfg.tickInterval);
            grow(stack, cfg);
            int severity = starvationSeverity(stack, now, cfg);
            if (severity > worstSeverity) {
                worstSeverity = severity;
            }
        }

        // One penalty per interval, from the hungriest item. Applying it per item would multiply the
        // punishment by however many Codex items happen to be in the pack.
        if (worstSeverity > 0) {
            punish(p, worstSeverity, cfg);
        }
    }

    // ----------------------------------------------------------------
    // FEEDING
    // ----------------------------------------------------------------

    /** Experience the player has earned since the last interval, never negative. */
    private static int consumeEarnedXp(EntityPlayer p) {
        int total = p.experienceTotal;
        Integer previous = LAST_XP_TOTAL.put(p, Integer.valueOf(total));
        if (previous == null) {
            return 0; // first sighting this session: seed the baseline, bank nothing
        }
        int delta = total - previous.intValue();
        // Only rises. Spending levels at an anvil or enchanting table must not withdraw from a bank
        // the player already paid into.
        return delta > 0 ? delta : 0;
    }

    private static int remainder(EntityPlayer p) {
        Integer carried = XP_REMAINDER.get(p);
        return carried == null ? 0 : carried.intValue();
    }

    /**
     * How much experience each active item gets this interval, storing whatever does not divide
     * evenly for next time.
     */
    private static int distribute(EntityPlayer p, int itemCount, int earned) {
        int pot = remainder(p) + earned;
        if (pot <= 0) {
            XP_REMAINDER.put(p, Integer.valueOf(0));
            return 0;
        }
        if (!ModConfig.enchantments.sentientCodex.splitXpBetweenItems) {
            XP_REMAINDER.put(p, Integer.valueOf(0));
            return pot;
        }
        int perItem = pot / itemCount;
        XP_REMAINDER.put(p, Integer.valueOf(pot - perItem * itemCount));
        return perItem;
    }

    /**
     * Banks experience on the stack, stamps the feeding time, and keeps the starvation clock honest.
     *
     * <p>🚨 The clock only runs while the item is being carried. {@code collectActive} never sees a
     * stack in a chest, on the ground or in a grave, so without the "last seen" stamp its
     * {@code LAST_FED_TAG} would freeze while world time kept moving - and taking a Codex out of
     * storage after a day would land the holder at maximum starvation severity on the first
     * interval, with no grace at all. Reappearing after a gap therefore restarts the clock.
     */
    private static void feed(ItemStack stack, int amount, long now, int tickInterval) {
        NBTTagCompound tag = ensureTag(stack);
        if (tag == null) {
            return;
        }

        // A couple of intervals of slack, so an ordinary tick-to-tick gap never reads as absence.
        long carriedGap = Math.max(1, tickInterval) * 4L;
        long lastSeen = tag.getLong(EnchantmentSentientCodex.LAST_SEEN_TAG);
        boolean wasCarried = tag.hasKey(EnchantmentSentientCodex.LAST_SEEN_TAG)
                && now >= lastSeen && now - lastSeen <= carriedGap;
        if (!wasCarried) {
            // Newly created, just taken out of storage, or picked back up off the floor - either
            // way the fast starts now. (A world time rewound by /time set lands here too, which is
            // the safe direction.)
            tag.setLong(EnchantmentSentientCodex.LAST_FED_TAG, now);
        }
        tag.setLong(EnchantmentSentientCodex.LAST_SEEN_TAG, now);

        if (amount > 0) {
            tag.setLong(EnchantmentSentientCodex.FED_TAG,
                    tag.getLong(EnchantmentSentientCodex.FED_TAG) + amount);
            tag.setLong(EnchantmentSentientCodex.LAST_FED_TAG, now);
        }
    }

    // ----------------------------------------------------------------
    // GROWTH
    // ----------------------------------------------------------------

    /** Applies any newly affordable steps to the item's live enchantment list. */
    private static void grow(ItemStack stack, SentientCodexCategory cfg) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }
        int target = stepsFor(tag.getLong(EnchantmentSentientCodex.FED_TAG), cfg);
        int applied = tag.getInteger(EnchantmentSentientCodex.LAST_BOOST_TAG);
        if (target <= applied) {
            return;
        }
        int delta = target - applied;

        int scId = Enchantment.getEnchantmentID(EnchantmentSentientCodex.INSTANCE);
        NBTTagList ench = stack.getEnchantmentTagList(); // live "ench" list, edited in place
        for (int i = 0; i < ench.tagCount(); i++) {
            NBTTagCompound en = ench.getCompoundTagAt(i);
            // getInteger, not getShort: JustEnoughIDs widens enchantment ids past 32767 and rewrites
            // vanilla's own reads, but not ours. getInteger reads a short tag fine, so this is safe
            // without JEID too. "lvl" stays a short - JEID only widens the id.
            int id = en.getInteger("id");
            if (id == scId) {
                continue; // never boost Sentient Codex itself
            }
            Enchantment ench2 = Enchantment.getEnchantmentByID(id);
            if (ench2 == null || !SentientCodexPool.canBoost(ench2, stack)) {
                continue;
            }
            int cap = SentientCodexPool.capFor(ench2);
            int lvl = en.getShort("lvl");
            if (lvl < cap) {
                en.setShort("lvl", (short) Math.min(lvl + delta, cap));
            }
        }
        tag.setInteger(EnchantmentSentientCodex.LAST_BOOST_TAG, target);
    }

    /**
     * How many growth steps a given amount of banked experience buys.
     *
     * <p>Step n costs {@code stepCostBase * stepCostGrowth^(n-1)}, so the answer is the largest N
     * whose running total fits. Walked rather than solved in closed form because the loop is bounded
     * by {@code maxSteps} (three, by default) and a logarithm would need the same clamping anyway.
     */
    public static int stepsFor(long fed, SentientCodexCategory cfg) {
        if (fed <= 0L || cfg.maxSteps <= 0) {
            return 0;
        }
        double cost = cfg.stepCostBase;
        double spent = 0.0D;
        for (int step = 1; step <= cfg.maxSteps; step++) {
            spent += cost;
            if (spent > fed) {
                return step - 1;
            }
            cost *= cfg.stepCostGrowth;
        }
        return cfg.maxSteps;
    }

    /** Total experience needed to reach {@code step}, for the tooltip's "next step" line. */
    public static long costOfSteps(int step, SentientCodexCategory cfg) {
        if (step <= 0) {
            return 0L;
        }
        double cost = cfg.stepCostBase;
        double spent = 0.0D;
        for (int i = 1; i <= step; i++) {
            spent += cost;
            cost *= cfg.stepCostGrowth;
        }
        return (long) Math.ceil(spent);
    }

    // ----------------------------------------------------------------
    // STARVATION
    // ----------------------------------------------------------------

    /** 0 when the item is content, otherwise how badly it is starving (1..maxStarvationSeverity). */
    public static int starvationSeverity(ItemStack stack, long now, SentientCodexCategory cfg) {
        if (!cfg.starvationEnabled) {
            return 0;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(EnchantmentSentientCodex.LAST_FED_TAG)) {
            return 0;
        }
        long fasting = now - tag.getLong(EnchantmentSentientCodex.LAST_FED_TAG);
        // A world time rewound by /time set reads as a negative fast; treat that as "just fed"
        // rather than letting it wrap into a huge severity.
        if (fasting <= cfg.starvationGraceTicks) {
            return 0;
        }
        long past = fasting - cfg.starvationGraceTicks;
        long severity = 1L + past / Math.max(1, cfg.starvationEscalationTicks);
        return (int) Math.min(severity, cfg.maxStarvationSeverity);
    }

    private static void punish(EntityPlayer p, int severity, SentientCodexCategory cfg) {
        if (cfg.starvationDamage > 0.0D) {
            // MAGIC, not OUT_OF_WORLD: the Codex feeding on its holder is exactly the kind of thing
            // protection enchantments should not stop but resistance potions should blunt.
            p.attackEntityFrom(DamageSource.MAGIC, (float) (cfg.starvationDamage * severity));
        }
        if (cfg.starvationExhaustion > 0.0D) {
            p.addExhaustion((float) (cfg.starvationExhaustion * severity));
        }
        if (cfg.starvationDebuffs) {
            int duration = Math.max(40, cfg.tickInterval * 3);
            p.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, duration, severity - 1, false, false));
            p.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, duration, severity - 1, false, false));
        }
    }

    // ----------------------------------------------------------------
    // REPAIR
    // ----------------------------------------------------------------

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        SentientCodexCategory cfg = ModConfig.enchantments.sentientCodex;
        if (!cfg.enabled || cfg.repairPerKill <= 0) {
            return;
        }
        EntityPlayer killer = killerOf(event.getSource());
        if (killer == null || killer.world == null || killer.world.isRemote) {
            return;
        }
        repairCarried(killer, cfg.repairPerKill, cfg);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        SentientCodexCategory cfg = ModConfig.enchantments.sentientCodex;
        if (!cfg.enabled || cfg.repairPerDamageDealt <= 0.0D) {
            return;
        }
        EntityPlayer attacker = killerOf(event.getSource());
        if (attacker == null || attacker.world == null || attacker.world.isRemote) {
            return;
        }
        int amount = (int) Math.floor(event.getAmount() * cfg.repairPerDamageDealt);
        if (amount > 0) {
            repairCarried(attacker, amount, cfg);
        }
    }

    /** The player behind a damage source, or null when it was not a player's doing. */
    private static EntityPlayer killerOf(DamageSource source) {
        if (source == null) {
            return null;
        }
        EntityLivingBase attacker = source.getTrueSource() instanceof EntityLivingBase
                ? (EntityLivingBase) source.getTrueSource()
                : null;
        return attacker instanceof EntityPlayer ? (EntityPlayer) attacker : null;
    }

    /**
     * Repairs the Codex gear the player has equipped.
     *
     * <p>🚨 Both hands, always. The anvil lock refuses every operation on a Codex item - material
     * repair included - and Mending is on the exclusion list, so this method is the ONLY way a Codex
     * item ever recovers durability. Missing the offhand here would have meant a Codex shield could
     * never be repaired by any means and was guaranteed to break for good. Armour is covered by
     * {@code repairArmorToo}, which defaults to ON for exactly the same reason.
     */
    private static void repairCarried(EntityPlayer p, int amount, SentientCodexCategory cfg) {
        repair(p.getHeldItemMainhand(), amount);
        repair(p.getHeldItemOffhand(), amount);
        if (cfg.repairArmorToo) {
            for (ItemStack armor : p.getArmorInventoryList()) {
                repair(armor, amount);
            }
        }
    }

    private static void repair(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty() || !stack.isItemStackDamageable()) {
            return;
        }
        if (EnchantmentSentientCodex.getSentientCodexLevel(stack) <= 0) {
            return;
        }
        stack.setItemDamage(Math.max(0, stack.getItemDamage() - amount));
    }

    // ----------------------------------------------------------------
    // SHARED
    // ----------------------------------------------------------------

    /** Every Codex-carrying stack the player is currently carrying: pack, armour and offhand. */
    private static List<ItemStack> collectActive(EntityPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        addIfCodex(out, p.inventory.mainInventory);
        addIfCodex(out, p.inventory.armorInventory);
        addIfCodex(out, p.inventory.offHandInventory);
        return out;
    }

    private static void addIfCodex(List<ItemStack> out, Iterable<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            // isItemEnchanted first: it is a cheap hasKey, whereas getEnchantmentTagList allocates an
            // empty list for every unenchanted stack, and this runs over the whole inventory.
            if (stack == null || stack.isEmpty() || !stack.isItemEnchanted()) {
                continue;
            }
            if (EnchantmentSentientCodex.getSentientCodexLevel(stack) > 0) {
                out.add(stack);
            }
        }
    }

    private static NBTTagCompound ensureTag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    // --- anvil block: don't modify an already-SentientCodex'd item (but DO allow applying the
    // book onto a clean item) ---
    @SubscribeEvent
    public void onAnvil(AnvilUpdateEvent e) {
        if (!ModConfig.enchantments.sentientCodex.blockAnvil) {
            return;
        }
        // A Property Book is not a modification of the item's enchantments - it grants an advanced
        // property, which is exactly the kind of thing a Codex-bound item should still be able to
        // receive. Without this exemption the cancel below would kill PropertyBookAnvilHandler's
        // output (cancelling AnvilUpdateEvent is terminal), and the recipe would look broken on
        // precisely the gear most likely to want it.
        if (e.getRight().getItem() instanceof com.spege.insanetweaks.items.PropertyBookItem) {
            return;
        }
        if (EnchantmentSentientCodex.hasSentientCodex(e.getLeft())) {
            e.setCanceled(true); // left = target; already SentientCodex'd -> locked
        }
        // right = Sentient Codex book onto a clean left -> allowed (creates a locked item)
    }
}
