package com.fartypoopy.sexmod.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.custommodel.CustomModelConfig;
import com.fartypoopy.sexmod.custommodel.CustomModels;
import com.fartypoopy.sexmod.network.CustomModelNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client half of the custom models (original br client parts, cu$a, g6$a): uploads each model's png as a dynamic texture
 * and bakes its geo, reports load progress in chat, downloads a whitelisted server's models into
 * {@code sexmod/custom_models/<server>/}.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class CustomModelClient implements CustomModels.ClientSide {
    public static final CustomModelClient INSTANCE = new CustomModelClient();
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, BakedGeoModel> MODELS = new ConcurrentHashMap<>();
    private static int textureCounter;
    /** Original cu$a.a: files of the running download received so far. */
    private static int received;

    private CustomModelClient() {
    }

    public static void install() {
        CustomModels.client = INSTANCE;
    }

    public static ResourceLocation texture(String name) {
        return TEXTURES.get(name);
    }

    public static BakedGeoModel model(String name) {
        return MODELS.get(name);
    }

    /** Original br.g(): the address of the current server without its port, null in singleplayer. */
    public static String serverHost() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null || Minecraft.getInstance().isLocalServer()) {
            return null;
        }
        String host = server.ip;
        int colon = host.indexOf(':');
        return colon == -1 ? host : host.substring(0, colon);
    }

    /** Original br.b(): a multiplayer server the player whitelisted. */
    public static boolean serverWhitelisted() {
        String host = serverHost();
        return host != null && CustomModels.whitelisted(host);
    }

    @Override
    public String folder() {
        String host = serverHost();
        return CustomModels.CLIENT_ROOT + "/" + (host == null ? "singleplayer" : host.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    @Override
    public String upload(String name, File folder) {
        if (!RenderSystem.isOnRenderThread()) {
            return String.format("Couldn't load the custom model '%s' outside the render thread", name);
        }
        File png = new File(folder, name + ".png");
        try (InputStream in = new FileInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "custom_model/" + (textureCounter++));
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
            ResourceLocation old = TEXTURES.put(name, location);
            if (old != null) {
                Minecraft.getInstance().getTextureManager().release(old);
            }
        } catch (IOException exception) {
            return String.format("The texture for the custom model '%s' at '%s' appears to be corrupted. Try making a new one", name, png.getPath());
        } catch (Exception exception) {
            return String.format("Couldn't load the texture for the custom model '%s' at '%s'. Maybe try increasing the amount of RAM of ur Minecraft client", name, png);
        }
        File geo = new File(folder, name + ".geo.json");
        try {
            JsonObject json = GsonHelper.parse(Files.readString(geo.toPath(), StandardCharsets.UTF_8));
            Model model = KeyFramesAdapter.GEO_GSON.fromJson(json, Model.class);
            MODELS.put(name, BakedModelFactory.getForNamespace(FapCraft.MOD_ID).constructGeoModel(GeometryTree.fromModel(model)));
        } catch (Exception exception) {
            return String.format("The geo model for the custom model '%s' at '%s' appears to be corrupted. Try replacing it.", name, geo.getPath());
        }
        return null;
    }

    @Override
    public void unloadAll() {
        Runnable release = () -> {
            TEXTURES.values().forEach(Minecraft.getInstance().getTextureManager()::release);
            TEXTURES.clear();
            MODELS.clear();
        };
        if (RenderSystem.isOnRenderThread()) {
            release.run();
        } else {
            Minecraft.getInstance().execute(release);
        }
    }

    /** Original br.a(Level, String): chat in DEBUG green, ERROR red, else white; the log when no player exists. */
    @Override
    public void report(CustomModels.Level level, String message) {
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                FapCraft.LOGGER.info(message);
                return;
            }
            ChatFormatting colour = level == CustomModels.Level.DEBUG ? ChatFormatting.DARK_GREEN
                    : level == CustomModels.Level.ERROR ? ChatFormatting.RED : ChatFormatting.WHITE;
            player.displayClientMessage(Component.literal(message).withStyle(colour), false);
        });
    }

    /** Original br.a(): ask the server which models it has. */
    public static void requestServerModels() {
        PacketDistributor.sendToServer(new CustomModelNetwork.Versions(Map.of()));
    }

    /** Original g6$a (client side): request every model that is missing or older than the server's. */
    public static void onServerVersions(Map<String, Float> versions) {
        if (!serverWhitelisted()) {
            return;
        }
        List<String> wanted = new ArrayList<>();
        versions.forEach((name, version) -> {
            if (!CustomModelConfig.validName(name)) {
                return;
            }
            CustomModelConfig local = CustomModels.get(name);
            if (local == null || version > local.version) {
                wanted.add(name);
            }
        });
        if (!wanted.isEmpty()) {
            PacketDistributor.sendToServer(new CustomModelNetwork.RequestModels(wanted));
        }
    }

    /** Original cu$a (client side): store one received file; once all arrived, reload every model. */
    public static void onModelFile(CustomModelNetwork.ModelFile payload) {
        if (!serverWhitelisted() || !CustomModelConfig.validName(payload.name())
                || payload.fileType() < 0 || payload.fileType() >= CustomModelNetwork.FileType.values().length) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String name = payload.name();
        File folder = new File(INSTANCE.folder() + "/" + name);
        folder.mkdirs();
        CustomModelNetwork.FileType type = CustomModelNetwork.FileType.values()[payload.fileType()];
        try {
            Files.write(new File(folder, name + type.ending).toPath(), payload.data());
        } catch (IOException exception) {
            FapCraft.LOGGER.warn("Could not write a downloaded custom model file", exception);
        }
        boolean complete = true;
        for (CustomModelNetwork.FileType each : CustomModelNetwork.FileType.values()) {
            complete &= new File(folder, name + each.ending).exists();
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(complete
                    ? String.format("%sSuccessfully downloaded the custom model '%s%s%s'!", ChatFormatting.GREEN, ChatFormatting.YELLOW, name, ChatFormatting.GREEN)
                    : String.format("%sdownloading custom model '%s%s%s' (%s/%s)...", ChatFormatting.GRAY, ChatFormatting.YELLOW, name, ChatFormatting.GRAY, received + 1, payload.total())), false);
        }
        if (++received >= payload.total()) {
            received = 0;
            CustomModels.loadFailed = CustomModels.reload(true) != 0;
        }
    }

    /** Original br$a: load this server's models on connecting and ask a whitelisted server for its own. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        received = 0;
        CustomModels.loadFailed = CustomModels.reload(true) != 0;
        if (serverWhitelisted()) {
            requestServerModels();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CustomModels.clear(true);
    }
}
