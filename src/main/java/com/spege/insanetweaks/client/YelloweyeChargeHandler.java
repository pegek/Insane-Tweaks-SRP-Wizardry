package com.spege.insanetweaks.client;

import java.util.Random;

import com.dhanantry.scapeandrunparasites.client.particle.ParticleSpawner;
import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.spege.insanetweaks.init.ModSpells;

import electroblob.wizardry.item.ISpellCastingItem;
import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders yellow-green SRP DOT particles converging on the casting hand while
 * the Yelloweye Gland chargeup is in progress. Purely cosmetic, no networking;
 * EBW itself provides the charge sound and the HUD charge bar. Renders for
 * every charging player this client can see — for remote players the use-tick
 * counter is a local approximation (vanilla never syncs it), which is fine for
 * a visual effect.
 */
@SideOnly(Side.CLIENT)
public class YelloweyeChargeHandler {

    private static final int CHARGE_RGB_R = 190;
    private static final int CHARGE_RGB_G = 210;
    private static final int CHARGE_RGB_B = 60;

    private final Random rand = new Random();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != Side.CLIENT || event.phase != TickEvent.Phase.END) {
            return;
        }

        EntityPlayer player = event.player;
        if (!player.isHandActive()) {
            return;
        }

        ItemStack stack = player.getActiveItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ISpellCastingItem)) {
            return;
        }

        Spell spell = ((ISpellCastingItem) stack.getItem()).getCurrentSpell(stack);
        // Compares against the unscaled chargeup: a chargeup-reducing wand
        // modifier makes the real shot fire a few ticks before particles stop.
        // Accepted simplification for a cosmetic effect.
        if (spell != ModSpells.YELLOWEYE_GLAND || player.getItemInUseMaxCount() >= spell.getChargeup()) {
            return;
        }

        Vec3d look = player.getLookVec();
        double anchorX = player.posX + look.x * 0.6D;
        double anchorY = player.posY + player.getEyeHeight() - 0.35D + look.y * 0.6D;
        double anchorZ = player.posZ + look.z * 0.6D;

        for (int i = 0; i < 2; i++) {
            double px = anchorX + (this.rand.nextFloat() * 2.0F - 1.0F) * 0.7D;
            double py = anchorY + (this.rand.nextFloat() * 2.0F - 1.0F) * 0.5D;
            double pz = anchorZ + (this.rand.nextFloat() * 2.0F - 1.0F) * 0.7D;
            ParticleSpawner.spawnParticle(SRPEnumParticle.DOT, px, py, pz,
                    (anchorX - px) * 0.2D, (anchorY - py) * 0.2D, (anchorZ - pz) * 0.2D,
                    CHARGE_RGB_R, CHARGE_RGB_G, CHARGE_RGB_B);
        }
    }
}
