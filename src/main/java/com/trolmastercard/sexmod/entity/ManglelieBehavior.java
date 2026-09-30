package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.data.GalathBindingData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Original f8 (Manglelie, Galath's daughter) and the adoption half of f_ (at, al, m, v).
 *
 * <p>A wild Manglelie (nether, weight 5, one per 700 blocks) runs to a Galath within 15 blocks. A tamed Galath whose owner
 * has no daughter yet runs to the nearest wild Manglelie, hugs her for 239 ticks and from then on carries her on her head
 * (bone {@code mangPos}). The daughter takes no damage of her own (it goes to her mother), is not collidable, and shoots
 * arrows at hostile mobs within 15 blocks in front of Galath: 28 tick wind-up, speed 4.0, one arrow per 60 ticks. Her
 * arrows never hurt other companions.</p>
 */
public final class ManglelieBehavior {
    public static final double RUN_SPEED = 0.65D;
    public static final double HUG_DISTANCE = 3.65D;
    public static final int HUG_TICKS = 239;
    public static final int WINDUP_TICKS = 28;
    public static final int RESET_TICKS = 60;
    public static final double ARROW_SPEED = 4.0D;
    /** Height of the {@code mangPos} bone above Galath's feet (pivot y 33.4 / 16). */
    public static final double HEAD_HEIGHT = 2.09D;

    private final CompanionEntity self;
    private final boolean galath;

    // Manglelie
    private boolean wild = true;
    private UUID pendingMother;
    private boolean shotFired;
    private boolean running;
    // Galath
    private int hugTicks = -1;
    private int missingDaughterTicks;

    public ManglelieBehavior(CompanionEntity self) {
        this.self = self;
        this.galath = self.kind() == CompanionKind.GALATH;
    }

    public boolean wild() {
        return this.wild;
    }

    public void setWild(boolean wild) {
        this.wild = wild;
    }

    public void setPendingMother(UUID mother) {
        this.pendingMother = mother;
    }

    public boolean hugging() {
        return this.hugTicks >= 0;
    }

    /** Original static helper: is this entity one a Manglelie may shoot (mobs, but not creepers, piglins, guardians, endermen). */
    public static boolean shootable(Entity entity) {
        return entity instanceof Monster
                && !(entity instanceof net.minecraft.world.entity.monster.Creeper)
                && !(entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin)
                && !(entity instanceof net.minecraft.world.entity.monster.Guardian)
                && !(entity instanceof net.minecraft.world.entity.monster.EnderMan);
    }

    public static CompanionEntity find(ServerLevel origin, UUID id) {
        if (id == null) {
            return null;
        }
        for (ServerLevel level : origin.getServer().getAllLevels()) {
            if (level.getEntity(id) instanceof CompanionEntity found && found.isAlive()) {
                return found;
            }
        }
        return null;
    }

    public void tick(ServerLevel level) {
        if (this.galath) {
            this.tickGalath(level);
        } else {
            this.tickManglelie(level);
        }
    }

    // ---------------------------------------------------------------- Manglelie (f8)

    private void tickManglelie(ServerLevel level) {
        CompanionEntity self = this.self;
        this.follow(level);
        this.relink(level);
        this.seekMother(level);
        this.dropBadTarget(level);
        this.resetShot(level);
        this.pickTarget(level);
        boolean adopted = self.familyId() != null;
        self.setNoGravity(adopted);
        self.noPhysics = adopted;
        this.fire(level);
        this.checkMother(level);
    }

    /** Original w(): pinned to the mother's {@code mangPos} bone. */
    private void follow(ServerLevel level) {
        CompanionEntity self = this.self;
        if (!self.mangRiding() || self.isInScene()) {
            return;
        }
        CompanionEntity mother = this.mother(level);
        if (mother == null) {
            return;
        }
        if (!mother.isAlive() || !self.getUUID().equals(mother.familyId())) {
            self.discard(); // a dead mother was saved on the daughter
            return;
        }
        double yaw = Math.toRadians(mother.getYRot());
        Vec3 at = mother.position().add(-Math.sin(yaw) * 0.03D, HEAD_HEIGHT, Math.cos(yaw) * 0.03D);
        self.getNavigation().stop();
        self.setDeltaMovement(Vec3.ZERO);
        self.setPos(at);
        self.setYRot(mother.getYRot());
        self.yBodyRot = mother.yBodyRot;
        self.setYHeadRot(mother.yHeadRot);
    }

    /** Original j(): a saved mother is linked again as soon as she is loaded. */
    private void relink(ServerLevel level) {
        if (this.pendingMother == null) {
            return;
        }
        CompanionEntity mother = find(level, this.pendingMother);
        if (mother == null || mother.kind() != CompanionKind.GALATH) {
            return;
        }
        this.self.setFamilyId(this.pendingMother);
        mother.setFamilyId(this.self.getUUID());
        this.self.setMangRiding(true);
        this.self.setAction(CompanionAction.MANG_RIDE);
        this.pendingMother = null;
        ManglelieBehavior motherBehavior = mother.manglelie();
        if (motherBehavior != null && motherBehavior.hugTicks >= 0) {
            motherBehavior.hugTicks = -1;
            mother.setAction(CompanionAction.NONE);
        }
    }

    /** Original c(): a wild Manglelie runs to a Galath within 15 blocks that has no daughter. */
    private void seekMother(ServerLevel level) {
        CompanionEntity self = this.self;
        if (self.mangRiding() || self.familyId() != null) {
            return;
        }
        CompanionEntity target = null;
        for (CompanionEntity candidate : level.getEntitiesOfClass(CompanionEntity.class, self.getBoundingBox().inflate(15.0D),
                c -> c.kind() == CompanionKind.GALATH && c.isAlive() && c.familyId() == null && c.onGround() && !c.isRemoved())) {
            target = candidate;
            break;
        }
        if (target == null) {
            if (self.action() == CompanionAction.MANG_RUN) {
                self.setAction(CompanionAction.NONE);
                self.getNavigation().stop();
            }
            return;
        }
        if (self.action() == CompanionAction.MANG_RIDE) {
            return;
        }
        self.setAction(CompanionAction.MANG_RUN);
        this.running = true;
        Vec3 delta = target.position().subtract(self.position());
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x))) - 90.0F;
        self.setYRot(yaw);
        self.yBodyRot = yaw;
        self.getNavigation().moveTo(target, RUN_SPEED);
    }

    /** Original t() and a(): she is removed when she has no mother, or when her mother took another daughter. */
    private void checkMother(ServerLevel level) {
        CompanionEntity self = this.self;
        if (self.familyId() != null) {
            this.wild = false;
        }
        if (this.wild || self.tickCount < 40) {
            return;
        }
        CompanionEntity mother = this.mother(level);
        if (mother == null) {
            self.discard();
            return;
        }
        if (mother.familyId() != null && !self.getUUID().equals(mother.familyId())) {
            self.discard();
        }
    }

    public CompanionEntity mother(ServerLevel level) {
        return find(level, this.self.familyId());
    }

    // ---- arrows

    private void pickTarget(ServerLevel level) {
        CompanionEntity self = this.self;
        if (self.mangTargetId() != -1 || !self.mangRiding()) {
            return;
        }
        CompanionEntity mother = this.mother(level);
        if (mother == null || mother.isInScene()) {
            return;
        }
        AABB box = new AABB(mother.blockPosition()).inflate(15.0D);
        for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
            if (this.invalidTarget(mob, mother)) {
                continue;
            }
            self.setMangTarget(mob.getId(), level.getGameTime());
            this.shotFired = false;
            return;
        }
    }

    private void dropBadTarget(ServerLevel level) {
        CompanionEntity self = this.self;
        Entity target = self.mangTarget();
        if (target == null) {
            return;
        }
        CompanionEntity mother = this.mother(level);
        if (mother == null || !self.mangRiding() || this.invalidTarget(target, mother)) {
            self.setMangTarget(-1, -1L);
        }
    }

    /** Original static a(Entity, f_): true when the mob must not be targeted. */
    public static boolean invalidTarget(Entity entity, CompanionEntity mother) {
        if (!entity.isAlive() || entity.level() != mother.level() || !shootable(entity)) {
            return true;
        }
        Vec3 eye = mother.position().add(0.0D, mother.getEyeHeight(), 0.0D);
        Vec3 to = entity.position().add(0.0D, entity.getEyeHeight(), 0.0D);
        var hit = mother.level().clip(new net.minecraft.world.level.ClipContext(eye, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mother));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return true;
        }
        Vec3 delta = entity.position().subtract(mother.position());
        if (delta.x * delta.x + delta.z * delta.z > 225.0D) {
            return true;
        }
        double yaw = Math.toRadians(mother.getYRot());
        return delta.x * -Math.sin(yaw) + delta.z * Math.cos(yaw) < 0.0D; // behind her
    }

    private void fire(ServerLevel level) {
        CompanionEntity self = this.self;
        long start = self.mangShotStart();
        if (start == -1L || level.getGameTime() < start + WINDUP_TICKS || this.shotFired) {
            return;
        }
        Entity target = self.mangTarget();
        CompanionEntity mother = this.mother(level);
        if (target == null || mother == null) {
            return;
        }
        Arrow arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
        arrow.setOwner(self);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        Vec3 from = mother.position().add(0.0D, 3.5D, 0.0D);
        arrow.setPos(from);
        Vec3 dir = target.position().subtract(from).normalize().scale(ARROW_SPEED);
        arrow.setDeltaMovement(dir);
        level.playSound(null, mother.getX(), mother.getY(), mother.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.addFreshEntity(arrow);
        this.shotFired = true;
    }

    private void resetShot(ServerLevel level) {
        CompanionEntity self = this.self;
        long start = self.mangShotStart();
        if (self.mangTarget() == null || start == -1L || level.getGameTime() - start < RESET_TICKS) {
            return;
        }
        this.shotFired = false;
        self.setMangTarget(-1, -1L);
    }

    // ---------------------------------------------------------------- Galath (adoption)

    private void tickGalath(ServerLevel level) {
        CompanionEntity self = this.self;
        Optional<UUID> owner = self.masterId();
        if (owner.isEmpty() || self.isInScene() || self.galathRider() != null) {
            return;
        }
        GalathBindingData data = GalathBindingData.get(level.getServer());
        if (this.hugTicks >= 0) {
            this.tickHug(level);
            return;
        }
        CompanionEntity daughter = find(level, self.familyId());
        if (data.hasMang(owner.get())) {
            // original m(): the owner has a daughter but none exists, so a new one is created
            if (daughter == null && self.tickCount > 20 && ++this.missingDaughterTicks > 40) {
                this.missingDaughterTicks = 0;
                spawnDaughter(level, self);
            } else if (daughter != null) {
                this.missingDaughterTicks = 0;
            }
            return;
        }
        if (self.familyId() != null) {
            return;
        }
        this.seekDaughter(level, data, owner.get());
    }

    /** Original at(): run to the nearest wild Manglelie, hug her, and adopt her. */
    private void seekDaughter(ServerLevel level, GalathBindingData data, UUID owner) {
        CompanionEntity self = this.self;
        CompanionEntity mang = null;
        double best = Double.MAX_VALUE;
        for (CompanionEntity candidate : level.getEntitiesOfClass(CompanionEntity.class, self.getBoundingBox().inflate(15.0D),
                c -> c.kind() == CompanionKind.MANGLELIE && c.isAlive() && c.familyId() == null)) {
            double d = candidate.distanceToSqr(self);
            if (d < best) {
                best = d;
                mang = candidate;
            }
        }
        if (mang == null) {
            if (this.running) {
                this.running = false;
                self.getNavigation().stop();
            }
            return;
        }
        this.running = true;
        if (self.distanceTo(mang) > HUG_DISTANCE) {
            self.getNavigation().moveTo(mang, RUN_SPEED);
            return;
        }
        this.running = false;
        this.hugTicks = 0;
        self.getNavigation().stop();
        self.setDeltaMovement(Vec3.ZERO);
        self.setAction(CompanionAction.GALATH_HUG_MANG);
        self.setFamilyId(mang.getUUID());
        mang.setFamilyId(self.getUUID());
        mang.setAction(CompanionAction.MANG_RIDE);
        ManglelieBehavior behavior = mang.manglelie();
        if (behavior != null) {
            behavior.wild = false;
        }
        data.setHasMang(owner);
    }

    private void tickHug(ServerLevel level) {
        CompanionEntity self = this.self;
        self.getNavigation().stop();
        self.setDeltaMovement(0.0D, self.getDeltaMovement().y, 0.0D);
        if (++this.hugTicks >= HUG_TICKS) {
            this.hugTicks = -1;
            self.setAction(CompanionAction.NONE);
            CompanionEntity mang = find(level, self.familyId());
            if (mang != null) {
                mang.setMangRiding(true);
            }
        }
    }

    /** Original v(): Galath's daughter is created riding on her head (coin summon and the failsafe). */
    public static CompanionEntity spawnDaughter(ServerLevel level, CompanionEntity galath) {
        CompanionEntity mang = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.MANGLELIE).create(level);
        if (mang == null) {
            return null;
        }
        mang.moveTo(galath.getX(), galath.getY() + HEAD_HEIGHT, galath.getZ(), galath.getYRot(), 0.0F);
        mang.setFamilyId(galath.getUUID());
        galath.setFamilyId(mang.getUUID());
        mang.setMangRiding(true);
        mang.setAction(CompanionAction.MANG_RIDE);
        ManglelieBehavior behavior = mang.manglelie();
        if (behavior != null) {
            behavior.wild = false;
        }
        level.addFreshEntity(mang);
        return mang;
    }

    /** Both of them leave together when the coin takes Galath back. */
    public static void removeDaughter(ServerLevel level, CompanionEntity galath) {
        CompanionEntity mang = find(level, galath.familyId());
        if (mang != null) {
            mang.discard();
        }
    }
}
