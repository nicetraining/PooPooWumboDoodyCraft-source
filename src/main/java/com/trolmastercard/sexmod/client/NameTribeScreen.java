package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.network.TribeCommandPayload;
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // enter
            this.submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, "Name Tribe", this.width / 2 - 39, this.height / 2 - 24, 0xFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
