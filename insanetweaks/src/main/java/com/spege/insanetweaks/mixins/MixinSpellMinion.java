package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import electroblob.wizardry.spell.SpellMinion;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

@Mixin(value = SpellMinion.class, remap = false)
public abstract class MixinSpellMinion {

    // Two @Redirect targets are sufficient:
    //   1. spawnMinions()            — covers the normal player cast path, because
    //      cast(World,EntityPlayer,EnumHand,int,SpellModifiers) delegates to spawnMinions()
    //      internally. Patching spawnMinions once handles all player-initiated summons.
    //   2. cast(World,double,...,SpellModifiers) — the dispenser cast reads MINION_COUNT
    //      independently (does not always call spawnMinions), so it needs its own @Redirect.
    //
    // Adding a third @Redirect for the player cast would cause double-application of the
    // bonus (once here, once inside spawnMinions from within that same cast call).

    @Redirect(method = "spawnMinions", at = @At(value = "INVOKE", target = "Lelectroblob/wizardry/spell/SpellMinion;getProperty(Ljava/lang/String;)Ljava/lang/Number;"), remap = false)
    private Number insanetweaks$applyGlobalMinionCountInSpawn(SpellMinion<?> spell, String propertyName, World world,
            EntityLivingBase caster, SpellModifiers modifiers) {
        return this.insanetweaks$modifyProperty(spell, propertyName, modifiers);
    }

    @Redirect(method = "cast(Lnet/minecraft/world/World;DDDLnet/minecraft/util/EnumFacing;IILelectroblob/wizardry/util/SpellModifiers;)Z", at = @At(value = "INVOKE", target = "Lelectroblob/wizardry/spell/SpellMinion;getProperty(Ljava/lang/String;)Ljava/lang/Number;"), remap = false)
    private Number insanetweaks$applyGlobalMinionCountInDispenserCast(SpellMinion<?> spell, String propertyName,
            World world, double x, double y, double z, EnumFacing facing, int ticksInUse, int something,
            SpellModifiers modifiers) {
        return this.insanetweaks$modifyProperty(spell, propertyName, modifiers);
    }

    @Unique
    private Number insanetweaks$modifyProperty(SpellMinion<?> spell, String propertyName, SpellModifiers modifiers) {
        Number base = spell.getProperty(propertyName);

        if (!SpellMinion.MINION_COUNT.equals(propertyName)) {
            return base;
        }

        int flatBonus = this.insanetweaks$getFlatMinionCountBonus(modifiers);
        if (flatBonus <= 0) {
            return base;
        }

        return Integer.valueOf(base.intValue() + flatBonus);
    }

    @Unique
    private int insanetweaks$getFlatMinionCountBonus(SpellModifiers modifiers) {
        return Math.max(0, Math.round(modifiers.get(SpellMinion.MINION_COUNT)) - 1);
    }
}
