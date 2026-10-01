package com.trolmastercard.sexmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

/** Original bb "energyBallHitBox": a 0.75 block box at one of her two orbs; hitting it (by a player) destroys the orb. */
public final class GalathPart extends PartEntity<CompanionEntity> {
    public final boolean left;
    private final EntityDimensions size = EntityDimensions.scalable(0.75F, 0.75F);
    private boolean active;

    public GalathPart(CompanionEntity parent, boolean left) {
        super(parent);
        this.left = left;
        this.refreshDimensions();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.size;
    }

    @Override
    public boolean isPickable() {
        return this.active;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || !this.active || this.getParent().boss() == null) {
            return false;
        }
        return this.getParent().boss().partHit(this, source);
    }

    @Override
    public boolean is(net.minecraft.world.entity.Entity entity) {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
