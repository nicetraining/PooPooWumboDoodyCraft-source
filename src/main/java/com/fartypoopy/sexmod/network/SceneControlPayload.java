package com.fartypoopy.sexmod.network;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the original's in-scene keys. Holding sneak = fast (0 slow / 1 fast is sent on change),
 * jump with a full meter = finish (2).
 */
public record SceneControlPayload(int entityId, int action) implements CustomPacketPayload {
    public static final int SLOW = 0;
    public static final int FAST = 1;
    public static final int FINISH = 2;

    public static final Type<SceneControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "scene_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SceneControlPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SceneControlPayload::entityId,
            ByteBufCodecs.VAR_INT, SceneControlPayload::action,
            SceneControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, SceneControlPayload::handle);
    }

    private static void handle(SceneControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.entityId());
            if (entity instanceof CompanionEntity companion && companion.canControlScene(player)) {
                switch (payload.action()) {
                    case FAST -> companion.setSceneFastHeld(true);
                    case SLOW -> companion.setSceneFastHeld(false);
                    case FINISH -> companion.finishSceneFromMeter(player);
                    default -> { }
                }
            }
        });
    }
}
