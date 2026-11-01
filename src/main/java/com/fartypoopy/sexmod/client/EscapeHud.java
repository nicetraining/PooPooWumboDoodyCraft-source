package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionScene;
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
    private static final int SLIDE = 20;

    private static boolean active;
    private static boolean closing;
    private static int entityId;
    private static float progress;
    private static int ticks;
    private static int closeTicks;
    /** Ticks spent waiting for her scene to reach the client (the packet can arrive before her synced data). */
    private static int waitTicks;
    private static boolean sceneSeen;
    private static int key = -1;
    private static boolean blink = true;
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
        waitTicks = 0;
        sceneSeen = false;
        key = -1;
        blink = true;
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
            if (sceneSeen || ++waitTicks > 20) {
                closing = true;
            }
            return;
        }
        sceneSeen = true;
        ticks++;
        if (ticks % 2 == 0) {
            blink = !blink; // original gb.b flips on every second tick
        }
        progress = Math.max(0.0F, progress - 0.006F);
        if (ticks >= SLIDE && (key < 0 || ticks % 35 == 0)) {
            int previous = key;
            do {
                key = mc.player.getRandom().nextInt(4); // original b(): never the same key twice in a row
            } while (key == previous);
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
            PacketDistributor.sendToServer(new com.fartypoopy.sexmod.network.GalathEscapePayload.Up(entityId));
            closing = true;
        }
    }

    /** Original b6.c / b6.d: easeOutBack and easeInBack. */
    private static double easeOut(double t) {
        double c = 1.70158D;
        return 1.0D + (c + 1.0D) * Math.pow(t - 1.0D, 3.0D) + c * Math.pow(t - 1.0D, 2.0D);
    }

    private static double easeIn(double t) {
        double c = 1.70158D;
        return (c + 1.0D) * t * t * t - c * t * t;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Original gb.a(RenderGameOverlayEvent): everything is drawn at scale 0.33 after a (485, 0) shift. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active) {
            return;
        }
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        double t = closing ? 1.0D - easeIn((closeTicks + partial) / 20.0D) : Math.min(1.0D, easeOut((ticks + partial) / 20.0D));
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int baseY = height + 385;
        int startY = 4 * height;
        boolean lit = blink;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        var pose = graphics.pose();
        pose.pushPose();
        pose.scale(0.33F, 0.33F, 1.0F);
        pose.translate(485.0F, 0.0F, 0.0F);
        graphics.blit(TEXTURE, width / 2 - 87, (int) lerp(startY, baseY, t), 0, 104, 174, 48, 256, 256);
        // keys in the order of the original texture columns: W 0, A 52, S 104, D 156
        key(graphics, (int) (width / 2.0F - 78.0F), (int) lerp(startY, baseY - 52, t), 52, lit && key == 1);
        key(graphics, (int) (width / 2.0F - 26.0F), (int) lerp(startY, baseY - 52, t), 104, lit && key == 2);
        key(graphics, (int) (width / 2.0F + 26.0F), (int) lerp(startY, baseY - 52, t), 156, lit && key == 3);
        key(graphics, (int) (width / 2.0F - 26.0F), (int) lerp(startY, baseY - 104, t), 0, lit && key == 0);
        int fill = (int) (158.0F * progress);
        if (fill > 0) {
            graphics.blit(TEXTURE, width / 2 - 87 + 8, (int) lerp(startY - 8, baseY + 8, t), 8, 152, fill, 32, 256, 256);
        }
        pose.popPose();
        RenderSystem.disableBlend();
    }

    private static void key(GuiGraphics graphics, int x, int y, int u, boolean highlighted) {
        graphics.blit(TEXTURE, x, y, u, highlighted ? 52 : 0, 52, 52, 256, 256);
    }
}
