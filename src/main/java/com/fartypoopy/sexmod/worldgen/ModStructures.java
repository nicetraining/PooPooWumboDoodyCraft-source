package com.fartypoopy.sexmod.worldgen;

import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The houses and goblin lairs as 1.21 structures (data/sexmod/worldgen/structure, structure_set and the
 * has_structure biome tags), so /locate structure finds them before their chunks exist. The original placed them
 * from a chunk generator hook (g3) after generation.
 */
public final class ModStructures {
    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, FapCraft.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, FapCraft.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<GirlHouseStructure>> GIRL_HOUSE =
            TYPES.register("girl_house", () -> () -> GirlHouseStructure.CODEC);
    public static final DeferredHolder<StructureType<?>, StructureType<GoblinLairStructure>> GOBLIN_LAIR =
            TYPES.register("goblin_lair", () -> () -> GoblinLairStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> GIRL_HOUSE_PIECE =
            PIECES.register("girl_house", () -> (StructurePieceType.ContextlessType) GirlHouseStructure.Piece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> GOBLIN_LAIR_PIECE =
            PIECES.register("goblin_lair", () -> (StructurePieceType.ContextlessType) GoblinLairStructure.Piece::new);

    private ModStructures() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }
}
