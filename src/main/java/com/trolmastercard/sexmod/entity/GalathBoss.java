package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.data.GalathBindingData;
import com.trolmastercard.sexmod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Original f_ (wild Galath) together with its attack table h8: a flying boss that picks a target within 20 blocks and cycles
 * CHANGE_POSITION, SUMMON_SKELETON, ATTACK_SWORD and RAPE (a charge that catches players standing on the ground). Reflecting her
 * energy balls knocks her out; her health can never reach zero, at zero she is paralysed and waits to be corrupted.
 */
public final class GalathBoss {
    // state ids (synced; the animation follows them)
    public static final int S_NONE = 0;
    public static final int S_FLY = 1;
    public static final int S_SUMMON = 2;
    public static final int S_SWORD = 3;
    public static final int S_KO_FLY = 4;
    public static final int S_KO_GROUND = 5;
    public static final int S_KO_STAND = 6;
    public static final int S_PREPARE = 7;
    public static final int S_CHARGE = 8;

    // flag bits (synced)
    public static final int F_KNOCKED = 1;
    public static final int F_PARALYSED = 2;
    public static final int F_ORB_RIGHT = 4;
    public static final int F_ORB_LEFT = 8;
    public static final int F_MIRROR = 16;

    /** Orb hitbox offsets in her local frame (original bz right, bC left). */
    private static final Vec3 ORB_RIGHT = new Vec3(-1.049342D, 2.0547214D, -0.0504824D);
    private static final Vec3 ORB_LEFT = new Vec3(1.2522261D, 1.4357733D, 0.2357099D);

    /** Original h8. */
    public enum Attack {
        CHANGE_POSITION(false, false),
        SUMMON_SKELETON(true, true),
        ATTACK_SWORD(true, false),
        RAPE(true, true);

        final boolean cooldown;
        final boolean playersOnly;

        Attack(boolean cooldown, boolean playersOnly) {
            this.cooldown = cooldown;
            this.playersOnly = playersOnly;
        }
    }

    private final CompanionEntity self;
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Galath"), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);
    private final GalathPart right;
    private final GalathPart left;
    private final List<UUID> skeletons = new ArrayList<>();

    private Attack attack;
    private int counter; // original ar(): the phase counter of the current attack
    private Vec3 moveTarget; // original O
    private Vec3 chargeStart; // original bd
    private int prepareTicks; // original aF
    private int swordTick = -1; // original az()
    private Vec3 swordOrigin;
    private boolean locked;
    private Vec3 lockPos;
    private float lockYaw;
    private int knockTicks; // original b3
    private int standTicks; // original bY

    public GalathBoss(CompanionEntity self) {
        this.self = self;
        this.right = new GalathPart(self, false);
        this.left = new GalathPart(self, true);
        self.setBossFlags(F_ORB_RIGHT | F_ORB_LEFT);
    }

    public GalathPart[] parts() {
        return new GalathPart[]{this.right, this.left};
    }

    public ServerBossEvent bar() {
        return this.bar;
    }

    public Attack attack() {
        return this.attack;
    }

    public LivingEntity target() {
        int id = this.self.bossTargetId();
        return id != -1 && this.self.level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    public boolean knocked() {
        return this.self.bossFlag(F_KNOCKED);
    }

    public boolean paralysed() {
        return this.self.bossFlag(F_PARALYSED);
    }

    public void addSkeleton(Entity skeleton) {
        this.skeletons.add(skeleton.getUUID());
    }

    // ---------------------------------------------------------------- damage and death

    /** Original death handler: her health never reaches zero, at zero she is paralysed instead. */
    public float adjustLethal(float amount) {
        if (this.paralysed()) {
            return 0.0F;
        }
        if (this.self.getHealth() - amount > 0.0F) {
            return amount;
        }
        this.self.setBossFlag(F_PARALYSED, true);
        if (this.self.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(this.self) <= 40.0D * 40.0D) {
                    player.sendSystemMessage(Component.translatable("message.sexmod.galath_paralyzed")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW));
                    player.sendSystemMessage(Component.translatable("message.sexmod.galath_paralyzed_hint")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }
            burst(level, this.self.position());
        }
        this.knockOut(null);
        return Math.max(0.0F, this.self.getHealth() - 1.0F);
    }

