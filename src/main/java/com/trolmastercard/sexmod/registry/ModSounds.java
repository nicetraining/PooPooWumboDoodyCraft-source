package com.trolmastercard.sexmod.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trolmastercard.sexmod.FapCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registers every key declared by the original sounds.json without maintaining
 * a second, error-prone list of several hundred event IDs.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, FapCraft.MOD_ID);
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> REGISTERED =
            new LinkedHashMap<>();

    static {
        registerSoundDefinitions();
    }

    private ModSounds() {
    }

    private static void registerSoundDefinitions() {
        try (InputStream stream = ModSounds.class.getResourceAsStream("/assets/sexmod/sounds.json")) {
            if (stream == null) {
                FapCraft.LOGGER.warn("No sounds.json was found; original sound events will not be registered");
                return;
            }

            JsonObject definitions = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            ).getAsJsonObject();

            definitions.keySet().forEach(path -> REGISTERED.put(path, SOUNDS.register(
                    path,
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, path)
                    )
            )));
            FapCraft.LOGGER.info("Prepared {} original sound event registrations", definitions.size());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load FapCraft sound definitions", exception);
        }
    }

    public static Optional<DeferredHolder<SoundEvent, SoundEvent>> find(String path) {
        return Optional.ofNullable(REGISTERED.get(path));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
