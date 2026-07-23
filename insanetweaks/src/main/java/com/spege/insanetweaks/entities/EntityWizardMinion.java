package com.spege.insanetweaks.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.google.common.base.Predicate;
import com.windanesz.ancientspellcraft.entity.ai.EntityAIBattlemageMelee;
import com.windanesz.ancientspellcraft.entity.ai.EntityAIBattlemageSpellcasting;
import com.windanesz.ancientspellcraft.entity.ai.EntityAIBlockWithShield;
import com.windanesz.ancientspellcraft.registry.ASItems;
import com.windanesz.ancientspellcraft.registry.ASSpells;
import com.windanesz.ancientspellcraft.entity.ai.EntityAIAttackSpellImproved;
import com.windanesz.ancientspellcraft.entity.ai.IShieldUser;
import com.windanesz.ancientspellcraft.entity.living.IArmourClassWizard;
import com.windanesz.ancientspellcraft.entity.living.ICustomCooldown;
import com.spege.insanetweaks.entities.ai.SummonTargetingHelper;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.registry.WizardrySounds;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.AllyDesignationSystem;
import electroblob.wizardry.util.ParticleBuilder;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

@SuppressWarnings({"null", "deprecation"})
public class EntityWizardMinion extends EntitySummonedCreature
        implements ISpellCaster, IArmourClassWizard, ICustomCooldown, IShieldUser {

    public static final int TEXTURE_VARIANT_COUNT = 6;
    private static final EntityEquipmentSlot[] ARMOUR_SLOTS = new EntityEquipmentSlot[] {
            EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
    };
    private static final electroblob.wizardry.constants.Tier[] ARCANE_TOOL_TIERS = new electroblob.wizardry.constants.Tier[] {
            electroblob.wizardry.constants.Tier.NOVICE,
            electroblob.wizardry.constants.Tier.APPRENTICE,
            electroblob.wizardry.constants.Tier.ADVANCED,
            electroblob.wizardry.constants.Tier.MASTER
    };

    private static final DataParameter<Integer> ELEMENT = EntityDataManager.createKey(EntityWizardMinion.class,
            DataSerializers.VARINT);
    private static final DataParameter<String> CONTINUOUS_SPELL = EntityDataManager.createKey(EntityWizardMinion.class,
            DataSerializers.STRING);
    private static final DataParameter<Integer> SPELL_COUNTER = EntityDataManager.createKey(EntityWizardMinion.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> TEXTURE_INDEX = EntityDataManager.createKey(EntityWizardMinion.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> ARMOUR_CLASS = EntityDataManager.createKey(EntityWizardMinion.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> SHIELD_DISABLED_TICK = EntityDataManager.createKey(
            EntityWizardMinion.class, DataSerializers.VARINT);

    private EntityAIAttackSpellImproved<EntityWizardMinion> spellCastingAI;
    private EntityAIBattlemageMelee<EntityWizardMinion> battlemageMeleeAI;
    private EntityAIBattlemageSpellcasting<EntityWizardMinion> battlemageSpellcastingAI;
    private EntityAIBlockWithShield<EntityWizardMinion> battlemageShieldAI;
    private final List<Spell> spells = new ArrayList<Spell>(4);
    private Predicate<EntityLivingBase> targetSelector;

    private float spellPotencyMultiplier = 1.0F;
    private int selfHealCooldown = 200;
    private int cooldown = -1;

    public EntityWizardMinion(World world) {
        super(world);
        this.setSize(0.6F, 1.95F);
        this.experienceValue = 0;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ELEMENT, Element.MAGIC.ordinal());
        this.dataManager.register(CONTINUOUS_SPELL, "ebwizardry:none");
        this.dataManager.register(SPELL_COUNTER, 0);
        this.dataManager.register(TEXTURE_INDEX, 0);
        this.dataManager.register(ARMOUR_CLASS, ItemWizardArmour.ArmourClass.WIZARD.ordinal());
        this.dataManager.register(SHIELD_DISABLED_TICK, 0);
    }

    @Override
    protected void initEntityAI() {
        this.targetSelector = entity -> entity != null
                && entity != this
                && entity != this.getCaster()
                && !entity.isInvisible()
                && AllyDesignationSystem.isValidTarget(this, entity)
                && this.isSupportedTargetType(entity)
                && !this.isBlacklistedTarget(entity);
        this.spellCastingAI = new EntityAIAttackSpellImproved<EntityWizardMinion>(this, 0.6D, 14.0F, 30, 80);
        this.battlemageMeleeAI = new EntityAIBattlemageMelee<EntityWizardMinion>(this, 0.6D, false);
        this.battlemageSpellcastingAI = new EntityAIBattlemageSpellcasting<EntityWizardMinion>(this, 0.6D, 14.0F, 30,
                50);
        this.battlemageShieldAI = new EntityAIBlockWithShield<EntityWizardMinion>(this);

        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2,
                new EntityAINearestAttackableTarget<EntityLivingBase>(this, EntityLivingBase.class, 0, false, true,
                        this.targetSelector));
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(7, new EntityAIWanderAvoidWater(this, 0.8D));
        this.tasks.addTask(8, new EntityAILookIdle(this));
        this.applyCombatAiProfile(this.getArmourClass());
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, 30.0D);
        this.setBaseAttribute(SharedMonsterAttributes.MOVEMENT_SPEED, 0.5D);
        this.setBaseAttribute(SharedMonsterAttributes.FOLLOW_RANGE, 24.0D);
        this.setBaseAttribute(SharedMonsterAttributes.ATTACK_DAMAGE, 2.0D);
    }

    private void setBaseAttribute(IAttribute attribute, double value) {
        IAttributeInstance instance = this.getEntityAttribute(attribute);

        if (instance == null) {
            instance = this.getAttributeMap().registerAttribute(attribute);
        }

        instance.setBaseValue(value);
    }

    public void configureLoadout(Element element, ItemWizardArmour.ArmourClass armourClass, int textureIndex,
            float spellPotencyMultiplier) {
        this.setElement(element == null ? Element.MAGIC : element);
        this.setArmourClass(armourClass == null ? ItemWizardArmour.ArmourClass.WIZARD : armourClass);
        this.setTextureIndex(textureIndex);
        this.spellPotencyMultiplier = Math.max(0.1F, spellPotencyMultiplier);
        this.refreshLoadout();
    }

    private void refreshLoadout() {
        this.spells.clear();
        this.cooldown = -1;
        this.setShieldDisabledTick(0);

        Element element = this.getElement();
        ItemWizardArmour.ArmourClass armourClass = this.getArmourClass();
        boolean masterPool = armourClass == ItemWizardArmour.ArmourClass.SAGE
                || armourClass == ItemWizardArmour.ArmourClass.WARLOCK;

        electroblob.wizardry.constants.Tier maxTier = IArmourClassWizard.populateSpells(this, this.spells, element,
                masterPool, 4, this.rand);

        if (armourClass == ItemWizardArmour.ArmourClass.WARLOCK) {
            this.spells.remove(Spells.magic_missile);
            if (this.rand.nextBoolean()) {
                this.spells.add(ASSpells.chaos_blast);
            }
            this.spells.add(ASSpells.chaos_orb);
        }

        if (this.spells.isEmpty()) {
            this.spells.add(Spells.magic_missile);
            maxTier = electroblob.wizardry.constants.Tier.NOVICE;
        }

        this.applyCombatAiProfile(armourClass);
        this.equipWizardGear(element, maxTier);
    }

    private void applyCombatAiProfile(ItemWizardArmour.ArmourClass armourClass) {
        if (this.spellCastingAI == null || this.battlemageMeleeAI == null || this.battlemageSpellcastingAI == null
                || this.battlemageShieldAI == null) {
            return;
        }

        this.tasks.removeTask(this.spellCastingAI);
        this.tasks.removeTask(this.battlemageMeleeAI);
        this.tasks.removeTask(this.battlemageSpellcastingAI);
        this.tasks.removeTask(this.battlemageShieldAI);

        if (armourClass == ItemWizardArmour.ArmourClass.BATTLEMAGE) {
            this.tasks.addTask(2, this.battlemageShieldAI);
            this.tasks.addTask(3, this.battlemageMeleeAI);
            this.tasks.addTask(3, this.battlemageSpellcastingAI);
        } else {
            this.tasks.addTask(3, this.spellCastingAI);
        }
    }

    private void equipWizardGear(Element element, electroblob.wizardry.constants.Tier maxTier) {
        ItemWizardArmour.ArmourClass armourClass = this.getArmourClass();

        for (EntityEquipmentSlot slot : ARMOUR_SLOTS) {
            this.setItemStackToSlot(slot,
                    new ItemStack(ItemWizardArmour.getArmour(element, armourClass, slot)));
            this.setDropChance(slot, 0.0F);
        }

        ArrayList<Spell> focusSpells = new ArrayList<Spell>(this.spells);
        focusSpells.add(Spells.heal);

        if (armourClass == ItemWizardArmour.ArmourClass.BATTLEMAGE) {
            ItemStack sword = new ItemStack(ASItems.battlemage_sword_master);
            NBTTagCompound swordNbt = sword.getTagCompound();
            if (swordNbt == null) {
                swordNbt = new NBTTagCompound();
            }
            swordNbt.setString("element", element.name());
            sword.setTagCompound(swordNbt);
            WandHelper.setSpells(sword, focusSpells.toArray(new Spell[focusSpells.size()]));
            this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, sword);
            this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, new ItemStack(ASItems.battlemage_shield));
        } else if (armourClass == ItemWizardArmour.ArmourClass.WARLOCK) {
            ItemStack orb = this.createClassFocus("warlock_orb", this.getRandomArcaneToolTier(), element);
            if (orb.isEmpty()) {
                orb = new ItemStack(WizardryItems.getWand(maxTier, element));
            }
            this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, orb);
            this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else if (armourClass == ItemWizardArmour.ArmourClass.SAGE) {
            ItemStack tome = this.createClassFocus("sage_tome", this.getRandomArcaneToolTier(), element);
            if (tome.isEmpty()) {
                tome = new ItemStack(WizardryItems.getWand(maxTier, element));
            }
            this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, tome);
            this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else {
            ItemStack wand = new ItemStack(WizardryItems.getWand(maxTier, element));
            WandHelper.setSpells(wand, focusSpells.toArray(new Spell[focusSpells.size()]));
            this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, wand);
            this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }

        this.setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EntityEquipmentSlot.OFFHAND, 0.0F);
    }

    private ItemStack createClassFocus(String prefix, electroblob.wizardry.constants.Tier tier, Element element) {
        String path = prefix + "_" + tier.toString().toLowerCase() + "_" + element.name().toLowerCase();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ancientspellcraft", path));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private electroblob.wizardry.constants.Tier getRandomArcaneToolTier() {
        return ARCANE_TOOL_TIERS[this.rand.nextInt(ARCANE_TOOL_TIERS.length)];
    }

    private boolean isSupportedTargetType(EntityLivingBase entity) {
        if (entity instanceof IMob || entity instanceof ISummonedCreature) {
            return true;
        }

        ResourceLocation entityId = EntityList.getKey(entity.getClass());
        return entityId != null
                && java.util.Arrays.asList(electroblob.wizardry.Wizardry.settings.summonedCreatureTargetsWhitelist)
                        .contains(entityId);
    }

    private boolean isBlacklistedTarget(EntityLivingBase entity) {
        ResourceLocation entityId = EntityList.getKey(entity.getClass());
        return entityId != null
                && java.util.Arrays.asList(electroblob.wizardry.Wizardry.settings.summonedCreatureTargetsBlacklist)
                        .contains(entityId);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!this.world.isRemote) {
            if (this.getShieldDisabledTick() > 0) {
                this.decrementShieldDisabledTick();
            }
            if (this.spells.isEmpty()) {
                this.refreshLoadout();
            }

            SummonInfectionSafetyHelper.onSummonServerTick(this);
            SummonTargetingHelper.syncCasterPriorityTarget(this);
            this.tickPassiveSelfHeal();

            EntityLivingBase caster = this.getCaster();
            if (caster != null && this.getAttackTarget() == null && this.getDistanceSq(caster) > 64.0D) {
                this.getNavigator().tryMoveToEntityLiving(caster, 1.05D);
            }
        }
    }

    private void tickPassiveSelfHeal() {
        if (this.isPotionActive(WizardryPotions.arcane_jammer)) {
            return;
        }

        if (this.selfHealCooldown > 0) {
            this.selfHealCooldown--;
            return;
        }

        if (this.getHealth() < this.getMaxHealth() && this.getHealth() > 0.0F) {
            float healAmount = this.getElement() == Element.HEALING ? 6.0F : 3.0F;
            this.heal(healAmount);
            this.playSound(Spells.heal.getSounds()[0], 0.7F, this.rand.nextFloat() * 0.4F + 1.0F);
        }

        this.selfHealCooldown = this.getHealth() < 10.0F ? 150 : 400;
    }

    @Override
    public List<Spell> getSpells() {
        return this.spells;
    }

    @Override
    public SpellModifiers getModifiers() {
        return new SpellModifiers().set(SpellModifiers.POTENCY, this.spellPotencyMultiplier, true);
    }

    public Element getElement() {
        int index = this.dataManager.get(ELEMENT);
        Element[] values = Element.values();
        if (index < 0 || index >= values.length) {
            return Element.MAGIC;
        }
        return values[index];
    }

    public void setElement(Element element) {
        this.dataManager.set(ELEMENT, element.ordinal());
    }

    @Override
    public Spell getContinuousSpell() {
        return Spell.get(this.dataManager.get(CONTINUOUS_SPELL));
    }

    @Override
    public void setContinuousSpell(Spell spell) {
        Spell resolved = spell == null ? Spells.none : spell;
        ResourceLocation registryName = resolved.getRegistryName();
        this.dataManager.set(CONTINUOUS_SPELL, registryName == null ? "ebwizardry:none" : registryName.toString());
    }

    @Override
    public int getSpellCounter() {
        return this.dataManager.get(SPELL_COUNTER);
    }

    @Override
    public void setSpellCounter(int count) {
        this.dataManager.set(SPELL_COUNTER, count);
    }

    public int getTextureIndex() {
        return this.dataManager.get(TEXTURE_INDEX);
    }

    public void setTextureIndex(int textureIndex) {
        int clamped = textureIndex % TEXTURE_VARIANT_COUNT;
        if (clamped < 0) {
            clamped += TEXTURE_VARIANT_COUNT;
        }
        this.dataManager.set(TEXTURE_INDEX, clamped);
    }

    @Override
    public ItemWizardArmour.ArmourClass getArmourClass() {
        int index = this.dataManager.get(ARMOUR_CLASS);
        ItemWizardArmour.ArmourClass[] values = ItemWizardArmour.ArmourClass.values();
        if (index < 0 || index >= values.length) {
            return ItemWizardArmour.ArmourClass.WIZARD;
        }
        return values[index];
    }

    @Override
    public void setArmourClass(ItemWizardArmour.ArmourClass armourClass) {
        this.dataManager.set(ARMOUR_CLASS, armourClass.ordinal());
    }

    @Override
    public UUID getOwnerId() {
        return super.func_184753_b();
    }

    @Override
    public Entity getOwner() {
        return this.getCaster();
    }

    @Override
    public boolean hasRangedAttack() {
        return true;
    }

    @Override
    public void onSpawn() {
        this.spawnParticleEffect();
    }

    @Override
    public void onDespawn() {
        this.spawnParticleEffect();
    }

    @Override
    public boolean hasParticleEffect() {
        return true;
    }

    @Override
    public boolean isInvisible() {
        return false;
    }

    private void spawnParticleEffect() {
        if (!this.world.isRemote) {
            return;
        }

        for (int i = 0; i < 15; i++) {
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC)
                    .pos(this.posX + this.rand.nextFloat() - 0.5F, this.posY + this.rand.nextFloat() * this.height,
                            this.posZ + this.rand.nextFloat() - 0.5F)
                    .clr(0.15F, 0.35F, 0.85F)
                    .spawn(this.world);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return WizardrySounds.ENTITY_WIZARD_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return WizardrySounds.ENTITY_WIZARD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WizardrySounds.ENTITY_WIZARD_DEATH;
    }

    @Override
    public int getCooldown() {
        return this.cooldown;
    }

    @Override
    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    @Override
    public int incrementCooldown() {
        return this.cooldown++;
    }

    @Override
    public int decrementCooldown() {
        return this.cooldown--;
    }

    public void setShieldDisabledTick(int count) {
        this.dataManager.set(SHIELD_DISABLED_TICK, Math.max(0, count));
    }

    @Override
    public int getShieldDisabledTick() {
        return this.dataManager.get(SHIELD_DISABLED_TICK);
    }

    public void decrementShieldDisabledTick() {
        this.setShieldDisabledTick(this.getShieldDisabledTick() - 1);
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        int knockback = 0;

        if (entityIn instanceof EntityLivingBase) {
            EntityLivingBase livingTarget = (EntityLivingBase) entityIn;
            damage += EnchantmentHelper.getModifierForCreature(this.getHeldItemMainhand(),
                    livingTarget.getCreatureAttribute());
            knockback += EnchantmentHelper.getKnockbackModifier(this);
        }

        boolean attacked = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), damage);

        if (attacked) {
            if (knockback > 0 && entityIn instanceof EntityLivingBase) {
                EntityLivingBase livingTarget = (EntityLivingBase) entityIn;
                livingTarget.knockBack(this, knockback * 0.5F, MathHelper.sin(this.rotationYaw * 0.017453292F),
                        -MathHelper.cos(this.rotationYaw * 0.017453292F));
                this.motionX *= 0.6D;
                this.motionZ *= 0.6D;
            }

            int fireAspect = EnchantmentHelper.getFireAspectModifier(this);
            if (fireAspect > 0) {
                entityIn.setFire(fireAspect * 4);
            }

            if (entityIn instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entityIn;
                ItemStack heldMainhand = this.getHeldItemMainhand();
                ItemStack activeItem = player.isHandActive() ? player.getActiveItemStack() : ItemStack.EMPTY;

                if (!heldMainhand.isEmpty() && !activeItem.isEmpty()
                        && heldMainhand.getItem().canDisableShield(heldMainhand, activeItem, player, this)
                        && activeItem.getItem().isShield(activeItem, player)) {
                    float shieldDisableChance = 0.25F + EnchantmentHelper.getEfficiencyModifier(this) * 0.05F;
                    if (this.rand.nextFloat() < shieldDisableChance) {
                        player.getCooldownTracker().setCooldown(activeItem.getItem(), 100);
                        this.world.setEntityState(player, (byte) 30);
                    }
                }
            }

            this.applyEnchantments(this, entityIn);

            if (entityIn instanceof EntityLivingBase) {
                SummonInfectionSafetyHelper.onSuccessfulSummonHit((EntityLivingBase) entityIn);
            }
        }

        return attacked;
    }

    @Override
    protected void blockUsingShield(EntityLivingBase attacker) {
        super.blockUsingShield(attacker);

        if (attacker != null && attacker.getHeldItemMainhand().getItem().canDisableShield(attacker.getHeldItemMainhand(),
                this.getActiveItemStack(), this, attacker)) {
            this.disableShield();
        }
    }

    @Override
    protected void damageShield(float damage) {
        if (damage >= 3.0F && this.getHeldItemOffhand().getItem().isShield(this.getHeldItemOffhand(), this)) {
            int shieldDamage = 1 + MathHelper.floor(damage);
            ItemStack shieldStack = this.getHeldItemOffhand();
            shieldStack.damageItem(shieldDamage, this);
            this.setActiveHand(EnumHand.OFF_HAND);

            if (shieldStack.isEmpty()) {
                this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
                this.playSound(SoundEvents.ITEM_SHIELD_BREAK, 0.8F, 0.8F + this.world.rand.nextFloat() * 0.4F);
            }
            return;
        }

        super.damageShield(damage);
    }

    private void disableShield() {
        float disableChance = 1.0F + EnchantmentHelper.getEfficiencyModifier(this) * 0.05F;
        if (this.rand.nextFloat() < disableChance) {
            this.setShieldDisabledTick(80);
            this.resetActiveHand();
            this.world.setEntityState(this, (byte) 30);
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("WizardElement", this.getElement().ordinal());
        compound.setInteger("WizardTexture", this.getTextureIndex());
        compound.setInteger("WizardArmourClass", this.getArmourClass().ordinal());
        compound.setFloat("WizardSpellPotency", this.spellPotencyMultiplier);
        compound.setInteger("WizardSelfHealCooldown", this.selfHealCooldown);
        compound.setInteger("WizardBattlemageCooldown", this.cooldown);
        compound.setInteger("WizardShieldDisabledTick", this.getShieldDisabledTick());

        NBTTagList spellList = new NBTTagList();
        for (Spell spell : this.spells) {
            if (spell != null && spell.getRegistryName() != null) {
                spellList.appendTag(new NBTTagString(spell.getRegistryName().toString()));
            }
        }
        compound.setTag("WizardSpells", spellList);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);

        if (compound.hasKey("WizardElement")) {
            int elementIndex = compound.getInteger("WizardElement");
            Element[] elements = Element.values();
            this.setElement(elementIndex >= 0 && elementIndex < elements.length ? elements[elementIndex] : Element.MAGIC);
        }

        if (compound.hasKey("WizardTexture")) {
            this.setTextureIndex(compound.getInteger("WizardTexture"));
        }

        if (compound.hasKey("WizardArmourClass")) {
            int armourIndex = compound.getInteger("WizardArmourClass");
            ItemWizardArmour.ArmourClass[] values = ItemWizardArmour.ArmourClass.values();
            this.setArmourClass(armourIndex >= 0 && armourIndex < values.length ? values[armourIndex]
                    : ItemWizardArmour.ArmourClass.WIZARD);
        }

        this.spellPotencyMultiplier = compound.hasKey("WizardSpellPotency")
                ? Math.max(0.1F, compound.getFloat("WizardSpellPotency"))
                : 1.0F;
        this.selfHealCooldown = compound.hasKey("WizardSelfHealCooldown")
                ? compound.getInteger("WizardSelfHealCooldown")
                : 200;
        this.cooldown = compound.hasKey("WizardBattlemageCooldown")
                ? compound.getInteger("WizardBattlemageCooldown")
                : -1;
        this.setShieldDisabledTick(compound.hasKey("WizardShieldDisabledTick")
                ? compound.getInteger("WizardShieldDisabledTick")
                : 0);
        this.applyCombatAiProfile(this.getArmourClass());

        this.spells.clear();
        if (compound.hasKey("WizardSpells", 9)) {
            NBTTagList spellList = compound.getTagList("WizardSpells", 8);
            for (int i = 0; i < spellList.tagCount(); i++) {
                Spell spell = Spell.get(spellList.getStringTagAt(i));
                if (spell != Spells.none) {
                    this.spells.add(spell);
                }
            }
        }
    }

}
