package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.LunaHookEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The vanilla fish hook look (sprite + sagging line), with the line tied to Luna instead of a player. */
public final class LunaHookRenderer extends EntityRenderer<LunaHookEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE);

    public LunaHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LunaHookEntity hook, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (!(hook.getOwner() instanceof CompanionEntity luna)) {
            return;
        }
        poseStack.pushPose();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer sprite = buffers.getBuffer(RENDER_TYPE);
        vertex(sprite, pose, light, 0.0F, 0, 0, 1);
        vertex(sprite, pose, light, 1.0F, 0, 1, 1);
        vertex(sprite, pose, light, 1.0F, 1, 1, 0);
        vertex(sprite, pose, light, 0.0F, 1, 0, 0);
        poseStack.popPose();

        float bodyYaw = Mth.lerp(partialTick, luna.yBodyRotO, luna.yBodyRot) * ((float) Math.PI / 180.0F);
        // original fi: the line leaves the rod hand 0.8 forward and 0.35 to the side, at eye height - 0.45
        Vec3 hand = new Vec3(
                Mth.lerp(partialTick, luna.xo, luna.getX()) - Mth.cos(bodyYaw) * 0.35D - Mth.sin(bodyYaw) * 0.8D,
                Mth.lerp(partialTick, luna.yo, luna.getY()) + luna.getEyeHeight() - (luna.isCrouching() ? 0.1875D : 0.45D),
                Mth.lerp(partialTick, luna.zo, luna.getZ()) - Mth.sin(bodyYaw) * 0.35D + Mth.cos(bodyYaw) * 0.8D);
        double hx = Mth.lerp(partialTick, hook.xo, hook.getX());
        double hy = Mth.lerp(partialTick, hook.yo, hook.getY()) + 0.25D;
        double hz = Mth.lerp(partialTick, hook.zo, hook.getZ());
        float dx = (float) (hand.x - hx);
        float dy = (float) (hand.y - hy);
        float dz = (float) (hand.z - hz);
        VertexConsumer lines = buffers.getBuffer(RenderType.lineStrip());
        PoseStack.Pose linePose = poseStack.last();
        for (int i = 0; i <= 16; i++) {
            stringVertex(dx, dy, dz, lines, linePose, fraction(i, 16), fraction(i + 1, 16));
        }
        poseStack.popPose();
        super.render(hook, yaw, partialTick, poseStack, buffers, light);
    }

    private static float fraction(int value, int max) {
        return (float) value / (float) max;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
                .setColor(-1)
                .setUv((float) u, (float) v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void stringVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose, float fraction, float next) {
        float px = x * fraction;
        float py = y * (fraction * fraction + fraction) * 0.5F + 0.25F;
        float pz = z * fraction;
        float nx = x * next - px;
        float ny = y * (next * next + next) * 0.5F + 0.25F - py;
        float nz = z * next - pz;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        consumer.addVertex(pose, px, py, pz).setColor(-16777216).setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(LunaHookEntity hook) {
        return TEXTURE;
    }
}
