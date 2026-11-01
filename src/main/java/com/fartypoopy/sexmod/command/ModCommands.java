package com.fartypoopy.sexmod.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionScene;
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
        // original aw: reload the server's custom models and offer them to every player
        event.getDispatcher().register(Commands.literal("reloadcustommodels").requires(source -> source.hasPermission(2))
                .executes(context -> {
                    com.fartypoopy.sexmod.custommodel.CustomModels.reload(false);
                    var versions = new com.fartypoopy.sexmod.network.CustomModelNetwork.Versions(
                            com.fartypoopy.sexmod.custommodel.CustomModels.versions());
                    for (var player : context.getSource().getServer().getPlayerList().getPlayers()) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, versions);
                    }
                    return 1;
                }));
        // the houses and lairs are worldgen structures: /locate structure sexmod:jenny (ellie, bia, luna, goblin_lair)
        event.getDispatcher().register(Commands.literal("locatenearestgoblinlair").requires(source -> source.hasPermission(4)).executes(ModCommands::locateLair));
        // not in the original: a test and debugging aid, so operators only
        event.getDispatcher().register(Commands.literal("fapcraft").requires(source -> source.hasPermission(2))
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
                // not in the original: places a structure template at the caller's feet (testing aid)
                .then(Commands.literal("placestructure")
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.List.of("jenny", "ellie", "bia", "luna", "goblin"), builder))
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "structure");
                                    var at = net.minecraft.core.BlockPos.containing(context.getSource().getPosition());
                                    if (!com.fartypoopy.sexmod.worldgen.OriginalStructures.place(context.getSource().getLevel(),
                                            name, at, net.minecraft.world.level.block.Rotation.NONE, null)) {
                                        context.getSource().sendFailure(Component.literal("No structure sexmod:" + name));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Placed sexmod:" + name + " at "
                                            + at.getX() + " " + at.getY() + " " + at.getZ()), true);
                                    return 1;
                                })))
                .then(Commands.literal("stopscene")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ModCommands::stopScene))));
    }
    /** Original CommonProxy.setUpCustomModelsOnServer: a dedicated server reads its custom models once at start. */
    @SubscribeEvent
    public static void serverStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (event.getServer().isDedicatedServer()) {
            com.fartypoopy.sexmod.custommodel.CustomModels.load(false);
        }
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
