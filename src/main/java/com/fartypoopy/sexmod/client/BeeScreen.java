package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.network.CompanionActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** The tamed bee's own screen (original ch): follow / stop following, go home, set home, and her chest. */
public final class BeeScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "textures/gui/girlinventory.png");

    private final CompanionEntity bee;
    private boolean following;
    private float slide;
    private Button followButton;
    private Button homeButton;
    private Button setHomeButton;

    public BeeScreen(CompanionEntity bee) {
        super(bee.getDisplayName());
        this.bee = bee;
        this.following = bee.hasMaster();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Original ch.drawScreen draws no background: the world stays sharp and undimmed behind the buttons. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void init() {
        this.followButton = this.addRenderableWidget(Button.builder(this.followLabel(), ignored -> this.toggleFollow())
                .bounds(0, 30, 1, 20).build());
        this.homeButton = this.addRenderableWidget(Button.builder(Component.translatable("action.names.gohome"), ignored -> {
            PacketDistributor.sendToServer(new CompanionActionPayload(this.bee.getId(), "bee_gohome"));
            this.onClose();
        }).bounds(0, 30, 1, 20).build());
        this.setHomeButton = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            PacketDistributor.sendToServer(new CompanionActionPayload(this.bee.getId(), "bee_sethome"));
            this.onClose();
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.translatable("bee.dialogue.home"));
            }
        }).bounds(0, 0, 20, 20).build());
    }

    private Component followLabel() {
        return Component.translatable(this.following ? "action.names.stopfollowme" : "action.names.followme");
    }

    private void toggleFollow() {
        PacketDistributor.sendToServer(new CompanionActionPayload(this.bee.getId(), this.following ? "bee_stop" : "bee_follow"));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.translatable(
                    this.following ? "bee.dialogue.sad" : "bee.dialogue.exited"));
        }
        this.following = !this.following;
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.slide = Math.min(1.0F, this.slide + partialTick / 5.0F);
        int width = Math.max(1, (int) (this.slide * 100.0F));
        int rise = (int) (15.0F - this.slide * 15.0F);
        this.followButton.setMessage(this.followLabel());
        this.followButton.setX(this.width / 2 - 119 + (int) (100.0F - 100.0F * this.slide));
        this.followButton.setWidth(width);
        this.homeButton.setX(this.width / 2 + 19);
        this.homeButton.setWidth(width);
        this.setHomeButton.setX(this.width / 2 - 10);
        this.setHomeButton.setY(59 - rise);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.blit(TEXTURE, this.width / 2 - 7, 61 - rise, 32, 0, 15, 15);
        graphics.blit(TEXTURE, this.width / 2 - 20, 20, this.bee.hasBeeChest() ? 0 : 40, 130, 40, 40);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.bee.hasBeeChest()
                && mouseX >= this.width / 2.0D - 20 && mouseX <= this.width / 2.0D + 20
                && mouseY >= 20 && mouseY <= 60) {
            // original ch.mouseClicked: the chest screen from the server replaces this one; the click still goes on
            PacketDistributor.sendToServer(new CompanionActionPayload(this.bee.getId(), "bee_chest"));
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
