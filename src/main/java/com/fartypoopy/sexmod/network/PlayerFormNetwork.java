package com.fartypoopy.sexmod.network;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.PlayerForms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/** Player-as-girl packets: bd (open the picker), b_ (the pick) and the strip / dress-up button. */
public final class PlayerFormNetwork {
    private PlayerFormNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(OpenPicker.TYPE, OpenPicker.CODEC, (payload, context) -> context.enqueueWork(
                () -> com.fartypoopy.sexmod.client.FormPickerScreen.open(payload.specifics())));
        registrar.playToServer(Choose.TYPE, Choose.CODEC, CustomPacketHandlers::choose);
        registrar.playToServer(Strip.TYPE, Strip.CODEC, CustomPacketHandlers::strip);
        registrar.playToServer(Request.TYPE, Request.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerForms.request(player, payload.girl(), payload.male(), payload.action(), payload.toGirl());
            }
        }));
        registrar.playToClient(Prompt.TYPE, Prompt.CODEC, (payload, context) -> context.enqueueWork(
                () -> com.fartypoopy.sexmod.client.FormPrompt.show(payload)));
        registrar.playToServer(Accept.TYPE, Accept.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerForms.accept(player, payload.girl(), payload.male(), payload.action());
            }
        }));
        registrar.playToServer(Leave.TYPE, Leave.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerForms.leave(player);
            }
        }));
        registrar.playToServer(FollowUp.TYPE, FollowUp.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerForms.chooseFollowUp(player, payload.action());
            }
        }));
    }

    /** Original bd: the per-girl values the player keeps for Kobold and Goblin. */
    public record OpenPicker(Map<String, String> specifics) implements CustomPacketPayload {
        public static final Type<OpenPicker> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "open_form_picker"));
        public static final StreamCodec<ByteBuf, OpenPicker> CODEC = ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(
                HashMap::new, ByteBufCodecs.stringUtf8(32), ByteBufCodecs.stringUtf8(256), 16).map(OpenPicker::new, OpenPicker::specifics);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Original b_: the picked kind, or "" to be the player again. */
    public record Choose(String kind) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "choose_form"));
        public static final StreamCodec<ByteBuf, Choose> CODEC = ByteBufCodecs.stringUtf8(32).map(Choose::new, Choose::kind);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final StreamCodec<ByteBuf, java.util.UUID> UUID_CODEC = net.minecraft.core.UUIDUtil.STREAM_CODEC.cast();

    /** Original g4 (client to server): ask the other player for an action of the girl's form. */
    public record Request(String action, java.util.UUID male, java.util.UUID girl, boolean toGirl) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "form_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Request::action, UUID_CODEC, Request::male, UUID_CODEC, Request::girl,
                ByteBufCodecs.BOOL, Request::toGirl, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Original g4 (server to client): the prompt for the asked player. */
    public record Prompt(String action, java.util.UUID male, java.util.UUID girl, boolean fromMale) implements CustomPacketPayload {
        public static final Type<Prompt> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "form_prompt"));
        public static final StreamCodec<ByteBuf, Prompt> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Prompt::action, UUID_CODEC, Prompt::male, UUID_CODEC, Prompt::girl,
                ByteBufCodecs.BOOL, Prompt::fromMale, Prompt::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Original eu: the asked player said yes. */
    public record Accept(java.util.UUID girl, java.util.UUID male, String action) implements CustomPacketPayload {
        public static final Type<Accept> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "form_accept"));
        public static final StreamCodec<ByteBuf, Accept> CODEC = StreamCodec.composite(
                UUID_CODEC, Accept::girl, UUID_CODEC, Accept::male, ByteBufCodecs.stringUtf8(64), Accept::action, Accept::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Original s (ResetGirl): the girl-player jumps out of her wait or scene. */
    public record Leave() implements CustomPacketPayload {
        public static final Type<Leave> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "form_leave"));
        public static final StreamCodec<ByteBuf, Leave> CODEC = StreamCodec.unit(new Leave());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Original ee "animationFollowUp": cowgirl or missionary after Ellie's sit-down. */
    public record FollowUp(String action) implements CustomPacketPayload {
        public static final Type<FollowUp> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "form_follow_up"));
        public static final StreamCodec<ByteBuf, FollowUp> CODEC = ByteBufCodecs.stringUtf8(64).map(FollowUp::new, FollowUp::action);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Strip() implements CustomPacketPayload {
        public static final Type<Strip> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "form_strip"));
        public static final StreamCodec<ByteBuf, Strip> CODEC = StreamCodec.unit(new Strip());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final class CustomPacketHandlers {
        static void choose(Choose payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    CompanionKind kind = null;
                    for (CompanionKind each : CompanionKind.values()) {
                        if (each.name().equals(payload.kind())) {
                            kind = each;
                        }
                    }
                    PlayerForms.become(player, kind);
                }
            });
        }

        static void strip(Strip payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    PlayerForms.strip(player);
                }
            });
        }
    }
}
