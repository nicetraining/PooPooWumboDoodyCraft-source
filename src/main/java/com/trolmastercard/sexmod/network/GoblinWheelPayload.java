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

/** Client to server: the choice made on the shoulder goblin's wheel (0 take back, 1 paizuri, 2 nelson). */
public record GoblinWheelPayload(int entityId, int op) implements CustomPacketPayload {
    public static final Type<GoblinWheelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "goblin_wheel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GoblinWheelPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GoblinWheelPayload::entityId,
            ByteBufCodecs.VAR_INT, GoblinWheelPayload::op,
            GoblinWheelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, GoblinWheelPayload::handle);
    }

    private static void handle(GoblinWheelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getEntity(payload.entityId()) instanceof CompanionEntity goblin
                    && goblin.goblin() != null
                    && goblin.goblinCarrier().filter(player.getUUID()::equals).isPresent()) {
                goblin.goblin().choose(player, payload.op());
            }
        });
    }
}
