package com.trolmastercard.sexmod.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
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

        return data;
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
        return tag;
    }
}
