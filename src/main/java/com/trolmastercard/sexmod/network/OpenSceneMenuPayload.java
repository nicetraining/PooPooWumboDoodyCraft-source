package com.trolmastercard.sexmod.network;

import com.trolmastercard.sexmod.FapCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server to client: open the action panel with an explicit list of scenes (Bia's second menu after "Talk"). */
public record OpenSceneMenuPayload(int entityId, List<String> scenes) implements CustomPacketPayload {
    public static final Type<OpenSceneMenuPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "open_scene_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSceneMenuPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenSceneMenuPayload::entityId,
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(8)), OpenSceneMenuPayload::scenes,
            OpenSceneMenuPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, OpenSceneMenuPayload::handle);
    }

    private static void handle(OpenSceneMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.trolmastercard.sexmod.client.ClientHooks.openSceneMenu(
                payload.entityId(), payload.scenes()));
    }
}
