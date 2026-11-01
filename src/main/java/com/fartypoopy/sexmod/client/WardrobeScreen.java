package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.fartypoopy.sexmod.custommodel.CustomModelConfig;
import com.fartypoopy.sexmod.custommodel.CustomModels;
import com.fartypoopy.sexmod.custommodel.WearType;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.network.CustomModelNetwork;
import com.fartypoopy.sexmod.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Original a + gq: the NPC editor wand's wardrobe. Left: one row per per-girl option (Kobold / Goblin appearance; arrows
 * or a 0..100 slider) and per wear slot (arrows through the custom models that fit her, with a turning preview, name
 * and author), plus a row to add / remove extra custom-bone slots. Right: a turning preview of the girl wearing the
 * selection; done sends the model code. In singleplayer three more buttons: reload the models, open their folder,
 * the original tutorial video.
 */
public final class WardrobeScreen extends Screen {
    public static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath("sexmod", "textures/gui/clothing_icons.png");
    private static final int ROW = 30;
    private static final int LEFT = 5;
    private static final int TEXT = 0x3A224F;
    private static final String WIDTH_SAMPLE = "MMMMMMMMMM";
    private static final String TUTORIAL = "http://fapcraft.org/assets/video/tutorial/girl_wand.mp4";

    /** Original a.b: the preview angle (degrees), shared by the girl and the list previews. */
    static float angle;
    private static int spinDirection = 1;
    private float momentum;
    private boolean dragging;
    private double lastDragX;
    private float dragThisFrame;

    private final UUID girlId;
    private final CompanionEntity preview;
    private final List<Row> rows = new ArrayList<>();
    private int girlSpecificCount;
    private float scroll;
    private Row sliding;

    /** Original a.m entries: a slot (or per-girl option), its choices and the chosen index. */
    private static final class Row {
        final WearType type;
        final List<String> options;
        int index;
        final int girlSpecificIndex;

        Row(WearType type, List<String> options, int index, int girlSpecificIndex) {
            this.type = type;
            this.options = options;
            this.index = index;
            this.girlSpecificIndex = girlSpecificIndex;
        }

        String chosen() {
            return this.options.get(this.index);
        }
    }

    private WardrobeScreen(CompanionEntity girl) {
        super(Component.empty());
        this.girlId = girl.getUUID();
        CompanionEntity copy = ModEntities.get(girl.kind()).create(girl.level());
        this.preview = copy == null ? girl : copy;
        if (copy != null) {
            copy.applyGirlSpecific(girl.girlSpecificValues());
            copy.setModelCode(girl.modelCode());
        }
        this.buildRows(girl);
    }

