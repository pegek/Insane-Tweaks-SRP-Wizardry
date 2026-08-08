package com.spege.srpwizmixins.mixins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dhanantry.scapeandrunparasites.bestiary.client.gui.GuiDistortionHelper;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.client.Minecraft;

/**
 * Fix - {@link java.util.ConcurrentModificationException} in the bestiary's GUI-distortion check,
 * which breaks item tooltips in JEI/HEI.
 *
 * <p>{@code GuiDistortionHelper.isDistortionActive} decides whether a nearby parasite should distort
 * the interface, and it does so by walking {@code mc.world.loadedEntityList} with a plain iterator.
 * The call reaches it through {@code shouldDistortItemTooltips} -> {@code DerivedDistortionTextHandler
 * .onItemTooltip}, a {@code ItemTooltipEvent} handler - and HEI rebuilds its tooltip cache on its own
 * worker thread ("Set Bonus HEI Tooltip Reload"). So the list is being mutated by the client thread
 * while the HEI thread iterates it, and the iterator throws:
 *
 * <pre>
 * [Had Enough Items]: Failed to get tooltip: 1xitem.ebwizardry:spell_book@69
 * java.util.ConcurrentModificationException
 *     at java.util.ArrayList$Itr.checkForComodification
 *     at GuiDistortionHelper.isDistortionActive(GuiDistortionHelper.java:75)
 * </pre>
 *
 * <p>The exception escapes through Forge's tooltip event, so the item ends up with no tooltip at all
 * - visible to the player, not just noise in the log.
 *
 * <p>We {@link Redirect} the one {@code List.iterator()} call in that method. On the client thread
 * nothing changes: the original iterator is returned, so there is no allocation on the render path.
 * Off-thread we iterate a snapshot instead, which cannot be invalidated mid-walk. The snapshot is
 * taken with {@code new ArrayList<>(list)}, i.e. through {@code toArray()} rather than an iterator,
 * so it cannot throw the same exception; a concurrent grow can leave trailing {@code null} slots,
 * and SRP's loop already skips null entries.
 *
 * <p>Reading entity positions off-thread stays approximate either way - that is inherent to a
 * cosmetic check being called from a worker, and a stale answer here costs nothing. As a last
 * resort any other failure yields an empty iterator, i.e. "no distortion", because throwing back
 * into HEI is the one outcome worth avoiding.
 *
 * <p>Client-only: registered under {@code "client"} in {@code mixins.srpwizmixins.json}. Gated on
 * {@code srpCompat.fixDistortionTooltipCrash}; with the flag off the original iterator is always
 * returned and the method behaves exactly like unmodified SRP.
 */
@Mixin(value = GuiDistortionHelper.class, remap = false)
public abstract class MixinSrpDistortionTooltipThread {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Redirect(
            method = "isDistortionActive",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;iterator()Ljava/util/Iterator;"),
            remap = false)
    private static Iterator insanetweaks$snapshotEntityList(List list) {
        if (!SrpWizMixinsConfig.srpCompat.fixDistortionTooltipCrash) {
            return list.iterator();
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.isCallingFromMinecraftThread()) {
            return list.iterator();
        }
        try {
            return new ArrayList(list).iterator();
        } catch (Throwable t) {
            return Collections.emptyList().iterator();
        }
    }
}
