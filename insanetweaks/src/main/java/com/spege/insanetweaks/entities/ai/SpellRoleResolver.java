package com.spege.insanetweaks.entities.ai;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.spell.SpellArrow;
import electroblob.wizardry.spell.SpellAreaEffect;
import electroblob.wizardry.spell.SpellBuff;
import electroblob.wizardry.spell.SpellMinion;
import electroblob.wizardry.spell.SpellProjectile;

/**
 * Resolves the {@link SpellRole} set of a spell: <b>derive by default, override by config</b>.
 *
 * <p>Neither half alone is sufficient. Pure derivation cannot see {@link SpellRole#SLOW} - the
 * slowness of {@code ice_shard} lives inside {@code EntityIceShard}, not in any spell metadata -
 * nor {@link SpellRole#ESCAPE}, since {@code blink} is a bare {@code Spell} with a single
 * {@code range} property. Pure config would mean a user who swaps {@code ice_shard} for some other
 * mod's frost bolt loses every heuristic until they hand-write a mapping, which is exactly the
 * failure this class exists to remove.
 *
 * <p>🚨 Roles are resolved LAZILY, never from a static initialiser or during pre-init. EBW loads
 * spell properties from JSON during its own init; {@code hasProperty} is null-safe and simply
 * returns false beforehand, so an early call would cache a permanently wrong (empty) answer.
 */
public final class SpellRoleResolver {

    // Property keys, verified against the EBW 4.3.19 spell JSONs rather than guessed.
    private static final String PROP_HEALTH = "health";
    private static final String PROP_HEAL_FACTOR = "heal_factor";
    private static final String PROP_MIN_TELEPORT = "minimum_teleport_distance";
    private static final String PROP_EFFECT_RADIUS = "effect_radius";
    private static final String PROP_SPLASH_DAMAGE = "splash_damage";
    private static final String PROP_BLAST_RADIUS = "blast_radius";

    /**
     * Entity AI may run off the main server thread under SRP's EntityThreading, so the cache is
     * concurrent. Values are immutable once published; a benign double-compute is preferable to
     * locking on every spell pick.
     */
    private static final Map<Spell, EnumSet<SpellRole>> CACHE =
            new ConcurrentHashMap<Spell, EnumSet<SpellRole>>();

    /** Parsed form of {@code spells.spellRoles}; rebuilt on demand, replaced atomically. */
    private static volatile Map<String, EnumSet<SpellRole>> overrides;

    private SpellRoleResolver() {
    }

    /**
     * Drops every cached answer. Wired to the config-changed event so editing the role overrides
     * (or the spell pool) takes effect without a restart.
     */
    public static void invalidateCache() {
        CACHE.clear();
        overrides = null;
    }

    /** @return the immutable role set of this spell; never null, never empty. */
    public static EnumSet<SpellRole> rolesOf(Spell spell) {
        if (spell == null) {
            return EnumSet.of(SpellRole.UNKNOWN);
        }
        EnumSet<SpellRole> cached = CACHE.get(spell);
        if (cached != null) {
            return cached;
        }
        EnumSet<SpellRole> resolved = resolve(spell);
        CACHE.put(spell, resolved);
        return resolved;
    }

    public static boolean hasRole(Spell spell, SpellRole role) {
        return rolesOf(spell).contains(role);
    }

    /** Appends every pooled spell carrying {@code role} to {@code out}, skipping duplicates. */
    public static void collect(List<Spell> pool, SpellRole role, Collection<Spell> out) {
        for (Spell spell : pool) {
            if (spell != null && hasRole(spell, role) && !out.contains(spell)) {
                out.add(spell);
            }
        }
    }

