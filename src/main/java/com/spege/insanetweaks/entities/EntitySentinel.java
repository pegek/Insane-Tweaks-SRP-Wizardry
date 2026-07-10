package com.spege.insanetweaks.entities;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.init.ModSpells;
import com.spege.insanetweaks.entities.ai.EntityAISentinelFollowOwner;
import com.spege.insanetweaks.entities.ai.EntityAISentinelGuardPatrol;
import com.spege.insanetweaks.entities.ai.EntityAISentinelReturnToAnchor;
import com.windanesz.ancientspellcraft.entity.ai.EntityAIBlockWithShield;
import com.windanesz.ancientspellcraft.entity.ai.IShieldUser;
import com.windanesz.ancientspellcraft.entity.living.ICustomCooldown;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.registry.WizardrySounds;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.AllyDesignationSystem;
import electroblob.wizardry.util.EntityUtils;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraft.util.NonNullList;

@SuppressWarnings({"null", "deprecation"})
public class EntitySentinel extends EntityCreature
        implements ISpellCaster, IEntityOwnable, ICustomCooldown, IShieldUser {

    public static final int TEXTURE_VARIANT_COUNT = 6;
    private static final int DEFAULT_GUARD_RADIUS = 20;
    private static final int GUARD_SEARCH_CAP = 512;
    private static final int LOOT_SLOT_COUNT = 20;
    private static final int GUARD_CHEST_SCAN_RADIUS = 8;
    private static final int GUARD_CHEST_SCAN_HEIGHT = 3;
    private static final int SENTINEL_STANDARD_SPELL_COUNT = 6;
    private static final EntityEquipmentSlot[] ARMOUR_SLOTS = new EntityEquipmentSlot[] {
            EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
    };

    private static final DataParameter<Integer> ELEMENT = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);
    private static final DataParameter<String> CONTINUOUS_SPELL = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.STRING);
    private static final DataParameter<Integer> SPELL_COUNTER = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> TEXTURE_INDEX = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> COMMAND_MODE = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);
    private static final DataParameter<Boolean> HAS_GUARD_ANCHOR = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> GUARD_ANCHOR_X = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> GUARD_ANCHOR_Y = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> GUARD_ANCHOR_Z = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> SHIELD_DISABLED_TICK = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);

    private final List<Spell> spells = new ArrayList<Spell>(SENTINEL_STANDARD_SPELL_COUNT + 1);
    private final List<BlockPos> guardPatrolPoints = new ArrayList<BlockPos>();
    private final NonNullList<ItemStack> lootInventory = NonNullList.withSize(LOOT_SLOT_COUNT, ItemStack.EMPTY);
    private Predicate<EntityLivingBase> targetSelector;
    private EntityAIBlockWithShield<EntitySentinel> shieldAI;
    private EntityAISentinelFollowOwner followOwnerAI;
    private EntityAISentinelReturnToAnchor returnToAnchorAI;
    private EntityAISentinelGuardPatrol guardPatrolAI;
    private UUID ownerUUID;
    private float spellPotencyMultiplier = 1.8F;
    private int cooldown = -1;
    private int selfHealCooldown = 200;
    private boolean guardRegionDirty = true;

    public EntitySentinel(World worldIn) {
        super(worldIn);
        this.setSize(0.6F, 1.95F);
        this.experienceValue = 0;
        this.setCanPickUpLoot(true);
        this.enablePersistence();
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ELEMENT, Element.MAGIC.ordinal());
        this.dataManager.register(CONTINUOUS_SPELL, "ebwizardry:none");
        this.dataManager.register(SPELL_COUNTER, 0);
        this.dataManager.register(TEXTURE_INDEX, 0);
        this.dataManager.register(COMMAND_MODE, SentinelCommandMode.FOLLOW.getId());
        this.dataManager.register(HAS_GUARD_ANCHOR, Boolean.FALSE);
        this.dataManager.register(GUARD_ANCHOR_X, 0);
        this.dataManager.register(GUARD_ANCHOR_Y, 0);
        this.dataManager.register(GUARD_ANCHOR_Z, 0);
        this.dataManager.register(SHIELD_DISABLED_TICK, 0);
    }

    @Override
    protected void initEntityAI() {
        this.targetSelector = entity -> entity != null
                && entity != this
                && entity != this.getOwnerEntity()
                && !entity.isInvisible()
                && AllyDesignationSystem.isValidTarget(this, entity)
                && this.isSupportedTargetType(entity)
                && !this.isBlacklistedTarget(entity)
                && this.isAllowedByCurrentCommandMode(entity);

        this.shieldAI = new EntityAIBlockWithShield<EntitySentinel>(this);
        this.followOwnerAI = new EntityAISentinelFollowOwner(this, 1.0D, 5.0F, 9.0F);
        this.returnToAnchorAI = new EntityAISentinelReturnToAnchor(this, 0.95D);
        this.guardPatrolAI = new EntityAISentinelGuardPatrol(this, 0.7D);

        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(2, this.shieldAI);
        // F1 (spec 2026-07-10): single combat task (melee <=6 blocks, cast beyond) replaces
        // ASC's battlemage melee/spellcasting pair, which cast far too rarely.
        this.tasks.addTask(3, new com.spege.insanetweaks.entities.ai.EntityAISentinelCombat(this));
        this.tasks.addTask(4, this.returnToAnchorAI);
        this.tasks.addTask(5, this.followOwnerAI);
        this.tasks.addTask(6, this.guardPatrolAI);
        this.tasks.addTask(8, new EntityAILookIdle(this));

        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2,
                new EntityAINearestAttackableTarget<EntityLivingBase>(this, EntityLivingBase.class, 0, false, true,
                        this.targetSelector));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, 80.0D);
        this.setBaseAttribute(SharedMonsterAttributes.MOVEMENT_SPEED, 0.38D);
        this.setBaseAttribute(SharedMonsterAttributes.FOLLOW_RANGE, 32.0D);
        this.setBaseAttribute(SharedMonsterAttributes.ATTACK_DAMAGE, 6.0D);
        this.setBaseAttribute(SharedMonsterAttributes.ARMOR, 10.0D);
    }

    private void setBaseAttribute(IAttribute attribute, double value) {
        IAttributeInstance instance = this.getEntityAttribute(attribute);
        if (instance == null) {
            instance = this.getAttributeMap().registerAttribute(attribute);
        }
        instance.setBaseValue(value);
    }

    @Override
    @Nullable
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData livingdata) {
        IEntityLivingData spawnData = super.onInitialSpawn(difficulty, livingdata);
        this.configureDefaultLoadout();
        this.bindToNearestPlayer();
        return spawnData;
    }

    private void configureDefaultLoadout() {
        this.setElement(Element.MAGIC);
        this.setTextureIndex(this.rand.nextInt(TEXTURE_VARIANT_COUNT));
        this.spells.clear();
        this.populateSentinelSpellPool();
        if (this.spells.isEmpty()) {
            this.spells.add(Spells.magic_missile);
        }
        this.equipBattlemageGear();
    }

    private void populateSentinelSpellPool() {
        ArrayList<Spell> standardPool = new ArrayList<Spell>(Spell.getSpells(this::isEligibleStandardSentinelSpell));
        java.util.Collections.shuffle(standardPool, this.rand);

        for (Spell spell : standardPool) {
            if (this.spells.size() >= SENTINEL_STANDARD_SPELL_COUNT) {
                break;
            }
            this.addSpellIfMissing(spell);
        }

        Spell abominationSpell = this.getRandomSentinelAbominationSpell();
        if (abominationSpell != null) {
            this.addSpellIfMissing(abominationSpell);
        }
    }

    private boolean isEligibleStandardSentinelSpell(Spell spell) {
        if (spell == null || spell == Spells.none || this.isSentinelAbominationSpell(spell)) {
            return false;
        }

        if (!spell.canBeCastBy(this, false)) {
            return false;
        }

        Tier tier = spell.getTier();
        return tier == Tier.ADVANCED || tier == Tier.MASTER;
    }

    private boolean isSentinelAbominationSpell(Spell spell) {
        return spell == ModSpells.SUMMON_FER_COW
                || spell == ModSpells.SUMMON_PRIMITIVE_YELLOWEYE
                || spell == ModSpells.SUMMON_PRIMITIVE_SUMMONER;
    }

    @Nullable
    private Spell getRandomSentinelAbominationSpell() {
        ArrayList<Spell> abominationPool = new ArrayList<Spell>(3);
        abominationPool.add(ModSpells.SUMMON_FER_COW);
        abominationPool.add(ModSpells.SUMMON_PRIMITIVE_YELLOWEYE);
        abominationPool.add(ModSpells.SUMMON_PRIMITIVE_SUMMONER);
        abominationPool.removeIf(spell -> spell == null || spell == Spells.none);
        if (abominationPool.isEmpty()) {
            return null;
        }
        return abominationPool.get(this.rand.nextInt(abominationPool.size()));
    }

    private void addSpellIfMissing(@Nullable Spell spell) {
        if (spell != null && spell != Spells.none && !this.spells.contains(spell)) {
            this.spells.add(spell);
        }
    }

    private void equipBattlemageGear() {
        this.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(ModItems.LIVING_BATTLEMAGE_HELMET));
        this.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(ModItems.LIVING_BATTLEMAGE_CHESTPLATE));
        this.setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(ModItems.LIVING_BATTLEMAGE_LEGGINGS));
        this.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(ModItems.LIVING_BATTLEMAGE_BOOTS));
        for (EntityEquipmentSlot slot : ARMOUR_SLOTS) {
            this.setDropChance(slot, 0.0F);
        }

        ArrayList<Spell> focusSpells = new ArrayList<Spell>(this.spells);
        focusSpells.add(Spells.heal);

        Element element = this.getElement();
        ItemStack sword = new ItemStack(ModItems.LIVING_SPELLBLADE);
        NBTTagCompound swordNbt = sword.getTagCompound();
        if (swordNbt == null) {
            swordNbt = new NBTTagCompound();
        }
        swordNbt.setString("element", element.name());
        sword.setTagCompound(swordNbt);
        WandHelper.setSpells(sword, focusSpells.toArray(new Spell[focusSpells.size()]));
        this.fillManaIfSupported(sword);
        this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, sword);

        ItemStack shield = new ItemStack(ModItems.LIVING_AEGIS);
        this.fillManaIfSupported(shield);
        this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, shield);
        this.setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EntityEquipmentSlot.OFFHAND, 0.0F);
    }

    private void fillManaIfSupported(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof IManaStoringItem) {
            IManaStoringItem manaItem = (IManaStoringItem) stack.getItem();
            manaItem.setMana(stack, manaItem.getManaCapacity(stack));
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (this.world.isRemote) {
            return;
        }

        if (this.getShieldDisabledTick() > 0) {
            this.decrementShieldDisabledTick();
        }

        if (this.spells.isEmpty()) {
            this.configureDefaultLoadout();
        }

        if (this.ownerUUID == null && this.ticksExisted > 5) {
            this.bindToNearestPlayer();
        }

        this.tickPassiveSelfHeal();
        this.tickLootCollection();
        this.tickGuardChestDeposit();
        this.syncOwnerPriorityTarget();
        this.enforceGuardBoundaries();

        if (this.guardRegionDirty && this.getGuardAnchor() != null) {
            this.rebuildGuardPatrolPoints();
        }
    }

    private void bindToNearestPlayer() {
        EntityPlayer nearest = this.world.getClosestPlayerToEntity(this, 24.0D);
        if (nearest != null) {
            this.setOwnerId(nearest.getUniqueID());
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
            float healAmount = this.getElement() == Element.HEALING ? 7.5F : 3.75F;
            this.heal(healAmount);
            this.playSound(Spells.heal.getSounds()[0], 0.7F, this.rand.nextFloat() * 0.4F + 1.0F);
        }

        this.selfHealCooldown = this.getHealth() < 10.0F ? 150 : 400;
    }

    private void tickLootCollection() {
        if (this.ticksExisted % 10 != 0) {
            return;
        }

        for (EntityItem entityItem : this.world.getEntitiesWithinAABB(EntityItem.class,
                this.getEntityBoundingBox().grow(1.25D, 0.75D, 1.25D))) {
            if (entityItem == null || entityItem.isDead || entityItem.cannotPickup()) {
                continue;
            }

            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack remaining = this.storeLootStack(stack.copy());
            if (remaining.isEmpty()) {
                entityItem.setDead();
            } else if (remaining.getCount() != stack.getCount()) {
                entityItem.setItem(remaining);
            }
        }
    }

    private ItemStack storeLootStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();

        for (int i = 0; i < this.lootInventory.size(); i++) {
            ItemStack stored = this.lootInventory.get(i);
            if (stored.isEmpty()) {
                continue;
            }

            if (!ItemStack.areItemsEqual(stored, remaining) || !ItemStack.areItemStackTagsEqual(stored, remaining)) {
                continue;
            }

            int transferable = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
            if (transferable <= 0) {
                continue;
            }

            stored.grow(transferable);
            remaining.shrink(transferable);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        for (int i = 0; i < this.lootInventory.size(); i++) {
            if (this.lootInventory.get(i).isEmpty()) {
                this.lootInventory.set(i, remaining.copy());
                return ItemStack.EMPTY;
            }
        }

        return remaining;
    }

    private void tickGuardChestDeposit() {
        if (this.getCommandMode() != SentinelCommandMode.GUARD || !this.hasLootItems() || this.ticksExisted % 40 != 0) {
            return;
        }

        this.depositLootIntoNearbyChests();
    }

    private boolean hasLootItems() {
        for (ItemStack stored : this.lootInventory) {
            if (!stored.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean depositLootIntoNearbyChests() {
        List<TileEntityChest> candidates = new ArrayList<TileEntityChest>();
        BlockPos center = new BlockPos(this);

        for (int dx = -GUARD_CHEST_SCAN_RADIUS; dx <= GUARD_CHEST_SCAN_RADIUS; dx++) {
            for (int dy = -GUARD_CHEST_SCAN_HEIGHT; dy <= GUARD_CHEST_SCAN_HEIGHT; dy++) {
                for (int dz = -GUARD_CHEST_SCAN_RADIUS; dz <= GUARD_CHEST_SCAN_RADIUS; dz++) {
                    TileEntity tileEntity = this.world.getTileEntity(center.add(dx, dy, dz));
                    if (tileEntity instanceof TileEntityChest && !candidates.contains(tileEntity)) {
                        candidates.add((TileEntityChest) tileEntity);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return false;
        }

        candidates.sort((first, second) -> {
            int scoreDelta = Integer.compare(this.getChestMatchScore(second), this.getChestMatchScore(first));
            if (scoreDelta != 0) {
                return scoreDelta;
            }

            double firstDistance = this.getDistanceSqToCenter(first.getPos());
            double secondDistance = this.getDistanceSqToCenter(second.getPos());
            return Double.compare(firstDistance, secondDistance);
        });

        for (TileEntityChest chest : candidates) {
            this.depositLootToInventory(chest);
            if (!this.hasLootItems()) {
                return true;
            }
        }

        return !this.hasLootItems();
    }

    private int getChestMatchScore(IInventory inventory) {
        int score = 0;

        for (ItemStack stored : this.lootInventory) {
            if (stored.isEmpty()) {
                continue;
            }

            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                ItemStack chestStack = inventory.getStackInSlot(slot);
                if (!chestStack.isEmpty() && ItemStack.areItemsEqual(chestStack, stored)
                        && ItemStack.areItemStackTagsEqual(chestStack, stored)) {
                    score++;
                    break;
                }
            }
        }

        return score;
    }

    private void depositLootToInventory(IInventory inventory) {
        if (inventory == null) {
            return;
        }

        for (int slot = 0; slot < this.lootInventory.size(); slot++) {
            ItemStack stored = this.lootInventory.get(slot);
            if (stored.isEmpty()) {
                continue;
            }

            ItemStack remaining = stored.copy();
            this.addStackToInventory(inventory, remaining);
            this.lootInventory.set(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        }
    }

    private boolean addStackToInventory(IInventory inventory, ItemStack stack) {
        int originalCount = stack.getCount();

        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack existing = inventory.getStackInSlot(slot);
            if (existing.isEmpty() || !ItemStack.areItemsEqual(existing, stack)
                    || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                continue;
            }

            int transferable = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (transferable <= 0) {
                continue;
            }

            existing.grow(transferable);
            stack.shrink(transferable);
            inventory.markDirty();
            if (stack.isEmpty()) {
                return true;
            }
        }

        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty() || !inventory.isItemValidForSlot(slot, stack)) {
                continue;
            }

            inventory.setInventorySlotContents(slot, stack.copy());
            stack.setCount(0);
            inventory.markDirty();
            return true;
        }

        return stack.getCount() < originalCount;
    }

    private void syncOwnerPriorityTarget() {
        EntityLivingBase owner = this.getOwnerEntity();
        if (owner == null || !owner.isEntityAlive()) {
            return;
        }

        EntityLivingBase priority = owner.getLastAttackedEntity();
        if (this.isValidPriorityTarget(priority)) {
            this.setAttackTarget(priority);
            return;
        }

        EntityLivingBase revengeTarget = owner.getRevengeTarget();
        if (this.isValidPriorityTarget(revengeTarget)) {
            this.setAttackTarget(revengeTarget);
        }
    }

    private boolean isValidPriorityTarget(@Nullable EntityLivingBase target) {
        return target != null
                && target.isEntityAlive()
                && target != this
                && target != this.getOwnerEntity()
                && !this.isOnSameTeam(target)
                && this.isAllowedByCurrentCommandMode(target);
    }

    private void enforceGuardBoundaries() {
        if (this.getCommandMode() != SentinelCommandMode.GUARD) {
            return;
        }

        BlockPos anchor = this.getGuardAnchor();
        if (anchor == null) {
            return;
        }

        EntityLivingBase target = this.getAttackTarget();
        if (target != null && target.isEntityAlive()) {
            double maxDistance = (DEFAULT_GUARD_RADIUS + 6) * (DEFAULT_GUARD_RADIUS + 6);
            if (target.getDistanceSqToCenter(anchor) > maxDistance) {
                this.setAttackTarget(null);
            }
        }

        if (this.getDistanceSqToGuardAnchor() > (DEFAULT_GUARD_RADIUS + 8) * (DEFAULT_GUARD_RADIUS + 8)) {
            this.setAttackTarget(null);
            this.getNavigator().tryMoveToXYZ(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.0D);
        }
    }

    public SentinelCommandMode getCommandMode() {
        return SentinelCommandMode.fromId(this.dataManager.get(COMMAND_MODE));
    }

    public void setCommandMode(SentinelCommandMode mode) {
        this.dataManager.set(COMMAND_MODE, mode.getId());

        if (mode == SentinelCommandMode.FOLLOW) {
            this.clearGuardAnchor();
        }
    }

    public void setGuardAnchor(@Nullable BlockPos anchor) {
        if (anchor == null) {
            this.clearGuardAnchor();
            return;
        }

        this.dataManager.set(HAS_GUARD_ANCHOR, Boolean.TRUE);
        this.dataManager.set(GUARD_ANCHOR_X, anchor.getX());
        this.dataManager.set(GUARD_ANCHOR_Y, anchor.getY());
        this.dataManager.set(GUARD_ANCHOR_Z, anchor.getZ());
        this.guardRegionDirty = true;
    }

    public void clearGuardAnchor() {
        this.dataManager.set(HAS_GUARD_ANCHOR, Boolean.FALSE);
        this.guardPatrolPoints.clear();
        this.guardRegionDirty = false;
    }

    @Nullable
    public BlockPos getGuardAnchor() {
        if (!this.dataManager.get(HAS_GUARD_ANCHOR)) {
            return null;
        }

        return new BlockPos(this.dataManager.get(GUARD_ANCHOR_X), this.dataManager.get(GUARD_ANCHOR_Y),
                this.dataManager.get(GUARD_ANCHOR_Z));
    }

    public double getDistanceSqToGuardAnchor() {
        BlockPos anchor = this.getGuardAnchor();
        return anchor == null ? 0.0D : this.getDistanceSqToCenter(anchor);
    }

    public List<BlockPos> getGuardPatrolPoints() {
        return this.guardPatrolPoints;
    }

    private void rebuildGuardPatrolPoints() {
        this.guardPatrolPoints.clear();
        this.guardRegionDirty = false;

        BlockPos anchor = this.findNearestWalkablePos(this.getGuardAnchor());
        if (anchor == null) {
            return;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<BlockPos>();
        Set<BlockPos> visited = new HashSet<BlockPos>();
        queue.add(anchor);
        visited.add(anchor);

        while (!queue.isEmpty() && this.guardPatrolPoints.size() < GUARD_SEARCH_CAP) {
            BlockPos current = queue.poll();
            this.guardPatrolPoints.add(current);

            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos next = this.findConnectedWalkablePos(current, facing);
                if (next == null || visited.contains(next)) {
                    continue;
                }

                if (next.distanceSq(anchor.getX(), anchor.getY(), anchor.getZ()) > (DEFAULT_GUARD_RADIUS + 1)
                        * (DEFAULT_GUARD_RADIUS + 1)) {
                    continue;
                }

                visited.add(next);
                queue.add(next);
            }
        }

        if (this.guardPatrolPoints.isEmpty()) {
            this.guardPatrolPoints.add(anchor);
        }
    }

    @Nullable
    private BlockPos findConnectedWalkablePos(BlockPos current, EnumFacing facing) {
        BlockPos horizontal = current.offset(facing);

        for (int deltaY = 0; deltaY >= -1; deltaY--) {
            BlockPos candidate = horizontal.add(0, deltaY, 0);
            if (this.isWalkableGuardPos(candidate)) {
                return candidate;
            }
        }

        BlockPos upCandidate = horizontal.up();
        return this.isWalkableGuardPos(upCandidate) ? upCandidate : null;
    }

    @Nullable
    private BlockPos findNearestWalkablePos(@Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }

        if (this.isWalkableGuardPos(pos)) {
            return pos;
        }

        for (int deltaY = -1; deltaY <= 1; deltaY++) {
            BlockPos adjusted = pos.add(0, deltaY, 0);
            if (this.isWalkableGuardPos(adjusted)) {
                return adjusted;
            }
        }

        return null;
    }

    private boolean isWalkableGuardPos(BlockPos pos) {
        if (this.isBoundaryBlock(pos) || this.isBoundaryBlock(pos.up())) {
            return false;
        }

        IBlockState feet = this.world.getBlockState(pos);
        IBlockState head = this.world.getBlockState(pos.up());
        IBlockState below = this.world.getBlockState(pos.down());

        return !feet.getMaterial().blocksMovement()
                && !head.getMaterial().blocksMovement()
                && (below.isTopSolid() || below.getMaterial().blocksMovement())
                && !this.isBoundaryBlock(pos.down());
    }

    private boolean isBoundaryBlock(BlockPos pos) {
        Block block = this.world.getBlockState(pos).getBlock();
        return block instanceof BlockDoor || block instanceof BlockFenceGate;
    }

    private boolean isAllowedByCurrentCommandMode(EntityLivingBase target) {
        if (this.getCommandMode() != SentinelCommandMode.GUARD) {
            return true;
        }

        BlockPos anchor = this.getGuardAnchor();
        return anchor == null || target.getDistanceSqToCenter(anchor) <= (DEFAULT_GUARD_RADIUS + 4)
                * (DEFAULT_GUARD_RADIUS + 4);
    }

    private boolean isSupportedTargetType(EntityLivingBase entity) {
        if (entity instanceof IMob || entity instanceof IEntityOwnable) {
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

    public Element getElement() {
        int index = this.dataManager.get(ELEMENT);
        Element[] values = Element.values();
        return index >= 0 && index < values.length ? values[index] : Element.MAGIC;
    }

    public void setElement(Element element) {
        this.dataManager.set(ELEMENT, element.ordinal());
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
    @Nonnull
    public List<Spell> getSpells() {
        return this.spells;
    }

    @Override
    @Nonnull
    public SpellModifiers getModifiers() {
        return new SpellModifiers().set(SpellModifiers.POTENCY, this.spellPotencyMultiplier, true);
    }

    @Override
    @Nonnull
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

    public void setOwnerId(@Nullable UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    @Nullable
    public UUID getOwnerId() {
        return this.ownerUUID;
    }

    @Nullable
    public EntityLivingBase getOwnerEntity() {
        return this.resolveOwner();
    }

    @Nullable
    public EntityLivingBase getOwner() {
        return this.resolveOwner();
    }

    @Nullable
    public EntityLivingBase func_70902_q() {
        return this.resolveOwner();
    }

    @Nullable
    private EntityLivingBase resolveOwner() {
        if (this.ownerUUID == null) {
            return null;
        }

        Entity entity = EntityUtils.getEntityByUUID(this.world, this.ownerUUID);
        if (entity instanceof EntityLivingBase) {
            return (EntityLivingBase) entity;
        }

        return null;
    }

    public boolean canPlayerCommand(EntityPlayer player) {
        return player != null && (this.ownerUUID == null || player.getUniqueID().equals(this.ownerUUID));
    }

    /** Direct access for the loot container GUI (server-side authoritative). */
    public NonNullList<ItemStack> getLootInventoryList() {
        return this.lootInventory;
    }

    public NBTTagList writeLootInventoryToNBT() {
        NBTTagList lootList = new NBTTagList();
        for (int i = 0; i < this.lootInventory.size(); i++) {
            ItemStack stored = this.lootInventory.get(i);
            if (stored.isEmpty()) {
                continue;
            }

            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("Slot", i);
            slotTag.setTag("Stack", stored.writeToNBT(new NBTTagCompound()));
            lootList.appendTag(slotTag);
        }
        return lootList;
    }

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (!this.world.isRemote && this.canPlayerCommand(player)) {
            if (this.ownerUUID == null) {
                this.setOwnerId(player.getUniqueID());
                player.sendStatusMessage(new TextComponentTranslation("entity.insanetweaks.sentinel.bound"), true);
            }
            return true;
        }

        return super.processInteract(player, hand);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        Entity trueSource = source.getTrueSource();
        if (trueSource instanceof EntityLivingBase && this.isOnSameTeam(trueSource)) {
            return false;
        }

        return super.attackEntityFrom(source, amount);
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

    @Override
    public boolean isOnSameTeam(@Nullable Entity entityIn) {
        return this.checkOwnerTeam(entityIn);
    }

    private boolean checkOwnerTeam(@Nullable Entity entityIn) {
        if (entityIn == null) {
            return false;
        }

        EntityLivingBase owner = this.getOwnerEntity();
        if (owner != null) {
            if (entityIn == owner || owner.isOnSameTeam(entityIn)) {
                return true;
            }

            if (entityIn instanceof IEntityOwnable) {
                Entity otherOwner = ((IEntityOwnable) entityIn).getOwner();
                if (otherOwner != null && (otherOwner == owner || owner.isOnSameTeam(otherOwner))) {
                    return true;
                }
            }
        }

        return super.isOnSameTeam(entityIn);
    }

    public boolean hasRangedAttack() {
        return true;
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
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("SentinelElement", this.getElement().ordinal());
        compound.setInteger("SentinelTexture", this.getTextureIndex());
        compound.setInteger("SentinelMode", this.getCommandMode().getId());
        compound.setFloat("SentinelPotency", this.spellPotencyMultiplier);
        compound.setInteger("SentinelCooldown", this.cooldown);
        compound.setInteger("SentinelSelfHealCooldown", this.selfHealCooldown);
        compound.setInteger("SentinelShieldDisabled", this.getShieldDisabledTick());
        if (this.ownerUUID != null) {
            compound.setUniqueId("SentinelOwner", this.ownerUUID);
        }
        BlockPos anchor = this.getGuardAnchor();
        if (anchor != null) {
            compound.setInteger("SentinelAnchorX", anchor.getX());
            compound.setInteger("SentinelAnchorY", anchor.getY());
            compound.setInteger("SentinelAnchorZ", anchor.getZ());
        }

        NBTTagList spellList = new NBTTagList();
        for (Spell spell : this.spells) {
            if (spell != null && spell.getRegistryName() != null) {
                spellList.appendTag(new NBTTagString(spell.getRegistryName().toString()));
            }
        }
        compound.setTag("SentinelSpells", spellList);

        compound.setTag("SentinelLootInventory", this.writeLootInventoryToNBT());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("SentinelElement")) {
            int elementIndex = compound.getInteger("SentinelElement");
            Element[] elements = Element.values();
            this.setElement(elementIndex >= 0 && elementIndex < elements.length ? elements[elementIndex] : Element.MAGIC);
        }
        if (compound.hasKey("SentinelTexture")) {
            this.setTextureIndex(compound.getInteger("SentinelTexture"));
        }
        this.setCommandMode(SentinelCommandMode.fromId(compound.getInteger("SentinelMode")));
        this.spellPotencyMultiplier = compound.hasKey("SentinelPotency")
                ? Math.max(0.2F, compound.getFloat("SentinelPotency"))
                : 1.8F;
        this.cooldown = compound.hasKey("SentinelCooldown") ? compound.getInteger("SentinelCooldown") : -1;
        this.selfHealCooldown = compound.hasKey("SentinelSelfHealCooldown")
                ? compound.getInteger("SentinelSelfHealCooldown")
                : 200;
        this.setShieldDisabledTick(compound.hasKey("SentinelShieldDisabled")
                ? compound.getInteger("SentinelShieldDisabled")
                : 0);

        if (compound.hasUniqueId("SentinelOwner")) {
            this.ownerUUID = compound.getUniqueId("SentinelOwner");
        } else {
            this.ownerUUID = null;
        }

        if (compound.hasKey("SentinelAnchorX")) {
            this.setGuardAnchor(new BlockPos(compound.getInteger("SentinelAnchorX"), compound.getInteger("SentinelAnchorY"),
                    compound.getInteger("SentinelAnchorZ")));
        } else {
            this.clearGuardAnchor();
        }

        this.spells.clear();
        if (compound.hasKey("SentinelSpells", 9)) {
            NBTTagList spellList = compound.getTagList("SentinelSpells", 8);
            for (int i = 0; i < spellList.tagCount(); i++) {
                Spell spell = Spell.get(spellList.getStringTagAt(i));
                if (spell != Spells.none) {
                    this.spells.add(spell);
                }
            }
        }

        for (int i = 0; i < this.lootInventory.size(); i++) {
            this.lootInventory.set(i, ItemStack.EMPTY);
        }
        if (compound.hasKey("SentinelLootInventory", 9)) {
            NBTTagList lootList = compound.getTagList("SentinelLootInventory", 10);
            for (int i = 0; i < lootList.tagCount(); i++) {
                NBTTagCompound slotTag = lootList.getCompoundTagAt(i);
                int slot = slotTag.getInteger("Slot");
                if (slot < 0 || slot >= this.lootInventory.size()) {
                    continue;
                }

                this.lootInventory.set(slot, new ItemStack(slotTag.getCompoundTag("Stack")));
            }
        }
    }
}
