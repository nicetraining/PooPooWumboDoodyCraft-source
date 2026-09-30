package com.trolmastercard.sexmod.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** A non-griefing replacement for the vanilla small fireball fallback. */
public final class EnergyBallEntity extends SmallFireball {
    private static final float DAMAGE = 4.0F;

    public EnergyBallEntity(EntityType<? extends EnergyBallEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!this.level().isClientSide) {
            Entity owner = this.getOwner();
            DamageSource source = owner instanceof LivingEntity livingOwner
                    ? this.damageSources().mobProjectile(this, livingOwner)
                    : this.damageSources().generic();
            result.getEntity().hurt(source, DAMAGE);
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!this.level().isClientSide) {
            this.discard();
        }
    }
}
