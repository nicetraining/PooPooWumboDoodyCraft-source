package com.fartypoopy.sexmod.network;

import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: play the screen wipe (the original's transition screen) and lock movement while it runs. */
public record TransitionPayload() implements CustomPacketPayload {
    public static final Type<TransitionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "transition"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransitionPayload> CODEC =
            StreamCodec.unit(new TransitionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, TransitionPayload::handle);
    }

    private static void handle(TransitionPayload payload, IPayloadContext context) {
        context.enqueueWork(com.fartypoopy.sexmod.client.TransitionOverlay::start);
    }
}
