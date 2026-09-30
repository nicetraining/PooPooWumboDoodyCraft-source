package com.trolmastercard.sexmod.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent server-wide ownership index for Galaths summoned by their coin.
 *
 * <p>The index lives in the overworld data storage so it is shared by every
 * dimension. An unloaded Galath checks this index when it is loaded again and
 * removes itself if its coin has already dismissed or replaced it.</p>
 */
public final class GalathBindingData extends SavedData {
    private static final String DATA_NAME = "sexmod_galath_bindings";
    private static final String BINDINGS_TAG = "Bindings";
    private static final String OWNER_TAG = "Owner";
    private static final String ENTITY_TAG = "Entity";
    private static final Factory<GalathBindingData> FACTORY = new Factory<>(
            GalathBindingData::new,
            GalathBindingData::load,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, UUID> activeByOwner = new HashMap<>();
    /** Original v.i: players whose Galath has adopted a daughter (Manglelie); she is recreated on every summon. */
    private final Set<UUID> mangOwners = new HashSet<>();
    /** Original fq: spawn points of wild Manglelies (nether) and wild Galaths, to keep them 700 / 1000 blocks apart. */
    private final List<net.minecraft.core.BlockPos> mangSpawns = new ArrayList<>();
    private final List<net.minecraft.core.BlockPos> galathSpawns = new ArrayList<>();
    private final Map<UUID, Long> lastCum = new HashMap<>();

    public static GalathBindingData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static GalathBindingData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        GalathBindingData data = new GalathBindingData();
        ListTag bindings = tag.getList(BINDINGS_TAG, Tag.TAG_COMPOUND);

        for (Tag rawEntry : bindings) {
            CompoundTag entry = (CompoundTag) rawEntry;
            if (entry.hasUUID(OWNER_TAG) && entry.hasUUID(ENTITY_TAG)) {
                data.activeByOwner.put(
                        entry.getUUID(OWNER_TAG),
                        entry.getUUID(ENTITY_TAG)
                );
            }
        }

        for (Tag raw : tag.getList("MangOwners", Tag.TAG_STRING)) {
            try {
                data.mangOwners.add(UUID.fromString(raw.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // skip a corrupt entry
            }
        }
        for (long packed : tag.getLongArray("MangSpawns")) {
            data.mangSpawns.add(net.minecraft.core.BlockPos.of(packed));
        }
        for (long packed : tag.getLongArray("GalathSpawns")) {
            data.galathSpawns.add(net.minecraft.core.BlockPos.of(packed));
        }
        CompoundTag cum = tag.getCompound("LastCum");
        for (String key : cum.getAllKeys()) {
            try {
                data.lastCum.put(UUID.fromString(key), cum.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // skip a corrupt entry
            }
        }
        return data;
    }

    public boolean hasMang(UUID owner) {
        return this.mangOwners.contains(owner);
    }

    public void setHasMang(UUID owner) {
        if (this.mangOwners.add(owner)) {
            this.setDirty();
        }
    }

    public void recordCum(UUID player, long time) {
        this.lastCum.put(player, time);
        this.setDirty();
    }

    /** Original v.a(uuid, world): the morning scene is open to a Manglelie owner whose last dosage lies in the past. */
    public boolean morningReady(UUID player, long time) {
        Long last = this.lastCum.get(player);
        return this.mangOwners.contains(player) && (last == null || time - last > 0L);
    }

    /** True when a Manglelie or Galath spawn point lies within {@code radius} blocks (original fq.c/fq.b). */
    public boolean spawnNear(net.minecraft.core.BlockPos pos, double radius, boolean manglelie, boolean galath) {
        if (manglelie) {
            for (var other : this.mangSpawns) {
                if (Math.sqrt(pos.distSqr(other)) < radius) {
                    return true;
                }
            }
        }
        if (galath) {
            for (var other : this.galathSpawns) {
                if (Math.sqrt(pos.distSqr(other)) < radius) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addSpawn(net.minecraft.core.BlockPos pos, boolean manglelie) {
        (manglelie ? this.mangSpawns : this.galathSpawns).add(pos.immutable());
        this.setDirty();
    }

    public Optional<UUID> activeFor(UUID ownerId) {
        return Optional.ofNullable(this.activeByOwner.get(ownerId));
    }

    public boolean isActive(UUID ownerId, UUID entityId) {
        return entityId.equals(this.activeByOwner.get(ownerId));
    }

    public void bind(UUID ownerId, UUID entityId) {
        if (!entityId.equals(this.activeByOwner.put(ownerId, entityId))) {
            this.setDirty();
        }
    }

    public void clear(UUID ownerId) {
        if (this.activeByOwner.remove(ownerId) != null) {
            this.setDirty();
        }
    }

    public void clearIfMatches(UUID ownerId, UUID entityId) {
        if (this.activeByOwner.remove(ownerId, entityId)) {
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        ListTag bindings = new ListTag();

        this.activeByOwner.forEach((ownerId, entityId) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(OWNER_TAG, ownerId);
            entry.putUUID(ENTITY_TAG, entityId);
            bindings.add(entry);
        });

        tag.put(BINDINGS_TAG, bindings);
        ListTag owners = new ListTag();
        this.mangOwners.forEach(id -> owners.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
        tag.put("MangOwners", owners);
        tag.putLongArray("MangSpawns", this.mangSpawns.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
        tag.putLongArray("GalathSpawns", this.galathSpawns.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
        CompoundTag cum = new CompoundTag();
        this.lastCum.forEach((id, time) -> cum.putLong(id.toString(), time));
        tag.put("LastCum", cum);
        return tag;
    }
}
