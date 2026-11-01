package com.fartypoopy.sexmod.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Original a_: the client command {@code /futa <true|false>} decides whether Galath's optional bones are drawn. The value is
 * kept in the file {@code sexmod/futa} of the game directory (default shown) and every loaded Galath answers with a puff.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class FutaSetting {
    private static boolean shown = true;
    private static boolean loaded;
    private static final Set<Integer> PUFFS = ConcurrentHashMap.newKeySet();

    private FutaSetting() {
    }

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("sexmod").resolve("futa");
    }

    public static boolean shown() {
        if (!loaded) {
            loaded = true;
            try {
                String line = Files.readAllLines(file()).stream().findFirst().orElse("").toLowerCase(Locale.ROOT);
                if (line.equals("true")) {
                    shown = true;
                } else if (line.equals("false")) {
                    shown = false;
                }
            } catch (IOException ignored) {
                // no file yet: the default stays
            }
        }
        return shown;
    }

    /** True once for every Galath the command just touched. */
    static boolean takePuff(CompanionEntity entity) {
        return !PUFFS.isEmpty() && PUFFS.remove(entity.getId());
    }

    private static void usage() {
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(String.format("%sYou can either do %s/futa true %sor %s/futa false",
                net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.YELLOW,
                net.minecraft.ChatFormatting.GRAY)));
    }

    private static int set(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.equals("true") && !lower.equals("false")) {
            usage();
            return 0;
        }
        loaded = true;
        shown = lower.equals("true");
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), lower);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (Minecraft.getInstance().level != null) {
            for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                if (entity instanceof CompanionEntity companion && companion.kind() == CompanionKind.GALATH && companion.isAlive()) {
                    PUFFS.add(companion.getId());
                }
            }
        }
        return 1;
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("futa")
                .executes(context -> {
                    usage();
                    return 0;
                })
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(context -> set(StringArgumentType.getString(context, "value")))));
    }
}
