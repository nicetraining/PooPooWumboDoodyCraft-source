package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.network.TribeCommandPayload;
import com.trolmastercard.sexmod.tribe.TribeCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Original j: the Dragon Staff's four-quadrant menu. Hover a quadrant to grow it; the quadrant that is largest when the
 * menu closes (release the mouse button) fires. Top left: follow mode; bottom right: staff compass style; bottom left:
 * register the bed/chest under the crosshair; top right: mark the tree to fell or the tunnel to dig.
 */
public final class StaffRadialScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "textures/gui/command.png");

    private float open;     // m: 0..1 pop-in over 5 ticks
    private float follow;   // k: top left
    private float compass;  // n: bottom right
    private float register; // a: bottom left
    private float work;     // i: top right
    private final BlockPos target;
    private final Direction face;
    private final BlockState state;
    private BlockPos mineStart;

    public StaffRadialScreen() {
        super(Component.empty());
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = BlockPos.ZERO;
        Direction dir = Direction.NORTH;
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getBlockPos();
            dir = hit.getDirection().getOpposite();
        }
        this.target = pos;
        this.face = dir;
        this.state = minecraft.level.getBlockState(pos);
        this.mineStart = this.state.is(BlockTags.LOGS) ? null
                : TribeCommands.mineStart(minecraft.level, pos, dir, minecraft.player.getY());
    }

    private static double easeOutBack(double x) {
        double c1 = 1.70158D;
        double c3 = c1 + 1.0D;
        return 1.0D + c3 * Math.pow(x - 1.0D, 3.0D) + c1 * Math.pow(x - 1.0D, 2.0D);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        this.open = Math.min(1.0F, this.open + delta / 5.0F);
        float offset = (float) (100.0D * (1.0D - easeOutBack(this.open)));
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.follow = Mth.clamp(this.follow + (mouseX < cx && mouseY < cy ? delta : -delta), 0.0F, 1.0F);
        this.compass = Mth.clamp(this.compass + (mouseX > cx && mouseY > cy ? delta : -delta), 0.0F, 1.0F);
        this.register = Mth.clamp(this.register + (mouseX < cx && mouseY > cy ? delta : -delta), 0.0F, 1.0F);
        this.work = Mth.clamp(this.work + (mouseX > cx && mouseY < cy ? delta : -delta), 0.0F, 1.0F);

        var pose = graphics.pose();
        // top left: follow mode
        this.quadrant(graphics, cx, cy, this.follow, -62.0F + offset - this.follow * 15.0F, -62.0F + offset - this.follow * 15.0F, 64, 64,
                TribeClient.follow() ? 128 : -1, 64);
        // bottom right: compass style
        this.quadrant(graphics, cx, cy, this.compass, -2.0F - offset + this.compass * 15.0F, -2.0F - offset + this.compass * 15.0F, 192, 64,
                TribeClient.compassList() ? 128 : -1, 64);
        boolean chest = this.state.getBlock() instanceof ChestBlock;
        boolean bed = this.state.getBlock() instanceof BedBlock;
        if (chest || bed) {
            this.quadrant(graphics, cx, cy, this.register, -62.0F + offset - this.register * 15.0F, -2.0F - offset + this.register * 15.0F,
                    chest ? 192 : 0, chest ? 0 : 64, TribeClient.isMarked(this.target) ? 128 : -1, 64);
        }
        boolean log = this.state.is(BlockTags.LOGS);
        if (log || this.mineStart != null) {
            this.quadrant(graphics, cx, cy, this.work, -2.0F - offset + this.work * 15.0F, -62.0F + offset - this.work * 15.0F,
                    log ? 64 : 128, 0, TribeClient.isMarked(this.target) ? 128 : -1, 64);
        }
    }

    /** One quadrant: base tile, its icon and (when toggled on) the red overlay, scaled about the screen centre. */
    private void quadrant(GuiGraphics graphics, int cx, int cy, float growth, float x, float y, int iconU, int iconV, int overlayU, int overlayV) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.scale(1.0F + growth * 0.5F, 1.0F + growth * 0.5F, 1.0F);
        graphics.blit(TEXTURE, (int) x, (int) y, 0, 0, 64, 64, 256, 256);
        graphics.blit(TEXTURE, (int) x, (int) y, iconU, iconV, 64, 64, 256, 256);
        if (overlayU >= 0) {
            graphics.blit(TEXTURE, (int) x, (int) y, overlayU, overlayV, 64, 64, 256, 256);
        }
        pose.popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // no darkened background: the world stays visible
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.onClose();
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        float largest = Math.max(Math.max(this.follow, this.compass), Math.max(this.register, this.work));
        if (largest == 0.0F) {
            return;
        }
        if (this.register == largest) {
            boolean chest = this.state.getBlock() instanceof ChestBlock;
            boolean bed = this.state.getBlock() instanceof BedBlock;
            if (chest || bed) {
                PacketDistributor.sendToServer(TribeCommandPayload.of(TribeCommandPayload.REGISTER, this.target));
            }
        }
        if (this.follow == largest) {
            PacketDistributor.sendToServer(new TribeCommandPayload(TribeCommandPayload.FOLLOW, BlockPos.ZERO, TribeClient.follow() ? "0" : "1", ""));
        }
        if (this.compass == largest) {
            TribeClient.toggleCompass();
        }
        if (this.work == largest) {
            if (this.state.is(BlockTags.LOGS)) {
                int op = TribeClient.isMarked(this.target) ? TribeCommandPayload.CANCEL : TribeCommandPayload.FELL;
                PacketDistributor.sendToServer(TribeCommandPayload.of(op, this.target));
            } else if (this.mineStart != null) {
                int op = TribeClient.isMarked(this.target) ? TribeCommandPayload.CANCEL : TribeCommandPayload.MINE;
                PacketDistributor.sendToServer(new TribeCommandPayload(op, op == TribeCommandPayload.CANCEL ? this.target : this.mineStart,
                        this.face.getName(), ""));
            }
        }
        TribeClient.request();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
