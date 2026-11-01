package com.fartypoopy.sexmod.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The goblin queen's hall (original g3: a 12 x 7 x 12 room set into a cave, its open front towards the cave, the queen
 * on her throne) as a 1.21 structure. The cave is found in the terrain noise before the chunk exists: a height from
 * y 8 (above the deepslate) to well below the surface with rock at the floor and ceiling corners and, on one side, two
 * open cells in a row outside the wall at floor and head height. That passage is kept open when the hall is built,
 * so every lair can be walked into from its cave.
 */
public final class GoblinLairStructure extends Structure {
    public static final MapCodec<GoblinLairStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance)
    ).apply(instance, GoblinLairStructure::new));

    private static final int MIN_Y = 8;
    private static final int HEIGHT_TRIES = 12;

    private GoblinLairStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!com.fartypoopy.sexmod.Config.SHOULD_GEN_BUILDINGS.get()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        ChunkGenerator generator = context.chunkGenerator();
        int x = chunk.getMinBlockX() + 3;
        int z = chunk.getMinBlockZ() + 3;
        int surface = generator.getBaseHeight(x + 6, z + 6, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
                context.randomState());
        int minY = Math.max(MIN_Y, context.heightAccessor().getMinBuildHeight() + 2);
        int maxY = surface - 16;
        if (maxY <= minY) {
            return Optional.empty();
        }
        BlockPos probe = new BlockPos(x, 0, z);
        // columns are read only when needed: the centre first, the passage sides next, the corners last
        NoiseColumn centre = generator.getBaseColumn(x + 6, z + 6, context.heightAccessor(), context.randomState());
        NoiseColumn[][] passage = new NoiseColumn[4][2];
        List<NoiseColumn> corners = null;
        RandomSource random = context.random();
        for (int attempt = 0; attempt < HEIGHT_TRIES; attempt++) {
            int y = minY + random.nextInt(maxY - minY);
            if (!solid(centre.getBlock(y - 1)) || !solid(centre.getBlock(y + OriginalStructures.HALL.getY()))) {
                continue;
            }
            int first = random.nextInt(4);
            for (int n = 0; n < 4; n++) {
                int side = (first + n) % 4;
                if (!open(column(context, passage, probe, side, 1), y) || !open(column(context, passage, probe, side, 2), y)) {
                    continue;
                }
                if (corners == null) {
                    corners = new ArrayList<>();
                    for (int[] c : new int[][]{{0, 0}, {OriginalStructures.HALL.getX(), 0}, {0, OriginalStructures.HALL.getZ()},
                            {OriginalStructures.HALL.getX(), OriginalStructures.HALL.getZ()}}) {
                        corners.add(generator.getBaseColumn(x + c[0], z + c[1], context.heightAccessor(), context.randomState()));
                    }
                }
                if (!this.inRock(corners, y)) {
                    break; // the room would break into open space: another height
                }
                BlockPos base = new BlockPos(x, y, z);
                BoundingBox box = new BoundingBox(x - 2, y - 1, z - 2,
                        x + OriginalStructures.HALL.getX() + 2, y + OriginalStructures.HALL.getY() + 1,
                        z + OriginalStructures.HALL.getZ() + 2);
                return Optional.of(new GenerationStub(base, builder -> builder.addPiece(new Piece(base, side, box))));
            }
        }
        return Optional.empty();
    }

    private static NoiseColumn column(GenerationContext context, NoiseColumn[][] cache, BlockPos probe, int side, int step) {
        if (cache[side][step - 1] == null) {
            BlockPos at = OriginalStructures.outside(probe, side, step);
            cache[side][step - 1] = context.chunkGenerator().getBaseColumn(at.getX(), at.getZ(), context.heightAccessor(),
                    context.randomState());
        }
        return cache[side][step - 1];
    }

    /** Rock (no air, no fluid) where the floor and the ceiling go, at the corners and the middle. */
    private boolean inRock(List<NoiseColumn> columns, int y) {
        for (NoiseColumn column : columns) {
            if (!solid(column.getBlock(y - 1)) || !solid(column.getBlock(y + OriginalStructures.HALL.getY()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    private static boolean open(NoiseColumn column, int y) {
        return column.getBlock(y).isAir() && column.getBlock(y + 1).isAir();
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.GOBLIN_LAIR.get();
    }

    /** The hall template, its queen, and the two passage cells outside its front kept open. */
    public static final class Piece extends StructurePiece {
        private final BlockPos base;
        private final int side;

        Piece(BlockPos base, int side, BoundingBox box) {
            super(ModStructures.GOBLIN_LAIR_PIECE.get(), 0, box);
            this.base = base;
            this.side = side;
        }

        public Piece(CompoundTag tag) {
            super(ModStructures.GOBLIN_LAIR_PIECE.get(), tag);
            this.base = new BlockPos(tag.getInt("BX"), tag.getInt("BY"), tag.getInt("BZ"));
            this.side = tag.getInt("Side");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("BX", this.base.getX());
            tag.putInt("BY", this.base.getY());
            tag.putInt("BZ", this.base.getZ());
            tag.putInt("Side", this.side);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
            OriginalStructures.buildHall(level, this.base, OriginalStructures.hallLayout(this.side), box);
            for (int step = 0; step <= 2; step++) {
                BlockPos cell = OriginalStructures.outside(this.base, this.side, step);
                for (BlockPos pos : new BlockPos[]{cell, cell.above()}) {
                    if (box.isInside(pos) && !level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
