package com.spege.tombtweaks.mixins.enigmatic;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.tombtweaks.util.CurseReliefHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Softens two of the three Ring of the Seven Curses penalties for holders of the
 * "Relief for the Damned" perk.
 *
 * <p>Enigmatic Legacy exposes no API, event or capability for its curses, so the reduction is
 * applied at the exact instruction where the mod reads its own constant. That placement is the
 * point: by the time these run, every one of EL's own guards has already passed — the ring is
 * worn, the victim is an {@code EntityMob} or {@code EntityDragon}, the attacker is the player,
 * and The Twist / The Infinitum / Eldritch Pan have had their chance to exempt the hit. We only
 * change the number EL is about to multiply by, so its logic stays entirely intact.
 *
 * <p>An event handler at {@code LOWEST} would have been the alternative, but it would mean
 * dividing the already-multiplied damage back out and re-deriving every one of those guards by
 * hand — wrong the moment EL changes one of them.
 *
 * <p>Verified with {@code javap -p -c} against enigmaticlegacy-legacy-2.6.0: each field is read
 * exactly once in the entire class, both reads inside {@code onEntityHurt}, so no
 * {@code ordinal} is needed. Re-check on any EL update.
 */
@Mixin(targets = "keletu.enigmaticlegacy.event.EnigmaticEvents", remap = false)
public abstract class MixinEnigmaticEventsCurses {

    /**
     * Curse 1: the wearer takes {@code painMultiplier}x damage from every source (default 2.0).
     * The victim here is the perk holder.
     */
    @Redirect(
            method = "onEntityHurt",
            at = @At(value = "FIELD",
                    target = "Lkeletu/enigmaticlegacy/EnigmaticConfigs;painMultiplier:F",
                    opcode = Opcodes.GETSTATIC),
            remap = false)
    private static float tombtweaks$softenPainMultiplier(LivingHurtEvent event) {
        float base = keletu.enigmaticlegacy.EnigmaticConfigs.painMultiplier;
        EntityLivingBase victim = event.getEntityLiving();
        if (!(victim instanceof EntityPlayer)) {
            return base;
        }
        return CurseReliefHelper.softenMultiplier(base, (EntityPlayer) victim);
    }

    /**
     * Curse 2: monsters take {@code (1 - monsterDamageDebuff)}x damage from the wearer
     * (default 0.5, i.e. half). The perk holder is the attacker.
     */
    @Redirect(
            method = "onEntityHurt",
            at = @At(value = "FIELD",
                    target = "Lkeletu/enigmaticlegacy/EnigmaticConfigs;monsterDamageDebuff:F",
                    opcode = Opcodes.GETSTATIC),
            remap = false)
    private static float tombtweaks$softenMonsterDamageDebuff(LivingHurtEvent event) {
        float base = keletu.enigmaticlegacy.EnigmaticConfigs.monsterDamageDebuff;
        if (event.getSource() == null) {
            return base;
        }
        Entity attacker = event.getSource().getTrueSource();
        if (!(attacker instanceof EntityPlayer)) {
            return base;
        }
        return CurseReliefHelper.softenDebuff(base, (EntityPlayer) attacker);
    }
}
