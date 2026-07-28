package com.spege.insanetweaks.items;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.api.AdvPropertyRegistry.Property;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A book that grants one advanced property to one item, applied on an anvil.
 *
 * <p><b>One item, many variants.</b> The property id lives in the stack's own NBT under
 * {@link #PROPERTY_TAG} rather than there being a separate {@code Item} per property - the same
 * shape vanilla uses for {@code ItemEnchantedBook}. That keeps the registry from growing by one
 * entry per property, keeps every book on a single model and texture, and means adding a property
 * needs no registration work at all: register it as book-grantable and its book appears.
 *
 * <p>The tag name carries the modid for the same reason as
 * {@code AdvPropertyStorage.PROPERTIES_TAG} and {@code EnchantGrantMarker.GRANTED_TAG} - these get
 * hand-written into FTB Quests reward JSON:
 * <pre>{insanetweaks_property:"ashen_legacy"}</pre>
 *
 * <p>A book with no tag, or with an unknown id, is inert: it names no property, and the anvil
 * handler refuses it. That is the correct behaviour for a book left in a world after its property
 * was removed from the registry.
 */
public class PropertyBookItem extends Item {

    /** Root-level NBT key naming the property this book grants. */
    public static final String PROPERTY_TAG = "insanetweaks_property";

    @SuppressWarnings("null")
    public PropertyBookItem() {
        super();
        this.setRegistryName("property_book");
        this.setUnlocalizedName("insanetweaks.property_book");
        this.setMaxStackSize(1);
        this.setMaxDamage(0);
        this.setCreativeTab(CreativeTabs.MISC);
    }

    /** Mints a book for {@code propertyId}. Does not check that the id is registered. */
    public static ItemStack create(Item bookItem, String propertyId) {
        ItemStack stack = new ItemStack(bookItem);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(PROPERTY_TAG, propertyId);
        stack.setTagCompound(tag);
        return stack;
    }

    /** The property id written on this book, or null if it carries none. */
    @Nullable
    public static String getPropertyId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof PropertyBookItem)) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(PROPERTY_TAG, 8)) {
            return null;
        }
        String id = tag.getString(PROPERTY_TAG);
        return id.isEmpty() ? null : id;
    }

    /** The registered property this book grants, or null when the book is blank or stale. */
    @Nullable
    public static Property getProperty(ItemStack stack) {
        String id = getPropertyId(stack);
        return id == null ? null : AdvPropertyRegistry.getProperty(id);
    }

    /**
     * Uses the <b>deprecated</b> {@code net.minecraft.util.text.translation.I18n} rather than the
     * client one, and that is not an oversight: this method runs on <b>both</b> sides -
     * {@code ItemStack.getDisplayName()} is called server-side for chat components and by our own
     * {@code PropertyBookAnvilHandler} when it carries a rename through - so touching
     * {@code net.minecraft.client.resources.I18n} here would be a {@code NoClassDefFoundError} on a
     * dedicated server. Vanilla's own {@code Item.getItemStackDisplayName} uses this same
     * deprecated class for exactly this reason.
     */
    @Override
    @Nonnull
    @SuppressWarnings("deprecation")
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        Property property = getProperty(stack);
        if (property == null) {
            return TextFormatting.GRAY
                    + net.minecraft.util.text.translation.I18n.translateToLocal(
                            "item.insanetweaks.property_book.blank");
        }
        return TextFormatting.GRAY
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                        "item.insanetweaks.property_book.named",
                        property.color + property.displayName + TextFormatting.GRAY);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        Property property = getProperty(stack);
        if (property == null) {
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.insanetweaks.property_book.blank"));
            return;
        }
        tooltip.add(TextFormatting.GOLD + I18n.format("tooltip.insanetweaks.property_book.grants"));
        property.addTooltipLines(tooltip, net.minecraft.client.gui.GuiScreen.isShiftKeyDown());
        tooltip.add("");
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.insanetweaks.property_book.anvil"));
    }

    /**
     * One entry per book-grantable property, so JEI and the creative tab list them all. Without
     * this the tab would show a single blank book and every real variant would be invisible.
     */
    @Override
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!this.isInCreativeTab(tab)) {
            return;
        }
        for (Property property : AdvPropertyRegistry.getAll()) {
            if (property.bookGrantable) {
                items.add(create(this, property.id));
            }
        }
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return getProperty(stack) != null;
    }

    @Override
    public boolean isDamageable() {
        return false;
    }
}
