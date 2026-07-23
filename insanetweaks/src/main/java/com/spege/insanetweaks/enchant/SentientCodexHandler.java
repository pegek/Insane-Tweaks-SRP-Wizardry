package com.spege.insanetweaks.enchant;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.SentientCodexCategory;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Runtime behaviour of the {@link EnchantmentSentientCodex} enchantment: per-tick monotonic step
 * growth (each XP threshold adds +1 to the item's enchantments, capped per-enchant at maxLevel +
 * config), owner-binding, and the anvil lock. Registered on the Forge event bus in
 * {@code InsaneTweaksMod#init} under {@code modules.enableSentientCodex}.
 *
 * <p>Drop protection (no burn / lingers far longer) is NOT handled here: a Sentient Codex item
 * confers the "Ashen Legacy" property, so {@code LegendaryDropHelper.isLegendaryDropItem}
 * routes it through {@code EntityItemIndestructible} via the always-on
 * {@code IndestructibleDropHandler}. Gated by {@code ModConfig.enchantments.sentientCodex.conferAshenLegacy}.
 *
 * <p>Enchantment registration itself is NOT here (that is {@code ModEnchantments} on the
 * MOD bus) - this class is a plain Forge-bus handler instance.
 *
 * <p>Every config lookup goes through {@code ModConfig.enchantments.sentientCodex.*} so the tunables
 * are live (no restart), matching the codebase config convention.
 */
@SuppressWarnings("null")
public class SentientCodexHandler {

    /** UE "Grimoire II" formula multiplier applied inside the log. Kept as a constant (the enchant
     *  is unified to level 1 here, so the level-2 tuning is baked in rather than exposed). */
    private static final double GRIMOIRE_MULTIPLIER = 2.0;

    // --- pseudo-tick: recompute boost + owner-binding (SERVER only) ---
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer p = e.player;
        if (p == null || p.world.isRemote) {
            return;
        }
        if (EnchantmentSentientCodex.INSTANCE == null) {
            return;
        }
        if (p.ticksExisted % Math.max(1, ModConfig.enchantments.sentientCodex.tickInterval) != 0) {
            return;
        }

        // only process held + worn items (where enchantments actually take effect)
        process(p, p.getHeldItemMainhand());
        process(p, p.getHeldItemOffhand());
        for (ItemStack armor : p.getArmorInventoryList()) {
            process(p, armor);
        }
    }

    private void process(EntityPlayer p, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int gLvl = EnchantmentSentientCodex.getSentientCodexLevel(stack);
        if (gLvl <= 0) {
            return;
        }
        SentientCodexCategory cfg = ModConfig.enchantments.sentientCodex;
        // Frozen: stop applying growth. Already-granted levels stay baked in (we keep no base
        // snapshot to restore). To fully remove the enchant, unregister via modules.enableSentientCodex.
        if (!cfg.enabled) {
            return;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();

        // owner-binding
        if (cfg.ownerBinding) {
            String owner = tag.getString(EnchantmentSentientCodex.OWNER_TAG);
            String me = p.getUniqueID().toString();
            if (owner.isEmpty()) {
                tag.setString(EnchantmentSentientCodex.OWNER_TAG, me);
            } else if (!owner.equals(me)) {
                p.attackEntityFrom(DamageSource.OUT_OF_WORLD, (float) cfg.bindingDamage);
            }
        }

        // Monotonic step growth: as XP crosses thresholds the target step count rises; each NEW step
        // adds +1 to every non-excluded enchant, up to that enchant's getMaxLevel() + Max Levels Above
        // Cap. We only ever raise levels (losing XP never lowers them) and store just the step counter
        // - no base snapshot, no rebuild. The anvil lock freezes the enchant set once SC is applied, so
        // the live "ench" list IS the source of truth, edited in place.
        int target = computeBoost(p.experienceLevel);
        int applied = tag.getInteger(EnchantmentSentientCodex.LAST_BOOST_TAG);
        if (target <= applied) {
            return;
        }
        int delta = target - applied;

        int scId = Enchantment.getEnchantmentID(EnchantmentSentientCodex.INSTANCE);
        NBTTagList ench = stack.getEnchantmentTagList(); // live "ench" list, edited in place
        for (int i = 0; i < ench.tagCount(); i++) {
            NBTTagCompound en = ench.getCompoundTagAt(i);
            int id = en.getShort("id");
            if (id == scId) {
                continue; // never boost Sentient Codex itself
            }
            Enchantment ench2 = Enchantment.getEnchantmentByID(id);
            if (ench2 == null || isExcluded(ench2)) {
                continue;
            }
            int cap = ench2.getMaxLevel() + cfg.maxLevelsAboveCap;
            int lvl = en.getShort("lvl");
            if (lvl < cap) {
                en.setShort("lvl", (short) Math.min(lvl + delta, cap));
            }
        }
        tag.setInteger(EnchantmentSentientCodex.LAST_BOOST_TAG, target);
    }

    private static int computeBoost(int playerLevel) {
        SentientCodexCategory cfg = ModConfig.enchantments.sentientCodex;
        // Progression Rate slows the climb by scaling the XP term (0.3 = ~70% slower). The log slope
        // (levelScaling) is untouched, so the boost still reaches the per-enchant cap - just later.
        double xpTerm = cfg.startLevel + playerLevel * cfg.progressionRate;
        double v = Math.log(xpTerm * GRIMOIRE_MULTIPLIER) * cfg.levelScaling - cfg.stepSkip;
        int b = (int) Math.floor(v);
        return b < 0 ? 0 : b;
    }

    private static boolean isExcluded(Enchantment ench) {
        if (ench.getRegistryName() == null) {
            return false;
        }
        String rn = ench.getRegistryName().toString();
        for (String s : ModConfig.enchantments.sentientCodex.excluded) {
            if (s.equalsIgnoreCase(rn)) {
                return true;
            }
        }
        return false;
    }

    // --- anvil block: don't modify an already-SentientCodex'd item (but DO allow applying the
    // book onto a clean item) ---
    @SubscribeEvent
    public void onAnvil(AnvilUpdateEvent e) {
        if (!ModConfig.enchantments.sentientCodex.blockAnvil) {
            return;
        }
        if (EnchantmentSentientCodex.hasSentientCodex(e.getLeft())) {
            e.setCanceled(true); // left = target; already SentientCodex'd -> locked
        }
        // right = Sentient Codex book onto a clean left -> allowed (creates a locked item)
    }
}
