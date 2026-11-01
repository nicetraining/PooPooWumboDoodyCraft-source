package com.fartypoopy.sexmod.worldgen;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Placing the original's 1.12 structure templates (houses and the goblin hall) in a 1.21 world: the worldgen
 * structures ({@link GirlHouseStructure}, {@link GoblinLairStructure}), /fapcraft placestructure and the tests use it.
 */
public final class OriginalStructures {
    /** The goblin hall's hollow: 12 x 7 x 12 blocks from its base (the template is set one block lower). */
    public static final Vec3i HALL = new Vec3i(11, 6, 11);

    private OriginalStructures() {
    }

    /** Places a template at {@code pos}; with a clip box only the part inside it (one chunk during world generation). */
    public static boolean place(ServerLevelAccessor level, String name, BlockPos pos, Rotation rotation, BoundingBox clip) {
        var template = level.getLevel().getStructureManager().get(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, name));
        if (template.isEmpty()) {
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        if (clip != null) {
            settings.setBoundingBox(clip);
        }
        // UPDATE_KNOWN_SHAPE: no neighbour shape cascade while the blocks go in one by one (a torch or button set
        // before its wall, a door half before its partner broke and dropped as items in front of the houses); the
        // template's own final pass still fits every shape once all blocks are there. Nothing it removes drops.
        template.get().placeInWorld(level, pos, pos, settings, level.getRandom(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        restoreDoors(level, name, pos, settings, clip);
        return true;
    }

    public static boolean place(ServerLevel level, String name, BlockPos pos, Rotation rotation) {
        return place(level, name, pos, rotation, null);
    }

    /** A door as the 1.12 template stored it, both halves combined: lower position in template space. */
    private record SavedDoor(BlockPos lower, net.minecraft.core.Direction facing, boolean open,
                             net.minecraft.world.level.block.state.properties.DoorHingeSide hinge, boolean powered) {
    }

    private static final Map<String, List<SavedDoor>> DOORS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The templates are 1.12 saves (DataVersion 1343). Upgrading them to 1.21 loses every door: the lower half stored
     * only facing and open, the upper half only hinge and powered (1.12 combined the two when drawing), and the upgrade
     * turns them into default doors facing east. Each door is set again from the template file itself, both halves
     * with the combined state as 1.12 showed it (double doors meet at the handles, decorative ones keep their state).
     */
    private static void restoreDoors(ServerLevelAccessor level, String name, BlockPos origin, StructurePlaceSettings settings,
                                     BoundingBox clip) {
        for (SavedDoor door : DOORS.computeIfAbsent(name, n -> readDoors(level.getLevel(), n))) {
            BlockPos lower = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                    .calculateRelativePosition(settings, door.lower()).offset(origin);
            if (clip != null && !clip.isInside(lower) && !clip.isInside(lower.above())) {
                continue; // the other chunk's pass sets this door
            }
            BlockState placed = level.getBlockState(lower);
            if (!(placed.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
                continue;
            }
            var hinge = settings.getMirror() == net.minecraft.world.level.block.Mirror.NONE ? door.hinge()
                    : door.hinge() == net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                    ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT
                    : net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT;
            BlockState state = placed.getBlock().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoorBlock.FACING,
                            settings.getMirror().mirror(settings.getRotation().rotate(door.facing())))
                    .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, door.open())
                    .setValue(net.minecraft.world.level.block.DoorBlock.HINGE, hinge)
                    .setValue(net.minecraft.world.level.block.DoorBlock.POWERED, door.powered());
            // flags 2 | 16: no neighbour updates, so neither half breaks the other while they are rewritten
            level.setBlock(lower, state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 18);
            level.setBlock(lower.above(), state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 18);
        }
    }

    private static List<SavedDoor> readDoors(ServerLevel level, String name) {
        List<SavedDoor> doors = new ArrayList<>();
        var file = ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "structure/" + name + ".nbt");
        try (var in = level.getServer().getResourceManager().getResourceOrThrow(file).open()) {
            var tag = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var palette = tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
            var blocks = tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
            Map<BlockPos, net.minecraft.nbt.CompoundTag> upper = new java.util.HashMap<>();
            Map<BlockPos, net.minecraft.nbt.CompoundTag> lower = new java.util.HashMap<>();
            for (int i = 0; i < blocks.size(); i++) {
                var block = blocks.getCompound(i);
                var entry = palette.getCompound(block.getInt("state"));
                if (!entry.getString("Name").endsWith("_door") || entry.getString("Name").contains("trap")) {
                    continue;
                }
                var at = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
                BlockPos pos = new BlockPos(at.getInt(0), at.getInt(1), at.getInt(2));
                var properties = entry.getCompound("Properties");
                (properties.getString("half").equals("upper") ? upper : lower).put(pos, properties);
            }
            for (var e : lower.entrySet()) {
                var top = upper.getOrDefault(e.getKey().above(), new net.minecraft.nbt.CompoundTag());
                var facing = net.minecraft.core.Direction.byName(e.getValue().getString("facing"));
                doors.add(new SavedDoor(e.getKey(), facing == null ? net.minecraft.core.Direction.NORTH : facing,
                        e.getValue().getString("open").equals("true"),
                        top.getString("hinge").equals("right") ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT
                                : net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT,
                        top.getString("powered").equals("true")));
            }
        } catch (java.io.IOException exception) {
            FapCraft.LOGGER.warn("Could not read the doors of structure {}", name, exception);
        }
        return doors;
    }

