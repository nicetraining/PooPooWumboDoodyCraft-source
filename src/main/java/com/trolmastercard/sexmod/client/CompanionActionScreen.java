package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionActions;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionScene;
import com.trolmastercard.sexmod.network.CompanionActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel that opens when a companion is right-clicked: a slide-out column of icon buttons
 * on the left (follow, stop following, go home, set home, equipment) and the companion's
 * scene actions on the right, each with its item cost.
 */
public final class CompanionActionScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "textures/gui/girlinventory.png");

    private static final String[] FIXED_ACTIONS = {"followme", "stopfollowme", "gohome", "setnewhome", "equipment"};
    private static final int[] ICON_U = {64, 80, 47, 32, 96};
    private static final int[] EXPANDED_WIDTH = {50, 90, 50, 80, 60};
    private static final EquipmentSlot[] SHOWN_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int[] SHOWN_SLOT_Y = {68, 87, 109, 127, 146, 166};

    private final CompanionEntity companion;
    private final List<CompanionScene> scenes;
    private final Button[] fixedButtons = new Button[FIXED_ACTIONS.length];
    private final List<Button> sceneButtons = new ArrayList<>();
    private final int[] hoverWidth = new int[FIXED_ACTIONS.length];
    private float slideIn;
    private float expand;

    public CompanionActionScreen(CompanionEntity companion) {
        super(companion.getDisplayName());
        this.companion = companion;
        this.scenes = CompanionScene.forKind(companion.kind());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        for (int index = 0; index < FIXED_ACTIONS.length; index++) {
            String action = FIXED_ACTIONS[index];
            this.fixedButtons[index] = this.addRenderableWidget(
                    Button.builder(Component.empty(), ignored -> this.choose(action)).bounds(0, 0, 0, 20).build());
        }
        this.sceneButtons.clear();
        for (CompanionScene scene : this.scenes) {
            this.sceneButtons.add(this.addRenderableWidget(Button.builder(
                    Component.translatable(scene.translationKey()),
                    ignored -> this.choose(scene.serializedName())
            ).bounds(0, 0, 100, 20).build()));
        }
    }

    private void choose(String action) {
        PacketDistributor.sendToServer(new CompanionActionPayload(this.companion.getId(), action));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float step = partialTick / 5.0F;
        this.slideIn = Math.min(1.0F, this.slideIn + step);
        if (this.slideIn == 1.0F) {
            this.expand = Math.min(1.0F, this.expand + step);
        }
        this.layoutButtons(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderDecorations(graphics);
    }

    private void layoutButtons(int mouseX, int mouseY) {
        int visibleIndex = 0;
        for (int index = 0; index < FIXED_ACTIONS.length; index++) {
            boolean shown = !(index == 0 && this.companion.isFollowing()) && !(index == 1 && !this.companion.isFollowing());
            Button button = this.fixedButtons[index];
            button.visible = shown && this.expand > 0.0F;
            if (!shown) {
                continue;
            }
            int y = 70 + 30 * visibleIndex++;
            int base = (int) Mth.lerp(this.expand, 0.0F, 23.0F);
            boolean hovered = mouseX >= 35 && mouseX <= 35 + base + this.hoverWidth[index]
                    && mouseY >= y && mouseY <= y + 20;
            this.hoverWidth[index] = hovered
                    ? Math.min(EXPANDED_WIDTH[index], this.hoverWidth[index] + 7)
                    : Math.max(0, this.hoverWidth[index] - 7);
            button.setX(36);
            button.setY(y);
            button.setWidth(Math.max(1, base + this.hoverWidth[index]));
            button.setMessage(this.hoverWidth[index] > 14
                    ? Component.translatable("action.names." + FIXED_ACTIONS[index])
                    : Component.empty());
        }
        int sceneX = this.width - (int) Mth.lerp(this.slideIn, -30.0F, 120.0F);
        for (int index = 0; index < this.sceneButtons.size(); index++) {
            Button button = this.sceneButtons.get(index);
            button.setX(sceneX);
            button.setY(this.height - 70 - 30 * index);
            button.active = CompanionActions.canAfford(this.minecraft.player, this.scenes.get(index));
        }
    }

    private void renderDecorations(GuiGraphics graphics) {
        int panelX = (int) Mth.lerp(this.slideIn, -30.0F, 120.0F) - 113;
        graphics.blit(TEXTURE, panelX, 60, 0, 0, 32, 130);
        for (int index = 0; index < SHOWN_SLOTS.length; index++) {
            ItemStack stack = this.companion.getItemBySlot(SHOWN_SLOTS[index]);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, panelX + 8, SHOWN_SLOT_Y[index]);
            }
        }
        if (this.expand <= 0.0F) {
            return;
        }
        for (int index = 0; index < FIXED_ACTIONS.length; index++) {
            Button button = this.fixedButtons[index];
            if (button.visible) {
                int iconX = this.hoverWidth[index] + 17 + (int) Mth.lerp(this.expand, 0.0F, 23.0F);
                graphics.blit(TEXTURE, iconX, button.getY() + 2, ICON_U[index], 0, 16, 16);
            }
        }
        for (int index = 0; index < this.sceneButtons.size(); index++) {
            ItemStack cost = CompanionActions.costOf(this.scenes.get(index));
            if (!cost.isEmpty()) {
                int x = this.width - (int) Mth.lerp(this.expand, 91.0F, 137.0F);
                int y = this.height - 68 - 30 * index;
                graphics.renderItem(cost, x, y);
                graphics.drawString(this.font, cost.getCount() + "x", x - 22, y + 4, 0xFFFFFF);
            }
        }
    }
}
