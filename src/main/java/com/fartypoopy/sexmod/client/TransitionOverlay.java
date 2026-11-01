package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The original's transition screen (class fh): two scrolling wipe strips with a black band between
 * them slide across the screen right to left, roughly 23 ticks in all. Movement is locked while it runs.
 */
public final class TransitionOverlay {
    private static final ResourceLocation STRIP = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/gui/transitionscreen.png");
    private static final ResourceLocation STRIP_MIRRORED = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/gui/mirroredtransitionscreen.png");

    private static boolean active;
    private static double progress;

    private TransitionOverlay() {
    }

    public static void start() {
        active = true;
        progress = 0.0D;
    }

    public static boolean isActive() {
        return active;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active) {
            return;
        }
        // 1.12.2 advanced 0.75 per rendered frame; measured side by side in the 1.12 client its visible sweep (e 18
        // to 69) took about 1.5 s, so 1.75 a tick. The screen is then fully covered about 20 to 28 ticks in, where the
        // scenes let the player go (their end comes 20 to 27 ticks after the black-screen keyframe). At 3 a tick
        // (the earlier guess) the wipe was over before the scene ended.
        progress += deltaTracker.getGameTimeDeltaTicks() * 1.75D;

        // the original chose by the GUI scale *option*: 1 and 2 have their own layouts, Auto (0) and 3+ the plain one
        int scale = Minecraft.getInstance().options.guiScale().get();
        double t = 0.5D * Math.cos(progress / 25.0D) + 0.5D;
        double offset;
        float zoom;
        if (scale == 1) {
            offset = Mth.lerp(t, -1800.0D, 1000.0D);
            zoom = 2.0F;
        } else if (scale == 2) {
            offset = Mth.lerp(t, -900.0D, 750.0D);
            zoom = 1.5F;
        } else {
            offset = Mth.lerp(t, -900.0D, 600.0D);
            zoom = 1.0F;
        }

        int scroll = Math.floorMod((int) (progress * 1.5D), 256);
        graphics.pose().pushPose();
        graphics.pose().scale(zoom, zoom, 1.0F);
        graphics.pose().translate((float) offset, 0.0F, 0.0F);
        for (int row = 0; row < 3; row++) {
            strip(graphics, STRIP, 0, row * 256, scroll);
            strip(graphics, STRIP_MIRRORED, 600, row * 256, scroll);
        }
        graphics.fill(200, 0, 600, 768, 0xFF000000);
        graphics.pose().popPose();

        if (progress > 69.0D) {
            active = false;
            progress = 0.0D;
        }
    }

    /** One 256x256 tile whose texture rows scroll downwards, wrapping around inside the texture. */
    private static void strip(GuiGraphics graphics, ResourceLocation texture, int x, int y, int scroll) {
        graphics.blit(texture, x, y, 0, scroll, 256, 256 - scroll);
        if (scroll > 0) {
            graphics.blit(texture, x, y + 256 - scroll, 0, 0, 256, scroll);
        }
    }
}
