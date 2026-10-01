package com.trolmastercard.sexmod.network;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Wild Galath catch. Server to client: show the escape minigame. Client to server (original packet cd): the bar is full,
 * the player breaks free.
 */
public record GalathEscapePayload(int entityId) implements CustomPacketPayload {
    public static final Type<GalathEscapePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "galath_escape"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GalathEscapePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GalathEscapePayload::entityId, GalathEscapePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(TYPE, CODEC, GalathEscapePayload::handleClient);
        registrar.playToServer(ESCAPE_TYPE, CODEC_UP, GalathEscapePayload::handleServer);
    }

    /** The upstream message is a separate type so both directions can be registered. */
    public record Up(int entityId) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ESCAPE_TYPE;
        }
    }

    public static final Type<Up> ESCAPE_TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "galath_escape_done"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Up> CODEC_UP = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Up::entityId, Up::new);

    private static void handleClient(GalathEscapePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.trolmastercard.sexmod.client.EscapeHud.start(payload.entityId()));
    }

    private static void handleServer(Up payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getEntity(payload.entityId()) instanceof CompanionEntity galath) {
                galath.galathEscape(player);
            }
        });
    }
}
