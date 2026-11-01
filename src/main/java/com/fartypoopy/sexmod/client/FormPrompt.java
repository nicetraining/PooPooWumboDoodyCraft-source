package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.network.PlayerFormNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Original w: a player-girl request shows up in chat ("&lt;name&gt; asked you for a &lt;action&gt;"); typing the accept word
 * (Yes) or the decline word (No) answers it; after 1200 ticks it times out.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class FormPrompt {
    private static PlayerFormNetwork.Prompt pending;
    private static float ticksLeft;

    private FormPrompt() {
    }

    /** Original w.a(w$a). */
    public static void show(PlayerFormNetwork.Prompt prompt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Player male = minecraft.level.getPlayerByUUID(prompt.male());
        Player girl = minecraft.level.getPlayerByUUID(prompt.girl());
        if (male == null || girl == null) {
            return;
        }
        String asker = (prompt.fromMale() ? male : girl).getName().getString();
        minecraft.player.sendSystemMessage(Component.literal(ChatFormatting.LIGHT_PURPLE + asker + " " + ChatFormatting.DARK_PURPLE
                + I18n.get("genderswap.sexpromt.playerxaskedfory") + " " + ChatFormatting.LIGHT_PURPLE + I18n.get(prompt.action())));
        minecraft.player.sendSystemMessage(Component.literal(ChatFormatting.DARK_PURPLE + I18n.get("genderswap.sexpromt.autodeletion")));
        minecraft.player.sendSystemMessage(Component.literal(ChatFormatting.DARK_PURPLE + "[ " + ChatFormatting.LIGHT_PURPLE
                + I18n.get("genderswap.sexpromt.accept") + ChatFormatting.DARK_PURPLE + " | " + ChatFormatting.LIGHT_PURPLE
                + I18n.get("genderswap.sexpromt.decline") + ChatFormatting.DARK_PURPLE + " ]"));
        pending = prompt;
        ticksLeft = 1200.0F;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (pending == null) {
            return;
        }
        ticksLeft -= 1.0F;
        if (ticksLeft <= 0.0F) {
            pending = null;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(ChatFormatting.DARK_PURPLE + I18n.get("genderswap.sexpromt.timeout")));
            }
        }
    }

    /** Original w.a(ClientChatEvent). */
    @SubscribeEvent
    public static void onChat(ClientChatEvent event) {
        if (pending == null) {
            return;
        }
        String message = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.equals(I18n.get("genderswap.sexpromt.accept").toLowerCase(java.util.Locale.ROOT))) {
            PacketDistributor.sendToServer(new PlayerFormNetwork.Accept(pending.girl(), pending.male(), pending.action()));
            pending = null;
            event.setCanceled(true);
        } else if (message.equals(I18n.get("genderswap.sexpromt.decline").toLowerCase(java.util.Locale.ROOT))) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(ChatFormatting.DARK_PURPLE
                        + I18n.get("genderswap.sexpromt.declineconformation")));
            }
            pending = null;
            event.setCanceled(true);
        }
    }
}
