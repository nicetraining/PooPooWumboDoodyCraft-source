package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionScene;
import com.trolmastercard.sexmod.menu.CompanionInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class CompanionInventoryScreen extends AbstractContainerScreen<CompanionInventoryMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID,
            "textures/gui/girlinventory.png"
    );

    private Button toggleScene;
    private Button slower;
    private Button faster;
    private Button finish;
    private Button outfit;
    private Button variant;
    private Button scale;
    private Button mode;
    private Button home;

    public CompanionInventoryScreen(CompanionInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 218;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        this.addRenderableWidget(this.button("<", x + 4, y + 4, 20, 20,
                CompanionInventoryMenu.BUTTON_SCENE_PREVIOUS));
        this.addRenderableWidget(this.button(">", x + 152, y + 4, 20, 20,
                CompanionInventoryMenu.BUTTON_SCENE_NEXT));
        this.toggleScene = this.addRenderableWidget(this.button(
                "screen.sexmod.scene_start", x + 4, y + 27, 41, 20,
                CompanionInventoryMenu.BUTTON_SCENE_TOGGLE
        ));
        this.slower = this.addRenderableWidget(this.button(
                "screen.sexmod.scene_slower", x + 47, y + 27, 39, 20,
                CompanionInventoryMenu.BUTTON_SCENE_SLOWER
        ));
        this.faster = this.addRenderableWidget(this.button(
                "screen.sexmod.scene_faster", x + 88, y + 27, 39, 20,
                CompanionInventoryMenu.BUTTON_SCENE_FASTER
        ));
        this.finish = this.addRenderableWidget(this.button(
                "screen.sexmod.scene_finish", x + 129, y + 27, 43, 20,
                CompanionInventoryMenu.BUTTON_SCENE_FINISH
        ));

        this.outfit = this.addRenderableWidget(this.button(
                "editor.sexmod.outfit", x + 4, y + 169, 54, 20,
                CompanionInventoryMenu.BUTTON_OUTFIT
        ));
        this.variant = this.addRenderableWidget(this.button(
                "editor.sexmod.variant", x + 61, y + 169, 54, 20,
                CompanionInventoryMenu.BUTTON_VARIANT
        ));
        this.scale = this.addRenderableWidget(this.button(
                "editor.sexmod.scale", x + 118, y + 169, 54, 20,
                CompanionInventoryMenu.BUTTON_SCALE
        ));
        this.mode = this.addRenderableWidget(this.button(
                "editor.sexmod.movement", x + 4, y + 192, 82, 20,
                CompanionInventoryMenu.BUTTON_MODE
        ));
        this.home = this.addRenderableWidget(this.button(
                "editor.sexmod.home", x + 89, y + 192, 83, 20,
                CompanionInventoryMenu.BUTTON_HOME
        ));
        this.updateButtonState();
    }

    private Button button(String translationKey, int x, int y, int width, int height, int buttonId) {
        return Button.builder(
                Component.translatable(translationKey),
                ignored -> this.sendButton(buttonId)
        ).bounds(x, y, width, height).build();
    }

    private void sendButton(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.updateButtonState();
    }

    private void updateButtonState() {
        if (this.toggleScene == null) {
            return;
        }
        CompanionEntity companion = this.menu.companion();
        CompanionScene scene = companion == null ? null : companion.currentScene();
        boolean active = scene != null;
        int phase = active ? companion.currentScenePhase() : -1;
        CompanionScene.Phase currentPhase = active ? scene.phase(phase) : null;
        boolean controllableLoop = currentPhase != null && currentPhase.loop();

        this.toggleScene.setMessage(Component.translatable(
                active ? "screen.sexmod.scene_stop" : "screen.sexmod.scene_start"
        ));
        this.slower.active = controllableLoop && scene.previousLoopPhase(phase) >= 0;
        this.faster.active = controllableLoop && scene.nextLoopPhase(phase) >= 0;
        this.finish.active = controllableLoop;
        this.outfit.active = !active;
        this.variant.active = !active;
        this.scale.active = !active;
        this.mode.active = !active;
        this.home.active = !active;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(
                this.leftPos,
                this.topPos,
                this.leftPos + this.imageWidth,
                this.topPos + this.imageHeight,
                0xE0101010
        );
        graphics.blit(TEXTURE, this.leftPos, this.topPos + 52, 80, 142, this.imageWidth, 114);
        graphics.fill(this.leftPos, this.topPos + 48, this.leftPos + this.imageWidth, this.topPos + 51, 0xFF705030);
        graphics.fill(this.leftPos, this.topPos + 166, this.leftPos + this.imageWidth, this.topPos + 168, 0xFF705030);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        CompanionEntity companion = this.menu.companion();
        CompanionScene activeScene = companion == null ? null : companion.currentScene();
        CompanionScene shownScene = activeScene == null ? this.menu.selectedScene() : activeScene;
        Component sceneName = shownScene == null
                ? Component.translatable("screen.sexmod.scene_none")
                : Component.translatable(shownScene.translationKey());
        graphics.drawCenteredString(this.font, sceneName, this.imageWidth / 2, 10, 0xFFFFFF);

        Component state = activeScene == null
                ? Component.translatable("screen.sexmod.scene_idle")
                : Component.translatable(
                        "screen.sexmod.scene_phase",
                        companion.currentScenePhase() + 1,
                        activeScene.phases().size()
                );
        graphics.drawCenteredString(this.font, state, this.imageWidth / 2, 50, 0xFFE0A0);
        graphics.drawString(
                this.font,
                this.playerInventoryTitle,
                this.inventoryLabelX,
                this.inventoryLabelY,
                0x404040,
                false
        );
    }
}
