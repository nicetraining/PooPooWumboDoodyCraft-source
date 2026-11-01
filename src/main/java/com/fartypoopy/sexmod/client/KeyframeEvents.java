package com.fartypoopy.sexmod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.registry.ModSounds;
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
        if (girl.kind() == com.fartypoopy.sexmod.entity.CompanionKind.LUNA && !girl.isPlayerForm()) {
            if (eventId.equals("idleDone")) {
                girl.lunaIdle2 = girl.getRandom().nextInt(10) == 0; // original eb.ad
            } else if (eventId.equals("idle2Done")) {
                girl.lunaIdle2 = false;
            }
        }
        if (eventId.equals("attackDone")) {
            girl.attackDone(); // original e2.S++ (0 at 3) and her action back to NULL on this client
        }
        if (girl.kind() == com.fartypoopy.sexmod.entity.CompanionKind.LUNA) {
            switch (eventId) {
                case "renderItem" -> girl.lunaItemShown = true; // original eb: Z = 1
                case "eat" -> girl.lunaItemScale -= 0.33333334F; // aa -= 1/3 per bite
                case "eatPay" -> girl.paymentScale -= 0.33333334F; // n -= 1/3
                case "eatingDone", "throw_away" -> {
                    girl.lunaItemShown = false;
                    girl.lunaItemScale = 1.0F;
                }
                case "paymentDone" -> girl.paymentScale = 1.0F;
                default -> {
                }
            }
        }
        JsonObject formEntry = null;
        if (girl.isPlayerForm()) {
            // player forms have their own handlers (original es/ee/...): some events are ignored, some differ
            JsonObject forms = tableFor("forms");
            JsonObject form = forms.has(girl.kind().registryPath()) ? forms.getAsJsonObject(girl.kind().registryPath()) : new JsonObject();
            if (form.has("skip")) {
                for (JsonElement skipped : form.getAsJsonArray("skip")) {
                    if (eventId.equals(skipped.getAsString())) {
                        return;
                    }
                }
            }
            if (form.has("override") && form.getAsJsonObject("override").has(eventId)) {
                formEntry = form.getAsJsonObject("override").getAsJsonObject(eventId);
            }
        }
        FluidStreams.keyframe(girl, eventId); // original ga / ep: creampie, futa and semen streams, clearcum
        if (girl.kind() == com.fartypoopy.sexmod.entity.CompanionKind.GALATH) {
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
                case "setCamCorrupt" -> girl.galathCorruptCam = true;
                case "enableBoyCam" -> girl.galathCorruptCam = false;
                default -> {
                }
            }
        }
        JsonObject kind = tableFor(girl.kind().registryPath());
        JsonObject entry = formEntry != null ? formEntry : kind.has(eventId) ? kind.getAsJsonObject(eventId) : null;
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
                        new com.fartypoopy.sexmod.network.GalathFlightPayload(
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
        if (entry.has("raw_chat") && local != null && girl.level() != null) {
            // Original em.b(text, true): sent (without a name) when the player nearest to her within 50 blocks is
            // the scene partner; everyone within 40 blocks of her gets it in chat.
            net.minecraft.world.entity.player.Player nearest = girl.level().getNearestPlayer(girl, 50.0D);
            boolean partnerNearest = nearest != null
                    && girl.scenePartnerId().filter(nearest.getUUID()::equals).isPresent();
            if (partnerNearest && local.distanceToSqr(girl) < 40.0D * 40.0D) {
                local.sendSystemMessage(Component.translatable(entry.get("raw_chat").getAsString()));
            }
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
            // Original em.h(text) (Jenny, Ellie's hug / sit-down lines, Bia form's strip line): sent by the partner's
            // client, then to everyone within 40 blocks of her; every other line is em.a(text): shown on each client
            // that sees her.
            boolean broadcast = broadcastLine(girl, eventId, entry);
            if (minecraft.player != null && (!broadcast
                    || girl.scenePartnerId().isPresent() && minecraft.player.distanceToSqr(girl) < 40.0D * 40.0D)) {
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
            // original attackSound: em.a(ENTITY_PLAYER_ATTACK_STRONG) at her block corner (integer coordinates)
            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                    .getOptional(net.minecraft.resources.ResourceLocation.parse(entry.get("vanilla_sound").getAsString()))
                    .ifPresent(sound -> girl.level().playLocalSound(girl.blockPosition().getX(), girl.blockPosition().getY(), girl.blockPosition().getZ(), sound,
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
     * One sound layer: {"sounds":[...],"volume":1,"pitch":1,"pitch_range":[0.9,1],"pitch_spread":0.2,"chance":1} plays a random pick; or
     * {"any_of":[layer,...],"weights":[1,4]} plays exactly one of the listed layers by weight.
     */
    private static final java.util.Set<String> ELLIE_BROADCAST = java.util.Set.of("hugMSG2", "hugMSG3", "hugMSG4",
            "hugMSG5", "hugselectedMSG1", "hugselectedMSG2", "sitdownMSG1");

    /** Lines the original sends with em.h (ex, es, el, ee, eg keyframe handlers) rather than em.a. */
    private static boolean broadcastLine(CompanionEntity girl, String eventId, JsonObject entry) {
        return switch (girl.kind()) {
            case JENNY -> !(entry.has("chat") && entry.get("chat").getAsString().startsWith("jenny.dialogue.doggytext"));
            case ELLIE -> ELLIE_BROADCAST.contains(eventId);
            case BIA -> girl.isPlayerForm() && eventId.equals("stripMSG1");
            default -> false;
        };
    }

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
        if (layer.has("pitch_range")) { // {"pitch_range":[min,max]}: min + random * (max - min)
            JsonArray range = layer.getAsJsonArray("pitch_range");
            float low = range.get(0).getAsFloat();
            pitch = low + girl.getRandom().nextFloat() * (range.get(1).getAsFloat() - low);
        } else if (layer.has("pitch_spread")) { // {"pitch_spread":s}: 1 + (random - random) * s, like vanilla eating
            pitch = 1.0F + (girl.getRandom().nextFloat() - girl.getRandom().nextFloat()) * layer.get("pitch_spread").getAsFloat();
        }
        if (girl.kind() == com.fartypoopy.sexmod.entity.CompanionKind.KOBOLD && path.startsWith("girls.kobold.")
                && !path.contains("cum")) {
            pitch *= 0.9F + 0.2F * (0.25F - girl.koboldSize()) / 0.25F; // original ff.b(SoundEvent, float)
        }
        if (path.contains(":")) {
            // A vanilla sound (the slime girl's squishes): "minecraft:entity.slime.squish".
            SoundEvent event = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(path));
            girl.level().playLocalSound(girl.blockPosition().getX(), girl.blockPosition().getY(), girl.blockPosition().getZ(), event,
                    SoundSource.NEUTRAL, volume, pitch, false);
        } else if (ModSounds.find(path).isPresent()) {
            SoundEvent event = SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, path));
            girl.level().playLocalSound(girl.blockPosition().getX(), girl.blockPosition().getY(), girl.blockPosition().getZ(), event,
                    SoundSource.NEUTRAL, volume, pitch, false);
        } else if (REPORTED.add(path)) {
            FapCraft.LOGGER.warn("keyframe_events names an unknown sound: {}", path);
        }
    }
}
