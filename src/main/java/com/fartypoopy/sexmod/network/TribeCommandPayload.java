package com.fartypoopy.sexmod.network;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.tribe.TribeCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the original's g9 (claim), h6 (register bed/chest), fj (follow mode), au (cancel task), fc (fell
 * tree), e6 (mine tunnel) and b3 (request tribe info) in one payload.
 */
public record TribeCommandPayload(int op, BlockPos pos, String a, String b) implements CustomPacketPayload {
    public static final int CLAIM = 0;
    public static final int REGISTER = 1;
    public static final int FOLLOW = 2;
    public static final int CANCEL = 3;
    public static final int FELL = 4;
    public static final int MINE = 5;
    public static final int REQUEST = 6;

    public static final Type<TribeCommandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "tribe_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TribeCommandPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TribeCommandPayload::op,
            BlockPos.STREAM_CODEC, TribeCommandPayload::pos,
            ByteBufCodecs.STRING_UTF8, TribeCommandPayload::a,
            ByteBufCodecs.STRING_UTF8, TribeCommandPayload::b,
            TribeCommandPayload::new);

    public static TribeCommandPayload of(int op, BlockPos pos) {
        return new TribeCommandPayload(op, pos, "", "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, TribeCommandPayload::handle);
    }

    private static void handle(TribeCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TribeCommands.handle(player, payload);
            }
        });
    }
}
