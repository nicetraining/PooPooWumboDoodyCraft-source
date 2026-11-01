package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.custommodel.CustomModels;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.PlayerForms;
import com.fartypoopy.sexmod.network.PlayerFormNetwork;
import com.fartypoopy.sexmod.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Original b5: after the horny potion, pick a girl to become (every kind with a player form) or yourself (last entry)
 * to turn back. The choice turns slowly (60 degrees a second); "&lt;" and "&gt;" page, "pick" sends it.
 */
public final class FormPickerScreen extends Screen {
    private static float spin;
    private final List<LivingEntity> choices = new ArrayList<>();
    private final List<CompanionKind> kinds = new ArrayList<>();
    private int index;

    private FormPickerScreen(Map<String, String> specifics) {
        super(Component.empty());
        Minecraft minecraft = Minecraft.getInstance();
        for (CompanionKind kind : CompanionKind.values()) {
            if (!PlayerForms.hasForm(kind) || minecraft.level == null) {
                continue;
            }
            CompanionEntity preview = ModEntities.get(kind).create(minecraft.level);
            if (preview == null) {
                continue;
            }
            String values = specifics.get(kind.name());
            if (values != null) {
                var parsed = CustomModels.parseValues(values);
                if (preview.girlSpecificValid(parsed)) {
                    preview.applyGirlSpecific(parsed);
                }
            }
            this.choices.add(preview);
            this.kinds.add(kind);
        }
        if (minecraft.player != null) {
            this.choices.add(minecraft.player);
            this.kinds.add(null);
        }
    }

    public static void open(Map<String, String> specifics) {
        Minecraft.getInstance().setScreen(new FormPickerScreen(specifics));
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.index = (this.index + 1) % this.choices.size())
                .bounds(this.width / 2 + 30, this.height / 2 - 10, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("<"),
                        b -> this.index = Math.floorMod(this.index - 1, this.choices.size()))
                .bounds(this.width / 2 - 50, this.height / 2 - 10, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("pick"), b -> this.pick())
                .bounds(this.width / 2 - 30, this.height / 2 + 30, 60, 20).build());
    }

    private void pick() {
        if (this.choices.isEmpty()) {
            return;
        }
        CompanionKind kind = this.kinds.get(this.index);
        PacketDistributor.sendToServer(new PlayerFormNetwork.Choose(kind == null ? "" : kind.name()));
        var player = Minecraft.getInstance().player;
        if (player != null && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = player.getAbilities().instabuild;
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Original b5.drawScreen draws no background: the world stays sharp and undimmed. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.choices.isEmpty()) {
            return;
        }
        spin += 60.0F / Math.max(0.1F, Minecraft.getInstance().getFps());
        LivingEntity entity = this.choices.get(this.index);
        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRot = entity.yHeadRot;
        float headRotO = entity.yHeadRotO;
        entity.yBodyRot = 180.0F - spin;
        entity.setYRot(180.0F - spin);
        entity.setXRot(0.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        PlayerFormClient.drawingPickerPlayer = true;
        try {
            InventoryScreen.renderEntityInInventory(graphics, this.width / 2.0F, this.height / 2.0F + 20.0F, 30.0F,
                    new Vector3f(), new Quaternionf().rotateZ((float) Math.PI), null, entity);
        } finally {
            PlayerFormClient.drawingPickerPlayer = false;
        }
        entity.yBodyRot = bodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRot = headRot;
        entity.yHeadRotO = headRotO;
    }
}
