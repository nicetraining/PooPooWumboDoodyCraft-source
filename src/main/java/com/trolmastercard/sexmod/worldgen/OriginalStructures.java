package com.trolmastercard.sexmod.worldgen;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Original g3 (IWorldGenerator): the four surface structures (ellie, jenny, bia, luna) and the goblin hall, tried once
 * for every newly generated chunk. Chunks are queued until the area around them is loaded (1.12 populated a chunk only
 * when its neighbours existed).
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class OriginalStructures {
    /** Original b: a surface structure and where it may stand. */
    public record Def(String name, Set<ResourceKey<Biome>> biomes, int sizeX, int sizeZ, int maxHeightDiff, boolean terraform) {
    }

    private static final int SAME_SPACING = 156;
    private static final int OTHER_SPACING = 62;
    public static final Vec3i HALL = new Vec3i(11, 6, 11);
    private static final int MAX_WAIT_TICKS = 600;

    /** Original list order (ellie is listed twice). The biome sets are the nearest 1.21 equivalents of the 1.12 biomes. */
    public static final List<Def> DEFS = List.of(
            new Def("ellie", Set.of(Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.SNOWY_TAIGA, Biomes.TAIGA, Biomes.DARK_FOREST),
                    30, 26, 9, true),
            new Def("jenny", Set.of(Biomes.PLAINS, Biomes.FOREST), 9, 9, 1, true),
            new Def("ellie", Set.of(Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.SNOWY_TAIGA, Biomes.TAIGA, Biomes.DARK_FOREST),
                    30, 26, 9, true),
            new Def("bia", Set.of(Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.BIRCH_FOREST), 11, 15, 2, true),
            new Def("luna", Set.of(Biomes.OCEAN, Biomes.DEEP_OCEAN), 3, 10, 0, false));

    private record Pending(int cx, int cz, int age) {
    }

    private static final Map<ServerLevel, List<Pending>> PENDING = new IdentityHashMap<>();
    private static boolean busy;

    private OriginalStructures() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level) || level.isFlat()
                || level.dimension() == net.minecraft.world.level.Level.END) {
            return;
        }
        var pos = event.getChunk().getPos();
        PENDING.computeIfAbsent(level, l -> new ArrayList<>()).add(new Pending(pos.x, pos.z, 0));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Pending> list = PENDING.get(level);
        if (list == null || list.isEmpty() || level.getGameTime() % 4 != 0) {
            return;
        }
        Pending next = list.remove(0);
        if (!ready(level, next.cx(), next.cz())) {
            if (next.age() < MAX_WAIT_TICKS / 4) {
                list.add(new Pending(next.cx(), next.cz(), next.age() + 1));
            }
            return;
        }
        generate(level, next.cx(), next.cz(), level.getRandom());
    }

    @SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.remove(level);
        }
    }

    private static boolean ready(ServerLevel level, int cx, int cz) {
        // structures reach 1 chunk around; the terraform skirt drifts up to 32 blocks north-west
        return level.hasChunksAt((cx - 3) << 4, (cz - 3) << 4, ((cx + 1) << 4) + 15, ((cz + 1) << 4) + 15);
    }

    /** Original g3.generate after the tribe roll: the goblin hall, then the surface structures. */
    public static void generate(ServerLevel level, int cx, int cz, RandomSource random) {
        if (level.isFlat()) {
            return;
        }
        tryHall(level, cx, cz, random);
        if (busy) {
            return;
        }
        busy = true;
        try {
            for (Def def : DEFS) {
                tryStructure(level, def, cx, cz);
            }
        } finally {
            busy = false;
        }
    }

    // ---------------------------------------------------------------- surface structures

    /** Original cj.a: the first grass, sand, water, stone or cobblestone from the top. */
    static int surface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int min = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (y-- > min) {
            Block block = level.getBlockState(pos.set(x, y, z)).getBlock();
            if (block == Blocks.GRASS_BLOCK || block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.WATER
                    || block == Blocks.STONE || block == Blocks.COBBLESTONE) {
                return y;
            }
        }
        return y;
    }

    public static boolean tryStructure(ServerLevel level, Def def, int cx, int cz) {
        PlacedStructures data = PlacedStructures.get(level);
        for (PlacedStructures.Entry entry : data.entries()) {
            int spacing = entry.name().equals(def.name()) ? SAME_SPACING : OTHER_SPACING;
            if (entry.distanceTo(cx, cz) < spacing) {
                return false;
            }
        }
        int x0 = cx * 16 + (16 - def.sizeX()) / 2;
        int z0 = cz * 16 + (16 - def.sizeZ()) / 2;
        if (!def.biomes().contains(level.getBiome(new BlockPos(x0, 80, z0)).unwrapKey().orElse(null))) {
            return false;
        }
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (int x = x0; x < x0 + def.sizeX(); x++) {
            for (int z = z0; z < z0 + def.sizeZ(); z++) {
                int y = surface(level, x, z);
                if (def.terraform() && level.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER)) {
                    return false;
                }
                max = Math.max(max, y);
                min = Math.min(min, y);
            }
        }
        if (max - min > def.maxHeightDiff()) {
            return false;
        }
        data.add(cx, cz, def.name());
        place(level, def.name(), new BlockPos(x0, max, z0), Rotation.NONE);
        if (def.terraform()) {
            terraform(level, x0, max, z0, def.sizeX(), def.sizeZ());
        }
        return true;
    }

    /** Original fill below the structure: grass where the sky is visible, dirt elsewhere; the window drifts one block per layer. */
    private static void terraform(ServerLevel level, int x0, int top, int z0, int sizeX, int sizeZ) {
        boolean changed = true;
        int y = top - 1;
        int x = x0;
        int z = z0;
        for (int layer = 0; changed && layer < 200 && y > level.getMinBuildHeight(); layer++) {
            changed = false;
            z--;
            x--;
            for (int i = x; i < x + sizeX + 2; i++) {
                for (int j = z; j < z + sizeZ + 2; j++) {
                    BlockPos pos = new BlockPos(i, y, j);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getCollisionShape(level, pos).isEmpty()) {
                        continue;
                    }
                    level.setBlock(pos, level.canSeeSky(pos) ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
                    changed = true;
                }
            }
            y--;
        }
    }

    public static boolean place(ServerLevel level, String name, BlockPos pos, Rotation rotation) {
        var template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, name));
        if (template.isEmpty()) {
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        template.get().placeInWorld(level, pos, pos, settings, level.getRandom(), 2);
        return true;
    }

    // ---------------------------------------------------------------- goblin hall

    /** Original g3.a(World, Random, int, int): a 12x6x12 hollow with a solid floor and ceiling and 1 to 4 entrances. */
    public static boolean tryHall(ServerLevel level, int cx, int cz, RandomSource random) {
        int y = random.nextInt(255);
        return placeHall(level, new BlockPos(16 * cx + 3, y, 16 * cz + 3));
    }

    public static boolean placeHall(ServerLevel level, BlockPos base) {
        List<BlockPos> entrances = new ArrayList<>();
        for (int i = 0; i <= HALL.getX(); i++) {
            for (int j = -1; j <= HALL.getY(); j++) {
                for (int k = 0; k <= HALL.getZ(); k++) {
                    BlockPos pos = base.offset(i, j, k);
                    BlockState state = level.getBlockState(pos);
                    boolean solid = state.isSolid();
                    if (!solid) {
                        if (j == -1 || j == HALL.getY()) {
                            return false;
                        }
                        boolean border = i == 0 || i == HALL.getX() || k == 0 || k == HALL.getZ();
                        if (border && j == 0 && state.isAir() && level.getBlockState(pos.above()).isAir()) {
                            entrances.add(pos);
                        }
                    }
                }
            }
        }
        if (entrances.isEmpty() || entrances.size() > 4) {
            return false;
        }
        BlockPos chosen = null;
        for (BlockPos entrance : entrances) {
            BlockPos rel = entrance.subtract(base.offset(6, 0, 6));
            int ax = Math.abs(rel.getX());
            int az = Math.abs(rel.getZ());
            if (ax == az || ax == az - 1 || ax - 1 == az) {
                continue;
            }
            chosen = rel;
            break;
        }
        if (chosen == null) {
            return false;
        }
        Rotation rotation;
        Vec3 throne;
        Vec3i offset = Vec3i.ZERO;
        float yaw = 0.0F;
        if (chosen.getZ() == -6) {
            rotation = Rotation.NONE;
            throne = new Vec3(5, 1, 9);
            yaw = 180.0F;
        } else if (chosen.getX() == 5) {
            rotation = Rotation.CLOCKWISE_90;
            throne = new Vec3(1, 1, 5);
            offset = new Vec3i(HALL.getX() - 1, 0, 0);
            yaw = -90.0F;
        } else if (chosen.getZ() == 5) {
            rotation = Rotation.CLOCKWISE_180;
            throne = new Vec3(5, 1, 1);
            offset = new Vec3i(HALL.getX() - 1, 0, HALL.getZ() - 1);
        } else {
            rotation = Rotation.COUNTERCLOCKWISE_90;
            throne = new Vec3(9, 1, 5);
            offset = new Vec3i(0, 0, HALL.getZ() - 1);
            yaw = 90.0F;
        }
        place(level, "goblin", base.offset(0, -1, 0).offset(offset), rotation);
        Vec3 at = new Vec3(base.getX() + throne.x + 0.5D, base.getY() + throne.y, base.getZ() + throne.z + 0.5D);
        CompanionEntity queen = ModEntities.get(CompanionKind.GOBLIN).create(level);
        if (queen != null) {
            queen.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            queen.setGoblinQueen(true);
            queen.goblin().thronePos = at;
            queen.goblin().throneRot = yaw;
            queen.setPersistenceRequired();
            level.addFreshEntity(queen);
        }
        return true;
    }
}
