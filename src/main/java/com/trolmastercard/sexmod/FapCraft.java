package com.trolmastercard.sexmod;

import com.mojang.logging.LogUtils;
import com.trolmastercard.sexmod.registry.ModBlocks;
import com.trolmastercard.sexmod.registry.ModEntities;
import com.trolmastercard.sexmod.registry.ModItems;
import com.trolmastercard.sexmod.registry.ModMenus;
import com.trolmastercard.sexmod.registry.ModPotions;
import com.trolmastercard.sexmod.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * NeoForge entry point for the Minecraft 1.21.1 port.
 *
 * <p>The original 1.12.2 mod used pre-init/init/post-init handlers and sided
 * proxies. NeoForge now supplies one mod event bus to the constructor, so all
 * registries are attached here and client-only code lives in a separate
 * package.</p>
 */
@Mod(FapCraft.MOD_ID)
public final class FapCraft {
    public static final String MOD_ID = "sexmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FapCraft(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModPotions.register(modEventBus);
        ModSounds.register(modEventBus);

        modEventBus.addListener(com.trolmastercard.sexmod.network.CompanionActionPayload::register);
        modEventBus.addListener(com.trolmastercard.sexmod.network.OpenSceneMenuPayload::register);
        modEventBus.addListener(com.trolmastercard.sexmod.network.TransitionPayload::register);
        modEventBus.addListener(ModEntities::createAttributes);
        modEventBus.addListener(ModEntities::registerSpawnPlacements);
        NeoForge.EVENT_BUS.addListener(ModPotions::registerBrewingRecipes);

        LOGGER.info("Loading FapCraft compatibility port for Minecraft 1.21.1");
    }
}
