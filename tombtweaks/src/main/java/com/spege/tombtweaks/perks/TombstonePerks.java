package com.spege.tombtweaks.perks;

import com.spege.tombtweaks.TombstoneTweaks;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ovh.corail.tombstone.api.capability.Perk;

/**
 * Registers this mod's perks into Corail Tombstone's own {@code tombstone:perks} Forge registry.
 *
 * <p>Tombstone builds that registry in {@code PerkRegistry.createRegistry} and registers its own
 * ten at {@code EventPriority.HIGH}, so a default-priority listener like this one lands after
 * them. {@code ScreenKnowledge} then iterates the registry and lays perks out procedurally —
 * there are no hardcoded positions or counts, so ours simply appear, and Tombstone handles
 * purchasing, persistence and client sync for free.
 *
 * <p><b>Registration is never config-gated.</b> Tombstone persists a player's perks by numeric
 * registry ID, so a perk that disappears when a flag is flipped would drop out of existing
 * worlds and trigger Forge's missing-registry-entry screen. Config controls the perk's
 * {@code isDisabled} state and its effect handler instead.
 *
 * <p>Registered from {@code TombstoneTweaks.preInit} only when Tombstone is loaded, which keeps
 * this class — and the {@link Perk} in its method signature — off the classloader otherwise.
 * Registry events fire on {@code MinecraftForge.EVENT_BUS} after preInit in 1.12.2.
 */
public class TombstonePerks {

    /**
     * The namespace both perks are registered under — deliberately <b>not</b> this mod's id.
     *
     * <p>These two perks shipped inside Insane Tweaks before this mod existed, and
     * {@code TBCapabilityDefault.serializeNBT} writes a player's perks as the numeric id
     * {@code PerkRegistry.perkRegistry} assigned to them (verified with {@code javap}). That id map
     * lives in {@code level.dat} keyed by registry name, so renaming the perks would make both
     * entries missing on an existing world and put their invested levels at the mercy of a
     * remapping pass. Keeping the old namespace costs one Forge log line about an alternative
     * prefix and nothing else.
     */
    private static final String LEGACY_NAMESPACE = "insanetweaks";

    public static PerkAssimilatedKnowledge ASSIMILATED_KNOWLEDGE;
    public static PerkReliefForTheDamned RELIEF_FOR_THE_DAMNED;

    @SubscribeEvent
    public void onRegisterPerks(RegistryEvent.Register<Perk> event) {
        ASSIMILATED_KNOWLEDGE = new PerkAssimilatedKnowledge();
        ASSIMILATED_KNOWLEDGE.setRegistryName(
                new ResourceLocation(LEGACY_NAMESPACE, "assimilated_knowledge"));

        RELIEF_FOR_THE_DAMNED = new PerkReliefForTheDamned();
        RELIEF_FOR_THE_DAMNED.setRegistryName(
                new ResourceLocation(LEGACY_NAMESPACE, "relief_for_the_damned"));

        event.getRegistry().register(ASSIMILATED_KNOWLEDGE);
        event.getRegistry().register(RELIEF_FOR_THE_DAMNED);

        TombstoneTweaks.LOGGER.info(
                "[TombstoneTweaks] Registered 2 custom Tombstone perks into tombstone:perks.");
    }
}
