package com.spege.insanetweaks.entities.ai;

/**
 * Tactical classification of a spell, used by the sim_wizard combat AI to choose what to cast.
 *
 * <p>Before this existed, every heuristic in {@code EntityAISimWizardCombat} looked its spells up
 * by hardcoded registry name ({@code "spark_bomb"}, {@code "ice_shard"} ...). Editing
 * {@code entities.assimilated_wizard.spells.spellPool} to anything else silently disabled the
 * entire tactical layer and left the wizard picking at random. Roles decouple "what the AI wants
 * to do" from "which spell happens to be installed".
 *
 * <p>Roles are a SET per spell, not a single value: {@code force_orb} is both
 * {@link #KNOCKBACK} and {@link #PROJECTILE_SHORT}. Resolution lives in {@link SpellRoleResolver}.
 */
public enum SpellRole {

    /** Lobbed, usually splashy - the close-quarters answer (force_orb, spark_bomb). */
    PROJECTILE_SHORT,
    /** Flat, fast, single target - the long-range answer (magic_missile, ice_shard). */
    PROJECTILE_LONG,
    /** Hits several targets at once; preferred when enemies bunch up. */
    AOE,
    /** Pushes the target away; used as an opener against a healthy target at close range. */
    KNOCKBACK,
    /** Impairs the target's movement; used against sprinting or hasted targets. */
    SLOW,
    /** Channelled life-steal; heals the caster while it runs. */
    DRAIN,
    /** Removes the target from melee range by force (banish). */
    DISPLACE,
    /** Moves the CASTER out of danger (blink). */
    ESCAPE,
    /** Restores the caster's own health. */
    SELF_HEAL,
    /** Any other self-only buff. */
    SELF_BUFF,
    /** Heals nearby allies rather than the caster alone (group_heal). */
    ALLY_HEAL,
    /** Spawns a minion. */
    SUMMON,
    /** Nothing could be derived and no override was configured. */
    UNKNOWN;

    /**
     * Whether casting this role requires a hostile target. Buffs apply to the caster, summons
     * appear beside it and an escape moves the caster - none of them need a victim.
     */
    public boolean needsTarget() {
        switch (this) {
            case SELF_HEAL:
            case SELF_BUFF:
            case ALLY_HEAL:
            case SUMMON:
            case ESCAPE:
                return false;
            default:
                return true;
        }
    }

    /** Whether terrain between caster and target should block this cast. */
    public boolean needsLineOfSight() {
        return this.needsTarget();
    }

    /**
     * Case-insensitive lookup used when parsing the config overrides.
     *
     * @return the matching role, or {@code null} when the name is not a role (the caller logs and
     *         skips - a typo must never silently become {@link #UNKNOWN}).
     */
    public static SpellRole byName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        for (SpellRole role : values()) {
            if (role.name().equalsIgnoreCase(trimmed)) {
                return role;
            }
        }
        return null;
    }
}
