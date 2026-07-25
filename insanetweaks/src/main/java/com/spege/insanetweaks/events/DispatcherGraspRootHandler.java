package com.spege.insanetweaks.events;

import com.spege.insanetweaks.init.ModSpells;

import electroblob.wizardry.util.EntityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Enforces the caster-side hard root of Dispatcher Grasp.
 *
 * <p>The root itself is a -100% MOVEMENT_SPEED attribute modifier (operation 2,
 * i.e. speed * 0), applied on the first hit by {@code SpellDispatcherGrasp} and
 * removed in its {@code finishCasting}. Attribute modifiers persist in the
 * player's NBT, so relogging or dying mid-cast would otherwise leave the player
 * permanently rooted — hence the safety net below, which strips the modifier the
 * moment the player is no longer channelling the spell.
 *
 * <p>Shape copied from {@link ZhonyaStasisHandler#applyRootModifier} /
 * {@code removeRootModifier} (own UUID, so the two roots never clobber each
 * other), including the both-sides motion backstop: the speed attribute alone
 * does not kill residual momentum or a jump already in flight.
 */
public class DispatcherGraspRootHandler {

    /** Fixed UUID for the Dispatcher Grasp movement-speed root modifier (op 2, -100%). */
    public static final java.util.UUID GRASP_ROOT_MODIFIER_UUID =
            java.util.UUID.fromString("3f5a1c78-9d24-4e61-b0af-6c2e73d1584b");
    private static final String GRASP_ROOT_MODIFIER_NAME = "InsaneTweaks Dispatcher Grasp Root";

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        EntityPlayer player = event.player;
        boolean rooted = hasRootModifier(player);

        if (!rooted) {
            return;
        }

        if (EntityUtils.isCasting(player, ModSpells.DISPATCHER_GRASP)) {
            // Backstop — both sides, so the client doesn't visibly slide while the
            // server holds the player in place.
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
            if (player.motionY > 0.0D) {
                player.motionY = 0.0D;
            }
            return;
        }

        // Safety net: modifier present but the channel is gone (logout, death,
        // interrupted cast, world unload mid-cast). Server owns the attribute map.
        if (!player.world.isRemote) {
            removeRootModifier(player);
        }
    }

    private static boolean hasRootModifier(EntityPlayer player) {
        net.minecraft.entity.ai.attributes.IAttributeInstance speed =
                player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(GRASP_ROOT_MODIFIER_UUID) != null;
    }

    /** Applies the -100% movement-speed root (operation 2 => speed * 0). Idempotent. */
    public static void applyRootModifier(EntityPlayer player) {
        net.minecraft.entity.ai.attributes.IAttributeInstance speed =
                player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        net.minecraft.entity.ai.attributes.AttributeModifier existing = speed.getModifier(GRASP_ROOT_MODIFIER_UUID);
        if (existing != null) speed.removeModifier(existing);
        speed.applyModifier(new net.minecraft.entity.ai.attributes.AttributeModifier(
                GRASP_ROOT_MODIFIER_UUID, GRASP_ROOT_MODIFIER_NAME, -1.0D, 2));
    }

    public static void removeRootModifier(EntityPlayer player) {
        net.minecraft.entity.ai.attributes.IAttributeInstance speed =
                player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        net.minecraft.entity.ai.attributes.AttributeModifier existing = speed.getModifier(GRASP_ROOT_MODIFIER_UUID);
        if (existing != null) speed.removeModifier(existing);
    }
}
