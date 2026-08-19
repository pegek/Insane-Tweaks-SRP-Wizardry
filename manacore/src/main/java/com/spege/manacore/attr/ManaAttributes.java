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

/**
 * The player's maximum mana, split across TWO attributes whose values are summed.
 *
 * <p>The split exists because the two kinds of source have opposite lifetimes, and one attribute
 * cannot have both:
 *
 * <ul>
 *   <li>{@link #MAX_MANA} - <b>persistent</b>. The config base, both progression budgets, and
 *   flat grants such as {@code /mana setmax}. Survives death, because every one of those sources
 *   is stored in the mana pool capability and re-applied from it on respawn.</li>
 *   <li>{@link #BONUS_MANA} - <b>dynamic</b>. Worn gear: EBW and Trinkets and Baubles artifacts,
 *   and anything else granting mana only while it is equipped. Deliberately does NOT survive
 *   death and is deliberately never written to disk, because whatever grants it re-applies it
 *   the moment the player is wearing it again - and if they are not, the bonus must be gone.</li>
 * </ul>
 *
 * <p>🚨 <b>An attribute modifier alone does not survive death.</b> Vanilla constructs a fresh
 * {@code EntityPlayerMP} in {@code PlayerList.respawnPlayer} and never copies the attribute map
 * onto it, so a modifier applied to the old entity is simply gone. That is why every persistent
 * source keeps its real value in the capability and the modifier is only a projection of it,
 * rebuilt by {@link #refreshPersistentModifiers}. Adding a new persistent source means adding a
 * field to the capability - not just calling {@link #applyMaxModifier} once and assuming it
 * sticks. (Which is what {@code /mana setmax} did before 2026-08-19, and why it reset on death.)
 */
