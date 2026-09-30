package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.trolmastercard.sexmod.FapCraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The original's Galath flight HUD (class hf): three wing charges. A boost needs a charge and 3 s since the
 * last use; a missing charge returns 5 s after the last use or regeneration. It fades in while riding.
 */
public final class FlightHud {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/gui/galath_flight_ui.png");

    private static boolean active;
    private static int charges = 3;
    private static long lastUse;
    private static long lastRegen;
    private static long fadeInStart;
    private static long hideRequested = Long.MAX_VALUE;

    private FlightHud() {
    }

    public static boolean isActive() {
        return active;
    }

    /** hf.f(): show (fade in). */
    public static void show() {
        hideRequested = Long.MAX_VALUE;
        if (!active) {
            active = true;
            fadeInStart = System.currentTimeMillis();
        }
    }

    /** hf.c(): fade out, then disable. */
    public static void hide() {
        if (active && hideRequested == Long.MAX_VALUE) {
            hideRequested = System.currentTimeMillis();
        }
    }

    public static boolean canBoost() {
        return charges > 0 && System.currentTimeMillis() - lastUse > 3000L;
    }

    /** hf.a(): spend a charge. */
    public static void consume() {
        charges--;
        lastUse = System.currentTimeMillis();
    }

    private static void regenerate() {
        long now = System.currentTimeMillis();
        if (charges < 3 && now - Math.max(lastUse, lastRegen) >= 5000L) {
            charges++;
            lastRegen = now;
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        regenerate();
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        float alpha = Math.min(1.0F, (now - fadeInStart) / 500.0F);
        if (hideRequested != Long.MAX_VALUE) {
            alpha = Math.min(alpha, 1.0F - (now - hideRequested) / 500.0F);
            if (alpha <= 0.0F) {
                active = false;
                hideRequested = Long.MAX_VALUE;
                return;
            }
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int frameX = width / 2 - 64;
        int frameY = height - 70 - 41;

        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(TEXTURE, frameX, frameY, 0, 77, 128, 41);
        for (int i = 0; i < 3; i++) {
            int centerX = width / 2 + (i - 1) * 27;
            graphics.blit(TEXTURE, centerX - 10, frameY + 5, 23, 2, 20, 31);
            if (i < charges) {
                graphics.blit(TEXTURE, centerX - 11, frameY + 3, 0, 0, 23, 36);
            }
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
