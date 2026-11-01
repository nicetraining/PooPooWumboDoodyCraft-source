package com.fartypoopy.sexmod.network;

import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Original b3 reply: the tribe follow flag and one (x, y, z, wool colour) entry per member; empty = no tribe. */
public record TribeUiPayload(boolean follow, List<int[]> members) implements CustomPacketPayload {
    public static final Type<TribeUiPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "tribe_ui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TribeUiPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.follow());
                buf.writeVarInt(payload.members().size());
                for (int[] member : payload.members()) {
                    for (int i = 0; i < 4; i++) {
                        buf.writeInt(member[i]);
                    }
                }
            },
            buf -> {
                boolean follow = buf.readBoolean();
                int count = buf.readVarInt();
                List<int[]> members = new java.util.ArrayList<>();
                for (int i = 0; i < count; i++) {
                    members.add(new int[]{buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()});
                }
                return new TribeUiPayload(follow, members);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, TribeUiPayload::handle);
    }

    private static void handle(TribeUiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.fartypoopy.sexmod.client.TribeClient.setUi(payload.follow(), payload.members()));
    }
}
