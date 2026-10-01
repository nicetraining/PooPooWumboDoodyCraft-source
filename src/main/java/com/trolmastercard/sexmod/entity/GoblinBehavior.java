package com.trolmastercard.sexmod.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Goblin (original e3), non-scene part: the queen's robbery cycle, the fleeing robber, catching, picking up onto the
 * shoulder, throwing. Breeding (queen + guards) is separate.
 */
public final class GoblinBehavior {
    public static final int NULL = 0;
    public static final int RUN = 1;
    public static final int CATCH = 2;
    public static final int CATCH_BJ = 3;
    public static final int CATCH_BJ_IDLE = 4;
    public static final int PICK_UP = 5;
    public static final int SHOULDER = 6;
    public static final int THROWING = 7;
    public static final int THROWN = 8;
    public static final int STAND_UP = 9;
    public static final int SIT = 10;
    public static final int AWAIT_PICK_UP = 11;
    public static final int VANISH = 12;
    public static final int JUMP = 13;
    public static final int JUMP_A = 14;
    public static final int JUMP_B = 15;
    public static final int BREED_A = 16;
    public static final int BREED_B = 17;

    private static final int ROB_PERIOD = 32000;
    private static final int PICK_UP_TICKS = 45;
    private static final int THROW_RELEASE = 15;
    private static final int THROW_END = 39;
    private static final int CATCH_DIALOGUE_END = 170; // catchDone at 8.5 s
    private static final int CATCH_BJ_END = 28; // catchBjDone at 1.4167 s
    private static final int SCENE_FADE = 15;
    private static final int PREGNANCY_TICKS = 8400;
    private static final int NAG_COOLDOWN = 1200;
    private static final int JUMP_TICKS = 26;
    /** Throne offsets by throne yaw (original aM, U, aB, ao) and the breeding spot relative to the throne (af, au, W, at). */
    private static final Vec3[] THRONE = {new Vec3(5, 1, 1), new Vec3(9, 1, 5), new Vec3(5, 1, 9), new Vec3(1, 1, 5)};
    private static final Vec3[] BREED_SPOT = {new Vec3(3, -1, 6), new Vec3(-6, -1, 3), new Vec3(-3, -1, -6), new Vec3(6, -1, -3)};

    private static final Set<Item> VALUABLES = Set.of(Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLDEN_APPLE, Items.GOLDEN_CARROT,
            Items.GOLDEN_HORSE_ARMOR, Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_AXE,
            Items.GOLDEN_HOE, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
            Items.GOLD_BLOCK, Items.GOLD_ORE);

    private final CompanionEntity k;
    public boolean queen;
    public UUID queenId;
    public int robTicks = ROB_PERIOD - 480;
    /** Original al / ac: where the queen sits and which way she faces. */
    public Vec3 thronePos;
    public float throneRot;
    private int stateTicks;
    private int pickTicks = -1;
    private float fade = 1.0F;
    private int groundTicks;
    private int fleeTicks;
    private CompanionScene pendingScene;
    private int pendingTicks;
    private UUID pendingPlayer;
    private boolean sceneSeen;
    /** Queen: her two guards, when she was last impregnated (game time) and the breeding progress (0 idle, 1 jumping, 2 in scene). */
    public final List<UUID> guards = new java.util.ArrayList<>();
    public long impregnationTick = -PREGNANCY_TICKS - 1L;
    private int breedStage;
    private int breedTicks;
    private UUID breedPlayer;
    private long lastNag = -NAG_COOLDOWN;
    private int lastPhase = -1;

    public GoblinBehavior(CompanionEntity k) {
        this.k = k;
    }

    public boolean isQueen() {
        return this.queen;
    }

    public void setState(int state) {
        this.k.setGoblinState(state);
        this.stateTicks = 0;
    }

    private Player carrier(ServerLevel level) {
        return this.k.goblinCarrier().map(level::getPlayerByUUID).orElse(null);
    }

