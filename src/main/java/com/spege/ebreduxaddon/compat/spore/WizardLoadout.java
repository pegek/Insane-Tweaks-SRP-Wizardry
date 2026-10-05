package com.spege.ebreduxaddon.compat.spore;

import com.binaris.wizardry.api.content.spell.Element;
import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.api.content.spell.SpellContexts;
import com.binaris.wizardry.api.content.spell.SpellTier;
import com.binaris.wizardry.api.content.util.CastItemDataHelper;
import com.binaris.wizardry.api.content.util.RegistryUtils;
import com.binaris.wizardry.core.platform.Services;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.binaris.wizardry.setup.registries.Spells;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.core.InfectedTiers;
import com.spege.ebreduxaddon.feature.ModSpells;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Zywiol, tier i zaklecia rzucajacego moba roju, wspolne dla zarazonego maga i Mykomanty: losowanie,
 * dziedziczenie przy ewolucji, rozdzka w rece i zapis NBT. Klucze NBT sa te same co w 0.2, wiec
 * zapisane swiaty czytaja sie bez zmian.
 */
public final class WizardLoadout {

    private final List<Spell> spells = new ArrayList<>();
    private Element element = Elements.EARTH;
    private int maxTier = InfectedTiers.APPRENTICE;
    /**
     * Czy zywiol, tier i zaklecia sa juz ustalone. Osobna flaga zamiast "lista pusta": przy
     * spellCount = 0 i bez Spine Volley pusta lista jest poprawnym wynikiem i nie moze losowac w kolko.
     */
    private boolean rolled;

    /** Losuje wszystko od zera: zywiol (bez magic i healing), {@code count} zaklec do tieru i Spine Volley. */
    public void roll(RandomSource random, int tier, int count, boolean spineVolley) {
        List<Element> elements = new ArrayList<>(Services.REGISTRY_UTIL.getElements());
        elements.remove(Elements.MAGIC);
        elements.remove(Elements.HEALING);
        this.element = elements.isEmpty() ? Elements.EARTH : elements.get(random.nextInt(elements.size()));
        this.maxTier = clampTier(tier);
        spells.clear();
        topUp(random, count);
        if (spineVolley && !spells.contains(ModSpells.SPINE_VOLLEY.get())) {
            spells.add(ModSpells.SPINE_VOLLEY.get());
        }
        this.rolled = true;
    }

    /**
     * Ewolucja: przejmuje zywiol i zaklecia poprzednika, podnosi tier i dobiera zaklecia zywiolu, az
     * zaklec innych niz Spine Volley bedzie {@code count}.
     */
    public void inherit(WizardLoadout from, RandomSource random, int tier, int count) {
        this.element = from.element;
        this.maxTier = clampTier(Math.max(tier, from.maxTier));
        spells.clear();
        spells.addAll(from.spells);
        topUp(random, count);
        this.rolled = true;
    }

    private void topUp(RandomSource random, int count) {
        SpellTier cap = tierOf(maxTier);
        List<Spell> pool = RegistryUtils.getSpells(spell -> spell.getElement() == element
                && spell.getTier().getLevel() <= cap.getLevel()
                && spell.canCastByEntity()
                && spell.isEnabled(SpellContexts.NPCS)
                && !spells.contains(spell));
        while (ownSpells() < count && !pool.isEmpty()) {
            spells.add(pool.remove(random.nextInt(pool.size())));
        }
    }

    private int ownSpells() {
        int n = 0;
        for (Spell s : spells) {
            if (s != ModSpells.SPINE_VOLLEY.get()) {
                n++;
            }
        }
        return n;
    }

    /** Rozdzka Redux zywiolu i tieru z zakleciami w rece moba. */
    public void equipWand(Mob mob, float dropChance) {
        Item item = RegistryUtils.getWand(tierOf(maxTier), element);
        if (item == Items.AIR) {
            return;
        }
        ItemStack wand = new ItemStack(item);
        CastItemDataHelper.setSpells(wand, spells);
        mob.setItemSlot(EquipmentSlot.MAINHAND, wand);
        mob.setDropChance(EquipmentSlot.MAINHAND, dropChance);
    }

    static SpellTier tierOf(int tier) {
        return switch (tier) {
            case InfectedTiers.MASTER -> SpellTiers.MASTER;
            case InfectedTiers.ADVANCED -> SpellTiers.ADVANCED;
            default -> SpellTiers.APPRENTICE;
        };
    }

    private static int clampTier(int tier) {
        return Math.max(InfectedTiers.APPRENTICE, Math.min(InfectedTiers.MASTER, tier));
    }

    public List<Spell> spells() {
        return spells;
    }

    public Element element() {
        return element;
    }

    public int maxTier() {
        return maxTier;
    }

    public boolean rolled() {
        return rolled;
    }

    // ------------------------------------------------------------------ NBT

    public void save(CompoundTag tag) {
        tag.putString("ebreduxaddon:element", element.getLocation().toString());
        tag.putInt("ebreduxaddon:max_tier", maxTier);
        tag.putBoolean("ebreduxaddon:rolled", rolled);
        ListTag list = new ListTag();
        spells.forEach(s -> list.add(StringTag.valueOf(s.getLocation().toString())));
        tag.put("ebreduxaddon:spells", list);
    }

    public void load(CompoundTag tag) {
        Element saved = Services.REGISTRY_UTIL.getElement(ResourceLocation.tryParse(tag.getString("ebreduxaddon:element")));
        if (saved != null) {
            this.element = saved;
        }
        this.maxTier = clampTier(tag.getInt("ebreduxaddon:max_tier"));
        this.rolled = tag.getBoolean("ebreduxaddon:rolled");
        spells.clear();
        for (Tag t : tag.getList("ebreduxaddon:spells", Tag.TAG_STRING)) {
            Spell spell = Services.REGISTRY_UTIL.getSpell(ResourceLocation.tryParse(t.getAsString()));
            if (spell != null && spell != Spells.NONE) {
                spells.add(spell);
            } else {
                EbreduxAddon.LOGGER.debug("[EbreduxAddon] dropping unknown spell {}", t.getAsString());
            }
        }
    }
}
