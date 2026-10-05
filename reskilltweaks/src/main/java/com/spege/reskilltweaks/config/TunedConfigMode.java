package com.spege.reskilltweaks.config;

/**
 * What {@link ReskillableConfigSwapper} may do to reskillable.cfg. Stored in
 * {@code reskilltweaks.cfg} by name, so the constant names are part of the config format - do not
 * rename them.
 */
public enum TunedConfigMode {
    /** Ask on the main menu; a dedicated server only logs. The default. */
    ASK,
    /** Install the tuned config, and re-install it when this mod's version changes. */
    APPLY,
    /** Never touch reskillable.cfg. */
    KEEP
}
