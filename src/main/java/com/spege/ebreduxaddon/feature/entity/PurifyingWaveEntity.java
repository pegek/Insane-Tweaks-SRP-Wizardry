package com.spege.ebreduxaddon.feature.entity;

import com.binaris.wizardry.api.client.ParticleBuilder;
import com.binaris.wizardry.api.content.util.MagicDamageSource;
import com.binaris.wizardry.core.AllyDesignation;
import com.binaris.wizardry.setup.registries.EBDamageSources;
import com.binaris.wizardry.setup.registries.client.EBParticles;
import com.spege.ebreduxaddon.feature.ModEntities;
import com.spege.ebreduxaddon.platform.Config;
import com.spege.ebreduxaddon.platform.IdLists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fala Purifying Pulse, z 1.12.2 (EntityPurifyingWave): pierscien rosnacy od srodka do promienia
 * maksymalnego przez {@link #DURATION} tickow. Kazda encja i kazdy blok jest obslugiwany raz,
 * w ticku, w ktorym pierscien przez niego przechodzi.
 *
 * <p>Bez modelu (NoopRenderer); po stronie klienta rysuje tylko czasteczki na krawedzi. Nie jest
 * zapisywana do swiata: fala przerwana wyladowaniem chunka po prostu znika.
 */
public class PurifyingWaveEntity extends Entity {

    public static final int DURATION = 20;
    /** Sufit zamian blokow na tick: duzy promien z duzym potency nie moze zaciac serwera. */
    static final int MAX_BLOCKS_PER_TICK = 512;

    public static final TagKey<EntityType<?>> FUNGUS_ENTITIES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("spore", "fungus_entities"));

    private static final EntityDataAccessor<Float> MAX_RADIUS =
            SynchedEntityData.defineId(PurifyingWaveEntity.class, EntityDataSerializers.FLOAT);

    private int verticalRange = 4;
    private float heal;
    private float fungalDamage;
    private float knockback;
    private UUID ownerId;
    private final Set<UUID> touched = new HashSet<>();

    public PurifyingWaveEntity(EntityType<? extends PurifyingWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public PurifyingWaveEntity(Level level) {
        this(ModEntities.PURIFYING_WAVE.get(), level);
    }

    public void configure(LivingEntity owner, float radius, int verticalRange, float heal, float fungalDamage, float knockback) {
        this.ownerId = owner == null ? null : owner.getUUID();
        this.entityData.set(MAX_RADIUS, radius);
        this.verticalRange = verticalRange;
        this.heal = heal;
        this.fungalDamage = fungalDamage;
        this.knockback = knockback;
    }

    public float maxRadius() {
        return entityData.get(MAX_RADIUS);
    }

    private float radiusAt(int age) {
        return maxRadius() * Math.min(1f, age / (float) DURATION);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MAX_RADIUS, 8f);
    }

    @Override
    public void tick() {
        super.tick();
        float inner = radiusAt(tickCount - 1);
        float outer = radiusAt(tickCount);
        if (level().isClientSide) {
            ringParticles(outer);
        } else {
            sweepEntities(inner, outer);
            sweepBlocks(inner, outer);
        }
        if (tickCount >= DURATION) {
            discard();
        }
    }

    private LivingEntity owner() {
        if (ownerId == null || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return null;
        }
        return server.getEntity(ownerId) instanceof LivingEntity living ? living : null;
    }

    private void sweepEntities(float inner, float outer) {
        LivingEntity owner = owner();
        AABB box = new AABB(position(), position()).inflate(outer, verticalRange, outer);
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double d = horizontalDistance(entity.position());
            if (d > outer || (d < inner && tickCount > 1) || !touched.add(entity.getUUID())) {
                continue;
            }
            boolean fungal = Config.INSTANCE.sporeEnabled.get() && entity.getType().is(FUNGUS_ENTITIES);
            if (fungal) {
                entity.hurt(owner != null ? MagicDamageSource.causeDirectMagicDamage(owner, EBDamageSources.RADIANT)
                        : entity.damageSources().magic(), fungalDamage);
            }
            if (isAlly(owner, entity) && !fungal) {
                if (heal > 0 && entity.getHealth() < entity.getMaxHealth()) {
                    entity.heal(heal);
                }
            } else if ((fungal || entity instanceof Enemy) && knockback > 0) {
                Vec3 away = entity.position().subtract(position()).multiply(1, 0, 1);
                Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(knockback);
                entity.push(push.x, 0.25, push.z);
                entity.hurtMarked = true;
            }
        }
    }

    /**
     * Sojusznik w rozumieniu Redux: sam rzucajacy, gracze z jego listy sojusznikow, jego przywolancy,
     * a moby pasywne tylko przy passive_mobs_are_allies = true (domyslnie false). Moby, ktore nie sa
     * ani sojusznikami, ani wrogami (Enemy), fala zostawia w spokoju.
     */
    private static boolean isAlly(LivingEntity owner, LivingEntity entity) {
        return owner != null && (entity == owner || !AllyDesignation.isValidTarget(owner, entity));
    }

    private void sweepBlocks(float inner, float outer) {
        Map<Block, Block> map = IdLists.purifiedBlocks();
        if (map.isEmpty()) {
            return;
        }
        BlockPos centre = blockPosition();
        int r = (int) Math.ceil(outer);
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                // Pierwszy tick obejmuje tez srodek; potem tylko nowy pas pierscienia.
                if (d > outer || (d <= inner && tickCount > 1)) {
                    continue;
                }
                for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                    pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState state = level().getBlockState(pos);
                    Block target = map.get(state.getBlock());
                    if (target == null) {
                        continue;
                    }
                    level().setBlock(pos, target.defaultBlockState(), Block.UPDATE_ALL);
                    if (++changed >= MAX_BLOCKS_PER_TICK) {
                        return;
                    }
                }
            }
        }
    }

    private double horizontalDistance(Vec3 p) {
        double dx = p.x - getX();
        double dz = p.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void ringParticles(float radius) {
        int count = Math.max(8, (int) (radius * 6));
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count + random.nextDouble() * 0.1;
            ParticleBuilder.create(EBParticles.SPARKLE)
                    .pos(getX() + Math.cos(a) * radius, getY() + 0.2, getZ() + Math.sin(a) * radius)
                    .time(10 + random.nextInt(6)).color(1.0f, 0.95f, 0.6f).spawn(level());
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
