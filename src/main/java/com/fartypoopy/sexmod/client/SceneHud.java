package com.fartypoopy.sexmod.client;

import com.google.gson.JsonObject;
import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The original's arousal meter (class ds): a vertical gauge at the top left that keyframe events fill,
 * a "hold shift = fast" hint at the bottom left, and a "jump" prompt once the meter is full.
 * Coordinates and texture rectangles are the original's (256x256 sheets).
 */
public final class SceneHud {
    private static final ResourceLocation BUTTONS = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/gui/buttons.png");
    private static final ResourceLocation METER = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/gui/hornymeter.png");

    private static boolean shown;
    private static boolean showShiftHint = true;
    private static double meter;
    private static double displayed;
    private static float appear;
    private static float slideOut;
    private static boolean full;
    private static boolean shiftHeld;
    private static boolean jumpHeld;

    private SceneHud() {
    }

    public static boolean isShown() {
        return shown;
    }

    public static boolean isFull() {
        return meter >= 1.0D;
    }

    public static void setKeys(boolean shift, boolean jump) {
        shiftHeld = shift;
        jumpHeld = jump;
    }

    /** ds.d(): show (does nothing while already shown) and reset. */
    public static void show(boolean shiftHint) {
        if (shown) {
            return;
        }
        reset();
        shown = true;
        showShiftHint = shiftHint;
    }

    /** ds.c(): hide and reset. */
    public static void hide() {
        reset();
        shown = false;
        showShiftHint = true;
        appear = 0.0F;
    }

    /** ds.b(): meter back to zero. */
    public static void reset() {
        meter = 0.0D;
        full = false;
        slideOut = 0.0F;
    }

    /** ds.a(delta). */
    public static void add(double delta) {
        meter = Math.min(1.0D, meter + delta);
    }

    /** Applies the optional "hud" / "meter" fields of a keyframe table entry. */
    public static void apply(JsonObject entry) {
        if (entry.has("hud")) {
            switch (entry.get("hud").getAsString()) {
                case "show" -> show(true);
                case "reset" -> reset();
                case "hide" -> hide();
                default -> { }
            }
        }
        if (entry.has("meter")) {
            add(entry.get("meter").getAsDouble());
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!shown) {
            return;
        }
        float frame = deltaTracker.getRealtimeDeltaTicks();
        appear = appear < 1.0F ? appear + frame / 25.0F : 1.0F;

        graphics.pose().pushPose();
        graphics.pose().scale(0.35F, 0.35F, 0.35F);
        if (meter >= 1.0D) {
            if (jumpHeld) {
                full = true;
            }
            int n = full ? 54 : 0;
            graphics.blit(BUTTONS, 240, 160, 0, 108 + n, 256, 52);
        }
        if (showShiftHint && !full) {
            int n = shiftHeld ? 54 : 0;
            graphics.blit(BUTTONS, (int) Mth.lerp(appear, -200.0F, 98.0F), 405, 0, n, 158, 54);
        }
        graphics.pose().scale(2.857143F, 2.857143F, 2.857143F);
        graphics.pose().scale(0.75F, 0.75F, 0.75F);
        graphics.blit(METER, 10, (int) Mth.lerp(appear, -200.0F, 10.0F), 0, 0, 146, 175);

        displayed = displayed + (meter - displayed) * Math.min(1.0D, frame);
        int height = (int) Mth.lerp(displayed, 0.0D, 160.0D);
        int v = (int) Mth.lerp(displayed, 167.0D, 8.0D);
        double fillY = Mth.lerp(displayed, 178.0D, 18.0D);
        if (!full) {
            graphics.blit(METER, 67, (int) Mth.lerp(appear, -45.0D, fillY), 159, v, 32, height);
            double inv = 1.0D - displayed;
            int capY = (int) Mth.lerp(appear, -58.0D, Mth.lerp(inv, 178.0D, 149.0D));
            int capV = (int) Mth.lerp(inv, 169.0D, 141.0D);
            int capH = (int) Mth.lerp(inv, 1.0D, 29.0D);
            graphics.blit(METER, 120, capY, 212, capV, 28, capH);
            graphics.blit(METER, 18, capY, 212, capV, 28, capH);
        } else {
            slideOut += frame / 15.0F;
            graphics.blit(METER, 67, (int) Mth.lerp(slideOut, 18.0F, -300.0F), 159, 8, 32, 160);
        }
        graphics.pose().popPose();
    }
}