    /** Original a.a(em): opens the wardrobe, unless a multiplayer server is not whitelisted. */
    public static void open(CompanionEntity girl) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WardrobeScreen || minecraft.player == null) {
            return;
        }
        if (CustomModelClient.serverHost() != null && !CustomModelClient.serverWhitelisted()) {
            minecraft.player.displayClientMessage(Component.literal("You have to whitelist the server to use its custom models. ")
                    .append(Component.literal("/whitelistserver").withStyle(ChatFormatting.YELLOW)), true);
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new WardrobeScreen(girl)));
    }

    /** Original a.e() and the constructor: the rows, then the girl's current choices. */
    private void buildRows(CompanionEntity girl) {
        List<Integer> bounds = girl.girlSpecificBounds();
        List<Integer> values = girl.girlSpecificValues();
        for (int i = 0; i < bounds.size(); i++) {
            List<String> options = new ArrayList<>();
            for (int j = 0; j < bounds.get(i); j++) {
                options.add("");
            }
            int value = i < values.size() ? Mth.clamp(values.get(i), 0, bounds.get(i) - 1) : 0;
            this.rows.add(new Row(WearType.GIRL_SPECIFIC, options, value, i));
        }
        this.girlSpecificCount = bounds.size();
        Map<WearType, List<String>> fitting = CustomModels.modelsFor(girl.kind());
        for (WearType type : WearType.values()) {
            if (type != WearType.GIRL_SPECIFIC) {
                List<String> options = new ArrayList<>();
                options.add("cross");
                options.addAll(fitting.get(type));
                this.rows.add(new Row(type, options, 0, -1));
            }
        }
        int customBones = 0;
        for (String name : girl.wornModels()) {
            CustomModelConfig config = CustomModels.get(name);
            WearType type = config == null ? WearType.HEAD : config.wearType;
            Row row = null;
            if (type == WearType.CUSTOM_BONE && ++customBones > 1) {
                row = this.newCustomBoneRow();
                this.rows.add(row);
            } else {
                for (Row each : this.rows) {
                    if (each.type == type) {
                        row = each;
                    }
                }
            }
            if (row != null) {
                row.index = Math.max(0, row.options.indexOf(name));
            }
        }
        this.sortRows();
    }

    private Row newCustomBoneRow() {
        List<String> options = new ArrayList<>();
        options.add("cross");
        options.addAll(CustomModels.modelsFor(this.preview.kind()).get(WearType.CUSTOM_BONE));
        return new Row(WearType.CUSTOM_BONE, options, 0, -1);
    }

    private void sortRows() {
        this.rows.sort(Comparator.comparingInt(row -> row.type.ordinal()));
    }

    private int customBoneRows() {
        return (int) this.rows.stream().filter(row -> row.type == WearType.CUSTOM_BONE).count();
    }

    /** Original a.b(): the chosen models. */
    private Set<String> chosenModels() {
        Set<String> names = new LinkedHashSet<>();
        for (Row row : this.rows) {
            if (row.type != WearType.GIRL_SPECIFIC && row.index != 0) {
                names.add(row.chosen());
            }
        }
        return names;
    }

    private List<Integer> chosenValues() {
        List<Integer> values = new ArrayList<>();
        for (Row row : this.rows) {
            if (row.type == WearType.GIRL_SPECIFIC) {
                values.add(row.index);
            }
        }
        return values;
    }

    private void updatePreview() {
        if (this.preview.getUUID().equals(this.girlId)) {
            return; // no copy could be made; never change the real girl client-side
        }
        this.preview.applyGirlSpecific(this.chosenValues());
        this.preview.setModelCode(CustomModels.toCode(this.chosenModels()));
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Original a.c(): send the selection and close. */
    private void done() {
        this.click();
        PacketDistributor.sendToServer(new CustomModelNetwork.SetModelCode(
                CustomModels.toCode(this.chosenModels()), this.girlId, this.chosenValues()));
        this.onClose();
    }

    private int pctX(float percent) {
        return Math.round(this.width * percent / 100.0F);
    }

    private int pctY(float percent) {
        return Math.round(this.height * percent / 100.0F);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private int listTop() {
        int total = (this.rows.size() + 1) * ROW;
        return total > this.height ? 0 : (this.height - total) / 2;
    }

    private int maxScroll() {
        return Math.max(0, (this.rows.size() + 1) * ROW - this.height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        this.preview.tickCount++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.spin();
        int girlX = this.pctX(76.0F);
        int girlY = this.pctY(89.0F);
        int buttonX = girlX - this.pctX(15.0F);
        graphics.blit(ICONS, buttonX, girlY - 20, 100, inside(mouseX, mouseY, buttonX, girlY - 20, 20, 20) ? 40 : 20, 20, 20);
        if (CustomModelClient.serverHost() == null) {
            graphics.blit(ICONS, buttonX, girlY - 40, 120, inside(mouseX, mouseY, buttonX, girlY - 40, 20, 20) ? 40 : 20, 20, 20);
            graphics.blit(ICONS, buttonX, girlY - 60, 20, inside(mouseX, mouseY, buttonX, girlY - 60, 20, 20) ? 170 : 150, 20, 20);
            graphics.blit(ICONS, buttonX, girlY - 80, 0, inside(mouseX, mouseY, buttonX, girlY - 80, 20, 20) ? 170 : 150, 20, 20);
        }
        this.renderGirl(graphics, girlX, girlY);
        this.renderList(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
    }

    /** Original a.a(): auto-turn, or keep turning after a drag and slow down. */
    private void spin() {
        float fps = Math.max(0.1F, Minecraft.getInstance().getFps());
        if (this.dragging) {
            angle += this.dragThisFrame;
            this.dragThisFrame = 0.0F;
            return;
        }
        if (this.momentum == 0.0F) {
            angle += spinDirection * 10.0F / fps;
            return;
        }
        angle += this.momentum / fps;
        this.momentum *= 1.0F - 0.25F / fps;
        if (Math.abs(this.momentum) <= 10.0F) {
            spinDirection = this.momentum > 0.0F ? 1 : -1;
            this.momentum = 0.0F;
        }
    }

    private void renderGirl(GuiGraphics graphics, int x, int y) {
        CompanionEntity girl = this.preview;
        float bodyRot = girl.yBodyRot;
        float yRot = girl.getYRot();
        float xRot = girl.getXRot();
        float headRot = girl.yHeadRot;
        float headRotO = girl.yHeadRotO;
        girl.yBodyRot = 180.0F - angle;
        girl.setYRot(180.0F - angle);
        girl.setXRot(0.0F);
        girl.yHeadRot = girl.getYRot();
        girl.yHeadRotO = girl.getYRot();
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(0.25F));
        InventoryScreen.renderEntityInInventory(graphics, x, y, 90.0F, new Vector3f(), pose, null, girl);
        girl.yBodyRot = bodyRot;
        girl.setYRot(yRot);
        girl.setXRot(xRot);
        girl.yHeadRot = headRot;
        girl.yHeadRotO = headRotO;
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = this.listTop() - (int) this.scroll;
        int y = top;
        for (Row row : this.rows) {
            if (y > -ROW && y < this.height) {
                this.renderRow(graphics, row, y, mouseX, mouseY);
            }
            y += ROW;
        }
        if (y > -ROW && y < this.height) {
            // original gq$a(boolean): add a custom-bone slot, or remove the last one when there is more than one
            boolean removable = this.customBoneRows() > 1;
            graphics.blit(ICONS, 30, y + 5, 40, inside(mouseX, mouseY, 30, y + 5, 20, 20) ? 40 : 20, 20, 20);
            graphics.blit(ICONS, 70, y + 5, removable ? 60 : 80, removable && inside(mouseX, mouseY, 70, y + 5, 20, 20) ? 40 : 20, 20, 20);
        }
    }

    /** Original gq$a.a / b: one row. */
    private void renderRow(GuiGraphics graphics, Row row, int y, int mouseX, int mouseY) {
        if (row.type == WearType.GIRL_SPECIFIC) {
            boolean slider = row.options.size() == 101;
            if (slider) {
                graphics.blit(ICONS, LEFT, y, 0, 60, 119, 30);
            } else {
                graphics.blit(ICONS, LEFT, y, 0, 90, 95, 30);
            }
            int[] icon = girlSpecificIcon(this.preview.kind(), row.girlSpecificIndex);
            graphics.blit(ICONS, LEFT + 10, y + 5, icon[0], icon[1], 20, 20);
            if (slider) {
                this.renderSlider(graphics, row, LEFT + 35, y + 5, mouseX, mouseY);
            } else {
                this.renderArrows(graphics, LEFT + 35, y + 5, mouseX, mouseY);
            }
            return;
        }
        graphics.blit(ICONS, LEFT, y, 0, 60, row.index == 0 ? 119 : 256, 30);
        graphics.blit(ICONS, LEFT + 10, y + 5, row.type.iconX, 0, 20, 20);
        this.renderArrows(graphics, LEFT + 35, y + 5, mouseX, mouseY);
        int x = LEFT + 35 + 60; // original gq$a.c returns the arrow x + 60
        String name = row.chosen();
        CustomModelConfig config = row.index == 0 ? null : CustomModels.get(name);
        float size = config == null ? 1.0F : config.guiSizeFactor;
        int offset = config == null ? 0 : (int) -config.guiVerticalPositioning;
        this.renderModelIcon(graphics, row.index == 0 ? null : name, x, y + 15 + (row.index == 0 ? 0 : 6) + offset, 30.0F * size);
        if (row.index == 0) {
            return;
        }
        x += 30;
        int column = this.font.width(WIDTH_SAMPLE);
        String author = config == null ? "" : config.author;
        graphics.drawString(this.font, shorten(name), x, y + 15, TEXT, false);
        graphics.drawString(this.font, shorten(author), x + column, y + 15, TEXT, false);
        if (inside(mouseX, mouseY, x, y + 15, column, this.font.lineHeight)) {
            graphics.renderTooltip(this.font, Component.literal(name), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x + column, y + 15, column, this.font.lineHeight)) {
            graphics.renderTooltip(this.font, Component.literal(author), mouseX, mouseY);
        }
    }

    private static String shorten(String text) {
        return text.length() > WIDTH_SAMPLE.length() ? text.substring(0, WIDTH_SAMPLE.length() - 3) + "..." : text;
    }

    /** Original gq$a.c: the left and right arrows. */
    private void renderArrows(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.blit(ICONS, x, y, 0, inside(mouseX, mouseY, x, y, 20, 20) ? 40 : 20, 20, 20);
        graphics.blit(ICONS, x + 20, y, 20, inside(mouseX, mouseY, x + 20, y, 20, 20) ? 40 : 20, 20, 20);
    }

    /** Original gq$a.a(int, int, int, int, int): track (140,20,79,20) and a 4 px handle. */
    private void renderSlider(GuiGraphics graphics, Row row, int x, int y, int mouseX, int mouseY) {
        graphics.blit(ICONS, x, y, 140, 20, 79, 20);
        int from = x + 4;
        int to = x + 71 - 4;
        int handle = (int) Mth.lerp(row.index / 100.0F, from, to);
        boolean hover = this.sliding == row || inside(mouseX, mouseY, handle, y, 4, 20);
        graphics.blit(ICONS, handle, y, hover ? 223 : 219, 20, 4, 20);
    }

    /** Original ff.g / e3.g: the icon of each per-girl option. */
    private static int[] girlSpecificIcon(CompanionKind kind, int index) {
        int[][] kobold = {{160, 0}, {180, 0}, {200, 0}, {220, 0}, {227, 20}, {140, 40}, {160, 40}, {180, 40}, {227, 40}, {0, 130}, {20, 130}};
        int[][] goblin = {{40, 130}, {60, 130}, {80, 130}, {100, 130}, {120, 130}, {140, 130}, {160, 130}, {180, 130}, {200, 0}, {200, 130}};
        int[][] table = kind == CompanionKind.KOBOLD ? kobold : kind == CompanionKind.GOBLIN ? goblin : new int[0][];
        return index >= 0 && index < table.length ? table[index] : new int[] {0, 0};
    }

    /** Original gq$a.a: the slot's model (or the cross) turning with the preview. */
    private void renderModelIcon(GuiGraphics graphics, String name, int x, int y, float scale) {
        BakedGeoModel model = name == null ? CustomModelRender.crossModel() : CustomModelClient.model(name);
        ResourceLocation texture = name == null ? CustomModelRender.CROSS_TEXTURE : CustomModelClient.texture(name);
        if (model == null || texture == null) {
            return;
        }
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 150.0F);
        poseStack.scale(scale, -scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - angle));
        Lighting.setupForEntityInInventory();
        CustomModelRender.drawModel(poseStack, model, graphics.bufferSource().getBuffer(RenderType.entityCutoutNoCull(texture)),
                LightTexture.FULL_BRIGHT);
        graphics.flush();
        Lighting.setupFor3DItems();
        poseStack.popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int girlX = this.pctX(76.0F);
        int girlY = this.pctY(89.0F);
        int buttonX = girlX - this.pctX(15.0F);
        if (inside(mouseX, mouseY, buttonX, girlY - 20, 20, 20)) {
            this.done();
            return true;
        }
        if (CustomModelClient.serverHost() == null) {
            if (inside(mouseX, mouseY, buttonX, girlY - 40, 20, 20)) {
                this.click();
                this.onClose();
                CustomModels.loadFailed = CustomModels.reload(true) != 0;
                if (!CustomModels.loadFailed && this.minecraft.level != null
                        && findGirl(this.girlId) instanceof CompanionEntity girl) {
                    open(girl);
                }
                return true;
            }
            if (inside(mouseX, mouseY, buttonX, girlY - 60, 20, 20)) {
                Util.getPlatform().openFile(new File(CustomModels.folder()));
                return true;
            }
            if (inside(mouseX, mouseY, buttonX, girlY - 80, 20, 20)) {
                ConfirmLinkScreen.confirmLinkNow(this, TUTORIAL);
                return true;
            }
        }
        if (mouseX < this.width / 2.0D) {
            this.listClicked(mouseX, mouseY);
        } else {
            this.dragging = true;
            this.lastDragX = mouseX;
        }
        return true;
    }

    private static net.minecraft.world.entity.Entity findGirl(UUID id) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        for (var entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    /** Original gq.a(int, int, int) and gq$a.a(int, int, int, int): which row, then arrows / slider / add / remove. */
    private void listClicked(double mouseX, double mouseY) {
        double local = mouseY - this.listTop() + this.scroll;
        int index = (int) Math.floor(local / ROW);
        int inRow = (int) (local - index * ROW);
        if (index < 0 || inRow < 5 || inRow > 25) {
            return;
        }
        if (index == this.rows.size()) {
            if (mouseX > 30 && mouseX < 50) {
                this.click();
                this.rows.add(this.newCustomBoneRow());
                this.sortRows();
                this.scroll = this.maxScroll();
            } else if (mouseX > 70 && mouseX < 90 && this.customBoneRows() > 1) {
                this.click();
                for (int i = this.rows.size() - 1; i >= 0; i--) {
                    if (this.rows.get(i).type == WearType.CUSTOM_BONE) {
                        this.rows.remove(i);
                        break;
                    }
                }
                this.updatePreview();
            }
            return;
        }
        if (index > this.rows.size()) {
            return;
        }
        Row row = this.rows.get(index);
        if (row.type == WearType.GIRL_SPECIFIC && row.options.size() == 101) {
            this.sliding = row;
            this.slideTo(row, mouseX);
            return;
        }
        if (mouseX > 40 && mouseX < 60) {
            this.cycle(row, false);
        } else if (mouseX > 60 && mouseX < 80) {
            this.cycle(row, true);
        }
    }

    /** Original a.a(gw, boolean, int): step through the choices, wrapping around. */
    private void cycle(Row row, boolean next) {
        this.click();
        int size = row.options.size();
        row.index = Math.floorMod(row.index + (next ? 1 : -1), size);
        this.updatePreview();
    }

    private void slideTo(Row row, double mouseX) {
        int from = LEFT + 35 + 4;
        int to = LEFT + 35 + 71 - 4;
        row.index = Math.round(Mth.clamp((float) (mouseX - from) / (to - from), 0.0F, 1.0F) * 100.0F);
        this.updatePreview();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.sliding != null) {
            this.slideTo(this.sliding, mouseX);
            return true;
        }
        if (this.dragging) {
            this.dragThisFrame += (float) (mouseX - this.lastDragX);
            this.lastDragX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging) {
            this.momentum = this.dragThisFrame * Math.max(1.0F, Minecraft.getInstance().getFps());
            this.dragThisFrame = 0.0F;
        }
        this.dragging = false;
        this.sliding = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < this.width / 2.0D && scrollY != 0.0D) {
            this.scroll = Mth.clamp(this.scroll + (scrollY > 0 ? -1 : 1) * ROW / 2.0F, 0.0F, this.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