    /** Original f(Vec3d): a knock-out from a reflected ball (or from running out of health). */
    public void knockOut(Vec3 from) {
        if (this.knocked()) {
            return;
        }
        this.self.setBossFlag(F_KNOCKED, true);
        this.endAttack();
        this.attack = null;
        Vec3 pos = this.self.position();
        Vec3 away = from == null
                ? new Vec3(this.self.getRandom().nextDouble(), this.self.getRandom().nextDouble(), this.self.getRandom().nextDouble()).normalize()
                : pos.subtract(from).normalize();
        this.self.setDeltaMovement(away.x, 1.0D, away.z);
        this.self.setBossState(S_KO_FLY);
        this.self.setNoGravity(false);
        this.self.noPhysics = false;
        this.self.getNavigation().stop();
        this.locked = false;
        this.sound("girls.galath.aaa.aaa", 2);
    }

    /** Original func_70965_a: a player hit on an orb hitbox destroys that orb. */
    public boolean partHit(GalathPart part, DamageSource source) {
        if (!(source.getEntity() instanceof Player) || !(this.self.level() instanceof ServerLevel level)) {
            return false;
        }
        this.self.setBossFlag(part.left ? F_ORB_LEFT : F_ORB_RIGHT, false);
        smokeRing(level, part.position());
        return true;
    }

    // ---------------------------------------------------------------- tick

    /** Runs every tick; a wild Galath fights here, a tamed one only through {@link #tickTamedCombat}. */
    public void tick(ServerLevel level) {
        this.bar.setProgress(this.self.getHealth() / this.self.getMaxHealth());
        if (this.self.hasMaster()) {
            this.bar.setVisible(false);
            this.updateParts();
            this.applyLock();
            return;
        }
        this.bar.setVisible(true);
        this.tickCombat(level, false);
        this.applyLock();
    }

    /** Original ao() for an owned Galath that has a daughter and is not following: she fights hostile mobs within 7 blocks. */
    public void tickTamedCombat(ServerLevel level) {
        this.tickCombat(level, true);
    }

    private boolean tamed;

    /** Original escape: she lets go and flies off to a new position. */
    public void flyAway(ServerLevel level) {
        this.endAttack();
        this.attack = Attack.CHANGE_POSITION;
        this.startAttack(level);
    }

    private void tickCombat(ServerLevel level, boolean tamed) {
        this.tamed = tamed;
        // original n(), j(), ah(): she flies while she has a target; the summon counter runs during the summon state
        if (!this.knocked()) {
            this.self.setNoGravity(this.target() != null);
        }
        if (this.self.bossState() == S_SUMMON) {
            int next = this.self.summonTick() + 1;
            this.self.setSummonTick(next > 46 ? 0 : next);
        } else {
            this.self.setSummonTick(0);
        }
        this.updateParts();
        if (this.self.isInScene() || this.self.manglelie() != null && this.self.manglelie().hugging()) {
            return;
        }
        this.acquireTarget(level);
        this.tickAttackSelection(level);
        if (this.attack != null) {
            this.runAttack(level);
        }
        this.fixNoClip(level);
        this.tickKnockLanding(level);
        this.freezeWhileDown();
        this.tickKnockGround();
        this.tickStandUp(level);
        this.tickSkeletonFx(level);
        this.cleanSkeletons(level);
        this.cullSkeletons(level);
    }

    // ---- target

    private boolean targetStillValid() {
        LivingEntity target = this.target();
        if (target == null || !target.isAlive() || target.level() != this.self.level()) {
            return false;
        }
        if (this.self.distanceTo(target) > (this.tamed ? 16.0F : 30.0F)) {
            return false;
        }
        if (target instanceof Player player) {
            return !CompanionEntity.playerInScene(player) && !player.isCreative() && !player.isSpectator();
        }
        return true;
    }

    private void acquireTarget(ServerLevel level) {
        if (this.targetStillValid()) {
            return;
        }
        LivingEntity target;
        if (this.tamed) {
            target = this.findMobTarget(level);
        } else {
            AABB box = this.self.getBoundingBox().inflate(20.0D);
            List<Player> players = level.getEntitiesOfClass(Player.class, box,
                    p -> !p.isCreative() && !p.isSpectator());
            target = players.isEmpty() ? null : players.get(0);
        }
        if (target == null) {
            this.loseTarget();
            return;
        }
        this.self.setBossTarget(target.getId());
        this.sound("girls.galath.dialog.dialog", 1, 1);
        this.endAttack();
        this.attack = Attack.CHANGE_POSITION;
        this.startAttack(level);
    }

