package com.trolmastercard.sexmod.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionScene;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class ModCommands {
    private ModCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("locatenearestgoblinlair").executes(ModCommands::locateLair));
        event.getDispatcher().register(Commands.literal("fapcraft")
                .then(Commands.literal("home")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ModCommands::setHome)))
                .then(Commands.literal("scene")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("scene", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(CompanionScene.values())
                                                        .map(CompanionScene::serializedName),
                                                builder
                                        ))
                                        .executes(ModCommands::startScene))))
                .then(Commands.literal("stopscene")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ModCommands::stopScene))));
    }
    /** Original gn: nearest loaded queen goblin; "goblin lairs don't exist" in the Nether and End. */
    private static int locateLair(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var level = source.getLevel();
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            boolean nether = level.dimension() == net.minecraft.world.level.Level.NETHER;
            source.sendSuccess(() -> Component.literal("goblin lairs don't exist in the ").withStyle(net.minecraft.ChatFormatting.YELLOW)
                    .append(Component.literal(nether ? "Nether" : "End")
                            .withStyle(nether ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.DARK_PURPLE)), false);
            return 0;
        }
        CompanionEntity nearest = null;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof CompanionEntity companion && companion.goblinQueen()
                    && (nearest == null || companion.distanceToSqr(source.getPosition()) < nearest.distanceToSqr(source.getPosition()))) {
                nearest = companion;
            }
        }
        if (nearest == null) {
            source.sendSuccess(() -> Component.literal("No nearby goblin lair found uwu").withStyle(net.minecraft.ChatFormatting.RED), false);
            return 0;
        }
        var pos = nearest.blockPosition();
        source.sendSuccess(() -> Component.literal("goblin lair found at ").withStyle(net.minecraft.ChatFormatting.YELLOW)
                .append(Component.literal(pos.getX() + " ").withStyle(net.minecraft.ChatFormatting.RED))
                .append(Component.literal(pos.getY() + " ").withStyle(net.minecraft.ChatFormatting.GREEN))
                .append(Component.literal(String.valueOf(pos.getZ())).withStyle(net.minecraft.ChatFormatting.BLUE)), false);
        return 1;
    }

    private static int setHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CompanionEntity companion = target(context);
        if (companion == null) {
            return 0;
        }

        companion.setHome(companion.blockPosition());
        context.getSource().sendSuccess(
                () -> Component.translatable("command.sexmod.home_set", companion.getDisplayName()),
                false
        );
        return 1;
    }

    private static int startScene(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CompanionEntity companion = target(context);
        if (companion == null) {
            return 0;
        }

        String value = StringArgumentType.getString(context, "scene");
        CompanionScene scene = CompanionScene.byName(value);
        if (scene == null || scene.kind() != companion.kind()) {
            context.getSource().sendFailure(Component.translatable("command.sexmod.invalid_scene", value));
            return 0;
        }

        if (!companion.startScene(context.getSource().getPlayerOrException(), scene)) {
            context.getSource().sendFailure(Component.translatable("message.sexmod.scene_unavailable"));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.sexmod.scene_started",
                        companion.getDisplayName(),
                        scene.path()
                ),
                false
        );
        return 1;
    }

    private static int stopScene(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CompanionEntity companion = target(context);
        if (companion == null) {
            return 0;
        }

        companion.stopScene();
        context.getSource().sendSuccess(
                () -> Component.translatable("command.sexmod.scene_stopped", companion.getDisplayName()),
                false
        );
        return 1;
    }
    private static CompanionEntity target(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "target");
        if (!(entity instanceof CompanionEntity companion)) {
            context.getSource().sendFailure(Component.translatable("command.sexmod.not_companion"));
            return null;
        }

        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.translatable(
                    "message.sexmod.editor_denied",
                    companion.getDisplayName()
            ));
            return null;
        }

        return companion;
    }
}
