package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.network.TribeCommandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** Original g7: a text field (15 characters) and a "set" button. */
public final class NameTribeScreen extends Screen {
    private final UUID tribeId;
    private EditBox field;

    public NameTribeScreen(UUID tribeId) {
        super(Component.literal("Name Tribe"));
        this.tribeId = tribeId;
    }

    @Override
    protected void init() {
        this.field = new EditBox(this.font, this.width / 2 - 50, this.height / 2 - 10, 100, 20, Component.literal("Name Tribe"));
        this.field.setMaxLength(15);
        this.addRenderableWidget(this.field);
        this.setInitialFocus(this.field);
        this.addRenderableWidget(Button.builder(Component.literal("set"), button -> this.submit())
                .bounds(this.width / 2 - 25, this.height / 2 + 20, 50, 20).build());
    }

    private void submit() {
        String name = this.field.getValue().strip();
        if (name.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new TribeCommandPayload(TribeCommandPayload.CLAIM, BlockPos.ZERO, this.tribeId.toString(), name));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // original: drawHoveringText("Name Tribe", w/2 - 39, h/2 - 10), a tooltip box above the field
        graphics.renderTooltip(this.font, Component.literal("Name Tribe"), this.width / 2 - 39, this.height / 2 - 10);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Original g7.drawScreen draws no background: the world stays sharp and undimmed. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
}
