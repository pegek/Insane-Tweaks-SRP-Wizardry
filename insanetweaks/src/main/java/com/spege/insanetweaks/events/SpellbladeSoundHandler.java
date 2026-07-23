package com.spege.insanetweaks.events;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.spege.insanetweaks.items.spellblade.BridgeSpellblade;

@SideOnly(Side.CLIENT)
public class SpellbladeSoundHandler {

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onSoundPlayed(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || event.getSound() == null) return;
        
        // Extract the coordinates of the audio source from the event
        float x = event.getSound().getXPosF();
        float y = event.getSound().getYPosF();
        float z = event.getSound().getZPosF();
        
        // Find the player closest to the sound origin (4-block radius), since they are the actual attacker.
        // The 'false' parameter makes the search ignore Spectator Mode players.
        EntityPlayer sourcePlayer = mc.world.getClosestPlayer(x, y, z, 4.0D, false);
        
        if (sourcePlayer != null) {
            ItemStack mainhand = sourcePlayer.getHeldItemMainhand();
            
            // Verify the inventory of the audio source (the actual attacking player)
            if (!mainhand.isEmpty() && mainhand.getItem() instanceof BridgeSpellblade) {
                ResourceLocation soundReg = event.getSound().getSoundLocation();
            
            // If the sound played originates from the Ancient Spellcraft mod...
            if (soundReg != null && ("ancientspellcraft".equals(soundReg.getResourceDomain()) || "ebwizardry".equals(soundReg.getResourceDomain()))) {
                String path = soundReg.getResourcePath();
                
                // And if its path resembles a sword strike or wand melee fighting (WizardrySounds.ITEM_WAND_MELEE)
                if (path.contains("sword") || path.contains("swing") || path.contains("hit") || path.contains("battlemage") || path.contains("attack") || path.contains("melee")) {
                    
                    // Replace the forced mage sound with a classic, clean saber / sword swing sound.
                    // Keep all spatial properties (X, Y, Z, volume) intact.
                    event.setResultSound(new net.minecraft.client.audio.PositionedSoundRecord(
                            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                            event.getSound().getCategory(),
                            1.0F, 
                            1.0F,
                            event.getSound().getXPosF(),
                            event.getSound().getYPosF(),
                            event.getSound().getZPosF()
                    ));
                }
            }
            }
        }
    }
}

