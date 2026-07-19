package com.spege.insanetweaks.enchant;

import com.spege.insanetweaks.config.ModConfig;

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
 * Runtime behaviour of the {@link EnchantmentGrimoire} enchantment: per-tick boost
 * recompute, owner-binding, and the anvil lock. Registered on the Forge event bus in
 * {@code InsaneTweaksMod#init} under {@code modules.enableGrimoire}.
 *
 * <p>Drop protection (no burn / lingers far longer) is NOT handled here: a Grimoire item
 * confers the "Ashen Legacy" property, so {@code LegendaryDropHelper.isLegendaryDropItem}
 * routes it through {@code EntityItemIndestructible} via the always-on
 * {@code IndestructibleDropHandler}. Gated by {@code ModConfig.grimoire.conferAshenLegacy}.
 *
 * <p>Enchantment registration itself is NOT here (that is {@code ModEnchantments} on the
 * MOD bus) - this class is a plain Forge-bus handler instance.
 *
 * <p>Every config lookup goes through {@code ModConfig.grimoire.*} so the tunables are
 * live (no restart), matching the codebase config convention.
 */
@SuppressWarnings("null")
public class GrimoireHandler {

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
        if (EnchantmentGrimoire.INSTANCE == null) {
            return;
        }
        if (p.ticksExisted % Math.max(1, ModConfig.grimoire.tickInterval) != 0) {
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
        int gLvl = EnchantmentGrimoire.getGrimoireLevel(stack);
        if (gLvl <= 0) {
            return;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();

        // owner-binding
        if (ModConfig.grimoire.ownerBinding) {
            String owner = tag.getString(EnchantmentGrimoire.OWNER_TAG);
            String me = p.getUniqueID().toString();
            if (owner.isEmpty()) {
                tag.setString(EnchantmentGrimoire.OWNER_TAG, me);
            } else if (!owner.equals(me)) {
                p.attackEntityFrom(DamageSource.OUT_OF_WORLD, (float) ModConfig.grimoire.bindingDamage);
            }
        }

        // capture the base enchantments once (without Grimoire), into grimoire_storage
        if (!tag.hasKey(EnchantmentGrimoire.STORAGE_TAG)) {
            NBTTagList live = stack.getEnchantmentTagList(); // reads "ench"
            NBTTagList storage = new NBTTagList();
            int gid = Enchantment.getEnchantmentID(EnchantmentGrimoire.INSTANCE);
            for (int i = 0; i < live.tagCount(); i++) {
                NBTTagCompound en = live.getCompoundTagAt(i);
                if (en.getShort("id") == gid) {
                    continue;
                }
                storage.appendTag(en.copy());
            }
            tag.setTag(EnchantmentGrimoire.STORAGE_TAG, storage);
        }

        // compute boost and rebuild "ench" = Grimoire + (base+boost) for every stored enchant
        int boost = computeBoost(p.experienceLevel, gLvl);
        NBTTagList storage = tag.getTagList(EnchantmentGrimoire.STORAGE_TAG, 10);
        NBTTagList out = new NBTTagList();
        // keep Grimoire itself
        NBTTagCompound self = new NBTTagCompound();
        self.setShort("id", (short) Enchantment.getEnchantmentID(EnchantmentGrimoire.INSTANCE));
        self.setShort("lvl", (short) gLvl);
        out.appendTag(self);
        for (int i = 0; i < storage.tagCount(); i++) {
            NBTTagCompound en = storage.getCompoundTagAt(i);
            int id = en.getShort("id");
            int base = en.getShort("lvl");
            Enchantment ench = Enchantment.getEnchantmentByID(id);
            int lvl = (ench != null && isExcluded(ench)) ? base : base + boost;
            NBTTagCompound o = new NBTTagCompound();
            o.setShort("id", (short) id);
            o.setShort("lvl", (short) lvl);
            out.appendTag(o);
        }
        tag.setTag("ench", out);
        tag.setInteger(EnchantmentGrimoire.LAST_BOOST_TAG, boost);
    }

    private static int computeBoost(int playerLevel, int gLvl) {
        double v = Math.log((ModConfig.grimoire.startLevel + playerLevel) * (double) gLvl)
                * ModConfig.grimoire.levelScaling - ModConfig.grimoire.stepSkip;
        int b = (int) Math.floor(v);
        return b < 0 ? 0 : b;
    }

    private static boolean isExcluded(Enchantment ench) {
        if (ench.getRegistryName() == null) {
            return false;
        }
        String rn = ench.getRegistryName().toString();
        for (String s : ModConfig.grimoire.excluded) {
            if (s.equalsIgnoreCase(rn)) {
                return true;
            }
        }
        return false;
    }

    // --- anvil block: don't modify an already-Grimoire'd item (but DO allow applying the
    // book onto a clean item) ---
    @SubscribeEvent
    public void onAnvil(AnvilUpdateEvent e) {
        if (!ModConfig.grimoire.blockAnvil) {
            return;
        }
        if (EnchantmentGrimoire.hasGrimoire(e.getLeft())) {
            e.setCanceled(true); // left = target; already Grimoire'd -> locked
        }
        // right = Grimoire book onto a clean left -> allowed (creates a locked item)
    }
}
