package com.fartypoopy.sexmod.worldgen;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Keeps world-generation trees off the girls' houses. Houses are placed before trees (the structures step comes before
 * vegetation), so a tree could root in a house's grass yard or right beside it and grow through the roof. A tree is
 * refused when it would stand on a house or within {@link #MARGIN} blocks of it. Trees grown later from saplings are not
 * affected.
 */
public final class HouseClearance {
    /** Blocks around a house's footprint where no tree may stand: the canopy radius of the widest trees (dark oak). */
    static final int MARGIN = 5;
    /** How far above a house a tree's base still counts (the house's roof and a bit). */
    static final int ABOVE = 4;

    private HouseClearance() {
    }

    /** Whether a tree about to be placed at {@code pos} during world generation would overlap a girl's house. */
    public static boolean blocksTree(WorldGenLevel level, BlockPos pos) {
        if (!(level instanceof WorldGenRegion region)) {
            return false; // a sapling growing in a loaded world
        }
        ChunkPos at = new ChunkPos(pos);
        if (!region.hasChunk(at.x, at.z)) {
            return false;
        }
        // the chunk knows which structures touch it, and in which chunks they start
        ChunkAccess chunk = region.getChunk(at.x, at.z);
        for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> entry : chunk.getAllReferences().entrySet()) {
            if (!(entry.getKey() instanceof GirlHouseStructure)) {
                continue;
            }
            for (long packed : entry.getValue()) {
                ChunkPos startChunk = new ChunkPos(packed);
                if (!region.hasChunk(startChunk.x, startChunk.z)) {
                    continue;
                }
                StructureStart start = region.getChunk(startChunk.x, startChunk.z).getStartForStructure(entry.getKey());
                if (start != null && start.isValid() && near(start, pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean near(StructureStart start, BlockPos pos) {
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (pos.getX() >= box.minX() - MARGIN && pos.getX() <= box.maxX() + MARGIN
                    && pos.getZ() >= box.minZ() - MARGIN && pos.getZ() <= box.maxZ() + MARGIN
                    && pos.getY() >= box.minY() - MARGIN && pos.getY() <= box.maxY() + ABOVE) {
                return true;
            }
        }
        return false;
    }
}
