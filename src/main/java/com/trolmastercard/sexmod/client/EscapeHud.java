package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionScene;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Original gb: the escape minigame while the wild Galath drains you. A key (W, A, S or D) lights up, changes every
 * 35 ticks; pressing the right one fills the bar by 0.08, a wrong one costs 0.04, and it drains 0.006 per tick. A full bar
 * frees you.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class EscapeHud {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/gui/escape_minigame_ui.png");
    private static final float SCALE = 0.5F;
    private static final int SLIDE = 20;

    private static boolean active;
    private static boolean closing;
    private static int entityId;
    private static float progress;
    private static int ticks;
    private static int closeTicks;
    private static int key = -1;
    private static final boolean[] WAS_DOWN = new boolean[4];

    private EscapeHud() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void start(int id) {
        active = true;
        closing = false;
        entityId = id;
        progress = 0.0F;
        ticks = 0;
        closeTicks = 0;
        key = -1;
    }

    private static KeyMapping[] keys(Minecraft mc) {
        return new KeyMapping[]{mc.options.keyUp, mc.options.keyLeft, mc.options.keyDown, mc.options.keyRight};
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            active = false;
            return;
        }
        if (closing) {
            if (++closeTicks > SLIDE) {
                active = false;
            }
            return;
        }
        if (!(mc.level.getEntity(entityId) instanceof CompanionEntity galath)
                || galath.currentScene() != CompanionScene.GALATH_WILD_RAPE) {
            closing = true;
            return;
        }
        ticks++;
        progress = Math.max(0.0F, progress - 0.006F);
        if (ticks >= SLIDE && (key < 0 || (ticks - SLIDE) % 35 == 0)) {
            key = mc.player.getRandom().nextInt(4);
        }
        KeyMapping[] mappings = keys(mc);
        for (int i = 0; i < 4; i++) {
            boolean down = mappings[i].isDown();
            if (down && !WAS_DOWN[i] && ticks >= SLIDE && key >= 0) {
                progress += i == key ? 0.08F : -0.04F;
            }
            WAS_DOWN[i] = down;
        }
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        if (progress >= 1.0F) {
            PacketDistributor.sendToServer(new com.trolmastercard.sexmod.network.GalathEscapePayload.Up(entityId));
            closing = true;
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active) {
            return;
        }
        float slide = closing ? 1.0F - Math.min(1.0F, closeTicks / (float) SLIDE) : Math.min(1.0F, ticks / (float) SLIDE);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float frameW = 174 * SCALE;
        float frameH = 48 * SCALE;
        float x = (width - frameW) / 2.0F;
        float y = height - (frameH + 26 * SCALE + 12) * slide;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(SCALE, SCALE, 1.0F);
        graphics.blit(TEXTURE, 0, 0, 0, 104, 174, 48, 256, 256);
        int fill = Math.round(158 * progress);
        if (fill > 0) {
            graphics.blit(TEXTURE, 8, 8, 0, 152, fill, 32, 256, 256);
        }
        if (key >= 0 && !closing) {
            boolean lit = (ticks / 2) % 2 == 0;
            graphics.blit(TEXTURE, (174 - 52) / 2, -52 - 4, key * 52, lit ? 52 : 0, 52, 52, 256, 256);
        }
        pose.popPose();
        RenderSystem.disableBlend();
    }
}
