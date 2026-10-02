package com.spege.ebreduxaddon.feature;

import com.spege.ebreduxaddon.core.GraspRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Trwajace chwyty Grasp, tylko po stronie serwera: rzucajacy -&gt; trzymany cel.
 *
 * <p>Unieruchomienie to modyfikator MOVEMENT_SPEED x0 (transient, wiec nie trafia do NBT i nie
 * przezyje przeladowania chunka ani relogu). Zdejmuje go endCast zaklecia. Na wypadek, gdyby endCast
 * nie przyszedl (crash rzucajacego, wylogowanie w trakcie kanalu), sweeper co tick zwalnia chwyty,
 * ktore od {@link #STALE_TICKS} tickow nie dostaly ticku zaklecia - inaczej mob zostalby zamrozony
 * na zawsze.
 */
public final class GraspState {

    static final UUID ROOT_ID = UUID.fromString("5d0c7a8e-3f61-4b7e-9d2a-41c9e0b7f6a3");
    private static final String ROOT_NAME = "ebreduxaddon.grasp";
    static final long STALE_TICKS = 5;

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private GraspState() {
    }

    public record Hold(LivingEntity caster, LivingEntity target, long lastTick) {
    }

    public static Hold get(LivingEntity caster) {
        return HOLDS.get(caster.getUUID());
    }

    /** Zaczyna albo odswieza chwyt i (ponownie) zaklada unieruchomienie obu stronom. */
    public static void hold(LivingEntity caster, LivingEntity target, long nowTick) {
        Hold previous = HOLDS.get(caster.getUUID());
        if (previous != null && previous.target() != target) {
            unroot(previous.target());
        }
        HOLDS.put(caster.getUUID(), new Hold(caster, target, nowTick));
        root(target);
        root(caster);
    }

    public static void release(LivingEntity caster) {
        Hold hold = HOLDS.remove(caster.getUUID());
        unroot(caster);
        if (hold != null) {
            unroot(hold.target());
        }
    }

    public static boolean isRooted(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(ROOT_ID) != null;
    }

    private static void root(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(ROOT_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(ROOT_ID, ROOT_NAME, -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void unroot(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(ROOT_ID);
        }
    }

    /** Nasluch na szynie Forge. Rejestrowany w EbreduxAddon. */
    public static final class Sweeper {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || HOLDS.isEmpty()) {
                return;
            }
            Iterator<Hold> it = HOLDS.values().iterator();
            while (it.hasNext()) {
                Hold hold = it.next();
                long now = hold.caster().level().getGameTime();
                if (GraspRules.stale(now, hold.lastTick(), STALE_TICKS) || !hold.caster().isAlive()) {
                    it.remove();
                    unroot(hold.caster());
                    unroot(hold.target());
                }
            }
        }

        @SubscribeEvent
        public void onServerStopping(ServerStoppingEvent event) {
            HOLDS.clear();
        }
    }
}
