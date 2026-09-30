package com.trolmastercard.sexmod.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** Original g3 "sexmod:generation" saved data: which structure was generated at which chunk. */
public final class PlacedStructures extends SavedData {
    private static final String NAME = "sexmod_generation";
    private static final Factory<PlacedStructures> FACTORY =
            new Factory<>(PlacedStructures::new, PlacedStructures::load, DataFixTypes.LEVEL);

    public record Entry(int chunkX, int chunkZ, String name) {
        /** Original e1.a: euclidean distance in chunks. */
        public float distanceTo(int cx, int cz) {
            float dx = cx - this.chunkX;
            float dz = cz - this.chunkZ;
            return (float) Math.sqrt(dx * dx + dz * dz);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public static PlacedStructures get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public void add(int cx, int cz, String name) {
        this.entries.add(new Entry(cx, cz, name));
        this.setDirty();
    }

    private static PlacedStructures load(CompoundTag tag, HolderLookup.Provider registries) {
        PlacedStructures data = new PlacedStructures();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.entries.add(new Entry(entry.getInt("x"), entry.getInt("z"), entry.getString("name")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : this.entries) {
            CompoundTag compound = new CompoundTag();
            compound.putInt("x", entry.chunkX());
            compound.putInt("z", entry.chunkZ());
            compound.putString("name", entry.name());
            list.add(compound);
        }
        tag.put("entries", list);
        return tag;
    }
}
