package com.spege.tombtweaks.effects;

/**
 * The three whitelists Tombstone's random-effect paths draw from.
 *
 * <p>Only three, because Tombstone offers exactly two choke points worth mixing into:
 * {@code EffectHelper.getRandomEffect(int, boolean, boolean, Function)} — where the {@code bad}
 * flag still tells beneficial from harmful — and {@code ItemMagicScroll.setRandomMagicEffect},
 * which never passes through the former and therefore gets a pool for free.
 *
 * <p>Everything else (Ankh of Prayer, Lollipop, Blessing / Tablet of Cupidity, Plague Bringer)
 * funnels through the first choke point and shares {@link #BENEFICIAL} or {@link #HARMFUL}
 * accordingly. Splitting those further would mean redirecting {@code addRandomPotion} at three
 * call sites — one of them inside a synthetic lambda — and reimplementing its body.
 */
public enum EffectPoolId {
    /** Ankh of Prayer, Lollipop, enchantment Blessing. */
    BENEFICIAL,
    /** Tablet of Cupidity, enchantment Plague Bringer. */
    HARMFUL,
    /** Magic Scroll. Falls back to {@link #BENEFICIAL} when left empty. */
    MAGIC_SCROLL
}
