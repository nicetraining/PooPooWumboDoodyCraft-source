package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * The slime girl's baby (original {@code ay}): a harmless vanilla-looking slime that hops about slowly, never
 * despawns and, after 7 minutes, turns into a new slime girl.
 */
public class FriendlySlimeEntity extends Slime {
    /** Original ay.b: 8400 ticks until she is grown. */
    public static final int GROW_AGE = 8400;

    private static final EntityDataAccessor<Integer> DATA_AGE =
            SynchedEntityData.defineId(FriendlySlimeEntity.class, EntityDataSerializers.INT);

    public FriendlySlimeEntity(EntityType<? extends Slime> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AGE, 0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Only floating and random hops: no attacking, no targets.
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.removeAllGoals(goal -> goal.getClass().getSimpleName().contains("Attack"));
    }

    @Override
    protected boolean isDealsDamage() {
        return false;
    }

    /** Original random hop every 50 to 150 ticks. */
    @Override
    protected int getJumpDelay() {
        return 50 + this.random.nextInt(101);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    public int ageInTicks() {
        return this.entityData.get(DATA_AGE);
    }

    @Override
    public void tick() {
        super.tick();
        int age = this.ageInTicks() + 1;
        if (this.level() instanceof ServerLevel serverLevel) {
            this.entityData.set(DATA_AGE, age);
            if (age > GROW_AGE) {
                CompanionEntity adult = ModEntities.get(CompanionKind.SLIME).create(serverLevel);
                if (adult != null) {
                    adult.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                    serverLevel.addFreshEntity(adult);
                    serverLevel.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    this.discard();
                }
            }
        } else if (age > GROW_AGE * 0.95D) {
            this.level().addParticle(ParticleTypes.CLOUD, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
        } else if (age > GROW_AGE * 0.7D && this.tickCount % 10 == 0) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ageInTicks", this.ageInTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_AGE, tag.getInt("ageInTicks"));
    }
}
