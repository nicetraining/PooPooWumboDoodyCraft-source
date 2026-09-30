package com.trolmastercard.sexmod.tribe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Original "tribes" WorldSavedData (ax.b), kept once per save in the overworld storage. */
public final class TribeData extends SavedData {
    private static final String NAME = "tribes";
    private static final Factory<TribeData> FACTORY = new Factory<>(TribeData::new, TribeData::load, DataFixTypes.LEVEL);

    private final Map<UUID, Tribe> tribes = new LinkedHashMap<>();

    public static TribeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Tribe tribe(UUID id) {
        return id == null ? null : this.tribes.get(id);
    }

    public Iterable<Tribe> all() {
        return new ArrayList<>(this.tribes.values());
    }

    /** Original ax.a(UUID, colour): create an empty record when the tribe is not known yet. */
    public Tribe getOrCreate(UUID id, KoboldColor color) {
        Tribe tribe = this.tribes.get(id);
        if (tribe == null) {
            tribe = new Tribe(id, color);
            this.tribes.put(id, tribe);
            this.setDirty();
        }
        return tribe;
    }

    /** Original ax.a(player): the tribe that player owns, if it still has members. */
    public Tribe ofMaster(UUID player) {
        for (Tribe tribe : this.tribes.values()) {
            if (player.equals(tribe.master) && !tribe.members.isEmpty()) {
                return tribe;
            }
        }
        return null;
    }

    public void touch() {
        this.setDirty();
    }

    private static Optional<BlockPos> parse(String text) {
        String[] parts = text.split("\\|");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static String format(BlockPos pos) {
        return pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    public static TribeData load(CompoundTag root, HolderLookup.Provider registries) {
        TribeData data = new TribeData();
        for (Tag raw : root.getList("Tribes", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            UUID id;
            try {
                id = UUID.fromString(tag.getString("id"));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Tribe tribe = new Tribe(id, KoboldColor.byName(tag.getString("color")));
            if (tag.contains("master", Tag.TAG_STRING) && !tag.getString("master").isEmpty()) {
                try {
                    tribe.master = UUID.fromString(tag.getString("master"));
                } catch (IllegalArgumentException ignored) {
                    // no master
                }
            }
            for (Tag m : tag.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag) m;
                try {
                    UUID uuid = UUID.fromString(member.getString("id"));
                    tribe.members.put(uuid, parse(member.getString("pos")).orElse(BlockPos.ZERO));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
            for (Tag b : tag.getList("beds", Tag.TAG_STRING)) {
                parse(b.getAsString()).ifPresent(tribe.beds::add);
            }
            for (Tag c : tag.getList("chests", Tag.TAG_STRING)) {
                parse(c.getAsString()).ifPresent(tribe.chests::add);
            }
            for (Tag t : tag.getList("tasks", Tag.TAG_COMPOUND)) {
                CompoundTag task = (CompoundTag) t;
                TribeTask.Kind kind;
                try {
                    kind = TribeTask.Kind.valueOf(task.getString("kind"));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                Optional<BlockPos> origin = parse(task.getString("pos"));
                if (origin.isEmpty()) {
                    continue;
                }
                Set<BlockPos> blocks = new HashSet<>();
                for (Tag block : task.getList("blocks", Tag.TAG_STRING)) {
                    parse(block.getAsString()).ifPresent(blocks::add);
                }
                Direction facing = Direction.byName(task.getString("facing"));
                tribe.tasks.add(new TribeTask(kind, origin.get(), blocks, facing));
            }
            data.tribes.put(id, tribe);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Tribe tribe : this.tribes.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", tribe.id.toString());
            tag.putString("color", tribe.color.name());
            if (tribe.master != null) {
                tag.putString("master", tribe.master.toString());
            }
            ListTag members = new ListTag();
            tribe.members.forEach((uuid, pos) -> {
                CompoundTag member = new CompoundTag();
                member.putString("id", uuid.toString());
                member.putString("pos", format(pos));
                members.add(member);
            });
            tag.put("members", members);
            ListTag beds = new ListTag();
            tribe.beds.forEach(pos -> beds.add(net.minecraft.nbt.StringTag.valueOf(format(pos))));
            tag.put("beds", beds);
            ListTag chests = new ListTag();
            tribe.chests.forEach(pos -> chests.add(net.minecraft.nbt.StringTag.valueOf(format(pos))));
            tag.put("chests", chests);
            ListTag tasks = new ListTag();
            for (TribeTask task : tribe.tasks) {
                CompoundTag t = new CompoundTag();
                t.putString("kind", task.kind.name());
                t.putString("pos", format(task.origin));
                t.putString("facing", task.facing.getName());
                ListTag blocks = new ListTag();
                task.blocks.forEach(pos -> blocks.add(net.minecraft.nbt.StringTag.valueOf(format(pos))));
                t.put("blocks", blocks);
                tasks.add(t);
            }
            tag.put("tasks", tasks);
            list.add(tag);
        }
        root.put("Tribes", list);
        return root;
    }
}
