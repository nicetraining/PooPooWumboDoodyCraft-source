package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.registry.ModPotions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The original bee's own rules (class fz): a wild bee flies to a player within 10 blocks every 4 minutes and starts
 * the scene herself, the mood timer afterwards decides whether she is tamed, and she hovers at most 3 blocks above
 * the ground, gliding down slowly.
 */
final class BeeBehavior {
    private static final float SEEK_AT = 4800.0F;

    private final CompanionEntity bee;
    private float seek = 3200.0F;
    private int mood;

    BeeBehavior(CompanionEntity bee) {
        this.bee = bee;
    }

    void tick(ServerLevel level) {
        if (this.bee.hasEffect(ModPotions.HORNY_EFFECT) && this.seek < SEEK_AT && !this.bee.hasMaster()) {
            this.bee.removeEffect(ModPotions.HORNY_EFFECT);
            this.seek = 6.9420184E7F; // forces the seek phase at once
        }
        this.seekPartner(level);
        if (this.bee.currentScene() == CompanionScene.BEE_SEX
                && this.bee.currentScenePhase() == BeeScene.CUM_PHASE) {
            this.mood = Math.max(1, this.mood);
        }
        this.tickMood(level);
        this.limitFlight(level);
    }

    /** Original c(): only while she follows nobody and is not busy. */
    private void seekPartner(ServerLevel level) {
        if (this.bee.hasMaster() || this.bee.isInScene()) {
            return;
        }
        this.seek += 1.0F;
        if (this.seek < SEEK_AT) {
            return;
        }
        Player target = level.getNearestPlayer(this.bee, 10.0D);
        if (target == null || target.isSpectator() || this.busy(level, target)) {
            return;
        }
        if (this.bee.distanceTo(target) < 1.5F) {
            this.bee.getNavigation().stop();
            if (this.bee.startScene(target, CompanionScene.BEE_SEX)) {
                this.seek = 0.0F;
            }
        } else {
            this.bee.getNavigation().moveTo(target, 1.0D);
        }
    }

    private boolean busy(ServerLevel level, Player player) {
        return !level.getEntitiesOfClass(CompanionEntity.class, player.getBoundingBox().inflate(64.0D),
                other -> other.scenePartnerId().filter(player.getUUID()::equals).isPresent()).isEmpty();
    }

    /** Original a(): after the scene she shows hearts (already tamed) or rolls a 50 % chance to be tamed. */
    private void tickMood(ServerLevel level) {
        if (this.mood == 0) {
            return;
        }
        this.mood++;
        double x = this.bee.getX();
        double y = this.bee.getY() + 0.8D;
        double z = this.bee.getZ();
        if (this.bee.isBeeTamed()) {
            if (this.mood < 40) {
                level.sendParticles(ParticleTypes.HEART, x, y, z, 1, 0.2D, 0.3D, 0.2D, 0.25D);
            } else {
                this.mood = 0;
            }
            return;
        }
        if (this.mood < 200) {
            level.sendParticles(ParticleTypes.EFFECT, x, y, z, 1, 0.2D, 0.3D, 0.2D, 0.25D);
        } else if (this.mood == 200) {
            this.bee.setBeeTamed(this.bee.getRandom().nextBoolean());
        } else if (this.mood < 250) {
            level.sendParticles(this.bee.isBeeTamed() ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER,
                    x, y, z, 3, 0.2D, 0.3D, 0.2D, 0.25D);
        } else {
            level.sendParticles(ParticleTypes.EFFECT, x, y, z, 10, 0.2D, 0.3D, 0.2D, 0.25D);
            this.mood = 0;
        }
    }

    /** Original b() and the glide: at most 3 blocks above the ground, and she falls slowly while idle. */
    private void limitFlight(ServerLevel level) {
        if (this.bee.isInScene()) {
            return;
        }
        Vec3 motion = this.bee.getDeltaMovement();
        if (!this.bee.onGround() && motion.y < 0.0D && this.seek < SEEK_AT) {
            this.bee.setDeltaMovement(motion.x, motion.y * 0.4D, motion.z);
            motion = this.bee.getDeltaMovement();
        }
        if (motion.y > 0.0D) {
            Vec3 from = this.bee.position();
            Vec3 to = new Vec3(from.x, Math.max(level.getMinBuildHeight(), from.y - 16.0D), from.z);
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.bee));
            double height = hit.getType() == HitResult.Type.MISS ? 16.0D : from.y - hit.getBlockPos().getY();
            if (height > 3.0D) {
                this.bee.setDeltaMovement(motion.x, 0.0D, motion.z);
            }
        }
    }

    /** Scene facts the bee needs. */
    static final class BeeScene {
        /** Index of the cum phase in BEE_SEX. */
        static final int CUM_PHASE = 3;

        private BeeScene() {
        }
    }
}
