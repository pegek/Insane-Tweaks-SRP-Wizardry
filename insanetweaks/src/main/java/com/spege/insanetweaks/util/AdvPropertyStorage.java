package com.spege.insanetweaks.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * Per-<b>stack</b> advanced properties, stored in the item's own NBT.
 *
 * <p>Until this existed, a property could only come from the {@code Item} class through
 * {@link com.spege.insanetweaks.api.ITweaksPropertyHolder}, which meant a property was a fact about
 * a <i>kind</i> of item and never about a particular one. Property Books need the opposite, so the
 * grant lives on the instance.
 *
 * <p>The tag is root-level and carries the modid in its name for the same reason as
 * {@link EnchantGrantMarker#GRANTED_TAG}: it gets hand-authored inside FTB Quests reward JSON, where
 * a short or ambiguous key is a trap. The canonical form is
 * <pre>{insanetweaks_properties:["ashen_legacy"]}</pre>
 *
 * <p>Reads never allocate the tag, so calling {@link #has} on an ordinary stack costs one map
 * lookup and nothing else - which matters, because the tooltip and drop handlers call it constantly.
 */
public final class AdvPropertyStorage {

    /** Root-level NBT key holding a list of property ids. */
    public static final String PROPERTIES_TAG = "insanetweaks_properties";

    private AdvPropertyStorage() {
    }

    /** Property ids granted to this specific stack. Empty, never null. */
    public static List<String> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Collections.emptyList();
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(PROPERTIES_TAG, 9)) {
            return Collections.emptyList();
        }
        NBTTagList list = tag.getTagList(PROPERTIES_TAG, 8);
        int count = list.tagCount();
        if (count == 0) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            String id = list.getStringTagAt(i);
            if (id != null && !id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Whether this stack's own NBT grants {@code propertyId}. Ignores class- and enchant-granted. */
    public static boolean has(ItemStack stack, String propertyId) {
        if (propertyId == null) {
            return false;
        }
        return read(stack).contains(propertyId);
    }

    /**
     * Grants {@code propertyId} to this stack.
     *
     * @return false when the stack already had it, so callers can refuse a no-op anvil recipe
     */
    public static boolean add(ItemStack stack, String propertyId) {
        if (stack == null || stack.isEmpty() || propertyId == null || propertyId.isEmpty()) {
            return false;
        }
        if (has(stack, propertyId)) {
            return false;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return false;
        }
        NBTTagList list = tag.hasKey(PROPERTIES_TAG, 9)
                ? tag.getTagList(PROPERTIES_TAG, 8)
                : new NBTTagList();
        list.appendTag(new NBTTagString(propertyId));
        tag.setTag(PROPERTIES_TAG, list);
        return true;
    }

    /**
     * Revokes {@code propertyId} from this stack, removing the tag entirely once it is empty so a
     * stack that never really had a property does not stop stacking with its plain twin.
     *
     * @return false when the stack did not have it
     */
    public static boolean remove(ItemStack stack, String propertyId) {
        if (!has(stack, propertyId)) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return false;
        }
        NBTTagList kept = new NBTTagList();
        for (String id : read(stack)) {
            if (!id.equals(propertyId)) {
                kept.appendTag(new NBTTagString(id));
            }
        }
        if (kept.tagCount() == 0) {
            tag.removeTag(PROPERTIES_TAG);
        } else {
            tag.setTag(PROPERTIES_TAG, kept);
        }
        return true;
    }
}
