package com.trolmastercard.sexmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Original f_.ae(): a tamed Galath WITH a daughter follows her owner (am()/ai()); without one she seeks a wild Manglelie (aJ()) (teleports to him beyond 16 blocks, stands
 * still within 4); otherwise, and always while she carries Manglelie, she wanders around: every 175 ticks a new spot within
 * 9 blocks, walked to at speed 0.35. The follow speed is 0.5 (0.55 when the owner sprints or is more than 7 blocks away)
 * plus 0.2 per started 5 blocks of distance.
 */
final class GalathFollowAi {
    private final CompanionEntity self;
    private BlockPos wanderTarget;
    private int wanderTicks;

    GalathFollowAi(CompanionEntity self) {
        this.self = self;
    }

    /** Whether the AI may steer her at all this tick. */
    private boolean busy() {
        ManglelieBehavior mang = this.self.manglelie();
        return this.self.isInScene() || this.self.isNoAi() || this.self.galathRider() != null
                || this.self.bossState() != GalathBoss.S_NONE
                || this.self.action() != CompanionAction.NONE
                || mang != null && mang.hugging();
    }

    void tick(ServerLevel level) {
        if (this.busy()) {
            return;
        }
        if (!this.follow(level)) {
            this.wander(level);
        }
    }

    private boolean follow(ServerLevel level) {
        if (this.self.familyId() == null) {
            return false; // original ae(): no daughter -> aJ() (seek a wild Manglelie, wander); with a daughter -> am() (follow)
        }
        Player owner = this.self.masterId().map(level::getPlayerByUUID).orElse(null);
        if (owner == null || owner.level() != level) {
            return false;
        }
        float distance = this.self.distanceTo(owner);
        if (distance < 4.0F) {
            this.self.setGalathRunning(false);
            this.self.getNavigation().stop();
            return false;
        }
        if (distance > 16.0F) {
            this.self.getNavigation().stop();
            this.teleportTo(level, owner);
            return true;
        }
        boolean quick = owner.isSprinting() || distance > 7.0F;
        this.self.setGalathRunning(quick);
        double speed = (quick ? 0.55D : 0.5D) + Math.floor(distance / 5.0F) * 0.2D;
        if (this.self.isInWater()) {
            speed *= 60.0D;
        }
        this.self.getNavigation().moveTo(owner, speed);
        return true;
    }

    private void teleportTo(ServerLevel level, Player owner) {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 20; i++) {
            BlockPos pos = base.offset(this.self.getRandom().nextInt(4), 0, this.self.getRandom().nextInt(4));
            if (level.noCollision(this.self, this.self.getBoundingBox().move(
                    pos.getX() + 0.5D - this.self.getX(), pos.getY() - this.self.getY(), pos.getZ() + 0.5D - this.self.getZ()))) {
                this.self.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                this.self.setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
        this.self.teleportTo(owner.getX(), owner.getY(), owner.getZ());
        this.self.setDeltaMovement(Vec3.ZERO);
    }

    private void wander(ServerLevel level) {
        Player near = level.getNearestPlayer(this.self, 15.0D);
        if (near != null && this.self.masterId().filter(near.getUUID()::equals).isPresent()
                && near.distanceTo(this.self) < 2.0F) {
            this.self.getNavigation().stop();
            return;
        }
        if (this.wanderTarget != null && this.wanderTicks <= 175) {
            this.wanderTicks++; // she keeps the spot she picked until 175 ticks are over
            return;
        }
        int dx = (this.self.getRandom().nextBoolean() ? 1 : -1) * this.self.getRandom().nextInt(10);
        int dz = (this.self.getRandom().nextBoolean() ? 1 : -1) * this.self.getRandom().nextInt(10);
        BlockPos here = this.self.blockPosition();
        int y = level.dimension() == Level.NETHER
                ? (int) Math.ceil(this.self.getY())
                : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, here.getX() + dx, here.getZ() + dz);
        this.wanderTarget = new BlockPos(here.getX() + dx, y, here.getZ() + dz);
        this.wanderTicks = 0;
        if (Math.sqrt(this.wanderTarget.distSqr(here)) > 2.0D) {
            this.self.getNavigation().moveTo(this.wanderTarget.getX() + 0.5D, this.wanderTarget.getY(),
                    this.wanderTarget.getZ() + 0.5D, 0.35D);
        }
    }
}
