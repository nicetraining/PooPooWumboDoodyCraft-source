package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionActions;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionScene;
import com.fartypoopy.sexmod.network.CompanionActionPayload;
import net.minecraft.client.Minecraft;
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
    /** Original m.b: spaces after each label so the centred text clears the icon on the right. */
    private static final int[] LABEL_PADDING = {4, 4, 5, 5, 4};
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
    private final boolean showFixed;
    /** Player-form mode (original em.a(player, girl, actions, false)): plain action labels and a callback. */
    private final List<String> labels;
    private final java.util.function.Consumer<String> onLabel;
    private float slideIn;
    private float expand;

    public CompanionActionScreen(CompanionEntity companion) {
        this(companion,
                CompanionActions.actionsFor(companion, Minecraft.getInstance().player),
                CompanionActions.showsFixedButtons(companion.kind())
                        && companion.canUsePanel(Minecraft.getInstance().player));
    }

    public CompanionActionScreen(CompanionEntity companion, List<CompanionScene> scenes, boolean showFixed) {
        super(companion.getDisplayName());
        this.companion = companion;
        this.showFixed = showFixed;
        this.scenes = scenes;
        this.labels = null;
        this.onLabel = null;
    }

    /** The action menu of a player form: one button per action (a lang key or the original's literal label). */
    public CompanionActionScreen(CompanionEntity form, List<String> labels, java.util.function.Consumer<String> onLabel) {
        super(form.getDisplayName());
        this.companion = form;
        this.showFixed = false;
        this.scenes = List.of();
        this.labels = labels;
        this.onLabel = onLabel;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Original m.drawScreen draws no background: the world stays sharp and undimmed behind the buttons. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void init() {
        for (int index = 0; index < FIXED_ACTIONS.length; index++) {
            String action = FIXED_ACTIONS[index];
            this.fixedButtons[index] = this.addRenderableWidget(
                    Button.builder(Component.empty(), ignored -> this.choose(action)).bounds(0, 0, 0, 20).build());
        }
        this.sceneButtons.clear();
        if (this.labels != null) {
            for (String label : this.labels) {
                this.sceneButtons.add(this.addRenderableWidget(Button.builder(Component.translatable(label), ignored -> {
                    this.onLabel.accept(label);
                    this.onClose();
                }).bounds(0, 0, 100, 20).build()));
            }
            return;
        }
        for (CompanionScene scene : this.scenes) {
            this.sceneButtons.add(this.addRenderableWidget(Button.builder(
                    Component.translatable(scene.isClothingToggle() && this.companion.isNude()
                            ? "action.names.dressup" : scene.menuLabelKey()),
                    ignored -> this.chooseScene(scene)
            ).bounds(0, 0, 100, 20).build()));
        }
    }

    @Override
    public void removed() {
        super.removed();
        // original ff.ac / e0.ac (menu closed): the Kobold ends the interaction; Bia, closed without a choice while she
        // waits in her talk, ends the scene
        if (this.labels == null && (this.companion.kind() == com.fartypoopy.sexmod.entity.CompanionKind.KOBOLD
                || (this.companion.kind() == com.fartypoopy.sexmod.entity.CompanionKind.BIA && !this.chose))) {
            PacketDistributor.sendToServer(new CompanionActionPayload(this.companion.getId(), CompanionActionPayload.CLOSE));
        }
    }

    /**
     * Original m.actionPerformed: the buttons stay clickable when the price is missing; the click then only prints
     * "<name> you cannot afford that..." and the girl's second sad "oh" plays where she stands, and the panel stays open.
     */
    private void chooseScene(CompanionScene scene) {
        var player = this.minecraft.player;
        if (CompanionActions.canAfford(player, this.companion, scene)) {
            this.choose(scene.serializedName());
            return;
        }
        player.displayClientMessage(Component.translatable("message.sexmod.cannot_afford", this.companion.getDisplayName()), false);
        com.fartypoopy.sexmod.registry.ModSounds.find("girls.jenny.sadoh.sadoh1").ifPresent(sound -> {
            var pos = this.companion.blockPosition();
            player.level().playLocalSound(pos.getX(), pos.getY(), pos.getZ(), sound.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F, false);
        });
    }

    private boolean chose;

    private void choose(String action) {
        this.chose = true;
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
            boolean shown = this.showFixed
                    && !(index == 0 && this.companion.isFollowing())
                    && !(index == 1 && !this.companion.isFollowing());
            Button button = this.fixedButtons[index];
            button.visible = shown && this.expand > 0.0F;
            if (!shown) {
                continue;
            }
            int y = 70 + 30 * visibleIndex++;
            int base = (int) Mth.lerp(this.expand, 0.0F, 23.0F);
            boolean hovered = mouseX >= 35 && mouseX <= 35 + 23 + this.hoverWidth[index]
                    && mouseY >= y && mouseY <= y + 20;
            this.hoverWidth[index] = hovered
                    ? Math.min(EXPANDED_WIDTH[index], this.hoverWidth[index] + 7)
                    : Math.max(0, this.hoverWidth[index] - 7);
            button.setX(36);
            button.setY(y);
            button.setWidth(Math.max(1, base + this.hoverWidth[index]));
            button.setMessage(this.hoverWidth[index] > 14
                    ? Component.translatable("action.names." + FIXED_ACTIONS[index]).append(" ".repeat(LABEL_PADDING[index]))
                    : Component.empty());
        }
        int sceneX = this.width - (int) Mth.lerp(this.slideIn, -30.0F, 120.0F);
        for (int index = 0; index < this.sceneButtons.size(); index++) {
            Button button = this.sceneButtons.get(index);
            button.setX(sceneX);
            button.setY(this.height - 70 - 30 * index);
        }
    }

    private void renderDecorations(GuiGraphics graphics) {
        // original m.a(int, int), only for girls with the fixed buttons: her equipment slides in first, the panel
        // behind it and the button icons follow once the buttons expand
        if (this.showFixed) {
            int panelX = (int) Mth.lerp(this.slideIn, -30.0F, 120.0F) - 113;
            if (this.expand > 0.0F) {
                graphics.blit(TEXTURE, panelX, 60, 0, 0, 32, 130);
                for (int index = 0; index < FIXED_ACTIONS.length; index++) {
                    Button button = this.fixedButtons[index];
                    if (button.visible) {
                        int iconX = this.hoverWidth[index] + 17 + (int) Mth.lerp(this.expand, 0.0F, 23.0F);
                        graphics.blit(TEXTURE, iconX, button.getY() + 2, ICON_U[index], 0, 16, 16);
                    }
                }
            }
            for (int index = 0; index < SHOWN_SLOTS.length; index++) {
                ItemStack stack = this.companion.getItemBySlot(SHOWN_SLOTS[index]);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, panelX + 8, SHOWN_SLOT_Y[index]);
                }
            }
        }
        if (this.expand <= 0.0F) {
            return;
        }
        for (int index = 0; index < this.sceneButtons.size() && this.labels == null; index++) {
            ItemStack cost = CompanionActions.costFor(this.companion, this.minecraft.player, this.scenes.get(index));
            if (!cost.isEmpty()) {
                int x = this.width - (int) Mth.lerp(this.expand, 91.0F, 137.0F);
                int y = this.height - 68 - 30 * index;
                // original m.a(List, x, y, font): a tooltip box with "Nx    " behind the item
                String text = cost.getCount() + "x    ";
                int textX = x - 12;
                int textY = y + 4;
                net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil.renderTooltipBackground(
                        graphics, textX, textY, this.font.width(text), 8, 0);
                graphics.drawString(this.font, text, textX, textY, 0xFFFFFFFF);
                graphics.renderItem(cost, x, y);
            }
        }
    }
}
