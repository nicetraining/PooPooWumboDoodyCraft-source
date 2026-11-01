package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.network.GoblinWheelPayload;
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
    private float left;   // original g: mouse in the left half
    private float right;  // original e: mouse in the right half (opens the two right-hand tiles)
    private float top;    // original d
    private float bottom; // original m

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
        float scale = (float) easeOutBack(this.open);
        float offset = (1.0F - scale) * 100.0F;
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.left = Mth.clamp(this.left + (mouseX < cx ? delta : -delta), 0.0F, 1.0F);
        this.right = Mth.clamp(this.right + (mouseX > cx ? delta : -delta), 0.0F, 1.0F);
        this.top = Mth.clamp(this.top + (mouseY < cy - 1 ? delta : -delta), 0.0F, 1.0F);
        this.bottom = Mth.clamp(this.bottom + (mouseY > cy ? delta : -delta), 0.0F, 1.0F);

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.scale(scale, scale, scale);
        // left tile: grows towards the mouse
        this.tile(graphics, 1.0F + this.left * 0.5F, -62.0F + offset - this.left * 15.0F, offset - 32.0F, 64, 128);
        // right hub shrinks away as the mouse moves right; the two sub-tiles take its place
        this.tile(graphics, 1.0F - this.right, -2.0F - offset + this.right * 32.0F, -offset - 32.0F, 0, 128);
        if (this.right > 0.0F) {
            float topScale = this.right + this.top * 0.5F;
            this.tile(graphics, topScale, -2.0F - offset + this.top * 5.0F, -offset - 64.0F - this.top * 5.0F / 2.0F, 128, 128);
            float bottomScale = this.right + this.bottom * 0.5F;
            this.tile(graphics, bottomScale, -2.0F - offset + this.bottom * 5.0F, -offset + this.bottom * 5.0F / 2.0F, 192, 128);
        }
        pose.popPose();
    }

    private void tile(GuiGraphics graphics, float scale, float x, float y, int iconU, int iconV) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1.0F);
        pose.translate(x, y, 0.0F);
        graphics.blit(TEXTURE, 0, 0, 0, 0, 64, 64, 256, 256);
        graphics.blit(TEXTURE, 0, 0, iconU, iconV, 64, 64, 256, 256);
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
        // original ea.onGuiClosed: the left tile wins as soon as it is open, otherwise the larger of top and bottom
        if (this.top == 0.0F && this.bottom == 0.0F && this.left == 0.0F) {
            return;
        }
        int op = this.left > 0.0F ? 0 : this.top > this.bottom ? 1 : 2;
        PacketDistributor.sendToServer(new GoblinWheelPayload(this.goblin.getId(), op));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
