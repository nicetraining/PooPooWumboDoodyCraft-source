package com.trolmastercard.sexmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.UUID;

/**
 * Original c4: Galath's energy ball. It flies in a straight line at 0.4 blocks per tick through everything, is drawn as a
 * pulsing billboard with dragon breath trailing behind it, and does nothing until it reaches a block: near the target
 * (within 15 blocks) a Wither Skeleton with a stone sword appears there, otherwise it explodes (power 2, fire).
 * A player hit reflects it along his look; a reflected ball that reaches Galath explodes (power 1) and knocks her out.
 * Arrows shatter it.
 */
public final class EnergyBallEntity extends Entity {
    public static final double SPEED = 0.4D;

    private Vec3 velocity = Vec3.ZERO;
    private boolean reflected;
    private boolean armed = true;
    private UUID owner;

    public EnergyBallEntity(EntityType<? extends EnergyBallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void launch(CompanionEntity galath, Vec3 direction) {
        this.owner = galath.getUUID();
        this.velocity = direction;
    }

    public boolean reflected() {
        return this.reflected;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // the original deletes it when it is read back
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        if (this.level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                double fraction = 0.5D * (i + 1);
                double x = this.xo + (this.getX() - this.xo) * fraction;
                double y = this.yo + (this.getY() - this.yo) * fraction;
                double z = this.zo + (this.getZ() - this.zo) * fraction;
                this.level().addParticle(ParticleTypes.DRAGON_BREATH, x + this.random.nextDouble() * 0.3D,
                        y + 0.25D + this.random.nextDouble() * 0.3D, z + this.random.nextDouble() * 0.3D, 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        this.setPos(this.getX() + this.velocity.x, this.getY() + this.velocity.y, this.getZ() + this.velocity.z);
        this.checkReflectedHit();
        if (!this.isAlive()) {
            return;
        }
        if (this.level().getBlockState(this.blockPosition()).isAir()) {
            return;
        }
        this.onBlock();
        this.discard();
    }

    private CompanionEntity ownerEntity() {
        if (this.owner != null && this.level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(this.owner) instanceof CompanionEntity galath) {
            return galath;
        }
        return null;
    }

    /** Original c(): a reflected ball that touches Galath explodes and knocks her out. */
    private void checkReflectedHit() {
        if (!this.reflected) {
            return;
        }
        AABB box = new AABB(this.position(), this.position()).inflate(0.75D);
        var hit = this.level().getEntitiesOfClass(CompanionEntity.class, box, c -> c.kind() == CompanionKind.GALATH && c.isAlive());
        if (hit.isEmpty()) {
            return;
        }
        this.level().explode(this, this.getX(), this.getY(), this.getZ(), 1.0F, true, Level.ExplosionInteraction.MOB);
        for (CompanionEntity galath : hit) {
            if (galath.boss() != null) {
                galath.boss().knockOut(this.position());
            }
        }
        this.discard();
    }

    /** Original b(): the ball reaches a block. */
    private void onBlock() {
        if (!this.armed || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 spot = new Vec3(this.getX(), this.blockPosition().getY() + 1, this.getZ());
        CompanionEntity galath = this.ownerEntity();
        net.minecraft.world.entity.LivingEntity target = galath == null || galath.boss() == null ? null : galath.boss().target();
        boolean near = galath == null || target == null
                || target.distanceToSqr(spot.x, spot.y, spot.z) < 15.0D * 15.0D;
        if (!near) {
            level.explode(this, this.getX(), this.getY(), this.getZ(), 2.0F, true, Level.ExplosionInteraction.MOB);
            this.armed = false;
            return;
        }
        WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level);
        if (skeleton == null) {
            return;
        }
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        skeleton.moveTo(spot.x, spot.y, spot.z, this.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(skeleton);
        GalathBoss.burst(level, spot);
        if (galath != null && galath.boss() != null) {
            galath.boss().addSkeleton(skeleton);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) {
            this.armed = false;
            this.discard();
            return true;
        }
        if (this.level().isClientSide) {
            return false;
        }
        if (source.is(DamageTypes.ARROW)) {
            this.armed = false;
            if (this.level() instanceof ServerLevel level) {
                GalathBoss.smokeRing(level, this.position());
            }
            Entity arrow = source.getDirectEntity();
            if (arrow instanceof AbstractArrow) {
                arrow.discard();
            }
            this.discard();
            return true;
        }
        if (!(source.getEntity() instanceof Player player)) {
            return false;
        }
        this.velocity = player.getLookAngle();
        this.reflected = true;
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return false;
    }

    public BlockPos cell() {
        return this.blockPosition();
    }
}
