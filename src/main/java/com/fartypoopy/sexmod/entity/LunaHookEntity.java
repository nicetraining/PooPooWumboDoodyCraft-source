package com.fartypoopy.sexmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Luna's bobber (original gi, a clone of the 1.12 fish hook that belongs to a Luna instead of a player).
 * It flies to the chosen spot, floats, and the bite timers decide when she reels in.
 */
public final class LunaHookEntity extends Projectile {
    /** Original constant m: the bite window value at which Luna reacts. */
    public static final int BITE_TICK = 15;

    private boolean bobbing;
    private boolean landed;
    private int landedTicks;
    private int airTicks;
    /** Original d: ticks left of the bite window; 9999 after the catch has been rolled. */
    private int catchable;
    /** Original c: ticks until a fish notices the bait. */
    private int waitTime;
    /** Original j: ticks until the fish arrives. */
    private int approach;
    private float fishAngle;
    private ItemStack rod = ItemStack.EMPTY;

    public LunaHookEntity(EntityType<? extends LunaHookEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public void setRod(ItemStack rod) {
        this.rod = rod.copy();
    }

    public int catchable() {
        return this.catchable;
    }

    public boolean isBobbing() {
        return this.bobbing;
    }

    /** Test hook: makes a fish bite right now. */
    public void forceBite() {
        this.catchable = 20 + this.random.nextInt(21);
        this.waitTime = 0;
        this.approach = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    /** Original a(double): aimed at the spot, pitch from the distance, speed factor distance * 0.01. */
    public void launch(CompanionEntity luna, BlockPos spot, double factor) {
        this.setOwner(luna);
        double dx = luna.getX() - spot.getX();
        double dy = luna.getY() - spot.getY();
        double dz = luna.getZ() - spot.getZ();
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float pitch = -22.5F + 45.0F * (distance / 7.0F);
        float yaw = luna.getYRot();
        float f13 = Mth.cos(-yaw * ((float) Math.PI / 180.0F) - (float) Math.PI);
        float f14 = Mth.sin(-yaw * ((float) Math.PI / 180.0F) - (float) Math.PI);
        float f15 = -Mth.cos(-pitch * ((float) Math.PI / 180.0F));
        float f16 = Mth.sin(-pitch * ((float) Math.PI / 180.0F));
        double x = luna.getX() - f14 * 0.3D;
        double y = luna.getEyeY();
        double z = luna.getZ() - f13 * 0.3D;
        this.moveTo(x, y, z, yaw, pitch);
        double vx = factor * -f14;
        double vy = factor * Mth.clamp(-(f16 / f15), -5.0F, 5.0F);
        double vz = factor * -f13;
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double scale = 0.6D / length + 0.5D;
        this.setDeltaMovement(
                vx * (scale + this.random.nextGaussian() * 0.0045D),
                vy * (scale + this.random.nextGaussian() * 0.0045D),
                vz * (scale + this.random.nextGaussian() * 0.0045D));
        this.setYRot((float) (Mth.atan2(this.getDeltaMovement().x, this.getDeltaMovement().z) * 57.29577951308232D));
        this.setXRot((float) (Mth.atan2(this.getDeltaMovement().y,
                Math.sqrt(this.getDeltaMovement().x * this.getDeltaMovement().x + this.getDeltaMovement().z * this.getDeltaMovement().z))
                * 57.29577951308232D));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    private CompanionEntity luna() {
        return this.getOwner() instanceof CompanionEntity companion ? companion : null;
    }

    @Override
    public void tick() {
        super.tick();
        CompanionEntity luna = this.luna();
        boolean server = !this.level().isClientSide;
        if (server && (luna == null || luna.isRemoved() || !luna.isAlive())) {
            this.discard();
            return;
        }
        if (server && this.landed && ++this.landedTicks >= 1200) {
            this.discard();
            return;
        }
        BlockPos pos = this.blockPosition();
        FluidState fluid = this.level().getFluidState(pos);
        float height = fluid.is(FluidTags.WATER) ? fluid.getHeight(this.level(), pos) : 0.0F;
        boolean inWater = height > 0.0F;

        if (!this.bobbing) {
            if (inWater) {
                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x * 0.3D, motion.y * 0.2D, motion.z * 0.3D);
                this.bobbing = true;
                return;
            }
            if (server) {
                this.checkCollision(luna);
                if (this.isRemoved()) {
                    return;
                }
            }
            if (this.landed || this.onGround() || this.horizontalCollision) {
                this.airTicks = 0;
                this.setDeltaMovement(Vec3.ZERO);
            } else {
                this.airTicks++;
            }
        } else {
            Vec3 motion = this.getDeltaMovement();
            double d = this.getY() + motion.y - pos.getY() - height;
            if (Math.abs(d) < 0.01D) {
                d += Math.signum(d) * 0.1D;
            }
            this.setDeltaMovement(motion.x * 0.9D, motion.y - d * this.random.nextFloat() * 0.2D, motion.z * 0.9D);
            if (server && inWater) {
                this.bite(pos);
            }
        }
        if (!inWater) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.updateRotation();
        this.setDeltaMovement(this.getDeltaMovement().scale(0.92D));
        this.reapplyPosition();

        // original baseTick: on the ground without a bite the spot is bad (blacklist + reset)
        if (server && this.onGround() && this.catchable == 0 && luna != null) {
            luna.lunaFishing().spotFailed();
        }
    }

    private void checkCollision(CompanionEntity luna) {
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, entity -> entity != luna && this.canHitEntity(entity));
        if (hit.getType() == HitResult.Type.ENTITY) {
            // original: hooking an entity counts as a failed spot
            if (luna != null) {
                luna.lunaFishing().spotFailed();
            }
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            this.landed = true;
        }
    }

    /** Original a(BlockPos): the wait / fish-approach / bite timers. */
    private void bite(BlockPos pos) {
        ServerLevel level = (ServerLevel) this.level();
        int speed = 1;
        BlockPos above = pos.above();
        if (this.random.nextFloat() < 0.25F && level.isRainingAt(above)) {
            speed++;
        }
        if (this.random.nextFloat() < 0.5F && !level.canSeeSky(above)) {
            speed--;
        }
        if (this.catchable > 0) {
            this.catchable--;
            if (this.catchable <= 0) {
                this.waitTime = 0;
                this.approach = 0;
            } else {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.2D * this.random.nextFloat() * this.random.nextFloat(), 0.0D));
            }
            return;
        }
        if (this.approach > 0) {
            this.approach -= speed;
            if (this.approach > 0) {
                this.fishAngle = (float) (this.fishAngle + this.random.nextGaussian() * 4.0D);
                float angle = this.fishAngle * ((float) Math.PI / 180.0F);
                float sin = Mth.sin(angle);
                float cos = Mth.cos(angle);
                double x = this.getX() + sin * this.approach * 0.1F;
                double y = Mth.floor(this.getBoundingBox().minY) + 1.0F;
                double z = this.getZ() + cos * this.approach * 0.1F;
                if (level.getFluidState(BlockPos.containing(x, y - 1.0D, z)).is(FluidTags.WATER)) {
                    if (this.random.nextFloat() < 0.15F) {
                        level.sendParticles(ParticleTypes.BUBBLE, x, y - 0.1F, z, 1, sin, 0.1D, cos, 0.0D);
                    }
                    float dx = sin * 0.04F;
                    float dz = cos * 0.04F;
                    level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, dz, 0.01D, -dx, 1.0D);
                    level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -dz, 0.01D, dx, 1.0D);
                }
                return;
            }
            // the fish bites
            this.setDeltaMovement(this.getDeltaMovement().x, -0.4F * Mth.nextFloat(this.random, 0.6F, 1.0F), this.getDeltaMovement().z);
            this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
            double splashY = this.getBoundingBox().minY + 0.5D;
            int count = (int) (1.0F + this.getBbWidth() * 20.0F);
            level.sendParticles(ParticleTypes.BUBBLE, this.getX(), splashY, this.getZ(), count, this.getBbWidth(), 0.0D, this.getBbWidth(), 0.2D);
            level.sendParticles(ParticleTypes.FISHING, this.getX(), splashY, this.getZ(), count, this.getBbWidth(), 0.0D, this.getBbWidth(), 0.2D);
            this.catchable = Mth.nextInt(this.random, 20, 40);
            return;
        }
        if (this.waitTime > 0) {
            this.waitTime -= speed;
            float chance = 0.15F;
            if (this.waitTime < 20) {
                chance += (20 - this.waitTime) * 0.05F;
            } else if (this.waitTime < 40) {
                chance += (40 - this.waitTime) * 0.02F;
            } else if (this.waitTime < 60) {
                chance += (60 - this.waitTime) * 0.01F;
            }
            if (this.random.nextFloat() < chance) {
                float angle = Mth.nextFloat(this.random, 0.0F, 360.0F) * ((float) Math.PI / 180.0F);
                float radius = Mth.nextFloat(this.random, 25.0F, 60.0F);
                double x = this.getX() + Mth.sin(angle) * radius * 0.1F;
                double y = Mth.floor(this.getBoundingBox().minY) + 1.0F;
                double z = this.getZ() + Mth.cos(angle) * radius * 0.1F;
                if (level.getFluidState(BlockPos.containing(x, y - 1.0D, z)).is(FluidTags.WATER)) {
                    level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2 + this.random.nextInt(2), 0.1D, 0.0D, 0.1D, 0.0D);
                }
            }
            if (this.waitTime <= 0) {
                this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
                this.approach = Mth.nextInt(this.random, 20, 80);
            }
            return;
        }
        this.waitTime = Mth.nextInt(this.random, 100, 600);
        CompanionEntity luna = this.luna();
        if (luna != null) {
            this.waitTime -= (int) (EnchantmentHelper.getFishingTimeReduction(level, this.rod, luna) * 20.0F);
        }
        this.waitTime = Math.max(this.waitTime, 1);
    }

    /** Original c(): rolls the fishing loot table and freezes the bite window. */
    public List<ItemStack> reel() {
        if (!(this.level() instanceof ServerLevel level)) {
            return List.of();
        }
        CompanionEntity luna = this.luna();
        if (this.catchable <= 0 || luna == null) {
            return List.of();
        }
        int luck = 0; // original gi.c builds the loot context without luck
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, this.position())
                .withParameter(LootContextParams.TOOL, this.rod)
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);
        List<ItemStack> loot = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params);
        this.catchable = 9999;
        return loot;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }
}
