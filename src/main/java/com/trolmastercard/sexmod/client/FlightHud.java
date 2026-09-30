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
        if (hideRequested != Long.MAX_VALUE && now - hideRequested > 500L) {
            active = false;
            hideRequested = Long.MAX_VALUE;
            fadeInStart = 0L;
            return;
        }
        float alpha = now < fadeInStart + 500L ? (now - fadeInStart) / 500.0F
                : hideRequested != Long.MAX_VALUE && now < hideRequested + 500L ? 1.0F + (hideRequested - now) / 500.0F : 1.0F;
        alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centre = width / 2;
        int top = height - 70;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(TEXTURE, centre - 64, top, 0, 77, 128, 41);
        graphics.blit(TEXTURE, (int) (centre - 1.5F * 23 + 1.0F), top + 3, 23, 2, 20, 31);
        graphics.blit(TEXTURE, centre - 11 + 1, top + 3, 23, 2, 20, 31);
        graphics.blit(TEXTURE, centre + 11 + 1, top + 3, 23, 2, 20, 31);

        // per-wing animation: a spent wing slides away and fades (150 ms), a regained one flies back in (500 ms)
        float spend = (float) Math.sin(Math.min(1.0F, (now - lastUse) / 150.0F) * Math.PI / 2.0D);
        float regain = spend == 1.0F ? Math.max(0.0F, Math.min(1.0F, 1.0F - (now - lastRegen) / 500.0F)) : 0.0F;
        wing(graphics, 1, -1.5F * 23, regain, spend, centre, top, alpha);
        wing(graphics, 2, -23 / 2.0F, regain, spend, centre, top, alpha);
        wing(graphics, 3, 23 / 2.0F, regain, spend, centre, top, alpha);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static final float[] SPENT_SHIFT = {-14.25F, -15.5F, -16.875F};
    private static final float[] REGAIN_SHIFT = {37.5F, 43.0F, 45.0F};

    private static void wing(GuiGraphics graphics, int index, float offset, float regainFlash, float spendAnim, int centre, int top, float alpha) {
        float spent = charges >= index ? 0.0F : (charges < index - 1 ? 1.0F : spendAnim);
        float regained = charges == index ? regainFlash : 0.0F;
        float scale = 1.0F + spent * 0.075F + regained * -0.15F;
        var pose = graphics.pose();
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(spent * SPENT_SHIFT[index - 1] + regained * REGAIN_SHIFT[index - 1], spent * -11.25F + regained * 37.5F, 0.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, alpha - spent - regained));
        graphics.blit(TEXTURE, (int) (centre + offset), top, 0, 0, 23, 36);
        graphics.setColor(1.0F, 1.0F, 1.0F, (float) Math.sin(Math.PI * spent) * 0.5F);
        graphics.blit(TEXTURE, (int) (centre + offset), top, 0, 36, 23, 36);
        pose.popPose();
    }
}
