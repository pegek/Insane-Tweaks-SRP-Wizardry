package com.spege.insanetweaks.events;

import java.util.List;
import java.util.UUID;

import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.AdvPropertyResolver;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
 * <p>Two ways in, both funnelling into {@link #isMechanicUnlocked}: the Spartan
 * {@code ModWeaponProperties.FLESHBOUND} weapon property declared by a {@code BridgeSpellblade}
 * class, and the {@code grip} advanced property granted to a single stack by a Property Book. The
 * third - a Sentient Spellblade earning it at 1900 kills - was removed when that blade's reward
 * became Arcane Sundering, so a book is now the only way to put this on a weapon that does not
 * declare it from its class.
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

    public static boolean isMechanicUnlocked(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == null) return false;

        // 1. Static check
        if (stack.getItem() instanceof com.spege.insanetweaks.items.spellblade.BridgeSpellblade) {
            if (((com.spege.insanetweaks.items.spellblade.BridgeSpellblade) stack.getItem()).hasWeaponProperty(com.spege.insanetweaks.init.ModWeaponProperties.FLESHBOUND)) {
                return true;
            }
        }

        // 2. The 'grip' advanced property, from a Property Book or from an item class that
        //    declares it. Granted per stack instead of per item type.
        //
        //    This used to be preceded by a third route: a Sentient Spellblade unlocked Fleshbound
        //    on its own at 1900 kills. That route is gone on purpose - Fleshbound is now something
        //    a player chooses to put on a weapon, and the blade's 1900-kill reward is Arcane
        //    Sundering instead. An already-evolved blade therefore loses the binding, which is the
        //    intended outcome and is reversible with a Grip book; nothing about the book route,
        //    the recovery routes below, or the sever-on-death cooldown changed with it.
        if (AdvPropertyResolver.has(stack, AdvPropertyRegistry.GRIP)) {
            return true;
        }

        return false;
    }

    public static boolean isFleshbound(ItemStack stack, World world) {
        if (!isMechanicUnlocked(stack)) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return true; // Newly evolved or statically assigned sword without tags is bound

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
            event.setCanceled(true);
            player.inventory.addItemStackToInventory(stack);
            player.sendStatusMessage(new TextComponentString(TextFormatting.DARK_RED + "The weapon is grafted to your flesh!"), true);
        }
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

    private static void returnToOwner(EntityJoinWorldEvent event, EntityItem entityItem,
            ItemStack stack, EntityPlayer player) {
        event.setCanceled(true);
        player.inventory.addItemStackToInventory(stack);
        entityItem.setDead();
        player.sendStatusMessage(
                new TextComponentString(TextFormatting.DARK_RED + "The weapon is grafted to your flesh!"), true);
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

            // Set the cooldown logic
            tag.setLong("FleshboundRegrowTime", world.getTotalWorldTime() + 36000L); // 30 minutes
            tag.setInteger("FleshboundRegrowKills", currentKills + 50);
        }
    }
}
