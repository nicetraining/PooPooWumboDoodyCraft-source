package com.trolmastercard.sexmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Luna's fishing routine (original eb.i / n / e / q / h / j). Server side only. She fishes while nobody leads her:
 * pick the deepest water within 60 blocks, walk to the shore, cast (start_fishing), wait (idle_fishing), reel in when
 * the bobber's bite window reaches 15, then eat the catch (food) or throw it away, and start over. Every third catch
 * she takes a 60 second break.
 */
public final class LunaFishing {
    public static final int NONE = 0;
    public static final int START = 1;
    public static final int IDLE = 2;
    public static final int EAT = 3;
    public static final int THROW_AWAY = 4;

    /** GameTests run in arbitrary biomes. */
    public static boolean ignoreBiomes = false;

    private static final int START_CAST_TICK = 24; // rod_shoot at 1.2083 s
    private static final int START_DONE_TICK = 57; // start_fishingDone at 2.8333 s
    private static final int THROW_TICK = 88; // throw_away at 4.4167 s
    private static final int THROW_DONE_TICK = 100; // eatingDone at 5.0 s
    private static final int EAT_DONE_TICK = 129; // eatingDone at 6.4583 s

    private final CompanionEntity k;
    private int cooldown = 8000; // aj
    private int catches; // aq
    private BlockPos spot; // ai
    private int spotDepth; // at
    private int spotStreak; // as
    private final Set<BlockPos> blacklist = new HashSet<>(); // an
    private long hookRemoveAt = -1L; // al
    private boolean done; // ar
    private LunaHookEntity hook; // av
    private int stateTicks;
    private int searchTicks;
    private int pathTicks;
    private boolean arrivedOnce;
    private Vec3 lockPos;

    public LunaFishing(CompanionEntity k) {
        this.k = k;
    }

    public BlockPos spot() {
        return this.spot;
    }

    public LunaHookEntity hook() {
        return this.hook;
    }

    public int cooldown() {
        return this.cooldown;
    }

    public int catches() {
        return this.catches;
    }

    /** Original ar: once she walked to a bed she does not fish again until reloaded. */
    public void markDone() {
        this.done = true;
    }

    /** True from the moment she is on her way to the water until the routine is reset: wander / look goals stay off. */
    public boolean isActive() {
        return this.spot != null || this.k.fishState() != NONE;
    }

    public boolean isLocked() {
        return this.k.fishState() != NONE;
    }

    /** Original o(): the hook did not land in water or caught something else; do not use that spot again. */
    public void spotFailed() {
        if (this.spot != null) {
            this.blacklist.add(this.spot);
        }
        this.reset();
    }

    /** Original q(). */
    public void reset() {
        this.spot = null;
        this.spotDepth = 0;
        this.spotStreak = 0;
        this.k.setFishCaught(ItemStack.EMPTY);
        this.k.setFishState(NONE);
        this.k.setFishCast(false);
        this.k.setSilent(false);
        this.lockPos = null;
        this.stateTicks = 0;
        this.arrivedOnce = false;
        this.pathTicks = 0;
        if (this.hook != null) {
            this.hook.discard();
            this.hook = null;
        }
        this.hookRemoveAt = -1L;
    }

    /** Original h(): after eating / throwing away. */
    private void finishCatch() {
        this.reset();
        if (++this.catches >= 3) {
            this.catches = 0;
            this.cooldown = 0;
        }
    }

    public void tick(ServerLevel level) {
        boolean busy = this.k.isFollowing() || this.k.isFishingBlocked() || this.done;
        if (busy) {
            if (this.k.fishCast() || this.isActive()) {
                this.reset();
            }
            return;
        }
        this.cooldown++;
        if (this.cooldown < 1200) {
            return;
        }

        if (this.hookRemoveAt >= 0L && level.getGameTime() >= this.hookRemoveAt) {
            if (this.hook != null) {
                this.hook.discard();
                this.hook = null;
            }
            this.hookRemoveAt = -1L;
        }
        this.k.setFishCast(this.hook != null && this.k.fishCaught().isEmpty());

        if (this.hook != null && this.hook.catchable() == LunaHookEntity.BITE_TICK) {
            this.reelIn(level);
        }

        int state = this.k.fishState();
        if (state != NONE) {
            this.stateTicks++;
            this.lockInPlace();
            this.tickState(level, state);
            return;
        }

        // not fishing yet: find a spot and walk to it
        if (++this.searchTicks >= 20 || this.spot == null && this.searchTicks >= 5) {
            this.searchTicks = 0;
            this.findSpot(level);
        }
        if (this.spot == null) {
            return;
        }
        this.walk(level);
    }

