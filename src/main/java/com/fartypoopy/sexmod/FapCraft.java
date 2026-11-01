package com.fartypoopy.sexmod;

import com.mojang.logging.LogUtils;
import com.fartypoopy.sexmod.registry.ModBlocks;
import com.fartypoopy.sexmod.registry.ModEntities;
import com.fartypoopy.sexmod.registry.ModItems;
import com.fartypoopy.sexmod.registry.ModMenus;
import com.fartypoopy.sexmod.registry.ModPotions;
import com.fartypoopy.sexmod.registry.ModSounds;
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

    /**
     * The original's animation files use bare names as values: easing names that its exporter wrote into keyframe
     * vectors ("easeInOutSine", "-97.0283+easeInOutSine"), which 1.12's GeckoLib read as 0, and the model variables
     * size / pitch / yaw / armpitch / armyaw. GeckoLib 4.9.2 created unknown variables on the fly; 4.9.3 rejects every
     * animation that names one that is not registered yet (132 of them), so they are registered before any load.
     */
    public static final java.util.List<String> ANIMATION_VARIABLES = animationVariables();

    private static java.util.List<String> animationVariables() {
        java.util.List<String> names = new java.util.ArrayList<>(java.util.List.of("linear", "step", "catmullrom",
                "bounce", "elastic", "size", "pitch", "yaw", "armpitch", "armyaw", "scale", "entity"));
        for (String family : new String[]{"sine", "quad", "cubic", "quart", "quint", "expo", "circ", "back", "elastic", "bounce"}) {
            for (String prefix : new String[]{"easein", "easeout", "easeinout"}) {
                names.add(prefix + family);
            }
        }
        return java.util.List.copyOf(names);
    }

    /** Client only: GeckoLib's variable registry loads client classes (a dedicated server would crash). */
    private static void registerAnimationVariables() {
        for (String name : ANIMATION_VARIABLES) {
            // easing names stay 0 (what 1.12 and GeckoLib 4.9.2 read them as); the renderers set the model variables
            software.bernie.geckolib.loading.math.MathParser.setVariable(name, () -> 0.0D);
        }
    }

    public FapCraft(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, Config.SPEC);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            registerAnimationVariables();
        }
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModPotions.register(modEventBus);
        ModSounds.register(modEventBus);
        com.fartypoopy.sexmod.worldgen.ModStructures.register(modEventBus);

        modEventBus.addListener(com.fartypoopy.sexmod.network.CompanionActionPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.OpenSceneMenuPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.TransitionPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.SceneControlPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.GalathFlightPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.GalathEscapePayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.TribeCommandPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.TribeMarkersPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.TribeUiPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.GoblinWheelPayload::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.CustomModelNetwork::register);
        modEventBus.addListener(com.fartypoopy.sexmod.network.PlayerFormNetwork::register);
        modEventBus.addListener(ModEntities::createAttributes);
        modEventBus.addListener(ModEntities::registerSpawnPlacements);
        NeoForge.EVENT_BUS.addListener(ModPotions::registerBrewingRecipes);

        LOGGER.info("Loading FapCraft compatibility port for Minecraft 1.21.1");
    }
}
