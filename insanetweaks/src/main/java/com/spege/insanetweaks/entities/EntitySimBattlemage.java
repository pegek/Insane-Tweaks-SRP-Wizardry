package com.spege.insanetweaks.entities;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.init.ModItems;
import com.windanesz.ancientspellcraft.entity.ai.EntityAIBlockWithShield;
import com.windanesz.ancientspellcraft.entity.ai.IShieldUser;
import com.windanesz.ancientspellcraft.entity.living.ICustomCooldown;

import net.minecraft.entity.IEntityLivingData;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;

/**
 * Assimilated Battlemage - the Ancient Spellcraft counterpart of {@link EntitySimWizard}. Produced
 * when SRP assimilates an {@code ancientspellcraft:class_wizard} or {@code evil_class_wizard},
 * which until now fell back to a plain {@code srparasites:sim_human}.
 *
 * <h3>Why a subclass rather than a sibling class</h3>
 * The design spec called for a separate entity because a battlemage needs its own model (ASC's
 * biped {@code ModelClassWizard}, not SRP's non-biped {@code ModelInfHuman}), its own equipment
 * stack (offhand shield, {@link IShieldUser}, {@link ICustomCooldown}), and because
 * {@code initEntityAI()} runs from the {@code EntityLiving} constructor - before NBT is read - so
 * a mere tier flag on one class could never drive a different task list.
 *
 * <p>Subclassing satisfies all three: a distinct class means a distinct renderer registration and
 * a distinct AI composition, while the tier, cast pipeline, spell roles, panic reaction and ally
 * support are inherited verbatim. Crucially it touches ZERO lines of
 * {@code EntityAISimWizardCombat} - the 600-line file that stands between this feature and the
 * long-running "rarely casts" class of bugs. The alternative (generifying that task over an
 * {@code ISimCaster} interface) would have bought nothing here and risked exactly that.
 *
 * <p>Balance: the battlemage inherits the assimilated_wizard tuning by design. Its extra threat
 * comes from the {@link SimWizardTier#ADEPT} floor applied on spawn plus the shield, not from a
 * parallel set of multipliers nobody would keep in sync.
 */
@SuppressWarnings("null")
public class EntitySimBattlemage extends EntitySimWizard implements ICustomCooldown, IShieldUser {

    /**
     * Ticks the shield stays disabled after being broken through.
     *
     * <p>🚨 Registered with THIS class, so its id continues after the parent's parameters rather
     * than colliding with them. Append-only here as well.
     */
    private static final DataParameter<Integer> SHIELD_DISABLED_TICK = EntityDataManager.createKey(
            EntitySimBattlemage.class, DataSerializers.VARINT);

    /**
     * Assigned from {@link #initEntityAI()}, which the superclass constructor calls. Safe only
     * because this field has no initialiser - a subclass field initialiser would run AFTER the
     * super constructor and blank it again.
     */
    private EntityAIBlockWithShield<EntitySimBattlemage> shieldAI;

    private int cooldown;

    public EntitySimBattlemage(World worldIn) {
        super(worldIn);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(SHIELD_DISABLED_TICK, 0);
    }

    /**
     * Inherits the whole sim_wizard AI and adds shield blocking.
     *
     * <p>{@code EntityAIBlockWithShield} never calls {@code setMutexBits}, so its mutex is 0 -
     * verified by disassembling its constructor. It therefore neither blocks nor is blocked by the
     * combat task at priority 3, and its own priority is free of the usual "a higher-numbered task
     * can never preempt a running one" trap.
     */
    @Override
    protected void initEntityAI() {
        super.initEntityAI();
        this.shieldAI = new EntityAIBlockWithShield<EntitySimBattlemage>(this);
        this.tasks.addTask(2, this.shieldAI);
    }

    /**
     * A battlemage carries the mod's own blade, not a wand.
     *
     * <p>This is the hook the parent's per-tick {@code ensureVisualWand} consults, so overriding it
     * here is what actually sticks - equipping the main hand directly would be undone on the very
     * next tick, and the mismatch would also re-equip the slot 20 times a second.
     *
     * <p>Purely cosmetic, like the parent's wand: EBW's NPC cast path calls
     * {@code spell.cast(World, EntityLiving, ...)} and never touches the held item, and
     * {@code BaseCustomWandItem.calculateModifiers} is player-only, so no held item can leak a
     * bonus into the cast (the v2.1 double-buff lesson).
     */
    @Override
    protected net.minecraft.item.Item tierWandItem() {
        return ModItems.LIVING_SPELLBLADE;
    }

    @Override
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, IEntityLivingData livingdata) {
        IEntityLivingData data = super.onInitialSpawn(difficulty, livingdata);
        // An ASC class wizard is never a novice. setTierFloor only ever raises, and re-applies the
        // attribute scaling from pristine base values, so this cannot compound.
        this.setTierFloor(SimWizardTier.ADEPT);
        this.equipShield();
        return data;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!this.world.isRemote) {
            if (this.getShieldDisabledTick() > 0) {
                this.setShieldDisabledTick(this.getShieldDisabledTick() - 1);
            }
            this.ensureShield();
        }
    }

    private void ensureShield() {
        ItemStack offHand = this.getHeldItemOffhand();
        if (offHand.isEmpty() || offHand.getItem() != ModItems.LIVING_AEGIS) {
            this.equipShield();
        }
    }

    private void equipShield() {
        ItemStack shield = new ItemStack(ModItems.LIVING_AEGIS);
        this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, shield);
        // Never drop it - the same rule the visual wand follows.
        this.setDropChance(EntityEquipmentSlot.OFFHAND, 0.0F);
    }

    // ------------------------------------------------------------------------
    // IShieldUser
    // ------------------------------------------------------------------------

    @Override
    public int getShieldDisabledTick() {
        return this.dataManager.get(SHIELD_DISABLED_TICK);
    }

    public void setShieldDisabledTick(int count) {
        this.dataManager.set(SHIELD_DISABLED_TICK, Math.max(0, count));
    }

    // ------------------------------------------------------------------------
    // ICustomCooldown
    // ------------------------------------------------------------------------

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
        return ++this.cooldown;
    }

    @Override
    public int decrementCooldown() {
        return --this.cooldown;
    }

    // ------------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------------

    @Override
    public void writeEntityToNBT(@Nonnull NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("BattlemageShieldDisabled", this.getShieldDisabledTick());
    }

    @Override
    public void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.setShieldDisabledTick(compound.hasKey("BattlemageShieldDisabled")
                ? compound.getInteger("BattlemageShieldDisabled")
                : 0);
    }
}