    private void lockInPlace() {
        this.k.getNavigation().stop();
        if (this.lockPos != null) {
            this.k.setDeltaMovement(0.0D, this.k.getDeltaMovement().y, 0.0D);
            this.k.teleportTo(this.lockPos.x, this.k.getY(), this.lockPos.z);
        }
        if (this.spot != null) {
            float yaw = (float) (Math.atan2(this.k.getZ() - this.spot.getZ(), this.k.getX() - this.spot.getX()) * 57.29578F) + 90.0F;
            this.k.setYRot(yaw);
            this.k.yBodyRot = yaw;
            this.k.yHeadRot = yaw;
        }
    }

    private void tickState(ServerLevel level, int state) {
        switch (state) {
            case START -> {
                if (this.stateTicks == START_CAST_TICK) {
                    this.cast(level);
                }
                if (this.stateTicks >= START_DONE_TICK) {
                    this.k.setFishState(IDLE);
                    this.stateTicks = 0;
                }
            }
            case EAT -> {
                if (this.stateTicks >= EAT_DONE_TICK) {
                    this.finishCatch();
                }
            }
            case THROW_AWAY -> {
                if (this.stateTicks == THROW_TICK) {
                    this.throwAway(level);
                }
                if (this.stateTicks >= THROW_DONE_TICK) {
                    this.finishCatch();
                }
            }
            default -> {
                if (this.hook == null && this.stateTicks > 400) {
                    this.reset(); // the cast never produced a bobber
                }
            }
        }
    }

    /** Original gp.use(): casts the bobber at the spot. */
    private void cast(ServerLevel level) {
        if (this.spot == null) {
            return;
        }
        double distance = this.k.position().distanceTo(Vec3.atLowerCornerOf(this.spot));
        level.playSound(null, this.k.getX(), this.k.getY(), this.k.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F,
                0.4F / (this.k.getRandom().nextFloat() * 0.4F + 0.8F));
        LunaHookEntity entity = new LunaHookEntity(com.trolmastercard.sexmod.registry.ModEntities.LUNA_HOOK.get(), level);
        entity.setRod(this.rod());
        entity.launch(this.k, this.spot, distance * 0.01D);
        level.addFreshEntity(entity);
        this.hook = entity;
        this.k.swing(InteractionHand.MAIN_HAND);
    }

    private ItemStack rod() {
        ItemStack slot = this.k.equipment().getItem(6);
        return slot.isEmpty() ? new ItemStack(com.trolmastercard.sexmod.registry.ModItems.LUNA_ROD.get()) : slot;
    }

    /** Original gp.use() with a hook out: reel in and react to the catch. */
    private void reelIn(ServerLevel level) {
        List<ItemStack> loot = this.hook.reel();
        level.playSound(null, this.k.getX(), this.k.getY(), this.k.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F,
                0.4F / (this.k.getRandom().nextFloat() * 0.4F + 0.8F));
        this.k.swing(InteractionHand.MAIN_HAND);
        this.hookRemoveAt = level.getGameTime() + 20L;
        ItemStack caught = ItemStack.EMPTY;
        for (ItemStack stack : loot) {
            caught = stack; // the original keeps the last rolled stack
        }
        this.k.setFishCaught(caught);
        this.k.setFishCast(false);
        if (!caught.isEmpty()) {
            this.k.setFishState(caught.has(net.minecraft.core.component.DataComponents.FOOD) ? EAT : THROW_AWAY);
            this.stateTicks = 0;
        }
    }

