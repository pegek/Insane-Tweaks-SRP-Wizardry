package com.spege.manacore.attr;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;
import com.spege.manacore.config.ManaCoreConfig;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaAttributes {

    /**
     * setShouldWatch(true) makes Forge sync the attribute value to the client on its own.
     * Because of that, we only ever sync `current` ourselves (see Task 6).
     */
    public static final IAttribute MAX_MANA = new RangedAttribute(
            (IAttribute) null, "manacore.maxMana", 100.0D, 0.0D, 1.0E7D)
            .setDescription("Max Mana")
            .setShouldWatch(true);

    /**
     * The progression modifier is identified SOLELY by this UUID - vanilla serializes the
     * AttributeMap together with its modifiers into the player's NBT, so changing this constant
     * in a future version of the mod would not remove the old modifier from existing worlds: it
     * would become orphaned, keep contributing to the max mana total, and the new code would add
     * a second modifier next to it with a fresh UUID. The result is a permanently doubled
     * progression bonus, not fixable without manually editing the player's save data. Do not
     * change this value.
     */
    private static final UUID PROGRESSION_MODIFIER_ID =
            UUID.fromString("6b7a1d54-3f6c-4a0e-9a1a-2f9c5b8e7d10");
    private static final String PROGRESSION_MODIFIER_NAME = "manacore.progression";

    private ManaAttributes() {
    }

    @SubscribeEvent
    public static void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (!(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntity();
        if (player.getAttributeMap().getAttributeInstance(MAX_MANA) == null) {
            player.getAttributeMap().registerAttribute(MAX_MANA);
        }
    }

    /**
     * Vanilla serializes the attribute base into the player's NBT and restores it on load, so
     * the value set in EntityConstructing gets overwritten by the save data from before the
     * config change. Without this handler, a change to `baseMaxMana` would NEVER reach an
     * existing character - only a brand new one. We set the base unconditionally, after loading.
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntity();
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        if (instance != null) {
            instance.setBaseValue(ManaCoreConfig.pool.baseMaxMana);
        }
    }

    public static double getMaxMana(@Nullable EntityPlayer player) {
        if (player == null) {
            return 0.0D;
        }
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        return instance == null ? 0.0D : instance.getAttributeValue();
    }

    /**
     * Recomputes the progression modifier from the sum of both progression fields stored in the
     * capability (cast progression + item progression). They are two independently capped
     * budgets, but they contribute to one and the same attribute modifier - see the note on
     * {@link #PROGRESSION_MODIFIER_ID} for why this stays a single modifier rather than two.
     * Safe to call from either side - does nothing on the client side.
     */
    public static void refreshProgressionModifier(@Nullable EntityPlayer player) {
        if (player == null) {
            return;
        }
        if (player.world.isRemote) {
            return;
        }
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        IManaPool pool = ManaCapabilities.get(player);
        if (instance == null) {
            // MAX_MANA is registered unconditionally for every EntityPlayer in
            // onEntityConstructing, with no event another mod could intercept to steal it -
            // unlike the capability below, there is no known path that leaves this null, so
            // there is nothing actionable to warn about here.
            return;
        }
        if (pool == null) {
            // Since onAttachCapabilities was added, every player is guaranteed a mana pool
            // provider, so this is no longer a normal state: it means some other mod
            // intercepted AttachCapabilitiesEvent before us, or this player instance was
            // constructed through a path that bypassed capability attachment entirely. Either
            // way the player silently loses their max mana progression bonus, which is why this
            // is logged at warn rather than passed through quietly.
            ManaCoreMod.LOGGER.warn("[ManaCore] No mana pool capability found on player {} while refreshing "
                    + "the progression modifier - the max mana progression bonus was not applied.",
                    player.getName());
            return;
        }

        applyMaxModifier(player, PROGRESSION_MODIFIER_ID, PROGRESSION_MODIFIER_NAME,
                pool.getCastProgression() + pool.getItemProgression(), 0);
    }

    /**
     * Installs or replaces one max-mana modifier, keyed by UUID. This is the single
     * implementation of the remove-then-apply dance: every source of bonus max mana goes
     * through here, including the progression bonus above and everything {@code ManaAPI}
     * exposes to other mods.
     *
     * <p>It lives in this class rather than in the API facade so the dependency runs one way
     * only - the facade calls into the attribute layer, never the reverse - and so there is no
     * second copy of the logic to drift out of step with this one.
     *
     * <p>An amount of zero removes the modifier instead of installing a no-op one, which keeps
     * a bonus that has fallen to zero from lingering in the player's serialised attribute map.
     *
     * @param operation vanilla attribute operation: 0 adds a flat amount, 1 and 2 are the
     *                  multiplicative forms. Percentage-based sources will want 1 or 2.
     */
    public static void applyMaxModifier(@Nullable EntityPlayer player, UUID id, String name,
            double amount, int operation) {
        if (player == null || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return;
        }
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        if (instance == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        if (amount != 0.0D) {
            instance.applyModifier(new AttributeModifier(id, name, amount, operation));
        }
    }
}
