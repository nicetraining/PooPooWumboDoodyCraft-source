package com.fartypoopy.sexmod.entity;

import com.fartypoopy.sexmod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The Pyrocinical (original {@code al}): a rare Nether ambient wanderer drawn as a flat sprite. Nothing can hurt
 * it; hitting it only makes it stand still, grow fat and vanish 6.25 seconds later.
 */
public class PyrocinicalEntity extends Mob {
    /** Original: removed 6250 ms (125 ticks) after the first hit. */
    private static final int VANISH_TICKS = 125;
    private static final EntityDataAccessor<Integer> DATA_HIT_TICK =
            SynchedEntityData.defineId(PyrocinicalEntity.class, EntityDataSerializers.INT);

    private BlockPos waypoint;
    private int waypointAge;
    private int vanishAt = -1;

    public PyrocinicalEntity(EntityType<? extends PyrocinicalEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.7D);
    }

    /** Original getCanSpawnHere: nextInt(100) < 1 && nextInt(100) < 10. */
    public static boolean checkSpawn(EntityType<PyrocinicalEntity> type, net.minecraft.world.level.ServerLevelAccessor level,
                                     net.minecraft.world.entity.MobSpawnType spawnType, BlockPos pos,
                                     net.minecraft.util.RandomSource random) {
        return random.nextInt(100) < 1 && random.nextInt(100) < 10;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HIT_TICK, -1);
    }

    /** Client only: the local player's tick count when they hit her (original al.a), or -1. */
    public int localHitTick = -1;

    /** Entity age at which a player hit it, or -1. */
    public int hitTick() {
        return this.entityData.get(DATA_HIT_TICK);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) { // 1.12 /kill was void damage
            this.discard();
            return true;
        }
        if (!(source.getEntity() instanceof Player player)) {
            return false;
        }
        if (this.level().isClientSide) {
            // original al.b(): only the hitting player's client starts the fat animation, timed by that player's ticks,
            // and plays the sound for itself; other players only see her vanish
            this.localHitTick = player.tickCount;
            ModSounds.find("misc.weoweo.weoweo3").ifPresent(sound -> player.playSound(sound.get(), 1.0F, 1.0F));
        } else {
            this.entityData.set(DATA_HIT_TICK, this.tickCount);
            if (this.vanishAt < 0) {
                this.vanishAt = this.tickCount + VANISH_TICKS;
            }
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return super.isPushable();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.vanishAt >= 0 && this.tickCount >= this.vanishAt) {
            this.discard();
            return;
        }
        if (this.hitTick() != -1) {
            this.getNavigation().stop();
            return;
        }
        Player player = this.level().getNearestPlayer(this, 15.0D);
        if (player != null && player.distanceTo(this) < 3.0F) {
            this.getNavigation().stop();
            return;
        }
        if (this.waypoint == null
                || this.distanceToSqr(this.waypoint.getX(), this.waypoint.getY(), this.waypoint.getZ()) > 1800.0D
                || this.waypointAge > 175) {
            int dx = (this.random.nextBoolean() ? 1 : -1) * this.random.nextInt(10);
            int dz = (this.random.nextBoolean() ? 1 : -1) * this.random.nextInt(10);
            int y = this.level().dimensionType().hasCeiling()
                    ? (int) Math.ceil(this.getY())
                    : this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, this.getBlockX() + dx, this.getBlockZ() + dz);
            this.waypoint = new BlockPos(this.getBlockX() + dx, y, this.getBlockZ() + dz);
            this.waypointAge = 0;
        }
        if (Math.sqrt(this.waypoint.distSqr(this.blockPosition())) > 2.0D) {
            this.getNavigation().moveTo(this.waypoint.getX(), this.waypoint.getY(), this.waypoint.getZ(), 0.35D);
            this.airborneNudge();
        } else {
            this.waypointAge++;
        }
    }

    /** Original d(): while airborne on a path she is pushed along it (one seventh of the next step per tick). */
    private void airborneNudge() {
        Path path = this.getNavigation().getPath();
        if (path == null || this.onGround() || this.isInWater()) {
            return;
        }
        int index = path.getNextNodeIndex();
        int count = path.getNodeCount();
        if (count == index || count - 1 == index) {
            return;
        }
        Node current = path.getNode(index);
        Node next = path.getNode(index + 1);
        Vec3 step = new Vec3(next.x - current.x, next.y - current.y, next.z - current.z);
        this.setDeltaMovement(step.x / 7.0D, this.getDeltaMovement().y, step.z / 7.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
