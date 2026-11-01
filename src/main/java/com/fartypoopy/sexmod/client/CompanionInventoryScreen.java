package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.menu.CompanionInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Original fb (six equipment slots) and az (Luna, with the rod slot): only the 176 x 114 strip of girlinventory.png
 * behind the slots, no title or inventory labels. The screen keeps GuiContainer's default 176 x 166 size, so the strip
 * starts 52 pixels below the top of it.
 */
public final class CompanionInventoryScreen extends AbstractContainerScreen<CompanionInventoryMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID,
            "textures/gui/girlinventory.png"
    );

    public CompanionInventoryScreen(CompanionInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int u = this.menu.hasRodSlot() ? 80 : 33;
        int v = this.menu.hasRodSlot() ? 142 : 16;
        graphics.blit(TEXTURE, this.width / 2 - 88, this.height / 2 - 7 - 24, u, v, 176, 114);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
