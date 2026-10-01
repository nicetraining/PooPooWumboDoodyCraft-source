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
        float t = (Mth.sin((ball.tickCount + partialTick) * 0.2F) + 1.0F) * 0.5F;
        int red = (int) (255 * t);
        int green = (int) (255 * (1.0F - t));
        int blue = (int) (251 - 15 * t);
        int colour = 0xFF000000 | red << 16 | green << 8 | blue;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.25F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        vertex(consumer, pose, colour, -0.25F, -0.25F, 0, 1);
        vertex(consumer, pose, colour, 0.25F, -0.25F, 1, 1);
        vertex(consumer, pose, colour, 0.25F, 0.25F, 1, 0);
        vertex(consumer, pose, colour, -0.25F, 0.25F, 0, 0);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int colour, float x, float y, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0F).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