    /** Original j(). */
    private void throwAway(ServerLevel level) {
        ItemStack caught = this.k.fishCaught();
        if (caught.isEmpty()) {
            return;
        }
        double yawRad = Math.toRadians(this.k.getYRot());
        double ly = 0.2D + Math.random() * 0.1D;
        double lz = -0.2D + Math.random() * -0.1D;
        // original ck.a(vec, yaw): the local z axis points where she faces
        double rx = -lz * Math.sin(yawRad);
        double rz = lz * Math.cos(yawRad);
        ItemEntity item = new ItemEntity(level, this.k.getX(), this.k.getY(), this.k.getZ(), caught.copy());
        item.setDeltaMovement(rx, ly, rz);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        this.k.setFishCaught(ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- walking

    private void walk(ServerLevel level) {
        PathNavigation nav = this.k.getNavigation();
        if (nav.getPath() == null && this.arrivedOnce && this.k.onGround() && !this.k.isInWater()) {
            this.arrive(); // original step 4: the path is gone and was gone on the previous tick too
            return;
        }
        if (nav.getPath() == null || this.pathTicks++ % 10 == 0) {
            nav.moveTo(this.spot.getX() + 0.5D, this.spot.getY(), this.spot.getZ() + 0.5D, 0.35D);
        }
        Path path = nav.getPath();
        if (path != null && path.getNodeCount() > path.getNextNodeIndex() + 1) {
            Node next = path.getNode(path.getNextNodeIndex() + 1);
            Node last = path.getNode(path.getNodeCount() - 1);
            Vec3 lastPos = new Vec3(last.x, last.y, last.z);
            BlockPos nextPos = new BlockPos(next.x, next.y, next.z);
            if (this.k.position().distanceTo(lastPos) < 0.75D) {
                nav.stop();
                this.k.setPos(lastPos);
            }
            if (isWater(level, nextPos.above()) || isWater(level, nextPos) || isWater(level, nextPos.below())) {
                nav.stop();
            }
        }
        this.arrivedOnce = nav.getPath() == null;
    }

    private static boolean isWater(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.WATER;
    }

    private void arrive() {
        this.k.setSilent(true);
        this.lockPos = this.k.position();
        this.k.setFishState(START);
        this.stateTicks = 0;
        this.k.setFishCast(false);
    }

    // ---------------------------------------------------------------- spot search

    private boolean biomeAllowed(ServerLevel level, BlockPos pos) {
        if (ignoreBiomes) {
            return true;
        }
        var holder = level.getBiome(pos);
        if (holder.is(Biomes.FROZEN_OCEAN) || holder.is(Biomes.DEEP_FROZEN_OCEAN) || holder.is(Biomes.FROZEN_RIVER)
                || holder.is(Biomes.SNOWY_BEACH)) {
            return false;
        }
        return holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_RIVER) || holder.is(BiomeTags.IS_BEACH)
                || holder.is(Biomes.STONY_SHORE) || holder.is(Biomes.SWAMP) || holder.is(Biomes.MANGROVE_SWAMP);
    }

    private record Hit(int count, BlockPos pos) {
    }

    /** Original em.a(pos, n, WATER, 60, 10, biomes): square spiral around her, +-10 blocks vertically, counting water blocks. */
    private List<Hit> scanWater(ServerLevel level, BlockPos start, int maxCount) {
        List<Hit> hits = new ArrayList<>();
        int radius = 1;
        int sign = -1;
        BlockPos at = start;
        int count = 0;
        while (radius < 60) {
            for (int pass = 0; pass < 2; pass++) {
                sign *= -1;
                for (int i = 0; i < radius; i++) {
                    at = at.offset(0, 0, sign);
                    for (int dy = -10; dy <= 10; dy++) {
                        BlockPos probe = at.offset(0, dy, sign);
                        if (isWater(level, probe)) {
                            count++;
                            if (this.biomeAllowed(level, at.offset(sign, dy, 0))) {
                                hits.add(new Hit(count, probe));
                                if (count >= maxCount) {
                                    return hits;
                                }
                            }
                        }
                    }
                }
                for (int i = 0; i < radius; i++) {
                    at = at.offset(sign, 0, 0);
                    for (int dy = -10; dy <= 10; dy++) {
                        BlockPos probe = at.offset(sign, dy, 0);
                        if (isWater(level, probe)) {
                            count++;
                            if (this.biomeAllowed(level, probe)) {
                                hits.add(new Hit(count, probe));
                                if (count >= maxCount) {
                                    return hits;
                                }
                            }
                        }
                    }
                }
            }
            radius++;
        }
        return hits;
    }

    private BlockPos firstHit(List<Hit> hits, int atLeast) {
        for (Hit hit : hits) {
            if (hit.count >= atLeast) {
                return hit.pos;
            }
        }
        return null;
    }

    /** Original n(): up to 50 candidate waters, keep the deepest one (stop at depth 6). */
    private void findSpot(ServerLevel level) {
        List<Hit> hits = this.scanWater(level, this.k.blockPosition(), 50);
        BlockPos best = null;
        int bestDepth = 0;
        for (int attempt = 1; attempt < 50; attempt++) {
            BlockPos found = this.firstHit(hits, attempt + 1);
            if (found == null) {
                break;
            }
            while (isWater(level, found.above())) {
                found = found.above();
            }
            int depth = 1;
            BlockPos down = found;
            while (isWater(level, down.below())) {
                down = down.below();
                depth++;
            }
            if (this.blacklist.contains(found)) {
                continue;
            }
            if (best == null) {
                best = found;
                bestDepth = depth;
                continue;
            }
            if (depth <= bestDepth) {
                continue;
            }
            best = found;
            bestDepth = depth;
            if (bestDepth >= 6) {
                break;
            }
        }
        if (best == null) {
            return;
        }
        if (this.spot == null || this.spotDepth < bestDepth) {
            this.spot = best;
            this.spotDepth = bestDepth;
        }
        if (this.spot.equals(best)) {
            this.spotStreak = 0;
        } else if (++this.spotStreak > 20) {
            this.spot = best;
            this.spotDepth = bestDepth;
        }
    }
}