    /** Original d(UUID): does this player already carry a goblin? */
    public static boolean carries(ServerLevel level, Player player) {
        for (CompanionEntity other : level.getEntitiesOfClass(CompanionEntity.class, player.getBoundingBox().inflate(24.0D),
                e -> e.goblin() != null)) {
            if (other.goblinCarrier().filter(player.getUUID()::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- interaction

    public void interact(Player player) {
        if (this.queen) {
            return;
        }
        int state = this.k.goblinState();
        if (state == RUN) {
            if (this.k.distanceTo(player) > 3.5F) {
                player.displayClientMessage(Component.literal("get a bit closer..."), true);
                return;
            }
            this.k.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            this.k.setGoblinCarrier(player.getUUID());
            this.setState(CATCH);
            this.k.getNavigation().stop();
            this.k.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (state != NULL && state != STAND_UP && state != AWAIT_PICK_UP) {
            return;
        }
        if (carries((ServerLevel) this.k.level(), player)) {
            player.displayClientMessage(Component.literal("you are already carrying a Goblin"), true);
            return;
        }
        this.k.setGoblinCarrier(player.getUUID());
        this.setState(PICK_UP);
        this.pickTicks = PICK_UP_TICKS;
        this.k.setGoblinTamed(true);
        this.k.getNavigation().stop();
    }

    /** Catch menu / wheel choices. */
    public void choose(Player player, int op) {
        if (!this.k.goblinCarrier().filter(player.getUUID()::equals).isPresent() && this.k.goblinState() != NULL) {
            return;
        }
        switch (op) {
            case 0 -> this.takeBack(player);
            case 1 -> this.queueScene(player, CompanionScene.GOBLIN_PAIZURI);
            case 2 -> this.queueScene(player, CompanionScene.GOBLIN_NELSON);
            default -> {
            }
        }
    }

    /** Original START_THROWING: the stolen item goes back, then the goblin is thrown away. */
    private void takeBack(Player player) {
        int state = this.k.goblinState();
        if (state != CATCH_BJ_IDLE && state != SHOULDER) {
            return;
        }
        ItemStack stolen = this.k.goblinItem();
        if (!stolen.isEmpty()) {
            player.getInventory().add(stolen.copy());
            this.k.setGoblinItem(ItemStack.EMPTY);
        }
        this.setState(THROWING);
    }

    private void queueScene(Player player, CompanionScene scene) {
        int state = this.k.goblinState();
        if (state != CATCH_BJ_IDLE && state != SHOULDER) {
            return;
        }
        if (player instanceof ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new com.trolmastercard.sexmod.network.TransitionPayload());
        }
        this.pendingScene = scene;
        this.pendingTicks = SCENE_FADE;
        this.pendingPlayer = player.getUUID();
    }

    // ---------------------------------------------------------------- tick

    public void tick(ServerLevel level) {
        if (this.k.isInScene()) {
            this.sceneSeen = true;
            if (this.queen && this.breedStage == 2) {
                this.mirrorGuards(level);
            }
            return;
        }
        if (this.sceneSeen) {
            this.sceneSeen = false;
            this.afterScene();
        } else if (this.queen && this.breedStage == 2) {
            this.endBreeding(level); // the scene ended before we ever saw it running
        }
        if (this.pendingScene != null) {
            this.tickPending(level);
            return;
        }
        this.stateTicks++;
        if (this.k.goblinState() == THROWING) {
            this.k.setGoblinPhase(Math.min(this.stateTicks, 255));
        }
        if (this.k.goblinModel().isEmpty()) {
            this.k.setGoblinModel(this.queen ? GoblinModel.queen(this.k.getRandom()) : GoblinModel.random(this.k.getRandom()));
        }
        if (this.queen) {
            this.tickQueen(level);
            return;
        }
        Player carrier = this.carrier(level);
        switch (this.k.goblinState()) {
            case NULL -> {
                if (this.queenId != null && !this.k.goblinTamed()) {
                    this.k.discard(); // a calm robber vanishes (and drops what it holds)
                } else if (this.k.goblinTamed()) {
                    this.tamedIdle(level);
                }
            }
            case RUN -> this.flee(level);
            case CATCH -> {
                if (!this.pin(carrier)) {
                    return;
                }
                if (this.stateTicks >= CATCH_DIALOGUE_END) {
                    this.setState(CATCH_BJ);
                }
            }
            case CATCH_BJ -> {
                if (!this.pin(carrier)) {
                    return;
                }
                if (this.stateTicks >= CATCH_BJ_END) {
                    this.setState(CATCH_BJ_IDLE);
                    if (carrier instanceof ServerPlayer sp) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new com.trolmastercard.sexmod.network.OpenSceneMenuPayload(
                                this.k.getId(), List.of(CompanionScene.GOBLIN_USE_HER.serializedName(),
                                CompanionScene.GOBLIN_TAKE_BACK.serializedName())));
                    }
                }
            }
            case CATCH_BJ_IDLE -> this.pin(carrier);
            case PICK_UP -> {
                if (carrier == null || this.k.distanceTo(carrier) > 10.0F) {
                    this.release();
                    return;
                }
                this.follow(carrier);
                if (--this.pickTicks <= 0) {
                    this.setState(SHOULDER);
                    this.k.noPhysics = true;
                }
            }
            case SHOULDER -> {
                if (carrier == null) {
                    this.release();
                    return;
                }
                this.follow(carrier);
                this.k.noPhysics = true;
            }
            case THROWING -> this.tickThrow(carrier);
            case THROWN -> {
                this.k.noPhysics = false;
                this.k.setNoGravity(false);
                if (!this.k.goblinTamed()) {
                    // original e3.H: an untamed thrown goblin fades away once it touches the ground or water
                    if (this.k.onGround() || this.k.isInWater()) {
                        this.fade -= 0.05F;
                        this.k.setGoblinFade(Math.max(0.0F, this.fade));
                        if (this.fade <= 0.0F) {
                            this.k.discard();
                        }
                    }
                } else if (this.k.onGround() && ++this.groundTicks >= 30) {
                    this.groundTicks = 0;
                    this.setState(STAND_UP);
                }
            }
            case STAND_UP -> {
                if (this.stateTicks >= 37) {
                    this.setState(NULL);
                }
            }
            case VANISH -> {
                this.fade -= 0.05F;
                this.k.setGoblinFade(Math.max(0.0F, this.fade));
                if (this.fade <= 0.0F) {
                    this.k.discard();
                }
            }
            default -> {
            }
        }
    }

    public float fade() {
        return this.fade;
    }

    private void release() {
        this.k.setGoblinCarrier(null);
        this.k.noPhysics = false;
        this.k.setNoGravity(false);
        this.setState(NULL);
    }

    private boolean pin(Player carrier) {
        if (carrier == null) {
            this.release();
            return false;
        }
        this.k.getNavigation().stop();
        this.k.setDeltaMovement(Vec3.ZERO);
        this.k.moveTo(carrier.getX(), carrier.getY(), carrier.getZ(), carrier.getYRot(), 0.0F);
        this.k.yBodyRot = carrier.getYRot();
        this.k.yHeadRot = carrier.getYRot();
        return true;
    }

    private void follow(Player carrier) {
        this.k.getNavigation().stop();
        this.k.setDeltaMovement(Vec3.ZERO);
        this.k.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
        this.k.setYRot(carrier.yBodyRot);
        this.k.yBodyRot = carrier.yBodyRot;
        this.k.yHeadRot = carrier.yBodyRot;
        this.k.setNoGravity(true);
    }

    private net.minecraft.core.BlockPos wanderSpot;
    private int wanderTicks;

    /** Original e3.n(): a tamed goblin faces a player who comes within 2 blocks, otherwise wanders around a spot at speed 0.3. */
    private void tamedIdle(ServerLevel level) {
        if (this.k.isInScene()) {
            return;
        }
        Player player = level.getNearestPlayer(this.k, 15.0D);
        if (player != null && player.distanceTo(this.k) < 2.0F) {
            double dx = player.getX() - this.k.getX();
            double dz = player.getZ() - this.k.getZ();
            this.k.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
            this.k.yHeadRot = this.k.getYRot();
            this.k.getNavigation().stop();
            return;
        }
        if (this.wanderSpot == null
                || this.k.distanceToSqr(this.wanderSpot.getX(), this.wanderSpot.getY(), this.wanderSpot.getZ()) > 800.0D
                || this.wanderTicks > 100) {
            int dx = (this.k.getRandom().nextBoolean() ? 1 : -1) * this.k.getRandom().nextInt(5);
            int dz = (this.k.getRandom().nextBoolean() ? 1 : -1) * this.k.getRandom().nextInt(5);
            int x = this.k.blockPosition().getX() + dx;
            int z = this.k.blockPosition().getZ() + dz;
            this.wanderSpot = new net.minecraft.core.BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            this.wanderTicks = 0;
        }
        if (Math.sqrt(this.wanderSpot.distSqr(this.k.blockPosition())) > 2.0D) {
            this.k.getNavigation().moveTo(this.wanderSpot.getX() + 0.5D, this.wanderSpot.getY(), this.wanderSpot.getZ() + 0.5D, 0.3D);
        }
        this.wanderTicks++;
    }

    private void tickThrow(Player carrier) {
        if (this.stateTicks == 1 && carrier != null) {
            this.k.setNoGravity(true);
        }
        if (this.stateTicks < THROW_RELEASE) {
            if (carrier != null) {
                this.follow(carrier);
            }
            return;
        }
        if (this.stateTicks == THROW_RELEASE && carrier != null) {
            Vec3 look = carrier.getLookAngle();
            this.k.noPhysics = false;
            this.k.setNoGravity(false);
            // original e3.e(): at the carrier's feet + eye height, 0.4 to the right of the head direction
            double headYaw = Math.toRadians(carrier.getYHeadRot());
            this.k.setPos(carrier.getX() - 0.4D * Math.cos(headYaw), carrier.getY() + carrier.getEyeHeight(),
                    carrier.getZ() - 0.4D * Math.sin(headYaw));
            this.k.setDeltaMovement(look.scale(1.5D));
            this.k.setYRot(carrier.getYRot());
            this.k.hasImpulse = true;
        }
        if (this.stateTicks >= THROW_END) {
            this.k.setGoblinCarrier(null);
            this.setState(THROWN);
        }
    }

    // ---------------------------------------------------------------- robber

    private void flee(ServerLevel level) {
        if (!this.k.onGround()) {
            return;
        }
        Player player = level.getNearestPlayer(this.k, 100.0D);
        if (player == null) {
            return;
        }
        if (++this.fleeTicks % 10 != 1 && !this.k.getNavigation().isDone()) {
            return;
        }
        Vec3 away = this.k.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        for (double distance = 20.0D; distance >= 1.0D; distance -= 1.0D) {
            Vec3 target = this.k.position().add(away.scale(distance));
            if (this.k.getNavigation().moveTo(target.x, target.y, target.z, 0.825D)) {
                return;
            }
        }
    }

    // ---------------------------------------------------------------- queen

    private void tickQueen(ServerLevel level) {
        if (this.breedStage == 1) {
            this.tickJump(level);
            return;
        }
        if (this.k.goblinState() != SIT) {
            this.setState(SIT);
        }
        if (this.k.isPreggo() && level.getGameTime() > this.impregnationTick + PREGNANCY_TICKS) {
            this.k.setGoblinPreggo(false);
        }
        this.tickGuards(level);
        if (this.thronePos != null) {
            // original m(): while nobody is with her she stays on the throne
            this.k.getNavigation().stop();
            if (this.k.position().distanceToSqr(this.thronePos) > 1.0E-4D) {
                this.k.moveTo(this.thronePos.x, this.thronePos.y, this.thronePos.z, this.throneRot, 0.0F);
            }
            this.k.setYRot(this.throneRot);
            this.k.yBodyRot = this.throneRot;
            this.k.yHeadRot = this.throneRot;
        }
        this.tickBreedingTrigger(level);
        if (this.k.goblinTamed() || this.k.isPreggo() || this.breedStage != 0) {
            return;
        }
        if (++this.robTicks < ROB_PERIOD) {
            return;
        }
        // original e3.B: only the single nearest player counts; it must be on the ground, not flying and close enough
        Player best = level.getNearestPlayer(this.k, 3000.0D);
        if (best == null || !best.onGround() || best.getAbilities().flying || best.isSpectator()) {
            return;
        }
        double dx = best.getX() - this.k.getX();
        double dz = best.getZ() - this.k.getZ();
        if (Math.sqrt(dx * dx + dz * dz) > 100.0D) {
            return;
        }
        List<Integer> slots = new java.util.ArrayList<>();
        for (int i = 0; i < best.getInventory().getContainerSize(); i++) {
            ItemStack candidate = best.getInventory().getItem(i);
            if (!candidate.isEmpty() && VALUABLES.contains(candidate.getItem())) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        int slot = slots.get(this.k.getRandom().nextInt(slots.size()));
        ItemStack stolen = best.getInventory().getItem(slot).copy();
        CompanionEntity robber = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
        if (robber == null || robber.goblin() == null) {
            return;
        }
        Vec3 behind = best.position().add(Vec3.directionFromRotation(0.0F, best.getYHeadRot() + 180.0F).scale(0.2D));
        robber.moveTo(behind.x, behind.y, behind.z, best.getYHeadRot(), 0.0F);
        robber.goblin().queenId = this.k.getUUID();
        robber.setGoblinItem(stolen);
        robber.setGoblinModel(GoblinModel.withBodyColor(robber.getRandom(), GoblinModel.bodyColorIndex(this.k.goblinModel())));
        level.addFreshEntity(robber);
        robber.goblin().setState(RUN);
        best.getInventory().setItem(slot, ItemStack.EMPTY);
        best.sendSystemMessage(Component.literal("<").append(robber.getDisplayName()).append("> I got your ")
                .append(stolen.getHoverName()).append(" hehe~"));
        this.robTicks = 0;
    }

    private static int slotOfValuable(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && VALUABLES.contains(stack.getItem())) {
                return slot;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- breeding (queen + two guards)

    private int throneIndex() {
        float yaw = Mth.wrapDegrees(this.throneRot);
        if (Math.abs(yaw) < 45.0F) {
            return 0;
        }
        if (yaw >= 45.0F && yaw < 135.0F) {
            return 1;
        }
        if (yaw <= -45.0F && yaw > -135.0F) {
            return 3;
        }
        return 2;
    }

    /** Original rotation of a throne-relative vector (entity-local x to the left, z forward). */
    private static Vec3 rotate(Vec3 v, float yaw) {
        double r = Math.toRadians(yaw);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    /** The hall's interior as a box (original E(): throne minus its offset in the hall template). */
    private net.minecraft.world.phys.AABB hallBox() {
        Vec3 origin = this.thronePos.subtract(0.5D, 0.0D, 0.5D).subtract(THRONE[this.throneIndex()]);
        return new net.minecraft.world.phys.AABB(origin, origin.add(11.0D, 6.0D, 11.0D));
    }

    private void tickBreedingTrigger(ServerLevel level) {
        if (this.thronePos == null || this.breedStage != 0 || this.k.goblinTamed()) {
            return;
        }
        for (Player player : level.getEntitiesOfClass(Player.class, this.hallBox())) {
            if (!player.onGround() || player.isSpectator() || player.getAbilities().flying) {
                continue;
            }
            if (this.k.isPreggo()) {
                if (level.getGameTime() - this.lastNag >= NAG_COOLDOWN) {
                    this.lastNag = level.getGameTime();
                    player.displayClientMessage(Component.literal("The Queen is still pregnant - so no breeding for you uwu"), true);
                }
                return;
            }
            this.startBreeding(level, player);
            return;
        }
    }

    private void startBreeding(ServerLevel level, Player player) {
        List<CompanionEntity> alive = this.aliveGuards(level);
        if (alive.size() != 2) {
            alive.forEach(CompanionEntity::discard);
            this.guards.clear();
            int colour = GoblinModel.bodyColorIndex(this.k.goblinModel());
            for (int i = 0; i < 2; i++) {
                CompanionEntity guard = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
                if (guard == null || guard.goblin() == null) {
                    return;
                }
                guard.goblin().queenId = this.k.getUUID();
                guard.setGoblinModel(GoblinModel.withBodyColor(guard.getRandom(), colour));
                guard.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
                level.addFreshEntity(guard);
                this.guards.add(guard.getUUID());
                alive.add(guard);
            }
        }
        this.breedPlayer = player.getUUID();
        this.breedStage = 1;
        this.breedTicks = 0;
        this.setState(JUMP);
        this.k.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
        for (int i = 0; i < alive.size(); i++) {
            CompanionEntity guard = alive.get(i);
            guard.goblin().setState(i == 0 ? JUMP_A : JUMP_B);
            guard.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
            guard.noPhysics = true;
            guard.setNoGravity(true);
        }
    }

    private List<CompanionEntity> aliveGuards(ServerLevel level) {
        List<CompanionEntity> result = new java.util.ArrayList<>();
        for (UUID id : this.guards) {
            if (level.getEntity(id) instanceof CompanionEntity guard && guard.isAlive() && guard.goblin() != null) {
                result.add(guard);
            }
        }
        return result;
    }

    /** Original t(): after 26 ticks of jumping the three go to the breeding spot and the scene begins. */
    private void tickJump(ServerLevel level) {
        Player player = this.breedPlayer == null ? null : level.getPlayerByUUID(this.breedPlayer);
        List<CompanionEntity> guards = this.aliveGuards(level);
        if (player == null || guards.size() != 2) {
            this.abortBreeding(guards);
            return;
        }
        this.k.getNavigation().stop();
        this.k.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
        for (CompanionEntity guard : guards) {
            guard.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
        }
        if (++this.breedTicks < JUMP_TICKS) {
            return;
        }
        Vec3 spot = this.thronePos.add(BREED_SPOT[this.throneIndex()]);
        this.k.setForcedSceneAnchor(spot, this.throneRot);
        this.setState(SIT);
        if (!this.k.startScene(player, CompanionScene.GOBLIN_BREEDING)) {
            this.abortBreeding(guards);
            return;
        }
        this.breedStage = 2;
        this.lastPhase = -1;
        for (int i = 0; i < guards.size(); i++) {
            CompanionEntity guard = guards.get(i);
            guard.goblin().setState(i == 0 ? BREED_A : BREED_B);
            guard.noPhysics = true;
            guard.setNoGravity(true);
            guard.setGoblinPhase(0);
            guard.moveTo(spot.x, spot.y, spot.z, this.throneRot, 0.0F);
        }
    }

    private void abortBreeding(List<CompanionEntity> guards) {
        this.breedStage = 0;
        guards.forEach(CompanionEntity::discard);
        this.guards.clear();
        this.setState(SIT);
    }

    /** While the scene runs the guards stand in the same spot and play the animation of the current phase. */
    private void mirrorGuards(ServerLevel level) {
        List<CompanionEntity> guards = this.aliveGuards(level);
        int phase = this.k.currentScenePhase();
        Vec3 spot = this.thronePos.add(BREED_SPOT[this.throneIndex()]);
        for (CompanionEntity guard : guards) {
            guard.setPos(spot);
            guard.setYRot(this.throneRot);
            guard.yBodyRot = this.throneRot;
            guard.yHeadRot = this.throneRot;
            guard.setDeltaMovement(Vec3.ZERO);
            guard.setGoblinPhase(phase);
        }
        if (phase == 3 && this.lastPhase != 3) {
            // original BREEDING_CUM_0: she is pregnant from now on
            this.k.setGoblinPreggo(true);
            this.impregnationTick = level.getGameTime();
            this.lastNag = level.getGameTime();
        }
        this.lastPhase = phase;
    }

    /** Original b(): the guards line up in front of the throne, the player is put opposite and must pick one. */
    private void endBreeding(ServerLevel level) {
        this.breedStage = 0;
        Player player = this.breedPlayer == null ? null : level.getPlayerByUUID(this.breedPlayer);
        List<CompanionEntity> guards = this.aliveGuards(level);
        float yaw = this.throneRot + 180.0F;
        Vec3[] stands = {new Vec3(1, -1, -3), new Vec3(-1, -1, -3)};
        for (int i = 0; i < guards.size() && i < 2; i++) {
            CompanionEntity guard = guards.get(i);
            Vec3 at = this.thronePos.add(rotate(stands[i], yaw));
            guard.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            guard.noPhysics = false;
            guard.setNoGravity(false);
            guard.goblin().setState(AWAIT_PICK_UP);
            guard.setGoblinPhase(0);
        }
        this.setState(SIT);
        if (player instanceof ServerPlayer sp) {
            Vec3 at = this.thronePos.add(rotate(new Vec3(0, -1, -4), yaw));
            sp.teleportTo(at.x, at.y, at.z);
            sp.setYRot(yaw);
            sp.setXRot(30.0F);
            sp.sendSystemMessage(Component.literal("<").append(this.k.getDisplayName()).append("> Thanks to you, my clan is soon going to get a few new members! In return I will bear of one of my guards to serve as your personal Onahole. Choose wisely~"));
        }
    }

    /** Original d(): once a guard has been picked the queen says goodbye and the other guards vanish. */
    private void tickGuards(ServerLevel level) {
        if (this.guards.isEmpty() || this.breedStage != 0) {
            return;
        }
        List<CompanionEntity> guards = this.aliveGuards(level);
        boolean picked = false;
        for (CompanionEntity guard : guards) {
            if (guard.goblinTamed()) {
                picked = true;
            }
        }
        if (!picked) {
            return;
        }
        Player player = this.breedPlayer == null ? null : level.getPlayerByUUID(this.breedPlayer);
        if (player != null) {
            player.sendSystemMessage(Component.literal("<").append(this.k.getDisplayName())
                    .append("> Farewell my knight. You are welcome once I am breedable again."));
        }
        for (CompanionEntity guard : guards) {
            if (!guard.goblinTamed()) {
                guard.goblin().setState(VANISH);
            }
        }
        this.guards.clear();
    }

    // ---------------------------------------------------------------- scene hand-off

    private void tickPending(ServerLevel level) {
        if (--this.pendingTicks > 0) {
            return;
        }
        CompanionScene scene = this.pendingScene;
        this.pendingScene = null;
        Player player = level.getPlayerByUUID(this.pendingPlayer);
        if (player == null) {
            this.release();
            return;
        }
        this.k.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
        this.k.noPhysics = false;
        this.k.setNoGravity(false);
        this.k.setGoblinCarrier(null);
        this.setState(NULL);
        if (!this.k.startScene(player, scene)) {
            this.release();
        }
    }

    /** Original D(): after the scene the robber is gone and the tamed goblin is free again. */
    private void afterScene() {
        if (this.queen) {
            if (this.breedStage == 2 && this.k.level() instanceof ServerLevel level) {
                this.endBreeding(level);
            }
            return;
        }
        this.k.setGoblinItem(ItemStack.EMPTY);
        this.k.noPhysics = false;
        this.k.setNoGravity(false);
        this.k.setGoblinCarrier(null);
        this.setState(NULL);
        if (!this.k.goblinTamed()) {
            this.k.discard();
        }
    }

    /** Drops the stolen item where the goblin dies / is removed (original func_70106_y). */
    public void dropItem() {
        ItemStack stolen = this.k.goblinItem();
        if (!stolen.isEmpty() && !this.k.level().isClientSide) {
            this.k.level().addFreshEntity(new ItemEntity(this.k.level(), this.k.getX(), this.k.getY(), this.k.getZ(), stolen.copy()));
            this.k.setGoblinItem(ItemStack.EMPTY);
        }
    }
}
