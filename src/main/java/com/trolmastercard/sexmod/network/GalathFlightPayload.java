package com.trolmastercard.sexmod.network;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server while riding Galath: a wing-flap impulse (action 0, original packet ct) or a boost (action 1). */
public record GalathFlightPayload(int entityId, int action, float x, float y, float z) implements CustomPacketPayload {
    public static final Type<GalathFlightPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "galath_flight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GalathFlightPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GalathFlightPayload::entityId,
            ByteBufCodecs.VAR_INT, GalathFlightPayload::action,
            ByteBufCodecs.FLOAT, GalathFlightPayload::x,
            ByteBufCodecs.FLOAT, GalathFlightPayload::y,
            ByteBufCodecs.FLOAT, GalathFlightPayload::z,
            GalathFlightPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, GalathFlightPayload::handle);
    }

    private static void handle(GalathFlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.entityId());
            if (entity instanceof CompanionEntity companion && companion.galathRider() == player) {
                if (payload.action() == 0) {
                    companion.applyFlapImpulse(player, new Vec3(payload.x(), payload.y(), payload.z()));
                } else if (payload.action() == 1) {
                    companion.startBoost(player);
                }
            }
        });
    }
}
