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
        if (girl.kind() == com.trolmastercard.sexmod.entity.CompanionKind.GALATH) {
            switch (eventId) {
                case "startRenderSword" -> {
                    girl.galathSword = true;
                    girl.galathBlade = true;
                }
                case "stopFadeInParticles" -> girl.galathBlade = false;
                case "stopRenderSword" -> {
                    girl.galathSword = false;
                    girl.galathBlade = false;
                }
                case "setNude" -> girl.galathNudeBurst = true;
                default -> {
                }
            }
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

        boolean partner = local != null && girl.scenePartnerId().filter(local.getUUID()::equals).isPresent();
        if (entry.has("wipe") && partner) {
            TransitionOverlay.start(); // original blackscreen: fh.b() for the owner
        }
        if (entry.has("face_girl") && partner && local != null) {
            // original setCoinLook: the owner's view turns to the girl's yaw + 180, level
            float yaw = girl.getYRot() + 180.0F;
            local.setYRot(yaw);
            local.yRotO = yaw;
            local.setXRot(0.0F);
            local.xRotO = 0.0F;
        }
        if (entry.has("arm") && girl.scenePartnerId().isPresent()) {
            if ("raise".equals(entry.get("arm").getAsString())) {
                PaymentArm.raise(girl.scenePartnerId().get());
            } else {
                PaymentArm.lower(girl.scenePartnerId().get());
            }
        }
        if (entry.has("view") && partner && local != null) {
            // original camera keyframes: yaw = scene yaw + offset, pitch, and the F5 perspective (0 first, 1 back, 2 front)
            JsonObject view = entry.getAsJsonObject("view");
            Minecraft minecraft = Minecraft.getInstance();
            if (viewBefore == null) {
                viewBefore = minecraft.options.getCameraType();
            }
            if (view.has("perspective")) {
                minecraft.options.setCameraType(switch (view.get("perspective").getAsInt()) {
                    case 0 -> net.minecraft.client.CameraType.FIRST_PERSON;
                    case 1 -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                    default -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                });
            }
            if (view.has("yaw")) {
                float yaw = girl.getYRot() + view.get("yaw").getAsFloat();
                local.setYRot(yaw);
                local.yRotO = yaw;
                local.setYHeadRot(yaw);
            }
            if (view.has("pitch")) {
                float pitch = view.get("pitch").getAsFloat();
                local.setXRot(pitch);
                local.xRotO = pitch;
            }
        }
        if (entry.has("show_egg")) {
            girl.koboldEggShown = true;
        }
        if (entry.has("fade") && girl.level() != null) {
            girl.allieFadeStart = girl.level().getGameTime(); // original ev: alpha 0.99, then -0.01 per rendered frame
        }
        if (entry.has("actionbar") && partner) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable(entry.get("actionbar").getAsString()), false);
        }
        if (entry.has("chat_by_uses")) {
            // Original summon_normalMSG3: "2 wishes left!" when the lamp's counter is 2, else "one last wish".
            JsonObject byUses = entry.getAsJsonObject("chat_by_uses");
            String uses = Integer.toString(girl.allieUses());
            String key = (byUses.has(uses) ? byUses.get(uses) : byUses.get("default")).getAsString();
            if (local != null && local.distanceToSqr(girl) < 40.0D * 40.0D) {
                local.sendSystemMessage(Component.literal("<")
                        .append(girl.getDisplayName())
                        .append("> ")
                        .append(Component.translatable(key)));
            }
        }
        if (entry.has("chat") || entry.has("text")) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.distanceToSqr(girl) < 40.0D * 40.0D) {
                minecraft.player.sendSystemMessage(Component.literal("<")
                        .append(girl.getDisplayName())
                        .append("> ")
                        .append(entry.has("chat")
                                ? Component.translatable(entry.get("chat").getAsString())
                                : Component.literal(entry.get("text").getAsString())));
            }
        }
        if (entry.has("player_chat")) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.distanceToSqr(girl) < 40.0D * 40.0D) {
                minecraft.player.sendSystemMessage(Component.literal("<")
                        .append(minecraft.player.getDisplayName())
                        .append("> ")
                        .append(Component.translatable(entry.get("player_chat").getAsString())));
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
        if (entry.has("vanilla_sound")) {
            // original attackSound: a vanilla sound event (player sweep attack) at her position
            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                    .getOptional(net.minecraft.resources.ResourceLocation.parse(entry.get("vanilla_sound").getAsString()))
                    .ifPresent(sound -> girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), sound,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F, false));
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

    /** The camera type before the first scripted view change; restored when the scene ends. */
    public static net.minecraft.client.CameraType viewBefore;
    private static final java.util.Map<String, Integer> LAST_PICK = new java.util.concurrent.ConcurrentHashMap<>();
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
        // original c.a(SoundEvent[]): reroll up to 10 times to avoid repeating the previous pick of this array
        int pick = girl.getRandom().nextInt(sounds.size());
        String listKey = sounds.toString();
        Integer last = LAST_PICK.get(listKey);
        for (int tries = 0; sounds.size() > 1 && last != null && pick == last && tries < 10; tries++) {
            pick = girl.getRandom().nextInt(sounds.size());
        }
        LAST_PICK.put(listKey, pick);
        String path = sounds.get(pick).getAsString();
        float volume = layer.has("volume") ? layer.get("volume").getAsFloat() : 1.0F;
        float pitch = layer.has("pitch") ? layer.get("pitch").getAsFloat() : 1.0F;
        if (girl.kind() == com.trolmastercard.sexmod.entity.CompanionKind.KOBOLD && path.startsWith("girls.kobold.")
                && !path.contains("cum")) {
            pitch *= 0.9F + 0.2F * (0.25F - girl.koboldSize()) / 0.25F; // original ff.b(SoundEvent, float)
        }
        if (path.contains(":")) {
            // A vanilla sound (the slime girl's squishes): "minecraft:entity.slime.squish".
            SoundEvent event = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(path));
            girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), event,
                    SoundSource.NEUTRAL, volume, pitch, false);
        } else if (ModSounds.find(path).isPresent()) {
            SoundEvent event = SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, path));
            girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), event,
                    SoundSource.NEUTRAL, volume, pitch, false);
        } else if (REPORTED.add(path)) {
            FapCraft.LOGGER.warn("keyframe_events names an unknown sound: {}", path);
        }
    }
}