@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaAttributes {

    /**
     * Persistent maximum. setShouldWatch(true) makes Forge sync the value to the client on its
     * own, which is why we only ever sync `current` ourselves.
     */
    public static final IAttribute MAX_MANA = new RangedAttribute(
            (IAttribute) null, "manacore.maxMana", 100.0D, 0.0D, 1.0E7D)
            .setDescription("Max Mana")
            .setShouldWatch(true);

    /**
     * Dynamic maximum from worn gear, added on top of {@link #MAX_MANA}. Base is always 0 - this
     * attribute has no inherent value, only modifiers.
     *
     * <p>The lower bound is negative on purpose: a cursed artifact that costs the wearer maximum
     * mana is a perfectly reasonable item for this pack, and there is no cost to allowing it.
     * {@link #getMaxMana} clamps the SUM at zero, so a large enough penalty can empty the pool
     * but never drive it negative.
     */
    public static final IAttribute BONUS_MANA = new RangedAttribute(
            (IAttribute) null, "manacore.bonusMana", 0.0D, -1.0E7D, 1.0E7D)
            .setDescription("Bonus Mana")
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

    /**
     * Flat granted maximum ({@code /mana setmax}, one-shot awards). Same do-not-change rule as
     * above; this particular value is inherited from when {@code CommandMana} owned the modifier
     * directly, and is reused here precisely SO that it is inherited - a player who ran
     * {@code setmax} before the persistence fix has this modifier sitting in their save data, and
     * reusing the id means the first refresh replaces it instead of leaving it orphaned beside a
     * new one.
     */
    private static final UUID GRANTED_MODIFIER_ID =
            UUID.fromString("c1f4a2b8-0d6e-4c53-9f21-7a8b3e5d4c60");
    private static final String GRANTED_MODIFIER_NAME = "manacore.granted";

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
        if (player.getAttributeMap().getAttributeInstance(BONUS_MANA) == null) {
            player.getAttributeMap().registerAttribute(BONUS_MANA);
        }
    }

    /**
     * Vanilla serializes the attribute base into the player's NBT and restores it on load, so
     * the value set in EntityConstructing gets overwritten by the save data from before the
     * config change. Without this handler, a change to `baseMaxMana` would NEVER reach an
     * existing character - only a brand new one. We set the base unconditionally, after loading.
     *
     * <p>BONUS_MANA's base is forced back to 0 for the same reason in reverse: it is not a source
     * of anything, so a non-zero base restored from an old save (or written by another mod) would
     * be a permanent bonus nothing in this mod could account for or remove.
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntity();
        IAttributeInstance max = player.getEntityAttribute(MAX_MANA);
        if (max != null) {
            max.setBaseValue(ManaCoreConfig.pool.baseMaxMana);
        }
        IAttributeInstance bonus = player.getEntityAttribute(BONUS_MANA);
        if (bonus != null) {
            bonus.setBaseValue(0.0D);
        }
    }

    /**
     * The player's effective maximum mana: the persistent attribute plus the dynamic gear bonus,
     * clamped at zero. This is the number the HUD, the regen handler and every bridge use - the
     * two attributes are an implementation detail everywhere except here.
     */
    public static double getMaxMana(@Nullable EntityPlayer player) {
        double total = getPersistentMaxMana(player) + getBonusMana(player);
        return total < 0.0D ? 0.0D : total;
    }

    /** The persistent half only: config base + progression + flat grants. */
    public static double getPersistentMaxMana(@Nullable EntityPlayer player) {
        return valueOf(player, MAX_MANA);
    }

    /** The dynamic half only: worn gear. Zero for a player wearing nothing that grants mana. */
    public static double getBonusMana(@Nullable EntityPlayer player) {
        return valueOf(player, BONUS_MANA);
    }

    private static double valueOf(@Nullable EntityPlayer player, IAttribute attribute) {
        if (player == null) {
            return 0.0D;
        }
        IAttributeInstance instance = player.getEntityAttribute(attribute);
        return instance == null ? 0.0D : instance.getAttributeValue();
    }

    /**
     * Rebuilds every persistent max-mana modifier from the capability: the progression bonus (the
     * sum of both progression fields) and the flat granted amount. This is what makes those
     * sources survive death - see the class javadoc. Safe to call from either side; does nothing
     * on the client.
     *
     * <p>Two modifiers rather than one because the two are edited independently and by different
     * code paths; the progression pair, by contrast, shares a single modifier because it is
     * always written as a sum anyway.
     */
    public static void refreshPersistentModifiers(@Nullable EntityPlayer player) {
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
            ManaCoreMod.LOGGER.warn("[ManaCore] No mana pool capability found on player {} while refreshing the "
                    + "persistent max mana modifiers - progression and granted bonuses were not applied.",
                    player.getName());
            return;
        }

        applyMaxModifier(player, PROGRESSION_MODIFIER_ID, PROGRESSION_MODIFIER_NAME,
                pool.getCastProgression() + pool.getItemProgression(), 0);
        applyMaxModifier(player, GRANTED_MODIFIER_ID, GRANTED_MODIFIER_NAME,
                pool.getGrantedMax(), 0);
    }

    /**
     * Installs or replaces one PERSISTENT max-mana modifier, keyed by UUID. This is the single
     * implementation of the remove-then-apply dance for {@link #MAX_MANA}: the progression and
     * granted bonuses above, and everything {@code ManaAPI} exposes to other mods.
     *
     * <p>It lives in this class rather than in the API facade so the dependency runs one way
     * only - the facade calls into the attribute layer, never the reverse - and so there is no
     * second copy of the logic to drift out of step with this one.
     *
     * <p>An amount of zero removes the modifier instead of installing a no-op one, which keeps
     * a bonus that has fallen to zero from lingering in the player's serialised attribute map.
     *
     * <p>🚨 Modifiers installed here are SAVED into the player's NBT but are NOT restored onto the
     * new entity after death. A caller that installs one and walks away therefore gets a bonus
     * that survives relogging and vanishes on dying - the worst of both. Either store the value
     * in the capability and re-apply it on respawn (what {@link #refreshPersistentModifiers}
     * does), or recompute it from the external state it derives from - a skill level, a worn item
     * - whenever that state changes. For gear specifically, use {@link #applyBonusModifier}.
     *
     * @param operation vanilla attribute operation: 0 adds a flat amount, 1 and 2 are the
     *                  multiplicative forms. Percentage-based sources will want 1 or 2.
     */
    public static void applyMaxModifier(@Nullable EntityPlayer player, UUID id, String name,
            double amount, int operation) {
        applyModifier(player, MAX_MANA, id, name, amount, operation, true);
    }

    /**
     * Installs or replaces one DYNAMIC bonus-mana modifier, for a source that grants mana only
     * while it is active - a worn bauble, a held item, a temporary effect. Removing the source
     * means calling this again with an amount of zero, and the player's maximum updates the same
     * tick.
     *
     * <p>Unlike {@link #applyMaxModifier}, these modifiers are marked NOT saved, so they never
     * reach the player's NBT. That is the whole point: a saved gear modifier would come back on
     * the next login whether or not the gear did, and there is no reliable moment to notice it
     * had gone stale. Being unsaved also means it does not survive death, which for gear is the
     * correct behaviour rather than a limitation - whatever equips the item re-applies it.
     */
    public static void applyBonusModifier(@Nullable EntityPlayer player, UUID id, String name,
            double amount, int operation) {
        applyModifier(player, BONUS_MANA, id, name, amount, operation, false);
    }

    private static void applyModifier(@Nullable EntityPlayer player, IAttribute attribute, UUID id,
            String name, double amount, int operation, boolean saved) {
        if (player == null || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return;
        }
        IAttributeInstance instance = player.getEntityAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        if (amount != 0.0D) {
            instance.applyModifier(new AttributeModifier(id, name, amount, operation).setSaved(saved));
        }
    }
}
