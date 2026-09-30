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

        net.minecraft.client.player.LocalPlayer local = Minecraft.getInstance().player;
        if ((entry.has("hud") || entry.has("meter")) && local != null
                && girl.scenePartnerId().filter(local.getUUID()::equals).isPresent()) {
            SceneHud.apply(entry);
        }

        if (entry.has("flap") && local != null && girl.getFirstPassenger() == local) {
            // Original flapControlled: each wing beat pushes her along the rider's input direction.
            FlightHud.show();
            float forward = local.input.forwardImpulse;
            float strafe = local.input.leftImpulse;
            if (forward != 0.0F || strafe != 0.0F) {
                net.minecraft.world.phys.Vec3 impulse = new net.minecraft.world.phys.Vec3(strafe, 0.0D, forward)
                        .xRot(-local.getXRot() * ((float) Math.PI / 180.0F))
                        .yRot(-local.getYRot() * ((float) Math.PI / 180.0F));
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.trolmastercard.sexmod.network.GalathFlightPayload(
                                girl.getId(), 0, (float) impulse.x, (float) impulse.y, (float) impulse.z));
            }
        }

        if (entry.has("chat") || entry.has("text")) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.distanceToSqr(girl) < 30.0D * 30.0D) {
                minecraft.player.sendSystemMessage(Component.literal("<")
                        .append(girl.getDisplayName())
                        .append("> ")
                        .append(entry.has("chat")
                                ? Component.translatable(entry.get("chat").getAsString())
                                : Component.literal(entry.get("text").getAsString())));
            }
        }
        if (entry.has("every")) {
            // Alternating events: only the 1st, (N+1)th, ... call plays (original toggles like Allie's fastMoan).
            int every = Math.max(1, entry.get("every").getAsInt());
            int count = COUNTERS.merge(girl.getId() + ":" + eventId, 1, Integer::sum);
            if ((count - 1) % every != 0) {
                return;
            }
        }
        if (entry.has("sounds")) {
            playLayer(girl, entry);
        }
        if (entry.has("layers")) {
            for (JsonElement layer : entry.getAsJsonArray("layers")) {
                playLayer(girl, layer.getAsJsonObject());
            }
        }
    }

    private static final java.util.Map<String, Integer> COUNTERS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * One sound layer: {"sounds":[...],"volume":1,"pitch":1,"chance":1} plays a random pick; or
     * {"any_of":[layer,...],"weights":[1,4]} plays exactly one of the listed layers by weight.
     */
    private static void playLayer(CompanionEntity girl, JsonObject layer) {
        if (layer.has("chance") && girl.getRandom().nextFloat() >= layer.get("chance").getAsFloat()) {
            return;
        }
        if (layer.has("any_of")) {
            JsonArray options = layer.getAsJsonArray("any_of");
            if (options.size() == 0) {
                return;
            }
            int[] weights = new int[options.size()];
            int total = 0;
            for (int i = 0; i < weights.length; i++) {
                weights[i] = layer.has("weights") ? layer.getAsJsonArray("weights").get(i).getAsInt() : 1;
                total += weights[i];
            }
            int roll = girl.getRandom().nextInt(Math.max(1, total));
            for (int i = 0; i < weights.length; i++) {
                roll -= weights[i];
                if (roll < 0) {
                    JsonElement chosen = options.get(i);
                    if (chosen.isJsonObject()) {
                        playLayer(girl, chosen.getAsJsonObject());
                    }
                    return;
                }
            }
            return;
        }
        if (!layer.has("sounds")) {
            return;
        }
        JsonArray sounds = layer.getAsJsonArray("sounds");
        if (sounds.size() == 0) {
            return;
        }
        String path = sounds.get(girl.getRandom().nextInt(sounds.size())).getAsString();
        float volume = layer.has("volume") ? layer.get("volume").getAsFloat() : 1.0F;
        float pitch = layer.has("pitch") ? layer.get("pitch").getAsFloat() : 1.0F;
        if (ModSounds.find(path).isPresent()) {
            SoundEvent event = SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, path));
            girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), event,
                    SoundSource.NEUTRAL, volume, pitch, false);
        } else if (REPORTED.add(path)) {
            FapCraft.LOGGER.warn("keyframe_events names an unknown sound: {}", path);
        }
    }
}
