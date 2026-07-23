package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.SentinelCommandMode;

import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateFlying;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityAISentinelFollowOwner extends EntityAIBase {

    private final EntitySentinel sentinel;
    private final EntityCreature creature;
    private final World world;
    private final double followSpeed;
    private final PathNavigate navigator;
    private final float minDist;
    private final float maxDist;
    private int timeToRecalcPath;
    private float oldWaterCost;
    private EntityLivingBase owner;

    public EntityAISentinelFollowOwner(EntitySentinel sentinel, double followSpeed, float minDist, float maxDist) {
        this.sentinel = sentinel;
        this.creature = sentinel;
        this.world = sentinel.world;
        this.followSpeed = followSpeed;
        this.navigator = sentinel.getNavigator();
        this.minDist = minDist;
        this.maxDist = maxDist;
        this.setMutexBits(3);

        if (!(this.navigator instanceof PathNavigateGround) && !(this.navigator instanceof PathNavigateFlying)) {
            throw new IllegalArgumentException("Unsupported mob type for Sentinel follow AI");
        }
    }

    @Override
    public boolean shouldExecute() {
        if (this.sentinel.getCommandMode() != SentinelCommandMode.FOLLOW) {
            return false;
        }

        EntityLivingBase ownerCandidate = this.sentinel.getOwnerEntity();
        if (ownerCandidate == null) {
            return false;
        }

        if (ownerCandidate instanceof EntityPlayer && ((EntityPlayer) ownerCandidate).isSpectator()) {
            return false;
        }

        if (this.sentinel.getAttackTarget() != null) {
            return false;
        }

        if (this.creature.getDistanceSq(ownerCandidate) < (double) (this.minDist * this.minDist)) {
            return false;
        }

        this.owner = ownerCandidate;
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.sentinel.getCommandMode() == SentinelCommandMode.FOLLOW
                && this.owner != null
                && !this.navigator.noPath()
                && this.creature.getDistanceSq(this.owner) > (double) (this.maxDist * this.maxDist)
                && this.sentinel.getAttackTarget() == null;
    }

    @Override
    public void startExecuting() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.creature.getPathPriority(PathNodeType.WATER);
        this.creature.setPathPriority(PathNodeType.WATER, 0.0F);
    }

    @Override
    public void resetTask() {
        this.owner = null;
        this.navigator.clearPath();
        this.creature.setPathPriority(PathNodeType.WATER, this.oldWaterCost);
    }

    @Override
    public void updateTask() {
        if (this.owner == null) {
            return;
        }

        this.creature.getLookHelper().setLookPositionWithEntity(this.owner, 10.0F,
                (float) this.creature.getVerticalFaceSpeed());

        if (--this.timeToRecalcPath > 0) {
            return;
        }

        this.timeToRecalcPath = 10;

        if (!this.navigator.tryMoveToEntityLiving(this.owner, this.followSpeed)
                && !this.creature.getLeashed()
                && !this.creature.isRiding()
                && this.creature.getDistanceSq(this.owner) >= 144.0D) {
            this.tryTeleportNearOwner();
        }
    }

    private void tryTeleportNearOwner() {
        int baseX = MathHelper.floor(this.owner.posX) - 2;
        int baseZ = MathHelper.floor(this.owner.posZ) - 2;
        int baseY = MathHelper.floor(this.owner.getEntityBoundingBox().minY);

        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if ((x >= 1 && x <= 3) && (z >= 1 && z <= 3)) {
                    continue;
                }

                if (!this.isTeleportFriendlyBlock(baseX, baseZ, baseY, x, z)) {
                    continue;
                }

                this.creature.setLocationAndAngles(baseX + x + 0.5D, baseY, baseZ + z + 0.5D,
                        this.creature.rotationYaw, this.creature.rotationPitch);
                this.navigator.clearPath();
                return;
            }
        }
    }

    private boolean isTeleportFriendlyBlock(int x, int z, int y, int xOffset, int zOffset) {
        BlockPos blockPos = new BlockPos(x + xOffset, y - 1, z + zOffset);
        IBlockState state = this.world.getBlockState(blockPos);
        return state.getBlockFaceShape((IBlockAccess) this.world, blockPos, EnumFacing.DOWN) == BlockFaceShape.SOLID
                && state.canEntitySpawn(this.creature)
                && this.world.isAirBlock(blockPos.up())
                && this.world.isAirBlock(blockPos.up(2));
    }
}
