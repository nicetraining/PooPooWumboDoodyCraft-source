package com.trolmastercard.sexmod.data;

import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Persistent cross-dimension limits for bonded companions and incubating eggs. */
public final class CompanionOwnershipData extends SavedData {
    public static final int MAX_TOTAL = 8;
    private static final String DATA_NAME = "sexmod_companion_ownership";
    private static final Factory<CompanionOwnershipData> FACTORY = new Factory<>(
            CompanionOwnershipData::new,
            CompanionOwnershipData::load,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Map<UUID, CompanionKind>> companionsByOwner = new HashMap<>();

    public static CompanionOwnershipData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static CompanionOwnershipData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionOwnershipData data = new CompanionOwnershipData();
        for (Tag rawEntry : tag.getList("Companions", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) rawEntry;
            if (entry.hasUUID("Owner") && entry.hasUUID("Entity")) {
                data.companionsByOwner
                        .computeIfAbsent(entry.getUUID("Owner"), ignored -> new HashMap<>())
                        .put(entry.getUUID("Entity"), CompanionKind.byName(entry.getString("Kind")));
            }
        }
        return data;
    }
    public boolean canClaim(UUID ownerId, CompanionKind kind, int amount) {
        if (amount <= 0) {
            return true;
        }
        Map<UUID, CompanionKind> owned = this.companionsByOwner.getOrDefault(ownerId, Map.of());
        long sameKind = owned.values().stream().filter(kind::equals).count();
        return owned.size() + amount <= MAX_TOTAL
                && sameKind + amount <= maximumFor(kind);
    }

    public boolean claim(UUID ownerId, UUID entityId, CompanionKind kind) {
        Map<UUID, CompanionKind> owned = this.companionsByOwner.get(ownerId);
        if (owned != null && owned.get(entityId) == kind) {
            return true;
        }
        if (!this.canClaim(ownerId, kind, 1)) {
            return false;
        }

        this.removeEntity(entityId);
        this.companionsByOwner
                .computeIfAbsent(ownerId, ignored -> new HashMap<>())
                .put(entityId, kind);
        this.setDirty();
        return true;
    }

    public void trackExisting(UUID ownerId, UUID entityId, CompanionKind kind) {
        Map<UUID, CompanionKind> owned = this.companionsByOwner.get(ownerId);
        if (owned != null && owned.get(entityId) == kind) {
            return;
        }
        this.removeEntity(entityId);
        this.companionsByOwner
                .computeIfAbsent(ownerId, ignored -> new HashMap<>())
                .put(entityId, kind);
        this.setDirty();
    }

    public boolean transfer(UUID ownerId, UUID oldEntityId, UUID newEntityId, CompanionKind kind) {
        Map<UUID, CompanionKind> owned = this.companionsByOwner.get(ownerId);
        if (owned != null && owned.remove(oldEntityId) != null) {
            owned.put(newEntityId, kind);
            this.setDirty();
            return true;
        }
        return this.claim(ownerId, newEntityId, kind);
    }

    public void release(UUID ownerId, UUID entityId) {
        Map<UUID, CompanionKind> owned = this.companionsByOwner.get(ownerId);
        if (owned != null && owned.remove(entityId) != null) {
            if (owned.isEmpty()) {
                this.companionsByOwner.remove(ownerId);
            }
            this.setDirty();
        }
    }
    private static int maximumFor(CompanionKind kind) {
        return kind == CompanionKind.KOBOLD ? 4 : 1;
    }

    private void removeEntity(UUID entityId) {
        Iterator<Map.Entry<UUID, Map<UUID, CompanionKind>>> owners =
                this.companionsByOwner.entrySet().iterator();
        while (owners.hasNext()) {
            Map<UUID, CompanionKind> owned = owners.next().getValue();
            if (owned.remove(entityId) != null && owned.isEmpty()) {
                owners.remove();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        this.companionsByOwner.forEach((ownerId, owned) ->
                owned.forEach((entityId, kind) -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("Owner", ownerId);
                    entry.putUUID("Entity", entityId);
                    entry.putString("Kind", kind.registryPath());
                    entries.add(entry);
                }));
        tag.put("Companions", entries);
        return tag;
    }
}
