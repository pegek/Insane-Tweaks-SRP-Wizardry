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
