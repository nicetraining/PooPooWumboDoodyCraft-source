package com.fartypoopy.sexmod.network;

import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Original h6 (server to client): start or stop drawing these blocks as marked (bed blue, chest green, task red). */
public record TribeMarkersPayload(boolean add, List<BlockPos> blocks) implements CustomPacketPayload {
    public static final Type<TribeMarkersPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "tribe_markers"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TribeMarkersPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TribeMarkersPayload::add,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), TribeMarkersPayload::blocks,
            TribeMarkersPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, TribeMarkersPayload::handle);
    }

    private static void handle(TribeMarkersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.fartypoopy.sexmod.client.TribeClient.markers(payload.add(), payload.blocks()));
    }
}
