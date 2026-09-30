package com.trolmastercard.sexmod.tribe;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The work half of the original ff: assigned tasks (fell a tree, mine a 3x3 tunnel), the 27-slot inventory, deposit
 * into registered chests, idle wandering. Timings are the original's: one block per 100 ticks per miner, tree swing 78
 * ticks plus a 24 tick wait.
 */
public final class KoboldWork {
    private static final double WALK = 0.35D;
    private static final int MINE_TOTAL = 100;
    private static final int MINE_BREAK_AT = 24;
    private static final int CHOP_SWING = 78;
    private static final int CHOP_WAIT = 24;

    private final CompanionEntity k;
    private BlockPos target;
    private int standStill;
    private int approachTicks;
    private int mineTimer = MINE_TOTAL;
    private int mineSwing = MINE_BREAK_AT;
    private int chopSwing = CHOP_SWING;
    private int chopWait = CHOP_WAIT;
    private BlockPos chopSpot;
    private boolean full;
    private int fullChatCooldown;
    private int wanderTicks;
    private BlockPos wanderTarget;

    KoboldWork(CompanionEntity k) {
        this.k = k;
    }

    // ---------------------------------------------------------------- inventory

    private Container inventory() {
        return this.k.storage();
    }

    /** Merge first into stacks of the same item, then into the first empty slot (NBT is not compared, like the original). */
    static ItemStack insert(Container container, ItemStack stack, boolean simulate) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItem(existing, rest)) {
                int room = Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
                int moved = Math.min(room, rest.getCount());
                if (moved > 0) {
                    if (!simulate) {
                        existing.grow(moved);
                        container.setChanged();
                    }
                    rest.shrink(moved);
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                int moved = Math.min(rest.getMaxStackSize(), rest.getCount());
                if (!simulate) {
                    container.setItem(slot, rest.copyWithCount(moved));
                    container.setChanged();
                }
                rest.shrink(moved);
            }
        }
        return rest;
    }

    private void addToInventory(ItemStack stack) {
        ItemStack rest = insert(this.inventory(), stack, false);
        if (!rest.isEmpty()) {
            this.k.spawnAtLocation(rest); // overflow becomes a world item
        }
    }

    private boolean canStore(List<ItemStack> stacks) {
        SimpleContainer copy = new SimpleContainer(this.inventory().getContainerSize());
        for (int i = 0; i < copy.getContainerSize(); i++) {
            copy.setItem(i, this.inventory().getItem(i).copy());
        }
        for (ItemStack stack : stacks) {
            if (!insert(copy, stack, false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean inventoryHasSpace() {
        for (int i = 0; i < this.inventory().getContainerSize(); i++) {
            if (this.inventory().getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- task bookkeeping

    void release(Tribe tribe) {
        TribeTask task = tribe.taskOf(this.k.getUUID());
        if (task != null) {
            task.workers.remove(this.k.getUUID());
        }
        this.target = null;
        this.chopSpot = null;
        this.standStill = 0;
        this.approachTicks = 0;
        this.mineTimer = MINE_TOTAL;
        this.mineSwing = MINE_BREAK_AT;
        this.chopSwing = CHOP_SWING;
        this.chopWait = CHOP_WAIT;
        if (this.k.koboldState() == KoboldBehavior.ST_WORK) {
            this.k.setKoboldState(KoboldBehavior.ST_NONE);
            this.k.setNoGravity(false);
            this.k.noPhysics = false;
        }
        this.k.setKoboldChopping(false);
    }

    private void finishTask(ServerLevel level, Tribe tribe, TribeTask task, boolean impossible) {
        for (java.util.UUID worker : new ArrayList<>(task.workers)) {
            CompanionEntity other = TribeManager.find(level.getServer(), worker);
            if (other != null && other.koboldBehavior() != null) {
                other.koboldBehavior().work().release(tribe);
            }
        }
        tribe.tasks.remove(task);
        TribeManager.markers(level, tribe, task.blocks, false);
        TribeData.get(level.getServer()).touch();
        if (impossible && !task.blocks.isEmpty() && tribe.master != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tribe.master);
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("<" + this.k.koboldName() + "> It's impossible to mine here..."));
            }
        }
    }

    /** Original g(tribe): continue the current task, otherwise take the nearest free one. Returns true while busy. */
    boolean work(ServerLevel level, Tribe tribe) {
        this.fullChatCooldown = Math.max(0, this.fullChatCooldown - 1);
        TribeTask task = tribe.taskOf(this.k.getUUID());
        if (task == null) {
            task = this.pickTask(level, tribe);
        }
        if (task == null) {
            return false;
        }
        boolean busy = task.kind == TribeTask.Kind.MINE ? this.mine(level, tribe, task) : this.fell(level, tribe, task);
        return busy;
    }

    private TribeTask pickTask(ServerLevel level, Tribe tribe) {
        List<CompanionEntity> members = TribeManager.loadedMembers(level.getServer(), tribe);
        for (TribeTask task : new ArrayList<>(tribe.tasks)) {
            if (task.workers.size() >= task.kind.maxWorkers) {
                continue;
            }
            // only the nearest free (non-working, non-interacting) member of the same ownership takes it
            CompanionEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (CompanionEntity member : members) {
                if (member.isOwnedKobold() != this.k.isOwnedKobold() || tribe.taskOf(member.getUUID()) != null
                        || member.isInScene() || member.koboldState() == KoboldBehavior.ST_SLEEP) {
                    continue;
                }
                double distance = member.distanceToSqr(Vec3.atCenterOf(task.origin));
                if (distance < best) {
                    best = distance;
                    nearest = member;
                }
            }
            if (nearest != this.k) {
                continue;
            }
            List<ItemStack> drops = new ArrayList<>();
            for (BlockPos pos : task.blocks) {
                drops.add(new ItemStack(level.getBlockState(pos).getBlock().asItem()));
            }
            if (!this.canStore(drops)) {
                this.full = true;
                continue;
            }
            if (!task.addWorker(this.k.getUUID())) {
                continue;
            }
            this.k.sayNearby(level, Component.literal(task.kind == TribeTask.Kind.MINE ? "Ima go mine uwu" : "Ima fall this tree owo"));
            if (task.kind == TribeTask.Kind.MINE) {
                this.portalTeleport(level, Vec3.atBottomCenterOf(task.origin));
                level.setBlock(task.origin, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            return task;
        }
        return null;
    }

    // ---------------------------------------------------------------- mining

    private BlockPos nextMineTarget(TribeTask task) {
        Direction dir = task.facing;
        int minDepth = Integer.MAX_VALUE;
        for (BlockPos pos : task.blocks) {
            minDepth = Math.min(minDepth, depth(task, pos, dir));
        }
        if (minDepth == Integer.MAX_VALUE) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : task.blocks) {
            if (depth(task, pos, dir) <= minDepth + 1) {
                candidates.add(pos);
            }
        }
        return candidates.get(this.k.getRandom().nextInt(candidates.size()));
    }

    private static int depth(TribeTask task, BlockPos pos, Direction dir) {
        BlockPos delta = pos.subtract(task.origin);
        return delta.getX() * dir.getStepX() + delta.getY() * dir.getStepY() + delta.getZ() * dir.getStepZ();
    }

    private boolean mine(ServerLevel level, Tribe tribe, TribeTask task) {
        if (this.target == null || !task.blocks.contains(this.target)) {
            this.target = this.nextMineTarget(task);
            this.standStill = 0;
            this.approachTicks = 0;
            this.mineTimer = MINE_TOTAL;
            this.mineSwing = MINE_BREAK_AT;
        }
        if (this.target == null) {
            this.finishTask(level, tribe, task, false);
            this.release(tribe);
            return false;
        }
        if (level.getBlockState(this.target).isAir()) {
            task.blocks.remove(this.target);
            TribeManager.markers(level, tribe, List.of(this.target), false);
            this.target = null;
            return true;
        }
        if (Math.abs(this.k.getY() - task.origin.getY()) > 3.0D) {
            this.portalTeleport(level, Vec3.atBottomCenterOf(task.origin.relative(task.facing.getOpposite())));
        }

        BlockState state = level.getBlockState(this.target);
        List<ItemStack> drop = List.of(new ItemStack(state.getBlock().asItem()));
        if (!this.canStore(drop)) {
            this.full = true;
            this.k.setKoboldState(KoboldBehavior.ST_NONE);
            this.deposit(level, tribe, true);
            return true;
        }

        if (this.k.koboldState() == KoboldBehavior.ST_WORK) {
            this.k.getNavigation().stop();
            this.faceBlock(this.target);
            this.mineTimer--;
            this.mineSwing--;
            if (this.mineSwing == 0) {
                BlockPos above = this.target.above();
                if (!(level.getBlockState(above).getBlock() instanceof FallingBlock)) {
                    task.blocks.remove(this.target);
                    TribeManager.markers(level, tribe, List.of(this.target), false);
                    for (ItemStack stack : Block.getDrops(state, level, this.target, null)) {
                        this.addToInventory(stack);
                    }
                    level.destroyBlock(this.target, false);
                    TribeData.get(level.getServer()).touch();
                }
            }
            if (this.mineTimer <= 0) {
                this.mineTimer = MINE_TOTAL;
                this.mineSwing = MINE_BREAK_AT;
                this.k.setKoboldState(KoboldBehavior.ST_NONE);
                this.target = null;
            }
            return true;
        }

        BlockPos stand = this.target.relative(task.facing.getOpposite());
        Vec3 standCenter = Vec3.atBottomCenterOf(stand);
        double distance = this.k.position().distanceToSqr(Vec3.atCenterOf(this.target));
        this.approachTicks++;
        if (distance > 9.0D) {
            this.standStill = 0;
            if (this.approachTicks > 200 || Math.abs(this.k.getY() - standCenter.y) > 4.0D) {
                this.portalTeleport(level, standCenter);
                this.approachTicks = 0;
            } else {
                this.k.getNavigation().moveTo(standCenter.x, standCenter.y, standCenter.z, WALK);
            }
            return true;
        }
        this.k.getNavigation().stop();
        if (this.k.onGround() && ++this.standStill >= 10) {
            this.k.setKoboldState(KoboldBehavior.ST_WORK);
            this.k.setKoboldChopping(false);
            this.mineTimer = MINE_TOTAL;
            this.mineSwing = MINE_BREAK_AT;
        }
        return true;
    }

    private void faceBlock(BlockPos pos) {
        double dx = pos.getX() + 0.5D - this.k.getX();
        double dz = pos.getZ() + 0.5D - this.k.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 57.29578D) - 90.0F;
        this.k.setYRot(yaw);
        this.k.yBodyRot = yaw;
        this.k.yHeadRot = yaw;
    }

    // ---------------------------------------------------------------- tree felling

    private boolean isLog(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.LOGS);
    }

    private BlockPos chopStandSpot(ServerLevel level, BlockPos base) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos spot = base.relative(dir);
            BlockState at = level.getBlockState(spot);
            BlockState below = level.getBlockState(spot.below());
            if (!below.isFaceSturdy(level, spot.below(), Direction.UP) || at.isSolidRender(level, spot)) {
                continue;
            }
            double distance = this.k.position().distanceToSqr(Vec3.atBottomCenterOf(spot));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = spot;
            }
        }
        return best;
    }

    private boolean fell(ServerLevel level, Tribe tribe, TribeTask task) {
        BlockPos base = task.origin;
        if (this.k.koboldState() != KoboldBehavior.ST_WORK && !this.isLog(level, base)) {
            // nothing left to cut: finished
            this.finishTask(level, tribe, task, false);
            this.release(tribe);
            return false;
        }
        if (this.chopSpot == null) {
            this.chopSpot = this.chopStandSpot(level, base);
            if (this.chopSpot == null) {
                this.finishTask(level, tribe, task, false);
                this.release(tribe);
                if (tribe.master != null) {
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tribe.master);
                    if (owner != null) {
                        owner.displayClientMessage(Component.literal("Your kobolds cannot fall this tree because it starts underground"), true);
                    }
                }
                return false;
            }
        }
        Vec3 spotCenter = Vec3.atBottomCenterOf(this.chopSpot);
        if (this.k.koboldState() != KoboldBehavior.ST_WORK) {
            this.approachTicks++;
            if (this.k.position().distanceToSqr(spotCenter) > 1.0D) {
                if (this.approachTicks > 200 || Math.abs(this.k.getY() - spotCenter.y) > 4.0D) {
                    this.portalTeleport(level, spotCenter);
                    this.approachTicks = 0;
                } else {
                    this.k.getNavigation().moveTo(spotCenter.x, spotCenter.y, spotCenter.z, WALK);
                }
                return true;
            }
            // at the spot: snap to its centre, face the log, swing
            this.k.getNavigation().stop();
            this.k.teleportTo(spotCenter.x, spotCenter.y, spotCenter.z);
            this.faceBlock(base);
            this.k.setNoGravity(true);
            this.k.setKoboldChopping(true);
            this.k.setKoboldState(KoboldBehavior.ST_WORK);
            if (!level.getBlockState(this.chopSpot.above()).isAir()) {
                level.destroyBlock(this.chopSpot.above(), false);
            }
            this.chopSwing = CHOP_SWING;
            this.chopWait = CHOP_WAIT;
            return true;
        }

        this.k.setDeltaMovement(Vec3.ZERO);
        this.faceBlock(base);
        if (this.chopSwing > 0) {
            this.chopSwing--;
            if (this.chopSwing % 20 == 0) {
                level.playSound(null, base, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return true;
        }
        if (--this.chopWait >= 0) {
            return true;
        }
        if (!this.isLog(level, base)) {
            // the trunk is used up (the falling logs have landed and the base is still air)
            this.finishTask(level, tribe, task, false);
            this.release(tribe);
            return false;
        }

        // One cycle: the base log is cut, branches (logs outside the trunk column) fall apart, and the trunk above drops
        // onto the base position as falling blocks, so the tree shrinks by one log per cycle until nothing is left.
        List<BlockPos> column = new ArrayList<>();
        BlockPos up = base.above();
        while (this.isLog(level, up)) {
            column.add(up);
            up = up.above();
        }
        List<BlockPos> removed = new ArrayList<>();
        for (BlockPos pos : new ArrayList<>(task.blocks)) {
            if (pos.equals(base) || column.contains(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS)) {
                this.addToInventory(new ItemStack(state.getBlock().asItem()));
                level.destroyBlock(pos, false);
            }
            removed.add(pos);
        }
        BlockState baseState = level.getBlockState(base);
        this.addToInventory(new ItemStack(baseState.getBlock().asItem()));
        level.destroyBlock(base, false);
        for (BlockPos pos : column) {
            BlockState state = level.getBlockState(pos);
            FallingBlockEntity.fall(level, pos, state);
        }
        if (!removed.isEmpty()) {
            task.blocks.removeAll(removed);
            TribeManager.markers(level, tribe, removed, false);
        }
        this.chopSwing = CHOP_SWING;
        this.chopWait = CHOP_WAIT;
        return true;
    }

    /** Builds a FALL_TREE task for the tree whose lowest log is {@code base} (original bs.a(world,pos,tribe)). */
    public static TribeTask treeTask(ServerLevel level, BlockPos start, Tribe tribe) {
        BlockPos base = start;
        while (level.getBlockState(base.below()).is(BlockTags.LOGS)) {
            base = base.below();
        }
        java.util.Set<BlockPos> blocks = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(base);
        blocks.add(base);
        while (!queue.isEmpty() && blocks.size() < 600) {
            BlockPos at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (!blocks.contains(next) && level.getBlockState(next).is(BlockTags.LOGS) && tribe.taskAt(next) == null) {
                            blocks.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return new TribeTask(TribeTask.Kind.FALL_TREE, base, blocks, Direction.NORTH);
    }

    // ---------------------------------------------------------------- deposit / idle

    /** Original b(tribe, allowTeleport): walk to the nearest registered chest that can take something and empty into it. */
    boolean deposit(ServerLevel level, Tribe tribe, boolean allowTeleport) {
        Container best = null;
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : tribe.chests) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock chest)) {
                continue;
            }
            Container container = ChestBlock.getContainer(chest, state, level, pos, true);
            if (container == null || !this.acceptsAny(container)) {
                continue;
            }
            double distance = this.k.position().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = container;
                bestPos = pos;
            }
        }
        if (best == null) {
            this.complainFull(level, tribe);
            return false;
        }
        if (bestDistance <= 4.0D) {
            for (int slot = 0; slot < this.inventory().getContainerSize(); slot++) {
                ItemStack stack = this.inventory().getItem(slot);
                if (!stack.isEmpty()) {
                    ItemStack rest = insert(best, stack, false);
                    this.inventory().setItem(slot, rest);
                }
            }
            level.playSound(null, bestPos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
            this.full = false;
            return true;
        }
        Vec3 chestCenter = Vec3.atCenterOf(bestPos);
        if (allowTeleport && (Math.abs(this.k.getY() - chestCenter.y) > 4.0D || !this.k.getNavigation().moveTo(chestCenter.x, chestCenter.y, chestCenter.z, WALK))) {
            this.portalTeleport(level, chestCenter.add(0.0D, -0.5D, 0.0D));
        } else if (!allowTeleport) {
            this.k.getNavigation().moveTo(chestCenter.x, chestCenter.y, chestCenter.z, WALK);
        }
        return true;
    }

    private boolean acceptsAny(Container container) {
        for (int slot = 0; slot < this.inventory().getContainerSize(); slot++) {
            ItemStack stack = this.inventory().getItem(slot);
            if (!stack.isEmpty() && insert(container, stack, true).getCount() < stack.getCount()) {
                return true;
            }
        }
        return false;
    }

    private void complainFull(ServerLevel level, Tribe tribe) {
        if (this.inventoryHasSpace() || this.fullChatCooldown > 0 || tribe.master == null) {
            return;
        }
        this.fullChatCooldown = 300;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tribe.master);
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(this.k.koboldName() + "s ").withStyle(tribe.color.chat())
                    .append(Component.literal("inventory is full and there are either no chests to put her items in or said chests are full as well")
                            .withStyle(ChatFormatting.WHITE)));
        }
    }

    /** Original u(tribe): nothing to do - empty the backpack if there is something in it, else wander. */
    void idle(ServerLevel level, Tribe tribe) {
        boolean carrying = false;
        for (int i = 0; i < this.inventory().getContainerSize(); i++) {
            carrying |= !this.inventory().getItem(i).isEmpty();
        }
        if (carrying && !tribe.chests.isEmpty() && this.deposit(level, tribe, false)) {
            return;
        }
        this.wander(level);
    }

    void wander(ServerLevel level) {
        Vec3 owner = this.k.masterId().map(level::getPlayerByUUID).map(p -> p.position()).orElse(null);
        if (owner != null) {
            double distance = this.k.position().distanceTo(owner);
            if (distance < 2.0D) {
                this.k.getNavigation().stop();
                return;
            }
        }
        this.wanderTicks++;
        if (this.wanderTarget == null || this.wanderTicks >= 100 || this.k.getNavigation().isDone()) {
            if (this.wanderTarget == null || this.wanderTicks >= 100) {
                BlockPos around = this.k.blockPosition();
                int x = around.getX() + this.k.getRandom().nextInt(9) - 4;
                int z = around.getZ() + this.k.getRandom().nextInt(9) - 4;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                this.wanderTarget = new BlockPos(x, y, z);
                this.wanderTicks = 0;
            }
            this.k.getNavigation().moveTo(this.wanderTarget.getX() + 0.5D, this.wanderTarget.getY(), this.wanderTarget.getZ() + 0.5D, WALK);
        }
    }

    // ---------------------------------------------------------------- misc

    void portalTeleport(ServerLevel level, Vec3 to) {
        level.sendParticles(ParticleTypes.PORTAL, this.k.getX(), this.k.getY() + 0.5D, this.k.getZ(), 30, 0.3D, 0.5D, 0.3D, 0.1D);
        this.k.teleportTo(to.x, to.y, to.z);
        this.k.getNavigation().stop();
        level.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 0.5D, to.z, 30, 0.3D, 0.5D, 0.3D, 0.1D);
    }

    boolean isFull() {
        return this.full;
    }
}
