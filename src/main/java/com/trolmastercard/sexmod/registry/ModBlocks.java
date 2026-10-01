package com.trolmastercard.sexmod.registry;

import com.trolmastercard.sexmod.FapCraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The old mod registered a non-ticking fire block used by scripted scenes.
 * Keeping the same ID prevents missing-registry errors in migrated worlds.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FapCraft.MOD_ID);

    public static final DeferredBlock<Block> FIRE = BLOCKS.register(
            "fire",
            () -> new CampFireBlock(
            BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .lightLevel(state -> 15)
                    .sound(SoundType.WOOL)
                    .replaceable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY))
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
