package com.spege.srpwizcore.dormant;

import java.util.List;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Client-only half of the Dormant Eye: the key item's tooltip, including the exact XYZ of the
 * nearest waystone once the server has written it into the stack NBT (see
 * {@link DormantEyeHandler}, which owns the server side of that contract).
 *
 * <p>🚨 This must stay a separate, {@code Side.CLIENT}-scoped class. {@code ItemTooltipEvent}
 * exposes {@code net.minecraft.client.util.ITooltipFlag}, so merely handing a class containing
 * this method to {@code EventBus.register()} resolves a client-only type — fatal on a dedicated
 * server, regardless of the event never firing there. {@code @Mod.EventBusSubscriber} with an
 * explicit side is safe because FML checks the side <em>before</em> it does {@code Class.forName}
 * on the subscriber.
 */
@Mod.EventBusSubscriber(modid = SrpWizCore.MODID, value = net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class DormantEyeTooltipHandler {

    private DormantEyeTooltipHandler() {
    }

    private static final String HINT_1 =
            "§5Locates a hidden waystone that opens a portal to the §8Underneath§5.";
    private static final String HINT_2 =
            "§8Hold it — coloured motes trace the path to the nearest one.";

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        // Registration is unconditional (annotation-driven), so the master switch is checked here
        // instead of at register time as it is for the server-side handlers in SrpWizCore.init().
        if (!SrpWizCoreConfig.dormantWaystones.enabled) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!DormantTeleportHandler.isKeyItem(stack)) {
            return;
        }
        List<String> tt = event.getToolTip();
        if (!tt.contains(HINT_1)) {
            tt.add(HINT_1);
            tt.add(HINT_2);
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(DormantEyeHandler.NBT_KEY)) {
            NBTTagCompound c = tag.getCompoundTag(DormantEyeHandler.NBT_KEY);
            tt.add("§7Nearest waystone: §f"
                    + c.getInteger("x") + " " + c.getInteger("y") + " " + c.getInteger("z"));
        }
    }
}
