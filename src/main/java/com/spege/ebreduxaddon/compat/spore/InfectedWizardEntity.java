package com.spege.ebreduxaddon.compat.spore;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.ExtremelySusThings.SporeSavedData;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.binaris.wizardry.api.content.entity.living.ISpellCaster;
import com.binaris.wizardry.api.content.spell.Element;
import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.api.content.spell.SpellContexts;
import com.binaris.wizardry.api.content.spell.SpellTier;
import com.binaris.wizardry.api.content.util.CastItemDataHelper;
import com.binaris.wizardry.api.content.util.RegistryUtils;
import com.binaris.wizardry.content.entity.goal.AttackSpellGoal;
import com.binaris.wizardry.core.platform.Services;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.binaris.wizardry.setup.registries.Spells;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.core.InfectedTiers;
import com.spege.ebreduxaddon.feature.ModSpells;
import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Zarazony mag: mag Redux przejety przez Spore (spec 0.2). Dziedziczy po Infected, wiec jest czlonkiem
 * roju (Spore nie atakuje swoich po klasie), ma glod, linkowanie z Hivemindem i cele roju za darmo.
 * Od siebie dokladamy rzucanie zaklec Redux przez jego AttackSpellGoal.
 *
 * <p>Powstaje z konwersji Spore (data/ebreduxaddon/spore_mob_conversion), ktora wola finalizeSpawn
 * z MobSpawnType.CONVERSION - tam losujemy zywiol, tier i zaklecia.
 */
public class InfectedWizardEntity extends Infected implements ISpellCaster {

