package com.fartypoopy.sexmod.tribe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

/** Original bs: a tree to fell (one worker) or a 30 long 3x3 tunnel to mine (three workers). */
public final class TribeTask {
    public enum Kind {
        FALL_TREE(1),
        MINE(3);

        public final int maxWorkers;

        Kind(int maxWorkers) {
            this.maxWorkers = maxWorkers;
        }
    }

    public static final int TUNNEL_LENGTH = 30;

    public final Kind kind;
    public final BlockPos origin;
    public final Direction facing;
    public final Set<BlockPos> blocks = new LinkedHashSet<>();
    public final Set<UUID> workers = new HashSet<>();

    public TribeTask(Kind kind, BlockPos origin, Set<BlockPos> blocks, Direction facing) {
        this.kind = kind;
        this.origin = origin;
        this.facing = facing == null ? Direction.NORTH : facing;
        this.blocks.addAll(blocks);
    }

    public boolean addWorker(UUID kobold) {
        if (this.workers.contains(kobold)) {
            return true;
        }
        if (this.workers.size() >= this.kind.maxWorkers) {
            return false;
        }
        return this.workers.add(kobold);
    }
}
