package com.fartypoopy.sexmod.tribe;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Original ax.a: one tribe record. Persisted: colour, master, members, beds, chests, tasks. The rest is runtime. */
public final class Tribe {
    public enum Mode {ACTIVE, REST}

    public final UUID id;
    public KoboldColor color;
    public UUID master;
    /** Every member ever known with the last position we saw it at (works for unloaded members). */
    public final Map<UUID, BlockPos> members = new LinkedHashMap<>();
    public final Set<BlockPos> beds = new HashSet<>();
    public final Set<BlockPos> chests = new HashSet<>();
    public final List<TribeTask> tasks = new ArrayList<>();

    // runtime only (as in the original)
    public UUID leader;
    public boolean follow;
    public Mode mode = Mode.REST;
    public BlockPos home;
    public final Set<UUID> enemies = new HashSet<>();
    /** Kobold uuid -> the two bed halves it sleeps in. */
    public final Map<UUID, BlockPos[]> sleeping = new HashMap<>();

    public Tribe(UUID id, KoboldColor color) {
        this.id = id;
        this.color = color;
    }

    public int memberCount() {
        return this.members.size();
    }

    public TribeTask taskAt(BlockPos pos) {
        for (TribeTask task : this.tasks) {
            if (task.blocks.contains(pos)) {
                return task;
            }
        }
        return null;
    }

    public TribeTask taskOf(UUID worker) {
        for (TribeTask task : this.tasks) {
            if (task.workers.contains(worker)) {
                return task;
            }
        }
        return null;
    }

    public boolean bedAssigned(BlockPos pos) {
        for (BlockPos[] pair : this.sleeping.values()) {
            for (BlockPos bed : pair) {
                if (bed.equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }
}
