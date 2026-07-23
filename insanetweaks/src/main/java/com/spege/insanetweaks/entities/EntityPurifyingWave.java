package com.spege.insanetweaks.entities;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.spege.insanetweaks.baubles.ItemRestorationHourglassArtefact;
import com.spege.insanetweaks.util.SpellCastFeedback;
import com.spege.insanetweaks.util.SrpPurificationHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

@SuppressWarnings("null")
public class EntityPurifyingWave extends EntityAreaEffectCloud {

    private static final int BASE_DURATION = 18;
    private static final int BECKON_GLOW_DURATION = 120;
    private static final int BECKON_DEBUFF_DURATION = 100;
    private static final float BECKON_DAMAGE = 6.0F;
    private static final int SEAR_DEBUFF_DURATION = 80;
    private static final float SEAR_DAMAGE = 2.0F;
    private static final int SEAR_RGB = 0xFFC83C;
    private static final int CLEANSE_RGB = 0xFFF0BE;

    private double maxWaveRadius = 8.0D;
    private int verticalRange = 4;
    private double processedRadius = 0.0D;
    private final Set<Integer> affectedEntities = new HashSet<Integer>();

    public EntityPurifyingWave(World world) {
        super(world);
        this.setupDefaults();
    }

    public EntityPurifyingWave(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.setupDefaults();
    }

    private void setupDefaults() {
        this.setWaitTime(0);
        this.setDuration(BASE_DURATION);
        this.setRadius(0.5F);
        this.setParticle(EnumParticleTypes.SPELL_INSTANT);
        this.setColor(0xE7F7B3);
    }

    public void configureWave(double radius, int verticalRange) {
        this.maxWaveRadius = Math.max(1.0D, radius);
        this.verticalRange = Math.max(1, verticalRange);
        this.processedRadius = 0.0D;
        this.setRadius(0.5F);
        this.setDuration(BASE_DURATION);
        this.setRadiusPerTick((float) ((this.maxWaveRadius - 0.5D) / (double) BASE_DURATION));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        double currentRadius = Math.min(this.maxWaveRadius, Math.max(0.5D, this.getRadius()));

        if (this.world.isRemote) {
            this.spawnWaveParticles(currentRadius);
            return;
        }

        this.purifyRing(this.processedRadius, currentRadius);
        this.affectParasites(currentRadius);
        ItemRestorationHourglassArtefact.tryRestoreInRange(
                this.world, this.posX, this.posY, this.posZ, currentRadius, this.getOwner());
        this.processedRadius = currentRadius;
    }