    /** Original a(AxisAlignedBB): the first hostile mob within 7 blocks that she may attack and can see. */
    private LivingEntity findMobTarget(ServerLevel level) {
        AABB box = this.self.getBoundingBox().inflate(7.0D);
        Vec3 eye = this.self.position().add(0.0D, this.self.getEyeHeight(), 0.0D);
        for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
            if (!ManglelieBehavior.shootable(mob)) {
                continue;
            }
            var hit = level.clip(new ClipContext(eye, mob.position().add(0.0D, mob.getEyeHeight(), 0.0D),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.self));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return mob;
            }
        }
        return null;
    }

    private void loseTarget() {
        if (this.self.bossTargetId() == -1) {
            return;
        }
        this.self.setBossTarget(-1);
        this.endAttack();
        this.attack = null;
        if (!this.knocked()) {
            this.self.setBossState(S_NONE);
        }
    }

    // ---- attack selection (original D, z, a(h8))

    private void tickAttackSelection(ServerLevel level) {
        if (this.target() == null) {
            this.attack = null;
            return;
        }
        if (this.attack == null || this.finished()) {
            this.pickAttack(level);
        }
    }

    private boolean allowed(Attack candidate) {
        if (candidate.playersOnly && !(this.target() instanceof Player)) {
            return false;
        }
        return candidate != Attack.SUMMON_SKELETON || this.aliveSkeletons() < 2;
    }

    private void pickAttack(ServerLevel level) {
        if (this.knocked()) {
            return;
        }
        Attack previous = this.attack;
        if (this.self.isInScene()) {
            this.endAttack();
            this.attack = null;
            return;
        }
        if (previous != null && previous.cooldown) {
            this.endAttack();
            this.attack = Attack.CHANGE_POSITION;
            this.startAttack(level);
            return;
        }
        Attack[] all = Attack.values();
        Attack chosen;
        do {
            chosen = all[this.self.getRandom().nextInt(all.length)];
        } while (!this.allowed(chosen));
        if (previous != null) {
            this.endAttack();
        }
        this.attack = chosen;
        this.startAttack(level);
    }

    // ---- attacks

    private void startAttack(ServerLevel level) {
        Attack current = this.attack;
        if (current == null) {
            return;
        }
        switch (current) {
            case CHANGE_POSITION -> this.startChangePosition(level);
            case SUMMON_SKELETON -> {
                this.self.setBossState(S_SUMMON);
                this.self.setSummonTick(0);
                this.self.setBossFlag(F_ORB_RIGHT, true);
                this.self.setBossFlag(F_ORB_LEFT, true);
                this.self.setBossFlag(F_MIRROR, this.self.getRandom().nextBoolean());
                this.sound("girls.galath.strongcharge.strongcharge", 4);
            }
            case ATTACK_SWORD -> {
                this.swordTick = 0;
                this.self.setBossState(S_SWORD);
                this.self.setDeltaMovement(Vec3.ZERO);
                this.swordOrigin = this.self.position();
                LivingEntity target = this.target();
                Vec3 delta = target.position().subtract(this.swordOrigin);
                float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
                this.lockAt(this.self.position(), yaw);
                this.sound("girls.galath.strongcharge.strongcharge", 4);
            }
            case RAPE -> {
                this.self.setBossState(S_PREPARE);
                this.prepareTicks = 0;
                this.chargeStart = null;
                this.moveTarget = null;
            }
        }
    }

    private void endAttack() {
        Attack current = this.attack;
        if (current == null) {
            return;
        }
        switch (current) {
            case CHANGE_POSITION -> {
                this.self.setDeltaMovement(Vec3.ZERO);
                this.counter = 0;
                if (this.self.level().getBlockState(this.self.blockPosition()).isAir()) {
                    this.self.noPhysics = false;
                }
            }
            case SUMMON_SKELETON -> this.self.setSummonTick(0);
            case ATTACK_SWORD -> {
                this.counter = 0;
                this.self.setDeltaMovement(Vec3.ZERO);
                this.swordTick = -1;
                this.locked = false;
            }
            case RAPE -> {
                this.moveTarget = null;
                this.chargeStart = null;
                this.prepareTicks = 0;
            }
        }
    }

    private boolean finished() {
        Attack current = this.attack;
        if (current == null) {
            return false;
        }
        return switch (current) {
            case CHANGE_POSITION, ATTACK_SWORD -> this.counter > 23;
            case SUMMON_SKELETON -> this.self.summonTick() >= 45;
            case RAPE -> {
                if (this.self.bossState() == S_NONE && this.self.isInScene()) {
                    yield true;
                }
                if (this.chargeStart == null || this.moveTarget == null) {
                    yield false;
                }
                Vec3 over = this.overshoot();
                yield this.self.distanceToSqr(over.x, over.y, over.z) < 0.01D;
            }
        };
    }

    private void runAttack(ServerLevel level) {
        Attack current = this.attack;
        switch (current) {
            case CHANGE_POSITION -> this.tickChangePosition();
            case SUMMON_SKELETON -> this.tickSummon(level);
            case ATTACK_SWORD -> this.tickSword();
            case RAPE -> this.tickCharge(level);
        }
    }

    // CHANGE_POSITION

    private void startChangePosition(ServerLevel level) {
        BlockPos me = this.self.blockPosition();
        LivingEntity target = this.target();
        BlockPos around = target.blockPosition();
        boolean grounded = !level.getBlockState(me.below()).isAir();
        List<BlockPos> reachable = new ArrayList<>();
        Map<BlockPos, Integer> scored = new HashMap<>();
        for (int dx = -10; dx < 10; dx++) {
            for (int dy = -10; dy < 10; dy++) {
                for (int dz = -10; dz < 10; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos cell = around.offset(dx, dy, dz);
                    if (grounded && me.getY() >= cell.getY()) {
                        continue;
                    }
                    if (!level.getBlockState(cell).isAir() || !level.getBlockState(cell.above()).isAir()
                            || !level.getBlockState(cell.above(2)).isAir()) {
                        continue;
                    }
                    var hit = level.clip(new ClipContext(Vec3.atLowerCornerOf(me), Vec3.atLowerCornerOf(cell),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this.self));
                    if (hit.getType() != HitResult.Type.MISS) {
                        continue;
                    }
                    int y = cell.getY();
                    while (--y >= level.getMinBuildHeight() && level.getBlockState(new BlockPos(cell.getX(), y, cell.getZ())).isAir()) {
                        // scan down to the ground below the cell
                    }
                    if (level.getBlockState(new BlockPos(cell.getX(), y, cell.getZ())).getBlock() instanceof LiquidBlock) {
                        continue;
                    }
                    reachable.add(cell);
                    if (!level.getBlockState(cell.below()).isAir() || !level.getBlockState(cell.below(2)).isAir()) {
                        continue;
                    }
                    if (Math.sqrt(around.distSqr(cell)) < 5.0D || Math.sqrt(me.distSqr(cell)) < 3.0D) {
                        continue;
                    }
                    int open = 0;
                    for (int ax = -1; ax < 2; ax++) {
                        for (int az = -1; az < 2; az++) {
                            for (int ay = -1; ay < 4; ay++) {
                                if (level.getBlockState(cell.offset(ax, ay, az)).isAir()) {
                                    open++;
                                }
                            }
                        }
                    }
                    if (open >= 25) {
                        scored.put(cell, open);
                    }
                }
            }
        }
        BlockPos goal;
        if (!scored.isEmpty()) {
            List<Map.Entry<BlockPos, Integer>> sorted = new ArrayList<>(scored.entrySet());
            sorted.sort(Comparator.comparing((Map.Entry<BlockPos, Integer> e) -> e.getValue()).reversed());
            goal = sorted.get(weightedIndex(sorted.size() - 1)).getKey();
        } else if (reachable.isEmpty()) {
            var random = this.self.getRandom();
            goal = around.offset(signed(random, 10), (int) (random.nextFloat() * 10.0F), signed(random, 10));
        } else {
            goal = reachable.get(this.self.getRandom().nextInt(reachable.size()));
        }
        this.moveTarget = Vec3.atLowerCornerOf(goal);
        this.counter = 0;
        this.self.setBossState(S_FLY);
    }

    private static int signed(net.minecraft.util.RandomSource random, int range) {
        return (int) (random.nextFloat() * range) * (random.nextBoolean() ? 1 : -1);
    }

    /** Original be.a(n): an index in 0..n, larger indices more likely. */
    private int weightedIndex(int n) {
        if (n <= 0) {
            return n;
        }
        int total = n * (n + 1) / 2;
        int roll = this.self.getRandom().nextInt(total) + 1;
        int sum = 0;
        for (int i = 0; i <= n; i++) {
            sum += i;
            if (sum >= roll) {
                return i;
            }
        }
        return n;
    }

    private void tickChangePosition() {
        if (this.moveTarget == null) {
            return;
        }
        int n = this.counter++;
        if (n != 0) {
            return;
        }
        Vec3 delta = this.moveTarget.subtract(this.self.position());
        Vec3 dir = delta.normalize();
        this.self.setDeltaMovement(dir.x * 0.6D, Math.max(-0.6D, Math.min(0.6D, delta.y * 0.6D)), dir.z * 0.6D);
    }

    private void fixNoClip(ServerLevel level) {
        if (this.attack != Attack.CHANGE_POSITION) {
            return;
        }
        this.self.noPhysics = this.counter == 0;
        if (!level.getBlockState(this.self.blockPosition()).isAir()) {
            this.self.noPhysics = true;
        }
    }

    // SUMMON_SKELETON

    private void updateParts() {
        int tick = this.self.summonTick();
        boolean active = this.self.bossState() == S_SUMMON && tick >= 9 && tick <= 30;
        this.right.setActive(active && this.self.bossFlag(F_ORB_RIGHT));
        this.left.setActive(active && this.self.bossFlag(F_ORB_LEFT));
        if (!active) {
            return;
        }
        boolean mirror = this.self.bossFlag(F_MIRROR);
        Vec3 pos = this.self.position();
        float yaw = 180.0F + this.self.yBodyRot;
        Vec3 r = pos.add(rotate(mirror ? mirror(ORB_RIGHT) : ORB_RIGHT, yaw));
        Vec3 l = pos.add(rotate(mirror ? mirror(ORB_LEFT) : ORB_LEFT, yaw));
        this.right.moveTo(r.x, r.y, r.z, this.self.yBodyRot, 0.0F);
        this.left.moveTo(l.x, l.y, l.z, this.self.yBodyRot, 0.0F);
    }

    private static Vec3 mirror(Vec3 v) {
        return new Vec3(-v.x, v.y, -v.z); // original ck.c
    }

    /** Original ck.a(vec, yaw). */
    static Vec3 rotate(Vec3 v, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vec3(-cos * v.x - sin * v.z, v.y, -sin * v.x + cos * v.z);
    }

    private void tickSummon(ServerLevel level) {
        this.self.setDeltaMovement(Vec3.ZERO);
        if (this.self.summonTick() != 30) {
            return;
        }
        LivingEntity target = this.target();
        if (target == null) {
            return;
        }
        Vec3 pos = this.self.position();
        boolean mirror = this.self.bossFlag(F_MIRROR);
        float yaw = 180.0F + this.self.yBodyRot;
        if (this.self.bossFlag(F_ORB_RIGHT)) {
            this.fireOrb(level, pos.add(rotate(mirror ? mirror(ORB_RIGHT) : ORB_RIGHT, yaw)), target.position());
        }
        if (this.self.bossFlag(F_ORB_LEFT)) {
            this.fireOrb(level, pos.add(rotate(mirror ? mirror(ORB_LEFT) : ORB_LEFT, yaw)), target.position());
        }
    }

    private void fireOrb(ServerLevel level, Vec3 from, Vec3 to) {
        var random = this.self.getRandom();
        Vec3 dir = to.subtract(from).normalize();
        dir = new Vec3(dir.x + random.nextDouble() * 0.3D, dir.y + random.nextDouble() * 0.3D, dir.z + random.nextDouble() * 0.3D).normalize();
        EnergyBallEntity ball = com.trolmastercard.sexmod.registry.ModEntities.ENERGY_BALL.get().create(level);
        if (ball == null) {
            return;
        }
        ball.launch(this.self, dir.scale(EnergyBallEntity.SPEED));
        ball.setPos(from);
        level.addFreshEntity(ball);
    }

    // ATTACK_SWORD

    private void tickSword() {
        LivingEntity target = this.target();
        if (target == null) {
            return;
        }
        int n = ++this.swordTick;
        if (n >= 24 && n < 32) {
            Vec3 eye = target.position().add(0.0D, target.getEyeHeight(), 0.0D);
            float yaw = (float) Math.toDegrees(Math.atan2(eye.z - this.self.getZ(), eye.x - this.self.getX())) - 90.0F;
            this.lockYaw = yaw;
            Vec3 goal = eye.add(rotate(new Vec3(0.0D, 0.0D, 3.0D), yaw + 180.0F));
            double f = (n - 24) / 8.0D;
            this.lockPos = this.swordOrigin.add(goal.subtract(this.swordOrigin).scale(f));
            return;
        }
        if (n >= 32 && n < 54) {
            this.lockPos = target.position().add(rotate(new Vec3(0.0D, 0.0D, 1.5D), this.lockYaw + 180.0F));
            target.hurtTime = 0;
            target.invulnerableTime = 0;
            if (n == 36 || n == 40) {
                target.hurt(galathSource(this.self), 5.0F);
            }
            return;
        }
        if (n == 54) {
            this.locked = false;
            this.self.setBossState(S_FLY);
            Vec3 back = this.swordOrigin.subtract(this.self.position()).normalize();
            this.self.setDeltaMovement(back.scale(0.6D));
            this.counter = 1;
            return;
        }
        this.counter++;
    }

    private void lockAt(Vec3 pos, float yaw) {
        this.locked = true;
        this.lockPos = pos;
        this.lockYaw = yaw;
    }

    // RAPE (charge)

    private Vec3 overshoot() {
        Vec3 over = this.moveTarget.add(this.moveTarget.subtract(this.chargeStart));
        return new Vec3(over.x, this.chargeStart.y, over.z);
    }

    private void tickCharge(ServerLevel level) {
        LivingEntity target = this.target();
        if (target == null) {
            return;
        }
        if (this.self.bossState() == S_PREPARE) {
            this.self.setDeltaMovement(Vec3.ZERO);
            if (++this.prepareTicks < 48) {
                return;
            }
            this.self.setBossState(S_CHARGE);
        }
        if (this.self.bossState() != S_CHARGE) {
            return;
        }
        if (this.chargeStart == null) {
            this.moveTarget = target.position().add(0.0D, target.getEyeHeight() / 2.0D, 0.0D);
            this.chargeStart = this.self.position();
            Vec3 dir = target.position().subtract(this.self.position()).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0F;
            this.self.setYRot(yaw);
            this.self.yBodyRot = yaw;
            this.self.yHeadRot = yaw;
        }
        Vec3 pos = this.self.position();
        AABB box = new AABB(pos.x - 0.65D, pos.y - 0.65D, pos.z - 0.65D, pos.x + 0.65D, pos.y + 0.65D, pos.z + 0.65D);
        for (Player player : level.getEntitiesOfClass(Player.class, box)) {
            if (!player.isAlive() || !player.onGround() || CompanionEntity.playerInScene(player)
                    || player.isCreative() || player.isSpectator()) {
                continue;
            }
            Vec3 local = rotate(pos.subtract(player.position()), this.self.getYRot());
            if (Math.abs(local.x) > 0.65D) {
                continue;
            }
            this.catchPlayer(level, player);
            return;
        }
        Vec3 start = this.chargeStart;
        Vec3 goal = this.moveTarget;
        Vec3 over = this.overshoot();
        boolean secondHalf = horizontal(pos.distanceTo(new Vec3(start.x, pos.y, start.z)))
                > horizontal(pos.distanceTo(new Vec3(over.x, pos.y, over.z)));
        double t;
        double length;
        if (secondHalf) {
            length = goal.distanceTo(over);
            t = progress(goal, over, pos);
        } else {
            length = start.distanceTo(goal);
            t = progress(start, goal, pos);
        }
        if (length < 1.0E-4D) {
            length = 1.0E-4D;
        }
        t += 1.0D / length; // one block per tick (original 0.05 per step x 20)
        if (!secondHalf && t < 0.9D) {
            this.moveTarget = target.position().add(0.0D, target.getEyeHeight() / 2.0D, 0.0D);
            goal = this.moveTarget;
        }
        Vec3 next;
        if (secondHalf) {
            double c = Math.min(1.0D, t);
            next = new Vec3(lerp(goal.x, over.x, c), lerp(goal.y, over.y, Math.min(1.0D, c * c * c)), lerp(goal.z, over.z, c));
        } else {
            double c = Math.min(1.0D, t);
            next = new Vec3(lerp(start.x, goal.x, c), lerp(start.y, goal.y, 1.0D - Math.pow(1.0D - c, 3.0D)), lerp(start.z, goal.z, c));
        }
        this.self.setPos(next);
    }

    private static double horizontal(double v) {
        return v;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Fraction of the way from a to b at which p lies (original ck.a on the x axis, falling back to z). */
    private static double progress(Vec3 a, Vec3 b, Vec3 p) {
        double dx = b.x - a.x;
        if (Math.abs(dx) > 1.0E-6D) {
            return (p.x - a.x) / dx;
        }
        double dz = b.z - a.z;
        return Math.abs(dz) > 1.0E-6D ? (p.z - a.z) / dz : 0.0D;
    }

    private void catchPlayer(ServerLevel level, Player player) {
        this.clearSkeletons(level);
        this.self.setBossState(S_NONE);
        if (player instanceof ServerPlayer serverPlayer) {
            this.self.startWildScene(serverPlayer, CompanionScene.GALATH_WILD_RAPE);
        }
        this.endAttack();
        this.attack = null;
    }

    // ---- knock-out lifecycle

    private void tickKnockLanding(ServerLevel level) {
        if (this.self.bossState() != S_KO_FLY) {
            return;
        }
        BlockPos pos = this.self.blockPosition();
        if (level.getBlockState(pos).getBlock() instanceof LiquidBlock) {
            BlockPos top = pos;
            while (level.getBlockState(top.above()).getBlock() instanceof LiquidBlock) {
                top = top.above();
            }
            for (int dx = -1; dx < 2; dx++) {
                for (int dz = -1; dz < 2; dz++) {
                    level.setBlockAndUpdate(top.offset(dx, 0, dz), Blocks.OBSIDIAN.defaultBlockState());
                }
            }
            top = top.above();
            this.self.teleportTo(top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D);
            burst(level, Vec3.atBottomCenterOf(top));
            level.playSound(null, this.self.getX(), this.self.getY(), this.self.getZ(), SoundEvents.LAVA_EXTINGUISH,
                    SoundSource.AMBIENT, 1.0F, 1.0F);
            this.self.setBossState(S_KO_GROUND);
            return;
        }
        if (this.self.onGround()) {
            this.self.setBossState(S_KO_GROUND);
        }
    }

    private void freezeWhileDown() {
        int state = this.self.bossState();
        if (state != S_KO_GROUND && state != S_KO_STAND) {
            return;
        }
        Vec3 motion = this.self.getDeltaMovement();
        this.self.setDeltaMovement(0.0D, this.paralysed() ? 0.0D : motion.y, 0.0D);
    }

    private void tickKnockGround() {
        if (this.self.bossState() != S_KO_GROUND || this.paralysed()) {
            return;
        }
        if (++this.knockTicks < 50) {
            return;
        }
        this.self.setBossState(S_KO_STAND);
        this.standTicks = 0;
        this.knockTicks = 0;
    }

    private void tickStandUp(ServerLevel level) {
        if (this.self.bossState() != S_KO_STAND) {
            return;
        }
        ++this.standTicks;
        if (this.standTicks == 39) {
            this.self.setNoGravity(true);
            this.self.setDeltaMovement(0.0D, 0.6D, 0.0D);
            Vec3 pos = this.self.position();
            AABB box = new AABB(pos.x - 2.0D, pos.y - 2.0D, pos.z - 2.0D, pos.x + 2.0D, pos.y + 2.0D, pos.z + 2.0D);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (living instanceof CompanionEntity companion && companion.kind() == CompanionKind.GALATH) {
                    continue;
                }
                Vec3 dir = living.position().subtract(pos).normalize();
                living.setDeltaMovement(dir.x, 1.0D, dir.z);
                living.hurt(galathSource(this.self), 0.5F);
                living.hurtMarked = true;
            }
        }
        if (this.standTicks < 58) {
            return;
        }
        this.self.setDeltaMovement(Vec3.ZERO);
        this.self.setBossFlag(F_KNOCKED, false);
        this.standTicks = 0;
    }

    // ---- skeletons

    private int aliveSkeletons() {
        if (!(this.self.level() instanceof ServerLevel level)) {
            return 0;
        }
        int alive = 0;
        for (UUID id : this.skeletons) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                alive++;
            }
        }
        return alive;
    }

    private void cleanSkeletons(ServerLevel level) {
        this.skeletons.removeIf(id -> {
            Entity entity = level.getEntity(id);
            return entity == null || !entity.isAlive();
        });
    }

    private void cullSkeletons(ServerLevel level) {
        LivingEntity target = this.target();
        if (target == null) {
            return;
        }
        for (UUID id : new ArrayList<>(this.skeletons)) {
            Entity entity = level.getEntity(id);
            if (entity == null || !entity.isAlive() || target.distanceTo(entity) < 15.0F) {
                continue;
            }
            burst(level, entity.position());
            entity.discard();
        }
    }

    private void clearSkeletons(ServerLevel level) {
        for (UUID id : this.skeletons) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                burst(level, entity.position());
                entity.discard();
            }
        }
        this.skeletons.clear();
    }

    /** Original per-tick while knocked out (aD) and skeleton sparkle (aA). */
    public void tickSkeletonFx(ServerLevel level) {
        if (this.knocked()) {
            this.clearSkeletons(level);
            return;
        }
        for (UUID id : this.skeletons) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive() && entity.tickCount % 10 == 0) {
                level.sendParticles(ParticleTypes.DRAGON_BREATH, entity.getX(), entity.getY() + entity.getEyeHeight() / 2.0D,
                        entity.getZ(), 5, 0.2D, 0.0D, 0.2D, 0.0D);
            }
        }
    }

    // ---- the lock (original a(true) / c(pos))

    /** Applied after everything else so the lock wins over any motion. */
    public void applyLock() {
        if (this.locked && this.lockPos != null) {
            this.self.setPos(this.lockPos);
            this.self.setYRot(this.lockYaw);
            this.self.yBodyRot = this.lockYaw;
            this.self.yHeadRot = this.lockYaw;
            this.self.setDeltaMovement(Vec3.ZERO);
        }
    }

    // ---- helpers

    private void sound(String prefix, int count) {
        this.sound(prefix, this.self.getRandom().nextInt(count), 0);
    }

    private void sound(String prefix, int index, int ignored) {
        ModSounds.find(prefix + index).ifPresent(sound -> this.self.level().playSound(null, this.self.getX(), this.self.getY(),
                this.self.getZ(), sound.get(), SoundSource.HOSTILE, 1.0F, 1.0F));
    }

    public static DamageSource galathSource(CompanionEntity galath) {
        return source(galath, "galath", galath);
    }

    public static DamageSource succubusSource(CompanionEntity galath) {
        return source(galath, "succubus", galath);
    }

    private static DamageSource source(Entity context, String path, Entity attacker) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("sexmod", path));
        return new DamageSource(context.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), attacker);
    }

    /** Original bv(pos, true): a burst of 100 dragon breath particles. */
    public static void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z, 100, 0.0D, 0.0D, 0.0D, 0.1D);
    }

    /** Original bv(pos, false): a ring of smoke and the shatter sound. */
    public static void smokeRing(ServerLevel level, Vec3 at) {
        for (float angle = 0.0F; angle < Math.PI * 2; angle += 1.8F) {
            double s = Math.sin(angle);
            double c = Math.cos(angle);
            level.sendParticles(ParticleTypes.SMOKE, at.x + s * 0.5D, at.y, at.z + c * 0.5D, 0, s * 0.15D,
                    level.random.nextDouble() * 0.15D, c * 0.15D, 1.0D);
        }
        ModSounds.find("misc.shatter.shatter0").ifPresent(sound -> level.playSound(null, at.x, at.y, at.z, sound.get(),
                SoundSource.AMBIENT, 0.7F, 1.0F));
    }

    // ---- spawning (original CheckSpawn hijack of Wither Skeletons and Blazes)

    /** Original f_.a(BlockPos, World): open sky above, no liquid below, no other Galath or spawn point within 1000 blocks. */
    public static boolean canSpawnAt(ServerLevel level, BlockPos pos) {
        GalathBindingData data = GalathBindingData.get(level.getServer());
        if (data.spawnNear(pos, 1000.0D, false, true)) {
            return false;
        }
        for (CompanionEntity other : level.getEntities(com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GALATH),
                e -> e.isAlive() && e.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 1000.0D * 1000.0D)) {
            return false;
        }
        for (int i = 0; i < 15; i++) {
            if (!level.getBlockState(pos.above(i)).isAir()) {
                return false;
            }
        }
        for (int i = 0; i < 5; i++) {
            if (level.getBlockState(pos.below(i)).getBlock() instanceof LiquidBlock) {
                return false;
            }
        }
        return true;
    }
}
