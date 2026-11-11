package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.ClientConfig;
import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.VersionChecker;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Tells the player in chat when a newer version of the mod is out. NeoForge does the checking: at startup it reads the
 * {@code updateJSONURL} from neoforge.mods.toml (the website's update.json) and compares its newest version with the
 * installed one; the Mods menu marks the mod by itself. This only adds a chat line, once per game, on joining a world.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class UpdateNotice {
    /** Where the download is; the update file's homepage when it names one. */
    private static final String SITE = "https://nicetraining.github.io/";

    private static boolean told;

    private UpdateNotice() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (told || !ClientConfig.updateNotice()) {
            return;
        }
        Component message = message();
        if (message != null) {
            told = true;
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(message, false);
                }
            });
        }
    }

    /** The chat line, or null while the check is running, failed, or found nothing newer. */
    static Component message() {
        var container = ModList.get().getModContainerById(FapCraft.MOD_ID).orElse(null);
        if (container == null) {
            return null;
        }
        VersionChecker.CheckResult result = VersionChecker.getResult(container.getModInfo());
        if (result.status() != VersionChecker.Status.OUTDATED && result.status() != VersionChecker.Status.BETA_OUTDATED
                || result.target() == null) {
            return null; // still checking (the next world join asks again), no connection, or up to date
        }
        String url = result.url() != null && result.url().startsWith("https://") ? result.url() : SITE;
        Component link = Component.translatable("chat.sexmod.update.download").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url))));
        return Component.translatable("chat.sexmod.update", result.target().toString(),
                container.getModInfo().getVersion().toString(), link).withStyle(ChatFormatting.YELLOW);
    }
}
