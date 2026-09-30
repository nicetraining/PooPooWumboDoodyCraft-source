package com.trolmastercard.sexmod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Client side of the original's animation sound listener: animation keyframes carry event ids
 * (for example "talk_hornyMSG1"); this looks the id up in {@code keyframe_events.json} and plays the
 * girl's chat line and sound. Scene timing itself stays on the server.
 */
public final class KeyframeEvents {
    private static final java.util.Map<String, JsonObject> tables = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> REPORTED = new HashSet<>();

    private KeyframeEvents() {
    }

    /** One file per kind: assets/sexmod/keyframe_events/&lt;kind&gt;.json, event id -> chat line / sounds / volume. */
    private static JsonObject tableFor(String kind) {
        return tables.computeIfAbsent(kind, key -> {
            try (InputStream stream = KeyframeEvents.class.getResourceAsStream(
                    "/assets/sexmod/keyframe_events/" + key + ".json")) {
                if (stream != null) {
                    return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            } catch (Exception exception) {
                FapCraft.LOGGER.warn("Could not read keyframe events for {}", key, exception);
            }
            return new JsonObject();
        });
    }

    public static void handle(CompanionEntity girl, String eventId) {
        if (eventId == null || eventId.isEmpty() || girl.level() == null || !girl.level().isClientSide) {
            return;
        }
        JsonObject kind = tableFor(girl.kind().registryPath());
        JsonObject entry = kind.has(eventId) ? kind.getAsJsonObject(eventId) : null;
        if (entry == null) {
            return; // events without an entry are timing markers or not ported yet
        }

        if (entry.has("chat")) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.distanceToSqr(girl) < 30.0D * 30.0D) {
                minecraft.player.sendSystemMessage(Component.literal("<")
                        .append(girl.getDisplayName())
                        .append("> ")
                        .append(Component.translatable(entry.get("chat").getAsString())));
            }
        }
        if (entry.has("sounds")) {
            JsonArray sounds = entry.getAsJsonArray("sounds");
            if (sounds.size() > 0) {
                JsonElement pick = sounds.get(girl.getRandom().nextInt(sounds.size()));
                float volume = entry.has("volume") ? entry.get("volume").getAsFloat() : 1.0F;
                String path = pick.getAsString();
                if (ModSounds.find(path).isPresent()) {
                    SoundEvent event = SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, path));
                    girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), event,
                            SoundSource.NEUTRAL, volume, 1.0F, false);
                } else if (REPORTED.add(path)) {
                    FapCraft.LOGGER.warn("keyframe_events.json names an unknown sound: {}", path);
                }
            }
        }
    }
}
