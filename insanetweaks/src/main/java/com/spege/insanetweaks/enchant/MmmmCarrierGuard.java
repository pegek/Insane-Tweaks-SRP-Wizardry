package com.spege.insanetweaks.enchant;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.MmmmCategory;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Keeps {@link EnchantmentMmmm} from ever being carried by a rot item.
 *
 * <p>The enchantment's whole promise is that rot finds no purchase on the food it sits on. Today that
 * is enforced against exactly one named vector - SRP's {@code attackEntityAsMobFood}, through
 * {@code MixinParasiteFoodContamination} and {@link MmmmHandler#protectsAgainstParasiteContamination}.
 * This class is the general form: whatever else in the pack would swap the enchanted food for
 * {@code minecraft:rotten_flesh} (or anything else listed in {@code enchantments.mmmm.forbiddenCarriers}),
 * the enchantment does not go along for the ride.
 *
 * <h3>Two outcomes</h3>
 * <ol>
 * <li><b>Restore.</b> When we know what the stack used to be, it is turned back into that food with
 *     its NBT - and therefore the enchantment - intact. The knowledge comes from a breadcrumb,
 *     {@link #CARRIER_TAG}, stamped onto any Mmmm-carrying food this class sees go past.</li>
 * <li><b>Strip.</b> Failing that, Mmmm is deleted from the offending stack, so the rot item is left
 *     as ordinary rot rather than as an enchanted trophy.</li>
 * </ol>
 *
 * <h3>🚨 What this cannot do</h3>
 * Forge 1.12.2 has no general "an item was transformed in place" event, so a guard can only watch the
 * routes it can hook: starting to eat, finishing eating, picking up, tossing, and the anvil. A stack
 * rewritten inside a closed inventory by foreign code is invisible here. If a log line from
 * {@code logCarrierGuard} points at a specific mod, the proper fix is a targeted mixin against that
 * mod - the {@code MixinParasiteFoodContamination} precedent - and this class stays as the net.
 *
 * <h3>Side safety</h3>
 * Server-side only (every handler bails on {@code world.isRemote}), plain types throughout, no
 * {@code @SideOnly} anywhere - and none must ever be added. Registered unconditionally in
 * {@code InsaneTweaksMod#init}; the gating is an early return so both config flags stay live.
 */
public class MmmmCarrierGuard {

    /**
     * Root-level NBT key remembering which item carried the enchantment, as {@code "modid:path#meta"}.
     * Root level rather than under {@code tag}, and verbosely namespaced, for the same reasons as
     * {@code EnchantGrantMarker.GRANTED_TAG}: it survives {@code ItemStack.copy()} and it is legible
     * when it turns up in a hand-authored quest JSON or an NBT dump.
     */
    public static final String CARRIER_TAG = "insanetweaks_mmmm_carrier";

    /** Deduplicates the diagnostic log by "item @ route", so a busy inventory cannot flood it. */
    private static final Set<String> LOGGED = Collections.synchronizedSet(new HashSet<String>());

    /**
     * Parsed {@code forbiddenCarriers}, rebuilt only when Forge hands us a different array instance -
     * which it does on every {@code ConfigManager.sync}, so a live config edit is picked up without
     * re-parsing the list on every pickup.
     */
    private static String[] cachedSource;
    private static Set<String> cachedForbidden = Collections.emptySet();

    // ------------------------------------------------------------------ events

    /**
     * Refuse to build the combination in the first place. {@link EnchantmentMmmm#canApply} already says
     * no, but {@code ContainerRepair} ignores {@code canApply} in creative mode, so this is the second
     * layer for the one case that slips through.
     *
     * <p>LOWEST for the same reason as {@code EnchantGrantAnvilHandler}: let anyone building a
     * legitimate output run first, then take the final say. Note that this event fires at the TOP of
     * {@code updateRepairOutput} and its output is empty for an ordinary anvil operation, so there is
     * nothing to stamp here - only inputs to judge.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        MmmmCategory cfg = activeConfig();
        if (cfg == null) {
            return;
        }
        ItemStack left = event.getLeft();
        if (!isForbiddenCarrier(left, cfg)) {
            return;
        }
        if (EnchantmentMmmm.getLevel(event.getRight()) > 0 || EnchantmentMmmm.getLevel(left) > 0) {
            logOnce(left, "anvil", "refused");
            event.setCanceled(true);
        }
    }

    /**
     * Stamp the breadcrumb on the way in, and catch a stack that is already wrong on the way out.
     *
     * <p>{@code Start} hands us the live held stack (vanilla passes {@code getHeldItem(hand)} straight
     * to the event), so the stamp sticks without any copy-back.
     */
    @SubscribeEvent
    public void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        MmmmCategory cfg = activeConfig();
        if (cfg == null || !(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world == null || player.world.isRemote) {
            return;
        }
        ItemStack stack = event.getItem();
        if (EnchantmentMmmm.getLevel(stack) <= 0) {
            return;
        }
        if (!isForbiddenCarrier(stack, cfg)) {
            if (isEligibleCarrier(stack)) {
                stamp(stack);
            }
            return;
        }
        ItemStack fixed = correct(stack, cfg, "use");
        if (fixed.isEmpty()) {
            return; // stripped in place; the stack is clean now and may be eaten as normal
        }
        if (replaceInInventory(player, stack, fixed)) {
            // 🚨 Must cancel. setActiveHand caches the stack object it was handed
            // (this.activeItemStack = itemstack) immediately after this event returns, so letting the
            // use proceed would have the player eat the old rot object that is no longer in any slot.
            // Cancelling makes onItemUseStart return -1, setActiveHand bails, and the next click eats
            // the restored food.
            event.setCanceled(true);
        } else {
            // Held in something we cannot reach (a bauble slot, a foreign container). Stripping in
            // place always works and needs no swap, so no cancel either.
            stripMmmm(stack);
        }
    }

    /**
     * The result of eating. Uniquely, no breadcrumb is needed here: {@code event.getItem()} is Forge's
     * copy of the stack taken <i>before</i> it shrank, so the original food is right there.
     *
     * <p>LOWEST so that every other handler has already had its say on the result stack.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        MmmmCategory cfg = activeConfig();
        if (cfg == null || !(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world == null || player.world.isRemote) {
            return;
        }
        ItemStack eaten = event.getItem();
        if (eaten == null || eaten.isEmpty() || EnchantmentMmmm.getLevel(eaten) <= 0) {
            return;
        }
        ItemStack result = event.getResultStack();
        if (!isForbiddenCarrier(result, cfg)) {
            return;
        }
        logOnce(result, "eat", "result restored");
        // Reproduce what ItemFood.onItemUseFinish would have returned: the same stack, one smaller,
        // falling back to the container item (bowl, bottle) once it runs out.
        ItemStack remainder = eaten.copy();
        remainder.shrink(1);
        if (remainder.isEmpty()) {
            remainder = eaten.getItem().hasContainerItem(eaten)
                    ? eaten.getItem().getContainerItem(eaten)
                    : ItemStack.EMPTY;
        }
        event.setResultStack(remainder);
    }

    /** HIGHEST: fix the stack before it is ever merged into the inventory. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemPickup(EntityItemPickupEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        guardEntityItem(event.getItem(), "pickup");
    }

    /** HIGHEST: a stack thrown out of the inventory is the other moment it leaves our sight. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemToss(ItemTossEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        guardEntityItem(event.getEntityItem(), "toss");
    }

    // ------------------------------------------------------------------ internals

    /** Config block when both gates are open, otherwise null. Read live on every event. */
    private static MmmmCategory activeConfig() {
        if (!ModConfig.modules.enableMmmm) {
            return null;
        }
        MmmmCategory cfg = ModConfig.enchantments.mmmm;
        return cfg.protectCarrierFromSwap ? cfg : null;
    }

    private void guardEntityItem(EntityItem entity, String route) {
        MmmmCategory cfg = activeConfig();
        if (cfg == null || entity == null) {
            return;
        }
        ItemStack stack = entity.getItem();
        if (EnchantmentMmmm.getLevel(stack) <= 0) {
            return;
        }
        if (!isForbiddenCarrier(stack, cfg)) {
            if (isEligibleCarrier(stack)) {
                stamp(stack);
            }
            return;
        }
        ItemStack fixed = correct(stack, cfg, route);
        if (!fixed.isEmpty()) {
            entity.setItem(fixed);
        }
    }

    /**
     * Decide what a known-bad stack should become.
     *
     * @return the replacement stack when the caller has to swap it in, or {@link ItemStack#EMPTY} when
     *         there was nothing to restore to - in which case the enchantment has already been stripped
     *         from {@code stack} <i>in place</i> and the caller needs to do nothing further.
     */
    private static ItemStack correct(ItemStack stack, MmmmCategory cfg, String route) {
        ItemStack restored = restoreFromBreadcrumb(stack);
        if (!restored.isEmpty() && !isForbiddenCarrier(restored, cfg)) {
            logOnce(stack, route, "restored to " + restored.getItem().getRegistryName());
            return restored;
        }
        if (stripMmmm(stack)) {
            logOnce(stack, route, "stripped");
        }
        return ItemStack.EMPTY;
    }

    /** Remember what this stack is, so a later swap can be undone. Cheap and idempotent. */
    private static void stamp(ItemStack stack) {
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null) {
            return;
        }
        String value = id.toString() + "#" + stack.getItemDamage();
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        } else if (value.equals(stack.getTagCompound().getString(CARRIER_TAG))) {
            return;
        }
        stack.getTagCompound().setString(CARRIER_TAG, value);
    }

    /** The food this stack used to be, rebuilt with its NBT (and so its enchantment) intact. */
    private static ItemStack restoreFromBreadcrumb(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        String raw = tag.getString(CARRIER_TAG);
        if (raw.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int hash = raw.indexOf('#');
        String name = hash < 0 ? raw : raw.substring(0, hash);
        if (name.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int meta = 0;
        if (hash >= 0) {
            try {
                meta = Integer.parseInt(raw.substring(hash + 1));
            } catch (NumberFormatException ignored) {
                meta = 0;
            }
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack restored = new ItemStack(item, stack.getCount(), meta);
        restored.setTagCompound(tag.copy());
        return restored;
    }

    /**
     * Delete Mmmm from a stack's NBT, in place.
     *
     * <p>🚨 This edits the {@link NBTTagList} directly and must keep doing so:
     * {@code EnchantmentHelper.setEnchantments} cannot <i>remove</i> an entry from a book, because for
     * {@code Items.ENCHANTED_BOOK} it calls {@code ItemEnchantedBook.addEnchantment} per entry without
     * clearing {@code StoredEnchantments} first. Same reasoning, and same three traps, as
     * {@code enchanteraser}'s {@code EraserState.stripFromStack} - reimplemented rather than reused
     * because content must not depend on the extracted mods.
     */
    private static boolean stripMmmm(ItemStack stack) {
        if (EnchantmentMmmm.INSTANCE == null || !stack.hasTagCompound()) {
            return false;
        }
        boolean book = stack.getItem() == Items.ENCHANTED_BOOK;
        NBTTagList list = book ? ItemEnchantedBook.getEnchantments(stack) : stack.getEnchantmentTagList();
        if (list.hasNoTags()) {
            return false;
        }
        int mmmmId = Enchantment.getEnchantmentID(EnchantmentMmmm.INSTANCE);
        boolean changed = false;
        // Backwards: removeTag shifts every later index down.
        for (int i = list.tagCount() - 1; i >= 0; i--) {
            // 🚨 getInteger, NOT getShort, even though vanilla writes a short here: JustEnoughIDs
            // widens enchantment ids, and getShort would truncate anything above 32767 into a
            // different enchantment. getInteger is correct either way - both NBTTagShort and NBTTagInt
            // are NBTPrimitive, and getInteger widens rather than truncates.
            if (list.getCompoundTagAt(i).getInteger("id") == mmmmId) {
                list.removeTag(i);
                changed = true;
            }
        }
        if (changed && list.hasNoTags()) {
            stack.getTagCompound().removeTag(book ? "StoredEnchantments" : "ench");
        }
        return changed;
    }

    /**
     * Swap a stack we hold a reference to for its corrected form. Reference equality, not
     * {@code areItemStacksEqual}: we specifically want the one slot holding <i>this</i> object, and an
     * inventory can easily hold several equal stacks.
     */
    private static boolean replaceInInventory(EntityPlayer player, ItemStack original, ItemStack fixed) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            if (player.inventory.getStackInSlot(i) == original) {
                player.inventory.setInventorySlotContents(i, fixed);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether Mmmm refuses to sit on this stack at all. Public because {@link EnchantmentMmmm#canApply}
     * uses it to close the front door - the guard below is only the net for what gets past it.
     *
     * <p>Reads config on every call so the list stays live; returns false outright while either gate is
     * off, so turning the feature off cannot retroactively make an existing item unenchantable.
     */
    public static boolean isForbiddenCarrier(ItemStack stack) {
        MmmmCategory cfg = activeConfig();
        return cfg != null && isForbiddenCarrier(stack, cfg);
    }

    private static boolean isForbiddenCarrier(ItemStack stack, MmmmCategory cfg) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Set<String> forbidden = forbiddenSet(cfg);
        if (forbidden.isEmpty()) {
            return false;
        }
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null) {
            return false;
        }
        String name = id.toString().toLowerCase(Locale.ROOT);
        // A bare "modid:path" entry matches every metadata value; "modid:path#meta" pins one.
        return forbidden.contains(name) || forbidden.contains(name + "#" + stack.getItemDamage());
    }

    private static Set<String> forbiddenSet(MmmmCategory cfg) {
        String[] source = cfg.forbiddenCarriers;
        if (source != cachedSource) {
            Set<String> built = new HashSet<String>();
            if (source != null) {
                for (String raw : source) {
                    if (raw == null) {
                        continue;
                    }
                    String entry = raw.trim().toLowerCase(Locale.ROOT);
                    if (!entry.isEmpty()) {
                        built.add(entry);
                    }
                }
            }
            cachedForbidden = built;
            cachedSource = source;
        }
        return cachedForbidden;
    }

    /**
     * One INFO line per item-and-route, and only while {@code logCarrierGuard} is on. The point is to
     * name the interaction that is rotting enchanted food so it can be fixed at the source.
     */
    private static void logOnce(ItemStack stack, String route, String action) {
        if (!ModConfig.enchantments.mmmm.logCarrierGuard || stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = stack.getItem().getRegistryName();
        String name = id == null ? stack.getItem().getClass().getName() : id.toString();
        if (LOGGED.add(route + "@" + name)) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Mmmm carrier guard: {} carrying the enchantment caught at '{}' - {}.",
                    name, route, action);
        }
    }

    /**
     * Only food is worth remembering. Mmmm also lives on enchanted books on their way to the anvil, and
     * stamping one of those would record "this used to be a book" - true, useless, and confusing in an
     * NBT dump. The single place to widen this if the enchantment ever stops being food-only.
     */
    private static boolean isEligibleCarrier(ItemStack stack) {
        return stack.getItem() instanceof ItemFood;
    }
}
