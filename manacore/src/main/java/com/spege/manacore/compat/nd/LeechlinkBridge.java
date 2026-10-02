package com.spege.manacore.compat.nd;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.item.IManaStoringItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Where Necromancer's Delight's Leechlink Amulet sends the mana a Mana Leech steals.
 *
 * <p>Upstream ({@code EntityManaLeechMinion.stealMana}, ND 1.1.0): the leech drains 5% of the
 * mana in its victim's held item, and when the leech's owner wears {@code amulet_leechlink}, half
 * of that is recharged into whichever mana item the owner is holding - main hand first. With the
 * player's wand no longer paying for casts, that recharge lands in mana nothing spends, so for a
 * player owner it goes to the pool instead. The amount and the gate (owner must be holding a
 * mana item) stay ND's own; only the destination changes.
 *
 * <p>Logic lives here rather than in the mixin so the mixin stays a one-line dispatcher, and
 * because the mixin targets ND by name and cannot see its types: the leech is reached through
 * EBW's {@link ISummonedCreature}, which {@code EntityLeechMinionBase} implements.
 *
 * <p>Known gap, deliberately left: the <i>victim</i> side is untouched. A leech attached to a
 * player still drains 5% of that player's frozen wand mana rather than their pool. Leeches are a
 * player's summon, so this only matters in PvP.
 */
public final class LeechlinkBridge {

    private LeechlinkBridge() {
    }

    public static void returnStolenMana(Object leech, IManaStoringItem item, ItemStack stack, int amount) {
        if (ManaCoreConfig.ebw.leechlinkToPool && leech instanceof ISummonedCreature) {
            EntityLivingBase owner = ((ISummonedCreature) leech).getCaster();
            if (owner instanceof EntityPlayer) {
                if (amount > 0) {
                    ManaAPI.add((EntityPlayer) owner, amount);
                }
                return;
            }
        }
        item.rechargeMana(stack, amount);
    }
}
