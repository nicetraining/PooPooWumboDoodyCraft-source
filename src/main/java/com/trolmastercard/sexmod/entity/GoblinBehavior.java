package com.trolmastercard.sexmod.entity;

import net.minecraft.network.chat.Component;
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

    private static final int ROB_PERIOD = 32000;
    private static final int PICK_UP_TICKS = 45;
    private static final int THROW_RELEASE = 15;
    private static final int THROW_END = 39;
    private static final int CATCH_DIALOGUE_END = 170; // catchDone at 8.5 s
    private static final int CATCH_BJ_END = 28; // catchBjDone at 1.4167 s
    private static final int SCENE_FADE = 15;

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
            return;
        }
        if (this.sceneSeen) {
            this.sceneSeen = false;
            this.afterScene();
        }
        if (this.pendingScene != null) {
            this.tickPending(level);
            return;
        }
        this.stateTicks++;
        if (this.queen) {
            this.tickQueen(level);
            return;
        }
        Player carrier = this.carrier(level);
        switch (this.k.goblinState()) {
            case NULL -> {
                if (this.queenId != null && !this.k.goblinTamed()) {
                    this.k.discard(); // a calm robber vanishes (and drops what it holds)
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
                if (this.k.onGround()) {
                    if (++this.groundTicks >= 30) {
                        this.groundTicks = 0;
                        this.setState(STAND_UP);
                    }
                } else if (!this.k.goblinTamed() && this.queenId != null) {
                    this.fade -= 0.05F;
                    if (this.fade <= 0.0F) {
                        this.k.discard();
                    }
                }
            }
            case STAND_UP -> {
                if (this.stateTicks >= 37) {
                    this.setState(NULL);
                }
            }
            case VANISH -> {
                this.fade -= 0.05F;
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
            this.k.setPos(carrier.getX(), carrier.getEyeY() - 0.2D, carrier.getZ());
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
        if (this.k.goblinState() != SIT) {
            this.setState(SIT);
        }
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
        if (this.k.goblinTamed() || this.k.isPreggo()) {
            return;
        }
        if (++this.robTicks < ROB_PERIOD) {
            return;
        }
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : level.players()) {
            if (!player.onGround() || player.getAbilities().flying || player.isSpectator()) {
                continue;
            }
            double dx = player.getX() - this.k.getX();
            double dz = player.getZ() - this.k.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > 100.0D || horizontal >= bestDistance || slotOfValuable(player) < 0) {
                continue;
            }
            best = player;
            bestDistance = horizontal;
        }
        if (best == null) {
            return;
        }
        int slot = slotOfValuable(best);
        ItemStack stolen = best.getInventory().getItem(slot).copy();
        CompanionEntity robber = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
        if (robber == null || robber.goblin() == null) {
            return;
        }
        Vec3 behind = best.position().add(Vec3.directionFromRotation(0.0F, best.getYHeadRot() + 180.0F).scale(0.2D));
        robber.moveTo(behind.x, behind.y, behind.z, best.getYHeadRot(), 0.0F);
        robber.goblin().queenId = this.k.getUUID();
        robber.setGoblinItem(stolen);
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
