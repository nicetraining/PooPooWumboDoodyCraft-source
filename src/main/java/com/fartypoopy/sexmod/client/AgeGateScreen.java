package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.File;
import java.io.IOException;

/**
 * The first-launch age warning (after the original g2 / fr, with new text). "18 or more" writes
 * {@code sexmod/dontAskAgain} and the warning never shows again. "Not 18" writes {@code sexmod/locked} and closes the
 * game; from then on every start shows only a locked notice with a quit button, so none of the mod can be reached.
 * The original instead wrote a batch file that deleted the mod jar; deleting files outside its own folder is not
 * ported.
 */
public final class AgeGateScreen extends Screen {
    static final File DONT_ASK_AGAIN = new File("sexmod/dontAskAgain");
    static final File LOCKED = new File("sexmod/locked");

    private static final String WARNING = "WARNING!!!!!! this mod contains SEX !!!! thats right, SEX!!!! there's SEX in"
            + " this mod !!!! you SEX a minecraft character !!!!!!!!!! if you are not old enough (18 years !!!!) then"
            + " LEAVE !!!! i do not want people under 18 viewing SEX in MINECRAFT 1!!!! i do not want ANYONE viewing SEX"
            + " in MINECRAFT actually....";
    private static final String LOCKED_TEXT = "You said you are not 18 or more year. This mod stays locked: remove it"
            + " from your mods folder to play.";

    private final Screen next;
    private final boolean locked;

    AgeGateScreen(Screen next, boolean locked) {
        super(Component.literal("WARNING!!!!!!"));
        this.next = next;
        this.locked = locked;
    }

    @Override
    protected void init() {
        int width = Math.min(400, this.width - 40);
        MultiLineTextWidget text = new MultiLineTextWidget(Component.literal(this.locked ? LOCKED_TEXT : WARNING),
                this.font).setMaxWidth(width).setCentered(true);
        text.setPosition((this.width - text.getWidth()) / 2, this.height / 2 - 70);
        this.addRenderableWidget(text);

        int y = this.height / 2 + 50;
        if (this.locked) {
            this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"), b -> this.minecraft.stop())
                    .bounds(this.width / 2 - 75, y, 150, 20).build());
            return;
        }
        this.addRenderableWidget(Button.builder(Component.literal("helo I am 18 or more year"), b -> this.confirm())
                .bounds(this.width / 2 - 155, y, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("helo i am not 18 or more year"), b -> this.refuse())
                .bounds(this.width / 2 + 5, y, 150, 20).build());
    }

    private static void mark(File file) {
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException exception) {
            FapCraft.LOGGER.warn("Could not write {}", file, exception);
        }
    }

    private void confirm() {
        mark(DONT_ASK_AGAIN);
        this.minecraft.setScreen(this.next);
    }

    private void refuse() {
        mark(LOCKED);
        this.minecraft.stop();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 95, 0xFF5555);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
    static final class Gate {
        @SubscribeEvent
        static void onScreenOpening(ScreenEvent.Opening event) {
            if (!(event.getNewScreen() instanceof TitleScreen title)) {
                return;
            }
            if (LOCKED.exists()) {
                event.setNewScreen(new AgeGateScreen(title, true)); // every time: the title screen is never reached
            } else if (!DONT_ASK_AGAIN.exists()) {
                event.setNewScreen(new AgeGateScreen(title, false));
            }
        }
    }
}
