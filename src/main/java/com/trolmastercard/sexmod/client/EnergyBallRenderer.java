package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.EnergyBallEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original c4 renderer: a 0.5 x 0.5 camera facing quad that pulses between cyan (0,255,251) and magenta (255,0,236). */
public final class EnergyBallRenderer extends EntityRenderer<EnergyBallEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/entity/galath/energy_ball.png");
    private static final RenderType TYPE = RenderType.entityTranslucent(TEXTURE);

    public EnergyBallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EnergyBallEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(EnergyBallEntity ball, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        // original ag: t = 0.5 * sin((worldTime + partialTick) * 0.5) + 0.5; a second quad (0.75 scale, +0.075 up, reversed colours)
        float t = 0.5F * Mth.sin((ball.level().getGameTime() + partialTick) * 0.5F) + 0.5F;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.25F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        quad(consumer, poseStack.last(), mix(t), 0.0F);
        poseStack.scale(0.75F, 0.75F, 0.75F);
        poseStack.translate(0.0F, 0.075F, 0.0F);
        quad(consumer, poseStack.last(), mix(1.0F - t), 0.001F);
        poseStack.popPose();
    }

    private static int mix(float t) {
        int red = (int) (255 * t);
        int green = (int) (255 * (1.0F - t));
        int blue = (int) (251 - 15 * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int colour, float z) {
        vertex(consumer, pose, colour, -0.25F, -0.25F, z, 0, 1);
        vertex(consumer, pose, colour, 0.25F, -0.25F, z, 1, 1);
        vertex(consumer, pose, colour, 0.25F, 0.25F, z, 1, 0);
        vertex(consumer, pose, colour, -0.25F, 0.25F, z, 0, 0);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int colour, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
