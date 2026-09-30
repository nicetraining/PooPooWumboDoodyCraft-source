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
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player picked an entry in the companion action panel. */
public record CompanionActionPayload(int entityId, String action) implements CustomPacketPayload {
    public static final Type<CompanionActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "companion_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CompanionActionPayload::entityId,
            ByteBufCodecs.stringUtf8(64), CompanionActionPayload::action,
            CompanionActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, CompanionActionPayload::handle);
    }

    private static void handle(CompanionActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.entityId());
            if (entity instanceof CompanionEntity companion
                    && companion.mayBeCommandedBy(player)
                    && player.distanceToSqr(companion) <= 64.0D) {
                companion.performAction(player, payload.action());
            }
        });
    }
}
