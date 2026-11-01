package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.EnergyBallEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Original c4 renderer: a 0.5 x 0.5 camera facing quad that pulses between cyan (0,255,251) and magenta (255,0,236). */
public final class EnergyBallRenderer extends EntityRenderer<EnergyBallEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/entity/galath/energy_ball.png");
    // original ag: alpha test without blending, so the ball is solid with a hard edge
    private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);

    public EnergyBallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EnergyBallEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(EnergyBallEntity ball, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        draw(this.entityRenderDispatcher, ball.level().getGameTime(), partialTick, 1.0D, poseStack, buffers);
    }

    /**
     * Original ag: a camera-facing quad 0.5 wide and 0.5 high standing on the ball's position, and a second one (0.75 scale,
     * 0.075 up) on top. At full size the colours pulse between cyan and magenta (t = 0.5 sin((time + partial) * 0.5) + 0.5),
     * the second quad the other way round; smaller balls (Galath charging them) fade from clear white to cyan with size.
     */
    static void draw(net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher, long gameTime, float partialTick,
                     double size, PoseStack poseStack, MultiBufferSource buffers) {
        int front;
        int back;
        if (size == 1.0D) {
            double t = 0.5D * Math.sin((gameTime + partialTick) * 0.5D) + 0.5D;
            front = lerp(CYAN, MAGENTA, t);
            back = lerp(MAGENTA, CYAN, t);
        } else {
            front = lerp(CLEAR, CYAN, size);
            back = front;
        }
        poseStack.pushPose();
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale((float) size, (float) size, (float) size);
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        quad(consumer, poseStack.last(), front, 0.0F);
        poseStack.scale(0.75F, 0.75F, 0.75F);
        poseStack.translate(0.0F, 0.075F, 0.0F);
        quad(consumer, poseStack.last(), back, 0.001F);
        poseStack.popPose();
    }

    /** Original gv colours as {r, g, b, a}. */
    private static final int[] CYAN = {0, 255, 251, 255};
    private static final int[] MAGENTA = {255, 0, 236, 255};
    private static final int[] CLEAR = {255, 255, 255, 0};

    /** Original b6.a(gv, gv, t): each channel truncated to int. */
    private static int lerp(int[] from, int[] to, double t) {
        int[] c = new int[4];
        for (int i = 0; i < 4; i++) {
            c[i] = (int) (from[i] + (to[i] - from[i]) * t);
        }
        return c[3] << 24 | c[0] << 16 | c[1] << 8 | c[2];
    }

    /** The original's vertices: bottom edge at y 0 with v 0, top edge at y 0.5 with v 1. */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int colour, float z) {
        vertex(consumer, pose, colour, -0.25F, 0.0F, z, 0, 0);
        vertex(consumer, pose, colour, 0.25F, 0.0F, z, 1, 0);
        vertex(consumer, pose, colour, 0.25F, 0.5F, z, 1, 1);
        vertex(consumer, pose, colour, -0.25F, 0.5F, z, 0, 1);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int colour, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
