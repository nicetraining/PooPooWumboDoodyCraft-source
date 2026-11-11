package com.fartypoopy.sexmod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.Optional;

/**
 * A girl's house (original g3 surface structures: ellie, jenny, bia, luna) as a 1.21 structure. The site rules are the
 * original's: the template centred on the chunk, refused when the footprint is more than {@code max_height_diff} uneven
 * or (land houses) has water on top. It stands at the height most of its footprint has (the original took the highest
 * point, which raised houses on a ledge on gently uneven ground). The original's dirt skirt below the house is replaced
 * by the structure's terrain adaptation (beard_thin in the JSON), which also cuts away ground rising into it.
 */
public final class GirlHouseStructure extends Structure {
    public static final MapCodec<GirlHouseStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            Codec.STRING.fieldOf("template").forGetter(s -> s.template),
            Codec.INT.fieldOf("size_x").forGetter(s -> s.sizeX),
            Codec.INT.fieldOf("size_z").forGetter(s -> s.sizeZ),
            Codec.INT.fieldOf("max_height_diff").forGetter(s -> s.maxHeightDiff),
            Codec.BOOL.optionalFieldOf("on_water", false).forGetter(s -> s.onWater)
    ).apply(instance, GirlHouseStructure::new));

    private final String template;
    private final int sizeX;
    private final int sizeZ;
    private final int maxHeightDiff;
    private final boolean onWater;

    private GirlHouseStructure(StructureSettings settings, String template, int sizeX, int sizeZ, int maxHeightDiff,
                               boolean onWater) {
        super(settings);
        this.template = template;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.maxHeightDiff = maxHeightDiff;
        this.onWater = onWater;
    }


    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!com.fartypoopy.sexmod.Config.SHOULD_GEN_BUILDINGS.get()) {
            return Optional.empty(); // original g3.i (config shouldGenBuildings)
        }
        ChunkPos chunk = context.chunkPos();
        int x0 = chunk.getMinBlockX() + (16 - this.sizeX) / 2;
        int z0 = chunk.getMinBlockZ() + (16 - this.sizeZ) / 2;
        ChunkGenerator generator = context.chunkGenerator();
        // the biome first: it is cheap and rules out almost every candidate (/locate tries thousands of them)
        var biome = context.biomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x0),
                net.minecraft.core.QuartPos.fromBlock(generator.getSeaLevel()), net.minecraft.core.QuartPos.fromBlock(z0),
                context.randomState().sampler());
        if (!context.validBiome().test(biome)) {
            return Optional.empty();
        }
        int cx = x0 + this.sizeX / 2;
        int cz = z0 + this.sizeZ / 2;
        int max = this.top(context, cx, cz);
        // biomes are 3D: the sea level check passes under hills, so check again where she would stand
        var surfaceBiome = context.biomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(cx),
                net.minecraft.core.QuartPos.fromBlock(max), net.minecraft.core.QuartPos.fromBlock(cz),
                context.randomState().sampler());
        if (!context.validBiome().test(surfaceBiome)) {
            return Optional.empty();
        }
        if (!this.onWater && generator.getBaseHeight(cx, cz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
                context.randomState()) - 1 < max) {
            return Optional.empty(); // original: a land house never stands on water
        }
        // the footprint on a 3 x 3 grid (corners, edge middles, centre). Not the original's highest point: on gently
        // uneven ground one high corner lifted the whole house, and the terrain adaptation built a one-block ledge of
        // dirt under it. The median is the height most of the footprint has; beard_thin fills in below the house and
        // cuts away ground that rises into it, as for villages
        int[] heights = new int[9];
        int n = 0;
        int min = max;
        for (int gx = 0; gx < 3; gx++) {
            for (int gz = 0; gz < 3; gz++) {
                int x = x0 + gx * (this.sizeX - 1) / 2;
                int z = z0 + gz * (this.sizeZ - 1) / 2;
                int top = gx == 1 && gz == 1 ? max : this.top(context, x, z); // the centre was sampled above
                heights[n++] = top;
                min = Math.min(min, top);
                if (Math.max(max, top) - min > this.maxHeightDiff) {
                    return Optional.empty(); // original: refused when the footprint is too uneven
                }
                max = Math.max(max, top);
            }
        }
        java.util.Arrays.sort(heights);
        int ground = heights[4];
        BlockPos pos = new BlockPos(x0, ground, z0);
        var template = context.structureTemplateManager().get(id(this.template));
        if (template.isEmpty()) {
            return Optional.empty();
        }
        BoundingBox box = template.get().getBoundingBox(new StructurePlaceSettings(), pos);
        return Optional.of(new GenerationStub(pos, builder -> builder.addPiece(new Piece(this.template, pos, box))));
    }


    private int top(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                context.randomState()) - 1;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.GIRL_HOUSE.get();
    }

    static ResourceLocation id(String template) {
        return ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, template);
    }

    /** The house template, placed chunk by chunk inside each chunk's box (its girl and animals come with it). */
    public static final class Piece extends StructurePiece {
        private final String template;
        private final BlockPos origin;

        Piece(String template, BlockPos origin, BoundingBox box) {
            super(ModStructures.GIRL_HOUSE_PIECE.get(), 0, box);
            this.template = template;
            this.origin = origin;
        }

        public Piece(CompoundTag tag) {
            super(ModStructures.GIRL_HOUSE_PIECE.get(), tag);
            this.template = tag.getString("Template");
            this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putString("Template", this.template);
            tag.putInt("OX", this.origin.getX());
            tag.putInt("OY", this.origin.getY());
            tag.putInt("OZ", this.origin.getZ());
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
            OriginalStructures.place(level, this.template, this.origin, net.minecraft.world.level.block.Rotation.NONE, box);
        }
    }
}
