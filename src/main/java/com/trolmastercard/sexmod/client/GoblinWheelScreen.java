package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.network.GoblinWheelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Original ea: the wheel for a goblin on the shoulder. Left half: take her stuff back and throw her away. Right half:
 * top = paizuri ("use her"), bottom = nelson. The tile that is largest when the key is released fires.
 */
public final class GoblinWheelScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "textures/gui/command.png");

    private final CompanionEntity goblin;
    private float open;
    private float left;
    private float top;
    private float bottom;

    public GoblinWheelScreen(CompanionEntity goblin) {
        super(Component.empty());
        this.goblin = goblin;
    }

    private static double easeOutBack(double x) {
        double c1 = 1.70158D;
        double c3 = c1 + 1.0D;
        return 1.0D + c3 * Math.pow(x - 1.0D, 3.0D) + c1 * Math.pow(x - 1.0D, 2.0D);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        this.open = Math.min(1.0F, this.open + delta / 5.0F);
        float offset = (float) (100.0D * (1.0D - easeOutBack(this.open)));
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.left = Mth.clamp(this.left + (mouseX < cx ? delta : -delta), 0.0F, 1.0F);
        this.top = Mth.clamp(this.top + (mouseX >= cx && mouseY < cy ? delta : -delta), 0.0F, 1.0F);
        this.bottom = Mth.clamp(this.bottom + (mouseX >= cx && mouseY >= cy ? delta : -delta), 0.0F, 1.0F);
        this.tile(graphics, cx, cy, this.left, -62.0F + offset - this.left * 15.0F, -32.0F, 0, 64, 128);
        this.tile(graphics, cx, cy, this.top, -2.0F - offset + this.top * 15.0F, -62.0F + offset - this.top * 15.0F, 128, 128, 128);
        this.tile(graphics, cx, cy, this.bottom, -2.0F - offset + this.bottom * 15.0F, -2.0F - offset + this.bottom * 15.0F, 192, 128, 128);
    }

    private void tile(GuiGraphics graphics, int cx, int cy, float growth, float x, float y, int baseV, int iconU, int iconV) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.scale(1.0F + growth * 0.5F, 1.0F + growth * 0.5F, 1.0F);
        graphics.blit(TEXTURE, (int) x, (int) y, 0, 0, 64, 64, 256, 256);
        graphics.blit(TEXTURE, (int) x, (int) y, iconU, iconV, 64, 64, 256, 256);
        pose.popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // the world stays visible
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GoblinWheelKey.KEY.getKey().getValue()) {
            this.onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        float largest = Math.max(this.left, Math.max(this.top, this.bottom));
        if (largest <= 0.0F) {
            return;
        }
        int op = this.left == largest ? 0 : this.top == largest ? 1 : 2;
        PacketDistributor.sendToServer(new GoblinWheelPayload(this.goblin.getId(), op));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
