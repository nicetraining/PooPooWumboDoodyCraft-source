package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.ClientConfig;
import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** A small link to the mod's Discord in the title screen's top-left corner (hidden with showDiscordLink = false). */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class DiscordLink {
    public static final String URL = "https://discord.gg/za6UejgY4y";

    private DiscordLink() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen) || !ClientConfig.showDiscordLink()) {
            return;
        }
        var font = screen.getMinecraft().font;
        Component text = Component.translatable("menu.sexmod.discord");
        // like the copyright line: plain text, underlined on hover; Minecraft asks before it opens the browser
        event.addListener(new PlainTextButton(4, 4, font.width(text), 10, text,
                button -> ConfirmLinkScreen.confirmLinkNow(screen, URL, true), font));
    }
}