    private void purifyRing(double previousRadius, double currentRadius) {
        double previousSq = previousRadius * previousRadius;
        double currentSq = currentRadius * currentRadius;
        int range = (int) Math.ceil(currentRadius);
        int minY = (int) Math.floor(this.posY) - this.verticalRange;
        int maxY = (int) Math.ceil(this.posY) + this.verticalRange;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                double dx = x + 0.5D;
                double dz = z + 0.5D;
                double horizontalSq = dx * dx + dz * dz;

                if (horizontalSq > currentSq || horizontalSq < previousSq) {
                    continue;
                }

                for (int y = minY; y <= maxY; y++) {
                    mutablePos.setPos((int) Math.floor(this.posX) + x, y, (int) Math.floor(this.posZ) + z);
                    IBlockState state = this.world.getBlockState(mutablePos);
                    if (!SrpPurificationHelper.isSrpInfested(state)) {
                        continue;
                    }

                    IBlockState purified = SrpPurificationHelper.getPurifiedState(state);
                    if (purified == null || !this.world.setBlockState(mutablePos, purified, 3)) {
                        continue;
                    }

                    this.spawnPurifyBurst(mutablePos);
                }
            }
        }
    }

    private void affectParasites(double currentRadius) {
        AxisAlignedBB area = new AxisAlignedBB(this.posX, this.posY, this.posZ, this.posX + 1.0D, this.posY + 1.0D,
                this.posZ + 1.0D).grow(currentRadius, this.verticalRange + 2, currentRadius);
        List<EntityLivingBase> entities = this.world.getEntitiesWithinAABB(EntityLivingBase.class, area);

        for (EntityLivingBase entity : entities) {
            if (entity == null || !entity.isEntityAlive()
                    || !this.affectedEntities.add(Integer.valueOf(entity.getEntityId()))) {
                continue;
            }

            double dx = entity.posX - this.posX;
            double dz = entity.posZ - this.posZ;
            if (dx * dx + dz * dz > currentRadius * currentRadius) {
                continue;
            }

            if (SrpPurificationHelper.isBeckon(entity)) {
                this.searBeckon(entity);
            } else if (entity instanceof EntityParasiteBase) {
                this.searParasite(entity);
            } else {
                this.cleanseCothInfection(entity);
            }
        }
    }

    private void searBeckon(EntityLivingBase entity) {
        EntityLivingBase owner = this.getOwner();
        entity.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, owner == null ? this : owner),
                BECKON_DAMAGE);
        entity.addPotionEffect(new PotionEffect(MobEffects.GLOWING, BECKON_GLOW_DURATION, 0, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, BECKON_DEBUFF_DURATION, 1, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, BECKON_DEBUFF_DURATION, 1, false, true));

        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.CRIT_MAGIC, entity.posX,
                    entity.posY + entity.height * 0.5D, entity.posZ, 12, entity.width * 0.35D,
                    entity.height * 0.25D, entity.width * 0.35D, 0.05D);
        }
    }

    private void searParasite(EntityLivingBase parasite) {
        EntityLivingBase owner = this.getOwner();
        parasite.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, owner == null ? this : owner),
                SEAR_DAMAGE);
        parasite.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, SEAR_DEBUFF_DURATION, 0, false, true));
        parasite.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, SEAR_DEBUFF_DURATION, 0, false, true));
        SpellCastFeedback.srpBurstAt(this.world, parasite, 0.5D, SRPEnumParticle.FLASH, SEAR_RGB, 2, 0.3F, 0.3F,
                0.01F);
    }

    private void cleanseCothInfection(EntityLivingBase entity) {
        boolean hadPotion = entity.isPotionActive(SRPPotions.COTH_E);
        NBTTagCompound tags = entity.getEntityData();
        // srpcothimmunity > 0 = tracked COTH victim (SRP 1.10.7). Value 0 means
        // immune (e.g. an Immune Bond target) — those are deliberately skipped.
        boolean hadTag = tags.getInteger("srpcothimmunity") > 0;
        if (!hadPotion && !hadTag) {
            return;
        }

        if (hadPotion) {
            entity.removePotionEffect(SRPPotions.COTH_E);
        }
        if (hadTag) {
            // Remove rather than zero: cured, but re-infectable later.
            tags.removeTag("srpcothimmunity");
        }

        SpellCastFeedback.srpBurstAt(this.world, entity, 0.5D, SRPEnumParticle.DOT, CLEANSE_RGB, 8, 0.4F, 0.5F,
                0.02F);
        this.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.6F, 0.8F);
    }

    private void spawnPurifyBurst(BlockPos pos) {
        if (!(this.world instanceof WorldServer)) {
            return;
        }

        WorldServer worldServer = (WorldServer) this.world;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.7D;
        double z = pos.getZ() + 0.5D;
        worldServer.spawnParticle(EnumParticleTypes.SPELL_INSTANT, x, y, z, 7, 0.25D, 0.2D, 0.25D, 0.01D);
        worldServer.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, x, y, z, 5, 0.2D, 0.25D, 0.2D, 0.02D);
    }

    private void spawnWaveParticles(double currentRadius) {
        int count = 8 + Math.max(4, (int) Math.round(currentRadius * 2.0D));

        for (int i = 0; i < count; i++) {
            double angle = this.rand.nextDouble() * Math.PI * 2.0D;
            double distance = this.rand.nextDouble() * currentRadius;
            double x = this.posX + Math.cos(angle) * distance;
            double y = this.posY + 0.1D + this.rand.nextDouble() * 0.35D;
            double z = this.posZ + Math.sin(angle) * distance;

            this.world.spawnParticle(EnumParticleTypes.SPELL_INSTANT, x, y, z, 0.0D, 0.0D, 0.0D);
            if (this.rand.nextBoolean()) {
                this.world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, x, y, z, 0.0D, 0.02D, 0.0D);
            }
        }
    }

    @Override
    protected void entityInit() {
        super.entityInit();
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.maxWaveRadius = compound.getDouble("MaxWaveRadius");
        this.verticalRange = compound.getInteger("VerticalRange");
        this.processedRadius = compound.getDouble("ProcessedRadius");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setDouble("MaxWaveRadius", this.maxWaveRadius);
        compound.setInteger("VerticalRange", this.verticalRange);
        compound.setDouble("ProcessedRadius", this.processedRadius);
    }
}
