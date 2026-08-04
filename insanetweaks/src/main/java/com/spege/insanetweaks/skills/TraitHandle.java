package com.spege.insanetweaks.skills;

import codersafterdark.reskillable.api.ReskillableRegistries;
import codersafterdark.reskillable.api.data.PlayerData;
import codersafterdark.reskillable.api.data.PlayerDataHandler;
import codersafterdark.reskillable.api.data.PlayerSkillInfo;
import codersafterdark.reskillable.api.skill.Skill;
import codersafterdark.reskillable.api.unlockable.Unlockable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * A resolved-once handle to one (skill, trait) pair.
 *
 * <p>{@link TraitBase#hasTrait(EntityPlayer, String, String)} takes two string ids and pays for
 * them on every single call: {@code new ResourceLocation(String)} allocates a {@code String[2]}
 * plus two substrings plus the ResourceLocation itself, so two of them is eight objects, and then
 * both are looked up in a Forge registry. Every call site in this mod passes compile-time
 * constants, so all of that is the same answer every time.
 *
 * <p>A handle does the string parsing once at class-load and the two registry lookups once, the
 * first time it is actually asked. What remains per call is what Reskillable itself would cost
 * anyway: one map lookup for the player's data, one for the skill info, and
 * {@code PlayerSkillInfo.isUnlocked} (a {@code List.contains}, i.e. a short linear scan).
 *
 * <p>Resolution is lazy and only cached on success, because the handles below are created when
 * this class loads but Reskillable's registries are not populated until
 * {@code RegistryEvent.Register<Unlockable>}. A handle for a trait that was switched off in the
 * config never resolves and simply answers false forever, which is the intended behaviour: a
 * disabled trait is left out of {@link SkillsModule#TRAITS} and so never reaches the registry.
 *
 * <p>Caching the instances is safe because Reskillable's registry is populated exactly once per
 * game launch - {@code SkillsModule.initTraits()} is itself guarded against running twice, and
 * {@code PlayerSkillInfo.isUnlocked} compares by identity ({@code Unlockable} does not override
 * {@code equals}), so a stale instance would silently answer false rather than crash.
 *
 * <p>🚨 The constants live on this class rather than on the individual handlers on purpose. They
 * reference Reskillable types, and {@code PacketChargeJump} - which asks about Coiled Spring - is
 * registered unconditionally, before we know whether Reskillable is present. Keeping them here
 * means the class initialises lazily, on the first constant access from inside a method body,
 * exactly like the {@code TraitBase.hasTrait} call it replaces.
 */
public final class TraitHandle {

    // Attack
    public static final TraitHandle FAST_LEARNER = of("reskillable:attack", "fast_learner");
    public static final TraitHandle ASSIMILATED_WARFARE = of("reskillable:attack", "assimilated_warfare");

    // Defense
    public static final TraitHandle IRON_STOMACH = of("reskillable:defense", "iron_stomach");
    public static final TraitHandle SPIDERS_GRACE = of("reskillable:defense", "spiders_grace");
    public static final TraitHandle SCARRED_FLESH = of("reskillable:defense", "scarred_flesh");

    // Gathering
    public static final TraitHandle DOUBLE_LOOT = of("reskillable:gathering", "double_loot");
    public static final TraitHandle ENCHANT_FISHING = of("reskillable:gathering", "enchant_fishing");

    // Mining
    public static final TraitHandle ASTRAL_PROSPECTOR = of("reskillable:mining", "astral_prospector");
    public static final TraitHandle STONE_FISTS = of("reskillable:mining", "stone_fists");

    // Farming
    public static final TraitHandle ANGRY_FARMER = of("reskillable:farming", "angry_farmer");
    public static final TraitHandle ADAPTED_VEGETATION = of("reskillable:farming", "adapted_vegetation");

    // Building
    public static final TraitHandle SUPREME_ENCHANTER = of("reskillable:building", "supreme_enchanter");
    public static final TraitHandle BOB_THE_BUILDER = of("reskillable:building", "bob_the_builder");

    // Agility
    public static final TraitHandle MEDITATION = of("reskillable:agility", "meditation");
    public static final TraitHandle COILED_SPRING = of("reskillable:agility", "coiled_spring");

    // Magic
    public static final TraitHandle ARCANE_MASTERY = of("reskillable:magic", "arcane_mastery");
    public static final TraitHandle SCHOOL_OF_ALTERATION = of("reskillable:magic", "school_of_alteration");
    public static final TraitHandle SCHOOL_OF_CONJURATION = of("reskillable:magic", "school_of_conjuration");
    public static final TraitHandle SCHOOL_OF_DESTRUCTION = of("reskillable:magic", "school_of_destruction");
    public static final TraitHandle ARCHMAGE = of("reskillable:magic", "archmage");

    /**
     * Native Reskillable traits, not ours. We only add effects on top of them, so they are spelled
     * out with the full id rather than through {@link SkillsModule#DOMAIN}.
     */
    public static final TraitHandle GOLDEN_OSMOSIS = ofFullId("reskillable:magic", "reskillable:golden_osmosis");
    public static final TraitHandle SAFE_PORT = ofFullId("reskillable:magic", "reskillable:safe_port");

    // -------------------------------------------------------------------------

    private final ResourceLocation skillKey;
    private final ResourceLocation unlockableKey;

    private Skill skill;
    private Unlockable unlockable;

    /** One of our own traits, i.e. under {@link SkillsModule#DOMAIN}. */
    private static TraitHandle of(String skillId, String traitName) {
        return new TraitHandle(skillId, SkillsModule.DOMAIN + ":" + traitName);
    }

    /** A trait owned by someone else, given as a complete {@code modid:name}. */
    private static TraitHandle ofFullId(String skillId, String unlockableId) {
        return new TraitHandle(skillId, unlockableId);
    }

    private TraitHandle(String skillId, String unlockableId) {
        this.skillKey = new ResourceLocation(skillId);
        this.unlockableKey = new ResourceLocation(unlockableId);
    }

    /**
     * Whether this player has unlocked the trait. Safe on both sides - Reskillable keeps a synced
     * copy of the player's data client-side, keyed separately per side.
     */
    public boolean has(EntityPlayer player) {
        if (player == null) return false;

        try {
            if ((skill == null || unlockable == null) && !resolve()) return false;

            PlayerData playerData = PlayerDataHandler.get(player);
            if (playerData == null) return false;

            PlayerSkillInfo skillInfo = playerData.getSkillInfo(skill);
            return skillInfo != null && skillInfo.isUnlocked(unlockable);
        } catch (Exception e) {
            return false;
        }
    }

    /** Caches only on success, so a call made before registration does not poison the handle. */
    private boolean resolve() {
        if (skill == null) skill = ReskillableRegistries.SKILLS.getValue(skillKey);
        if (unlockable == null) unlockable = ReskillableRegistries.UNLOCKABLES.getValue(unlockableKey);
        return skill != null && unlockable != null;
    }

    @Override
    public String toString() {
        return unlockableKey.toString();
    }
}