    // ---------------------------------------------------------------- goblin hall

    /** How the hall template turns so its open front faces the cave: rotation, throne spot, template offset, queen yaw. */
    public record HallLayout(Rotation rotation, Vec3 throne, Vec3i offset, float yaw) {
    }

    /** Original g3 hall rotations by the side the entrance is on: 0 north (z = 0), 1 east, 2 south, 3 west. */
    public static HallLayout hallLayout(int side) {
        return switch (side) {
            case 0 -> new HallLayout(Rotation.NONE, new Vec3(5, 1, 9), Vec3i.ZERO, 180.0F);
            case 1 -> new HallLayout(Rotation.CLOCKWISE_90, new Vec3(1, 1, 5), new Vec3i(HALL.getX() - 1, 0, 0), -90.0F);
            case 2 -> new HallLayout(Rotation.CLOCKWISE_180, new Vec3(5, 1, 1),
                    new Vec3i(HALL.getX() - 1, 0, HALL.getZ() - 1), 0.0F);
            default -> new HallLayout(Rotation.COUNTERCLOCKWISE_90, new Vec3(9, 1, 5), new Vec3i(0, 0, HALL.getZ() - 1), 90.0F);
        };
    }

    /** The cell just outside the middle of a side, {@code step} blocks out (1 = next to the wall). */
    public static BlockPos outside(BlockPos base, int side, int step) {
        return switch (side) {
            case 0 -> base.offset(6, 0, -step);
            case 1 -> base.offset(HALL.getX() + step, 0, 6);
            case 2 -> base.offset(6, 0, HALL.getZ() + step);
            default -> base.offset(-step, 0, 6);
        };
    }

    /** Sets the hall template and (when its throne is inside the clip box) the queen on her throne. */
    public static void buildHall(ServerLevelAccessor level, BlockPos base, HallLayout layout, BoundingBox clip) {
        place(level, "goblin", base.offset(0, -1, 0).offset(layout.offset()), layout.rotation(), clip);
        Vec3 at = new Vec3(base.getX() + layout.throne().x + 0.5D, base.getY() + layout.throne().y,
                base.getZ() + layout.throne().z + 0.5D);
        if (clip != null && !clip.isInside(BlockPos.containing(at))) {
            return;
        }
        CompanionEntity queen = ModEntities.get(CompanionKind.GOBLIN).create(level.getLevel());
        if (queen != null) {
            queen.moveTo(at.x, at.y, at.z, layout.yaw(), 0.0F);
            queen.setGoblinQueen(true);
            queen.goblin().thronePos = at;
            queen.goblin().throneRot = layout.yaw();
            queen.setPersistenceRequired();
            level.addFreshEntity(queen);
        }
    }

    /**
     * Original g3.a (hall placed into an existing hollow) for /fapcraft placestructure and the tests: a solid floor and
     * ceiling, 1 to 4 entrances at floor level, each leading on into the cave; the template turns to the first entrance
     * that is not near a corner.
     */
    public static boolean placeHall(ServerLevel level, BlockPos base) {
        List<BlockPos> entrances = new ArrayList<>();
        for (int i = 0; i <= HALL.getX(); i++) {
            for (int j = -1; j <= HALL.getY(); j++) {
                for (int k = 0; k <= HALL.getZ(); k++) {
                    BlockPos pos = base.offset(i, j, k);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isSolid()) {
                        if (j == -1 || j == HALL.getY()) {
                            return false;
                        }
                        boolean border = i == 0 || i == HALL.getX() || k == 0 || k == HALL.getZ();
                        if (border && j == 0 && state.isAir() && level.getBlockState(pos.above()).isAir()
                                && opensOutwards(level, pos, i, k)) {
                            entrances.add(pos);
                        }
                    }
                }
            }
        }
        if (entrances.isEmpty() || entrances.size() > 4) {
            return false;
        }
        for (BlockPos entrance : entrances) {
            BlockPos rel = entrance.subtract(base.offset(6, 0, 6));
            int ax = Math.abs(rel.getX());
            int az = Math.abs(rel.getZ());
            if (ax == az || ax == az - 1 || ax - 1 == az) {
                continue;
            }
            int side = rel.getZ() == -6 ? 0 : rel.getX() == 5 ? 1 : rel.getZ() == 5 ? 2 : 3;
            buildHall(level, base, hallLayout(side), null);
            return true;
        }
        return false;
    }

    /** An entrance must lead on into the cave: the cell beyond the border is open too. */
    private static boolean opensOutwards(ServerLevel level, BlockPos pos, int i, int k) {
        int dx = i == 0 ? -1 : i == HALL.getX() ? 1 : 0;
        int dz = dx != 0 ? 0 : k == 0 ? -1 : k == HALL.getZ() ? 1 : 0;
        BlockPos out = pos.offset(dx, 0, dz);
        return !level.getBlockState(out).isSolid() && !level.getBlockState(out.above()).isSolid();
    }
}