    /** Human-readable role list for the diagnostic log. */
    public static String describe(Spell spell) {
        return rolesOf(spell).toString();
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    private static EnumSet<SpellRole> resolve(Spell spell) {
        EnumSet<SpellRole> override = lookupOverride(spell);
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return derive(spell);
    }

    private static EnumSet<SpellRole> lookupOverride(Spell spell) {
        if (spell.getRegistryName() == null) {
            return null;
        }
        Map<String, EnumSet<SpellRole>> map = overrides;
        if (map == null) {
            map = parseOverrides();
            overrides = map;
        }
        return map.get(spell.getRegistryName().toString());
    }

    private static Map<String, EnumSet<SpellRole>> parseOverrides() {
        Map<String, EnumSet<SpellRole>> map = new HashMap<String, EnumSet<SpellRole>>();
        String[] entries = ModConfig.entities.assimilatedWizard.spells.spellRoles;
        if (entries == null) {
            return Collections.unmodifiableMap(map);
        }
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            int split = entry.indexOf('=');
            if (split <= 0 || split == entry.length() - 1) {
                InsaneTweaksMod.LOGGER.warn(
                        "[InsaneTweaks][SimWizard] Malformed spellRoles entry '{}' - expected"
                                + " '<registry_name>=ROLE[,ROLE]'. Skipped.", entry);
                continue;
            }
            String id = entry.substring(0, split).trim();
            EnumSet<SpellRole> roles = EnumSet.noneOf(SpellRole.class);
            for (String token : entry.substring(split + 1).split(",")) {
                SpellRole role = SpellRole.byName(token);
                if (role == null) {
                    InsaneTweaksMod.LOGGER.warn(
                            "[InsaneTweaks][SimWizard] Unknown spell role '{}' for '{}' - skipped.",
                            token.trim(), id);
                    continue;
                }
                roles.add(role);
            }
            if (!roles.isEmpty()) {
                map.put(id, roles);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Derivation from EBW metadata. Order matters: the targeting class is decided first, because
     * it determines whether the spell needs a victim at all, and the situational tags below must
     * not contradict it (group_heal carries {@code effect_radius} but is emphatically not an
     * offensive AoE).
     */
    private static EnumSet<SpellRole> derive(Spell spell) {
        EnumSet<SpellRole> roles = EnumSet.noneOf(SpellRole.class);

        // --- targeting class ---
        if (spell instanceof SpellMinion || spell.getType() == electroblob.wizardry.constants.SpellType.MINION) {
            roles.add(SpellRole.SUMMON);
        }
        if (spell instanceof SpellAreaEffect && spell.hasProperty(PROP_HEALTH)) {
            roles.add(SpellRole.ALLY_HEAL);
        }
        if (spell instanceof SpellBuff) {
            // SpellBuff's NPC overload discards the target and applies to the caster, so every
            // SpellBuff is self-only regardless of what it does.
            roles.add(spell.hasProperty(PROP_HEALTH) ? SpellRole.SELF_HEAL : SpellRole.SELF_BUFF);
        }

        // --- situational tags ---
        if (spell.isContinuous && spell.hasProperty(PROP_HEAL_FACTOR)) {
            roles.add(SpellRole.DRAIN);
        }
        if (spell.hasProperty(PROP_MIN_TELEPORT)) {
            roles.add(SpellRole.DISPLACE);
        }
        if (!roles.contains(SpellRole.ALLY_HEAL)
                && (spell.hasProperty(PROP_EFFECT_RADIUS) || spell.hasProperty(PROP_SPLASH_DAMAGE))) {
            roles.add(SpellRole.AOE);
        }
        if (spell.hasProperty(PROP_BLAST_RADIUS)) {
            roles.add(SpellRole.KNOCKBACK);
        }

        // --- range band ---
        // Verified against EBW 4.3.19: magic_missile and ice_shard are SpellArrow, force_orb and
        // spark_bomb are SpellProjectile. That split reproduces the old hardcoded distance bands
        // exactly, without naming a single spell.
        if (spell instanceof SpellArrow) {
            roles.add(SpellRole.PROJECTILE_LONG);
        } else if (spell instanceof SpellProjectile) {
            roles.add(SpellRole.PROJECTILE_SHORT);
        }

        if (roles.isEmpty()) {
            roles.addAll(deriveFromType(spell));
        }
        if (roles.isEmpty()) {
            roles.add(SpellRole.UNKNOWN);
        }
        return roles;
    }

    /** Last-resort classification from the spell's declared type and tier. */
    private static EnumSet<SpellRole> deriveFromType(Spell spell) {
        switch (spell.getType()) {
            case MINION:
                return EnumSet.of(SpellRole.SUMMON);
            case BUFF:
            case DEFENCE:
                return EnumSet.of(SpellRole.SELF_BUFF);
            case ATTACK:
            case PROJECTILE:
                return EnumSet.of(spell.getTier().ordinal() >= Tier.APPRENTICE.ordinal()
                        ? SpellRole.PROJECTILE_LONG
                        : SpellRole.PROJECTILE_SHORT);
            default:
                return EnumSet.noneOf(SpellRole.class);
        }
    }
}
