package com.fartypoopy.sexmod.network;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.custommodel.CustomModelConfig;
import com.fartypoopy.sexmod.custommodel.CustomModels;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The custom-model packets: fw (apply a model code), g6 (which models the server has, with their versions) and cu
 * (request missing models / one model file). Names are checked against {@link CustomModelConfig#validName} on both
 * sides so neither side can read or write outside its models folder.
 */
public final class CustomModelNetwork {
    /** One model file at most this big is sent (the original sent any size). */
    public static final int MAX_FILE_BYTES = 1_000_000;
    private static final int MAX_NAMES = 256;

    /** Original cu$b. */
    public enum FileType {
        CFG(".cfg"), PNG(".png"), GEO(".geo.json");

        public final String ending;

        FileType(String ending) {
            this.ending = ending;
        }
    }

    private CustomModelNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SetModelCode.TYPE, SetModelCode.CODEC, CustomModelNetwork::handleSetModelCode);
        registrar.playBidirectional(Versions.TYPE, Versions.CODEC, CustomModelNetwork::handleVersions);
        registrar.playToServer(RequestModels.TYPE, RequestModels.CODEC, CustomModelNetwork::handleRequest);
        registrar.playToClient(ModelFile.TYPE, ModelFile.CODEC, CustomModelNetwork::handleFile);
    }

    /** Original fw (client to server): set a girl's model code and, for Kobolds and Goblins, her per-girl values. */
    public record SetModelCode(String code, UUID girl, List<Integer> values) implements CustomPacketPayload {
        public static final Type<SetModelCode> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "set_model_code"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetModelCode> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(4096), SetModelCode::code,
                UUIDUtil.STREAM_CODEC, SetModelCode::girl,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), SetModelCode::values,
                SetModelCode::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Original g6: empty from a client = "what do you have?"; from the server = every model with its version. */
    public record Versions(Map<String, Float> versions) implements CustomPacketPayload {
        public static final Type<Versions> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "model_versions"));
        public static final StreamCodec<ByteBuf, Versions> CODEC = ByteBufCodecs.<ByteBuf, String, Float, Map<String, Float>>map(
                HashMap::new, ByteBufCodecs.stringUtf8(64), ByteBufCodecs.FLOAT, MAX_NAMES).map(Versions::new, Versions::versions);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Original cu (client to server): the models the client is missing or has in an older version. */
    public record RequestModels(List<String> names) implements CustomPacketPayload {
        public static final Type<RequestModels> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "request_models"));
        public static final StreamCodec<ByteBuf, RequestModels> CODEC = ByteBufCodecs.stringUtf8(64)
                .apply(ByteBufCodecs.list(MAX_NAMES)).map(RequestModels::new, RequestModels::names);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Original cu (server to client): one file of one model; {@code total} is the number of files of the whole download. */
    public record ModelFile(String name, int fileType, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<ModelFile> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "model_file"));
        public static final StreamCodec<ByteBuf, ModelFile> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), ModelFile::name,
                ByteBufCodecs.VAR_INT, ModelFile::fileType,
                ByteBufCodecs.VAR_INT, ModelFile::total,
                ByteBufCodecs.byteArray(MAX_FILE_BYTES), ModelFile::data,
                ModelFile::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static CompanionEntity findGirl(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof CompanionEntity girl) {
                return girl;
            }
        }
        return null;
    }

    /** Original fw$a: like the original, any player may restyle any loaded girl. */
    private static void handleSetModelCode(SetModelCode payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            CompanionEntity girl = findGirl(player.server, payload.girl());
            if (girl == null) {
                return;
            }
            // original fw$a: valid girl-specific values go on the target first; an NPC then takes the code, while for a
            // player form the code (and valid values) are stored for the sender's own current form, never another's
            if (!payload.values().isEmpty() && girl.girlSpecificValid(payload.values())) {
                girl.applyGirlSpecific(payload.values());
            }
            if (!girl.isPlayerForm()) {
                girl.setModelCode(payload.code());
                return;
            }
            CompanionEntity own = com.fartypoopy.sexmod.entity.PlayerForms.formOf(player);
            if (own != null) {
                com.fartypoopy.sexmod.entity.PlayerForms.storeLook(own, payload.code(), payload.values());
            }
        });
    }

    private static void handleVersions(Versions payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // original g6$a, server side: answer with every model and its version
                PacketDistributor.sendToPlayer(player, new Versions(CustomModels.versions()));
            } else {
                com.fartypoopy.sexmod.client.CustomModelClient.onServerVersions(payload.versions());
            }
        });
    }

    /** Original cu$a (server side): read every file of every requested model and send them one by one. */
    private static void handleRequest(RequestModels payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            List<ModelFile> files = new ArrayList<>();
            for (String name : payload.names()) {
                if (!CustomModelConfig.validName(name) || !CustomModels.has(name)) {
                    continue;
                }
                for (FileType type : FileType.values()) {
                    File file = new File(CustomModels.SERVER_ROOT + "/" + name + "/" + name + type.ending);
                    if (!file.exists()) {
                        FapCraft.LOGGER.warn("{} doesnt exist lol", file.getAbsolutePath());
                        continue;
                    }
                    try {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        if (bytes.length > MAX_FILE_BYTES) {
                            FapCraft.LOGGER.warn("{} is larger than {} bytes and is not sent", file, MAX_FILE_BYTES);
                            continue;
                        }
                        files.add(new ModelFile(name, type.ordinal(), 0, bytes));
                    } catch (IOException exception) {
                        FapCraft.LOGGER.warn("Could not read {}", file, exception);
                    }
                }
            }
            for (ModelFile file : files) {
                PacketDistributor.sendToPlayer(player, new ModelFile(file.name(), file.fileType(), files.size(), file.data()));
            }
        });
    }

    private static void handleFile(ModelFile payload, IPayloadContext context) {
        context.enqueueWork(() -> com.fartypoopy.sexmod.client.CustomModelClient.onModelFile(payload));
    }
}
