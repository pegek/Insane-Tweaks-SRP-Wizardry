package com.spege.srpwizcore.mixins;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side thread-safety fix for multithreaded entity ticking (EntityThreading).
 *
 * <p>EntityThreading patches the COMMON {@code World.updateEntities}, so client-world
 * entities tick on worker threads too. Its own deferral covers only
 * {@code World.playSound}; anything reaching {@code SoundManager} directly from a worker
 * thread mutates the {@code playingSounds} {@code HashBiMap} while the client tick
 * iterates it in {@code updateAllSounds} (CME crash 2026-07-25 03:50). Off-thread calls
 * to the three public mutators are replayed on the client main thread via
 * {@link Minecraft#addScheduledTask} — the replay passes the thread check and runs the
 * full original method (including other mods' hooks). Gated on
 * {@code threadingCompat.fixSoundManagerBounce}.
 */
@Mixin(SoundManager.class)
public class MixinSoundManagerBounce {

    @Inject(method = {"playSound", "func_148611_c"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void srpwizcore$bouncePlaySound(final ISound sound, CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!SrpWizCoreConfig.threadingCompat.fixSoundManagerBounce
                || mc.isCallingFromMinecraftThread()) {
            return;
        }
        final SoundManager self = (SoundManager) (Object) this;
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                self.playSound(sound);
            }
        });
        ci.cancel();
    }

    @Inject(method = {"stopSound", "func_148602_b"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void srpwizcore$bounceStopSound(final ISound sound, CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!SrpWizCoreConfig.threadingCompat.fixSoundManagerBounce
                || mc.isCallingFromMinecraftThread()) {
            return;
        }
        final SoundManager self = (SoundManager) (Object) this;
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                self.stopSound(sound);
            }
        });
        ci.cancel();
    }

    @Inject(method = {"stopAllSounds", "func_148614_c"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void srpwizcore$bounceStopAllSounds(CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!SrpWizCoreConfig.threadingCompat.fixSoundManagerBounce
                || mc.isCallingFromMinecraftThread()) {
            return;
        }
        final SoundManager self = (SoundManager) (Object) this;
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                self.stopAllSounds();
            }
        });
        ci.cancel();
    }
}