    private static final EntityDataAccessor<String> CONTINUOUS_SPELL =
            SynchedEntityData.defineId(InfectedWizardEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SPELL_COUNTER =
            SynchedEntityData.defineId(InfectedWizardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TEXTURE_INDEX =
            SynchedEntityData.defineId(InfectedWizardEntity.class, EntityDataSerializers.INT);

    public static final int TEXTURE_COUNT = 6;

    private final List<Spell> spells = new ArrayList<>();
    private Element element = Elements.EARTH;
    private int maxTier = InfectedTiers.APPRENTICE;
    /**
     * Czy zywiol, tier i zaklecia sa juz wylosowane. Osobna flaga zamiast "lista pusta": przy
     * spellCount = 0 i bez Spine Volley pusta lista jest poprawnym wynikiem i nie moze losowac w kolko.
     */
    private boolean rolled;

    public InfectedWizardEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        double health = SConfig.SERVER.global_health.get();
        double damage = SConfig.SERVER.global_damage.get();
        double armor = SConfig.SERVER.global_armor.get();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0 * health)
                .add(Attributes.ATTACK_DAMAGE, 4.0 * damage)
                .add(Attributes.ARMOR, 4.0 * armor)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        // Wzorzec InfectedHuman: wlasne cele, potem super - Infected dokleja cele roju i namierzanie.
        this.goalSelector.addGoal(2, new AttackSpellGoal<>(this, 0.6, 14.0f, 30, 50));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        super.registerGoals();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CONTINUOUS_SPELL, Spells.NONE.getLocation().toString());
        this.entityData.define(SPELL_COUNTER, 0);
        this.entityData.define(TEXTURE_INDEX, 0);
    }

    // ------------------------------------------------------------------ spawn

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty,
                                        @NotNull MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        this.entityData.set(TEXTURE_INDEX, this.random.nextInt(TEXTURE_COUNT));
        if (!rolled) {
            roll(level.getLevel());
        }
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    /**
     * 🚨 /summon z NBT (i spawner z danymi encji) NIE wola finalizeSpawn, wiec taki mag nie mialby
     * zaklec ani rozdzki. Losujemy przy pierwszym ticku serwera, jesli nic jeszcze nie losowalo.
     */
    @Override
    public void tick() {
        if (!rolled && this.level() instanceof ServerLevel server) {
            this.entityData.set(TEXTURE_INDEX, this.random.nextInt(TEXTURE_COUNT));
            roll(server);
        }
        super.tick();
    }

    /** Losuje zywiol, tier i zaklecia. Publiczne dla GameTestow. */
    public void roll(ServerLevel level) {
        List<Element> elements = new ArrayList<>(Services.REGISTRY_UTIL.getElements());
        elements.remove(Elements.MAGIC);
        elements.remove(Elements.HEALING);
        this.element = elements.isEmpty() ? Elements.EARTH : elements.get(this.random.nextInt(elements.size()));
        this.maxTier = InfectedTiers.maxTier(hiveminds(level), protoThreshold(), this.random.nextDouble());

        spells.clear();
        SpellTier cap = tierOf(maxTier);
        List<Spell> pool = RegistryUtils.getSpells(spell -> spell.getElement() == element
                && spell.getTier().getLevel() <= cap.getLevel()
                && spell.canCastByEntity()
                && spell.isEnabled(SpellContexts.NPCS));
        int wanted = Config.INSTANCE.infectedSpellCount.get();
        while (spells.size() < wanted && !pool.isEmpty()) {
            spells.add(pool.remove(this.random.nextInt(pool.size())));
        }
        if (Config.INSTANCE.infectedAlwaysSpineVolley.get() && !spells.contains(ModSpells.SPINE_VOLLEY.get())) {
            spells.add(ModSpells.SPINE_VOLLEY.get());
        }
        equipWand(cap);
        this.rolled = true;
    }

    private void equipWand(SpellTier tier) {
        Item item = RegistryUtils.getWand(tier, element);
        if (item == Items.AIR) {
            return;
        }
        ItemStack wand = new ItemStack(item);
        CastItemDataHelper.setSpells(wand, spells);
        this.setItemSlot(EquipmentSlot.MAINHAND, wand);
        this.setDropChance(EquipmentSlot.MAINHAND, Config.INSTANCE.infectedWandDropChance.get().floatValue());
    }

    private static int hiveminds(ServerLevel level) {
        try {
            return SporeSavedData.getDataLocation(level).getAmountOfHiveminds();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int protoThreshold() {
        try {
            return SConfig.SERVER.proto_spawn_world_mod.get();
        } catch (RuntimeException e) {
            return 3;
        }
    }

    static SpellTier tierOf(int tier) {
        return switch (tier) {
            case InfectedTiers.MASTER -> SpellTiers.MASTER;
            case InfectedTiers.ADVANCED -> SpellTiers.ADVANCED;
            default -> SpellTiers.APPRENTICE;
        };
    }

    // ------------------------------------------------------------------ ISpellCaster

    @Override
    public @NotNull List<Spell> getSpells() {
        return spells;
    }

    @Override
    public @NotNull Spell getContinuousSpell() {
        Spell spell = Services.REGISTRY_UTIL.getSpell(ResourceLocation.tryParse(this.entityData.get(CONTINUOUS_SPELL)));
        return spell == null ? Spells.NONE : spell;
    }

    @Override
    public void setContinuousSpell(Spell spell) {
        this.entityData.set(CONTINUOUS_SPELL, (spell == null ? Spells.NONE : spell).getLocation().toString());
    }

    @Override
    public int getSpellCounter() {
        return this.entityData.get(SPELL_COUNTER);
    }

    @Override
    public void setSpellCounter(int count) {
        this.entityData.set(SPELL_COUNTER, count);
    }

    public Element getElement() {
        return element;
    }

    public int getMaxTier() {
        return maxTier;
    }

    public boolean isRolled() {
        return rolled;
    }

    public int getTextureIndex() {
        return Math.floorMod(this.entityData.get(TEXTURE_INDEX), TEXTURE_COUNT);
    }

    // ------------------------------------------------------------------ Spore

    /** Lup idzie z naszej tabeli lupu, nie z listy configu Spore. */
    @Override
    public List<? extends String> getDropList() {
        return List.of();
    }

    @Override
    public String origin() {
        return "ebwizardry:wizard";
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ebreduxaddon:element", element.getLocation().toString());
        tag.putInt("ebreduxaddon:max_tier", maxTier);
        tag.putInt("ebreduxaddon:texture", getTextureIndex());
        tag.putBoolean("ebreduxaddon:rolled", rolled);
        ListTag list = new ListTag();
        spells.forEach(s -> list.add(StringTag.valueOf(s.getLocation().toString())));
        tag.put("ebreduxaddon:spells", list);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Element saved = Services.REGISTRY_UTIL.getElement(ResourceLocation.tryParse(tag.getString("ebreduxaddon:element")));
        if (saved != null) {
            this.element = saved;
        }
        this.maxTier = Math.max(InfectedTiers.APPRENTICE, Math.min(InfectedTiers.MASTER, tag.getInt("ebreduxaddon:max_tier")));
        this.entityData.set(TEXTURE_INDEX, tag.getInt("ebreduxaddon:texture"));
        this.rolled = tag.getBoolean("ebreduxaddon:rolled");
        spells.clear();
        for (Tag t : tag.getList("ebreduxaddon:spells", Tag.TAG_STRING)) {
            Spell spell = Services.REGISTRY_UTIL.getSpell(ResourceLocation.tryParse(t.getAsString()));
            if (spell != null && spell != Spells.NONE) {
                spells.add(spell);
            } else {
                EbreduxAddon.LOGGER.debug("[EbreduxAddon] infected wizard: dropping unknown spell {}", t.getAsString());
            }
        }
    }
}
