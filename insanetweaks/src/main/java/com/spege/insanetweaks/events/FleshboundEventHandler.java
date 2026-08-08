package com.spege.insanetweaks.events;

import java.util.List;
import java.util.UUID;

import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.FleshboundCategory;
import com.spege.insanetweaks.util.AdvPropertyResolver;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Fleshbound / Grip: the weapon cannot leave the player's inventory.
 *
 * <p>One way in, through {@link #isMechanicUnlocked}: the {@code grip} advanced property, granted to
 * a single stack by a Property Book (or declared by an item class). Two older routes are gone - a
 * Sentient Spellblade earning it at 1900 kills, removed when that blade's reward became Arcane
 * Sundering, and a {@code ModWeaponProperties.FLESHBOUND} weapon property that no item ever actually
 * declared, so its branch here could never be true.
 *
 * <h3>Rip-out</h3>
 * Recovery used to be unconditional. Under {@code fleshbound.enableRipOut} it is rationed instead:
 * {@code recoveryLimit} recoveries fit into a {@code recoveryWindowTicks} window, and the next
 * disarm past that is allowed through - the weapon lands on the ground, the owner takes the hit, and
 * {@link #isFleshbound} reports false until {@code ripOutCooldownTicks} elapse. The counters live on
 * the stack, not on the player, so two Fleshbound weapons are rationed independently.
 *
 * <h3>What was already here, and stays</h3>
 * Two recovery routes predate Grip and are load-bearing in practice against {@code infernalmobs}
 * and {@code champions}: {@link #onItemToss} (pressing Q, or dragging the item out of the
 * inventory) and the {@code getThrower()} branch of {@link #onEntityJoinWorld}. Neither is
 * modified, and the thrower branch is still tried first - it is the cheapest and the most certain.
 *
 * <h3>What is new, and why it was needed</h3>
 * Both live disarm sources in this pack bypass those two entirely. EB Wizardry's {@code Telekinesis}
 * and SoManyEnchantments' {@code Disarmament} both call {@code Entity.entityDropItem}
 * ({@code func_70099_a}), which spawns the {@code EntityItem} directly: it fires no
 * {@code ItemTossEvent} and, crucially, never sets {@code thrower}. So the old handler saw a
 * nameless dropped item and could do nothing, and the Spellblade tooltip's promise to prevent
 * "disarm" was not actually true.
 *
 * <p>Two branches are appended <b>below</b> the thrower check, tried in order: an owner UUID bound
 * to the stack when it is equipped (via {@link LivingEquipmentChangeEvent}, which costs nothing per
 * tick because it only fires on an actual equipment change), and finally a nearest-player search.
 * The proximity search is a heuristic and is deliberately last.
 *
 * <p>Player death is unchanged: the item still drops, and {@link #severLink} sets a regrow
 * cooldown. That is the established behaviour, and it keeps out of the way of Tombstone, which
 * manages item retention on death in this pack.
 */
public class FleshboundEventHandler {

    /** UUID of the player this stack is bound to, used to recover it from a disarm. */
    private static final String OWNER_TAG = "insanetweaks_bound_owner";

    /** int: recoveries counted inside the current rip-out window. */
    public static final String RECOVERIES_TAG = "insanetweaks_grip_recoveries";
    /** long: world time the current rip-out window opened. */
    public static final String WINDOW_TAG = "insanetweaks_grip_window";
    /** long: world time the weapon stops being torn loose. */
    public static final String RIPPED_TAG = "insanetweaks_grip_ripped_until";

    public static boolean isMechanicUnlocked(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == null) return false;

        // The 'grip' advanced property, from a Property Book or from an item class that declares it.
        // Granted per stack instead of per item type.
        return AdvPropertyResolver.has(stack, AdvPropertyRegistry.GRIP);
    }

    /** World time at which this stack stops being torn loose, or 0 when it is not. */
    public static long rippedUntil(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? 0L : tag.getLong(RIPPED_TAG);
    }

    /** Recoveries counted inside the current window; only meaningful while rip-out is on. */
    public static int recoveryCount(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? 0 : tag.getInteger(RECOVERIES_TAG);
    }

    public static boolean isFleshbound(ItemStack stack, World world) {
        if (!isMechanicUnlocked(stack)) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return true; // Newly evolved or statically assigned sword without tags is bound

        // Torn loose: behaves like an ordinary item until the cooldown runs out. Checked before the
        // sever logic because it is the more specific state - a weapon can be both, and while it is
        // ripped nothing should pull it back.
        if (world.getTotalWorldTime() < tag.getLong(RIPPED_TAG)) {
            return false;
        }

        if (!tag.hasKey("FleshboundRegrowTime") && !tag.hasKey("FleshboundRegrowKills")) {
            return true;
        }

        long regrowTime = tag.getLong("FleshboundRegrowTime");
        int regrowKills = tag.getInteger("FleshboundRegrowKills");
        int currentKills = tag.getInteger("SentientKills");

        // If time passed and enough kills gathered
        if (world.getTotalWorldTime() >= regrowTime && currentKills >= regrowKills) {
            return true;
        }

        return false;
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        EntityItem entityItem = event.getEntityItem();
        if (entityItem == null) return;

        ItemStack stack = entityItem.getItem();
        EntityPlayer player = event.getPlayer();

        if (player != null && !player.world.isRemote && isFleshbound(stack, player.world)) {
            // Rationed like every other recovery: wrestling the weapon out yourself strains the
            // graft exactly as much as a mob doing it for you.
            if (!tryRecover(player, stack)) {
                return; // torn loose - let the toss go through
            }
            // Same order as returnToOwner: never cancel the toss until the stack is safely stored.
            if (!player.inventory.addItemStackToInventory(stack)) {
                refundRecovery(stack);
                return;
            }
            event.setCanceled(true);
            player.sendStatusMessage(new TextComponentString(TextFormatting.DARK_RED + "The weapon is grafted to your flesh!"), true);
        }
    }

    /**
     * Spends one recovery from the stack's rip-out budget.
     *
     * @return {@code true} when the weapon may be recovered; {@code false} when it has just been
     *         torn loose, in which case the caller must leave the dropped item alone.
     */
    private static boolean tryRecover(EntityPlayer player, ItemStack stack) {
        FleshboundCategory cfg = ModConfig.fleshbound;
        if (!cfg.enableRipOut) {
            return true;
        }
        long now = player.world.getTotalWorldTime();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            stack.setTagCompound(new NBTTagCompound());
            tag = stack.getTagCompound();
            if (tag == null) {
                return true;
            }
        }

        long windowStart = tag.getLong(WINDOW_TAG);
        int count = tag.getInteger(RECOVERIES_TAG);
        // A window that has run out - or a world time rewound by /time set, which is why the
        // now < windowStart arm exists - starts a fresh one instead of carrying stale counts.
        if (count <= 0 || now < windowStart || now - windowStart >= cfg.recoveryWindowTicks) {
            windowStart = now;
            count = 0;
        }

        if (count >= cfg.recoveryLimit) {
            ripOut(player, stack, tag, now, cfg);
            return false;
        }

        tag.setLong(WINDOW_TAG, windowStart);
        tag.setInteger(RECOVERIES_TAG, count + 1);
        return true;
    }

    /**
     * Tears the weapon loose: starts the cooldown, resets the window, and bills whoever it was
     * grafted to.
     *
     * <p>🚨 The penalty follows the <b>bound owner</b>, not the player the recovery was resolved
     * against. Route 3 in {@link #onEntityJoinWorld} is a nearest-player heuristic that exists
     * precisely for the case where the owner cannot be found, so billing {@code recoveredBy} would
     * damage a bystander, stagger them and tell them a weapon that is not theirs was torn from
     * their flesh. When the owner is unreachable nobody is billed - the weapon still drops, which is
     * the part that matters.
     */
    private static void ripOut(EntityPlayer recoveredBy, ItemStack stack, NBTTagCompound tag,
            long now, FleshboundCategory cfg) {
        tag.setLong(RIPPED_TAG, now + cfg.ripOutCooldownTicks);
        tag.setInteger(RECOVERIES_TAG, 0);
        tag.setLong(WINDOW_TAG, 0L);

        EntityPlayer victim = penaltyTarget(recoveredBy, stack);
        if (victim == null) {
            return;
        }

        if (cfg.ripOutDamage > 0.0D) {
            // MAGIC rather than GENERIC: the wound is the graft tearing, so armour should not soak it.
            victim.attackEntityFrom(DamageSource.MAGIC, (float) cfg.ripOutDamage);
        }
        if (cfg.ripOutWeaknessTicks > 0) {
            victim.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, cfg.ripOutWeaknessTicks,
                    cfg.ripOutWeaknessAmplifier));
        }
        Potion bleed = resolveBleed(cfg);
        if (bleed != null && cfg.ripOutBleedTicks > 0) {
            victim.addPotionEffect(new PotionEffect(bleed, cfg.ripOutBleedTicks, 0));
        }
        victim.sendStatusMessage(new TextComponentString(
                TextFormatting.DARK_RED + "The weapon is torn from your flesh!"), true);
    }

    /**
     * Who pays for a rip-out: the stack's bound owner if they are around, otherwise the recovering
     * player only when they ARE that owner. Null means nobody is billed.
     */
    private static EntityPlayer penaltyTarget(EntityPlayer recoveredBy, ItemStack stack) {
        UUID owner = readOwner(stack);
        if (owner == null) {
            // Never bound - equipping is what binds it, so this is a weapon nobody has wielded.
            return recoveredBy;
        }
        if (owner.equals(recoveredBy.getUniqueID())) {
            return recoveredBy;
        }
        EntityPlayer bound = recoveredBy.world.getPlayerEntityByUUID(owner);
        return bound != null && bound.isEntityAlive() ? bound : null;
    }

    /**
     * The configured bleed effect, or null when nothing registers that name.
     *
     * <p>Looked up by registry name on purpose: it keeps the bleed source a pack decision and costs
     * this handler no compile dependency on whichever mod provides it. An absent effect is not an
     * error - the rest of the rip-out still happens.
     */
    private static Potion resolveBleed(FleshboundCategory cfg) {
        String name = cfg.ripOutBleedPotion;
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return Potion.REGISTRY.getObject(new ResourceLocation(name.trim()));
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity().world.isRemote) return;
        if (!(event.getEntity() instanceof EntityItem)) return;

        EntityItem entityItem = (EntityItem) event.getEntity();
        ItemStack stack = entityItem.getItem();
        if (stack.isEmpty() || !isFleshbound(stack, entityItem.world)) return;

        // 1. The original route: whoever threw it. Cheapest and unambiguous, so it stays first.
        String throwerName = entityItem.getThrower();
        if (throwerName != null) {
            EntityPlayer player = entityItem.world.getPlayerEntityByName(throwerName);
            if (player != null && player.isEntityAlive()) {
                returnToOwner(event, entityItem, stack, player);
                return;
            }
        }

        // 2. Bound owner. This is the branch that catches a real disarm: entityDropItem sets no
        //    thrower, so route 1 cannot see EB Wizardry's Telekinesis or SoManyEnchantments'
        //    Disarmament at all.
        UUID owner = readOwner(stack);
        if (owner != null) {
            EntityPlayer player = entityItem.world.getPlayerEntityByUUID(owner);
            if (player != null && player.isEntityAlive()) {
                returnToOwner(event, entityItem, stack, player);
                return;
            }
        }

        // 3. Last resort: nearest player. A heuristic, so it is last and its radius is small.
        double radius = ModConfig.propertyBooks.gripRecoveryRadius;
        if (radius <= 0.0D) return;
        EntityPlayer nearest = findNearestPlayer(entityItem, radius);
        if (nearest != null) {
            returnToOwner(event, entityItem, stack, nearest);
        }
    }

    /**
     * Binds the stack to whoever equipped it, so a later disarm has something to resolve against.
     *
     * <p>{@link LivingEquipmentChangeEvent} rather than a tick handler on purpose: it fires only
     * when equipment actually changes, so this costs nothing while the player just holds the
     * weapon.
     */
    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        EntityLivingBase living = event.getEntityLiving();
        if (living == null || living.world == null || living.world.isRemote) return;
        if (!(living instanceof EntityPlayer)) return;

        ItemStack stack = event.getTo();
        if (stack.isEmpty() || !isMechanicUnlocked(stack)) return;

        UUID id = living.getUniqueID();
        if (id.equals(readOwner(stack))) return;

        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.setString(OWNER_TAG, id.toString());
        }
    }

    /**
     * Pulls the dropped weapon back into the player's inventory.
     *
     * <p>🚨 Cancels the event and kills the entity only once the stack is actually somewhere. It
     * used to do both unconditionally and ignore {@code addItemStackToInventory}'s return value, so
     * a full inventory destroyed the weapon outright - the exact loss this whole mechanic exists to
     * prevent. A recovery charge is spent before that point, which is why it now backs out cleanly
     * and refunds it instead.
     */
    private static void returnToOwner(EntityJoinWorldEvent event, EntityItem entityItem,
            ItemStack stack, EntityPlayer player) {
        if (!tryRecover(player, stack)) {
            return; // torn loose - the item stays where it landed
        }
        if (!player.inventory.addItemStackToInventory(stack)) {
            refundRecovery(stack);
            return; // no room - leave it on the ground rather than deleting it
        }
        event.setCanceled(true);
        entityItem.setDead();
        player.sendStatusMessage(
                new TextComponentString(TextFormatting.DARK_RED + "The weapon is grafted to your flesh!"), true);
    }

    /** Gives back the charge {@link #tryRecover} spent, when the recovery could not be completed. */
    private static void refundRecovery(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }
        int count = tag.getInteger(RECOVERIES_TAG);
        if (count > 0) {
            tag.setInteger(RECOVERIES_TAG, count - 1);
        }
    }

    private static EntityPlayer findNearestPlayer(EntityItem entityItem, double radius) {
        AxisAlignedBB box = entityItem.getEntityBoundingBox().grow(radius);
        List<EntityPlayer> candidates = entityItem.world.getEntitiesWithinAABB(EntityPlayer.class, box);
        EntityPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityPlayer candidate : candidates) {
            if (!candidate.isEntityAlive()) continue;
            double dist = candidate.getDistanceSq(entityItem);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private static UUID readOwner(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(OWNER_TAG, 8)) return null;
        try {
            return UUID.fromString(tag.getString(OWNER_TAG));
        } catch (IllegalArgumentException ex) {
            return null; // hand-edited or corrupted tag; treat as unbound
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        World world = player.world;

        for (ItemStack stack : player.inventory.mainInventory) {
            severLink(stack, world);
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            severLink(stack, world);
        }
    }

    private void severLink(ItemStack stack, World world) {
        if (!isMechanicUnlocked(stack)) return;

        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            int currentKills = tag.getInteger("SentientKills");
            FleshboundCategory cfg = ModConfig.fleshbound;

            // Set the cooldown logic
            tag.setLong("FleshboundRegrowTime", world.getTotalWorldTime() + cfg.severRegrowTicks);
            tag.setInteger("FleshboundRegrowKills", currentKills + cfg.severRegrowKills);
        }
    }
}
