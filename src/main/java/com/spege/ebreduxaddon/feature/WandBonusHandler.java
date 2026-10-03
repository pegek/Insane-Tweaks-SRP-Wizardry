package com.spege.ebreduxaddon.feature;

import com.binaris.wizardry.api.content.event.SpellCastEvent;
import com.binaris.wizardry.api.content.spell.internal.CastContext;
import com.binaris.wizardry.api.content.spell.internal.PlayerCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.api.content.util.CastItemUtils;
import com.binaris.wizardry.core.event.WizardryEventBus;
import com.spege.ebreduxaddon.core.SymbiosisCurve;
import com.spege.ebreduxaddon.feature.entity.PurifyingWaveEntity;
import com.spege.ebreduxaddon.feature.item.SentientWandItem;
import com.spege.ebreduxaddon.feature.item.SymbioticWandItem;
import com.spege.ebreduxaddon.platform.Config;
import com.spege.ebreduxaddon.platform.WandProgress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Bonusy i postep rozdzek Symbiotic/Sentient.
 *
 * <ul>
 *   <li><b>Pre</b> (szyna Redux): mnozniki kosztu, czasu trwania i potency. 🚨 Pre leci w KAZDYM
 *   ticku kanalu na tym samym obiekcie modyfikatorow (WandItem.createContext oddaje dla tick &gt; 0
 *   instancje zapisana w WizardData), wiec mnozenie bez znacznika naroslaby wykladniczo. Znacznik
 *   {@link #MARKER} w samych modyfikatorach mowi "juz zastosowane". Chargeup jest liczony przed Pre
 *   i tu go nie ruszamy.</li>
 *   <li><b>Post</b> (tylko castingTicks == 0) i <b>Finish</b> (koniec kanalu): punkty symbiozy =
 *   mana faktycznie pobrana, liczona tymi samymi funkcjami Redux, ktore ja pobieraja.</li>
 *   <li>Zabicie moba z spore:fungus_entities z rozdzka w reku: {@code wand.fungalKillPoints}.</li>
 *   <li>Ewolucja w {@link WandEvolution}, na koncu ticku gracza - nigdy w srodku Post, zeby Redux
 *   nie pisal juz po podmianie do starego stacka.</li>
 * </ul>
 */
public final class WandBonusHandler {

    static final String MARKER = "ebreduxaddon.wand_bonus";

    private WandBonusHandler() {
    }

    public static void registerRedux() {
        WizardryEventBus bus = WizardryEventBus.getInstance();
        bus.register(SpellCastEvent.Pre.class, WandBonusHandler::onPre);
        bus.register(SpellCastEvent.Post.class, WandBonusHandler::onPost);
        bus.register(SpellCastEvent.Finish.class, WandBonusHandler::onFinish);
    }

    static void onPre(SpellCastEvent.Pre event) {
        if (event.getSource() != SpellCastEvent.Sources.WAND || !(event.getContext() instanceof PlayerCastContext ctx)) {
            return;
        }
        applyBonus(ctx.caster().getItemInHand(ctx.hand()), event.getModifiers());
    }

    /** Publiczne dla GameTestow. Zwraca, czy cokolwiek zmieniono. */
    public static boolean applyBonus(ItemStack wand, SpellModifiers modifiers) {
        if (modifiers.getFactor(MARKER) != 1.0f) {
            return false;
        }
        if (wand.getItem() instanceof SymbioticWandItem) {
            double p = SymbiosisCurve.progress(WandProgress.get(wand), Config.INSTANCE.wandEvolveAt.get());
            modifiers.multiply(SpellModifiers.COST, (float) SymbiosisCurve.costMultiplier(p, Config.INSTANCE.symbioticMaxCostReduction.get()));
            modifiers.multiply(SpellModifiers.DURATION, (float) SymbiosisCurve.durationMultiplier(p, Config.INSTANCE.symbioticMaxDurationBonus.get()));
        } else if (wand.getItem() instanceof SentientWandItem) {
            modifiers.multiply(SpellModifiers.COST, Config.INSTANCE.sentientCostMultiplier.get().floatValue());
            modifiers.multiply(SpellModifiers.DURATION, Config.INSTANCE.sentientDurationMultiplier.get().floatValue());
            modifiers.multiply(SpellModifiers.POTENCY, Config.INSTANCE.sentientPotencyMultiplier.get().floatValue());
        } else {
            return false;
        }
        modifiers.set(MARKER, 2.0f);
        return true;
    }

    static void onPost(SpellCastEvent.Post event) {
        if (event.getSource() != SpellCastEvent.Sources.WAND || !(event.getContext() instanceof PlayerCastContext ctx)
                || ctx.world().isClientSide || !event.getSpell().isInstantCast()) {
            return;
        }
        ItemStack wand = ctx.caster().getItemInHand(ctx.hand());
        if (wand.getItem() instanceof SymbioticWandItem) {
            WandProgress.add(wand, CastItemUtils.calcCastCost(event.getSpell(), event.getModifiers()));
        }
    }

    /** Kanal: mana schodzi dopiero przy puszczeniu (WandItem.releaseUsing), wiec i punkty tutaj. */
    static void onFinish(SpellCastEvent.Finish event) {
        CastContext ctx = event.getContext();
        if (event.getSource() != SpellCastEvent.Sources.WAND || ctx == null || ctx.world().isClientSide
                || !(ctx.caster() instanceof Player player)) {
            return;
        }
        ItemStack wand = player.getUseItem();
        if (!(wand.getItem() instanceof SymbioticWandItem)) {
            return;
        }
        int total = CastItemUtils.calcCastCost(event.getSpell(), event.getModifiers());
        WandProgress.add(wand, CastItemUtils.getAccumulatedCastCost(event.getSpell(), ctx.castingTicks(), total));
    }

    /** Nasluch na szynie Forge: zabojstwa mobow Spore i ewolucja. */
    public static final class ForgeEvents {

        @SubscribeEvent
        public void onDeath(LivingDeathEvent event) {
            int bonus = Config.INSTANCE.wandFungalKillPoints.get();
            LivingEntity victim = event.getEntity();
            if (bonus <= 0 || victim.level().isClientSide || !Config.INSTANCE.sporeEnabled.get()
                    || !(event.getSource().getEntity() instanceof Player killer)
                    || !victim.getType().is(PurifyingWaveEntity.FUNGUS_ENTITIES)) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = killer.getItemInHand(hand);
                if (held.getItem() instanceof SymbioticWandItem) {
                    WandProgress.add(held, bonus);
                    return;
                }
            }
        }

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                    || event.player.isUsingItem() || event.player.tickCount % 10 != 0) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                WandEvolution.tryEvolve(event.player, hand);
            }
        }
    }
}
