package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.items.nunchaku.ParasiteEffectBypass;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;

/**
 * SRP faction fix for third-party parasites (v3.2).
 *
 * {@code EntityParasiteBase.canAttackClass} excludes fellow parasites by a REGISTRY-NAME
 * STRING check: {@code EntityList.getKey(cls).toString().contains("srparasites")}. Any
 * parasite subclass registered under another namespace - like our
 * {@code insanetweaks:sim_wizard} - passes that filter, so native SRP mobs proactively
 * target it through {@code EntityAINearestAttackableTargetStatus.isSuitableTarget}, which
 * delegates to this method. That is why parasites kept attacking the sim_wizard even after
 * friendly-fire damage was cancelled.
 *
 * Fix: inject at HEAD and return {@code false} whenever the target CLASS is itself an
 * {@link EntityParasiteBase} subclass - the instanceof semantics SRP's string check clearly
 * intends. Covers sim_wizard, any future insanetweaks parasite (sim_battlemage), and other
 * mods' parasite subclasses (e.g. srpextra). Unconditional bugfix semantics - no config
 * gate, per mixin_authoring_guide section 9.
 *
 * Dual method name: dev jars are fg.deobf-remapped so the override is named
 * {@code canAttackClass}; production SRP jar keeps the SRG name {@code func_70686_a}.
 * With {@code defaultRequire = 1} in late.json exactly one of the two must match.
 */
@Mixin(value = EntityParasiteBase.class, remap = false)
public abstract class MixinEntityParasiteBase {

    @Inject(method = {"canAttackClass", "func_70686_a"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void insanetweaks$neverTargetFellowParasites(Class<? extends EntityLivingBase> cls,
            CallbackInfoReturnable<Boolean> cir) {
        if (cls != null && EntityParasiteBase.class.isAssignableFrom(cls)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    /**
     * Przepuszcza efekty nakładane przez pasożytnicze nunchaku mimo odporności pasożyta.
     *
     * <p>{@code isPotionApplicable} odrzuca w SRP dokładnie cztery efekty — {@code COTH_E},
     * {@code VIRA_E}, {@code CORRO_E}, {@code DLER_E} — a dwa z nich (Viral i Needler) są całą
     * wartością naszej broni. Bez tego wstrzyknięcia nunchaku wykuwane z pasożytów nie robi
     * pasożytom nic.
     *
     * <p>🚨 Bramka jest CELOWO wąska. Reagujemy wyłącznie, gdy bieżący wątek stoi w środku
     * naszego {@code addPotionEffect} — patrz {@link ParasiteEffectBypass}. Każde inne źródło,
     * łącznie z własnymi atakami obszarowymi SRP, napotyka nietkniętą odporność, więc pasożyty
     * dalej nie zarażają same siebie. To jest powód, dla którego blacklista prawdopodobnie
     * istnieje, i nie chcemy go znieść.
     *
     * <p>Podwójna nazwa metody z tego samego powodu, co przy {@code canAttackClass} powyżej:
     * jar w środowisku dev bywa zremapowany na nazwy MCP, produkcyjny trzyma SRG. Przy
     * {@code defaultRequire = 1} dokładnie jedna z nich musi się dopasować.
     */
    @Inject(method = {"isPotionApplicable", "func_70687_e"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void insanetweaks$allowOurParasiteEffects(PotionEffect effect,
            CallbackInfoReturnable<Boolean> cir) {
        if (ParasiteEffectBypass.isActive()) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
