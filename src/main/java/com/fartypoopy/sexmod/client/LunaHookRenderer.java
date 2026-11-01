package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.LunaHookEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The vanilla fish hook look (sprite + sagging line), with the line tied to Luna instead of a player. */
public final class LunaHookRenderer extends EntityRenderer<LunaHookEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE);

    public LunaHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * Original fi. With a catch on the line the hook and the catch (drawn at twice the hook's size) fly to Luna's chest
     * (0.875 above her feet) in about half a second, and are hidden once there; the line is only drawn without a catch.
     */
    @Override
    public void render(LunaHookEntity hook, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (!(hook.getOwner() instanceof CompanionEntity luna)) {
            return;
        }
        ItemStack caught = luna.fishCaught();
        if (caught.isEmpty()) {
            // deviation: the original only reset this after the check below, so after her first catch every later hook
            // of that Luna stayed invisible until she was reloaded
            luna.hookReel = 0.0F;
        }
        if (luna.hookReel == 1.0F) {
            return;
        }
        poseStack.pushPose();
        if (!caught.isEmpty()) {
            float fps = Minecraft.getInstance().getFps();
            if (fps == 0.0F) {
                fps = 0.1F;
            }
            luna.hookReel = Math.min(1.0F, luna.hookReel + 60.0F / fps * 0.01666F * 2.0F);
            Vec3 hookAt = hook.getPosition(partialTick);
            Vec3 chest = luna.getPosition(partialTick).add(0.0D, 0.875D, 0.0D);
            Vec3 move = chest.subtract(hookAt).scale(luna.hookReel);
            poseStack.translate(move.x, move.y, move.z);
        }
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        if (!caught.isEmpty()) {
            poseStack.pushPose();
            poseStack.scale(2.0F, 2.0F, 2.0F);
            poseStack.translate(0.0F, -0.2F, 0.0F);
            Minecraft.getInstance().getItemRenderer().renderStatic(luna, caught, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
                    poseStack, buffers, luna.level(), light, OverlayTexture.NO_OVERLAY, luna.getId());
            poseStack.popPose();
        }
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer sprite = buffers.getBuffer(RENDER_TYPE);
        vertex(sprite, pose, light, 0.0F, 0, 0, 1);
        vertex(sprite, pose, light, 1.0F, 0, 1, 1);
        vertex(sprite, pose, light, 1.0F, 1, 1, 0);
        vertex(sprite, pose, light, 0.0F, 1, 0, 0);
        poseStack.popPose();

        if (caught.isEmpty()) {
            // original fi: the rod hand is 0.8 ahead and 0.35 to the side at eye height - 0.45 (another 0.1875 lower when
            // sneaking); the side flips because her main hand never holds a fishing rod item
            int arm = luna.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
            if (!(luna.getMainHandItem().getItem() instanceof FishingRodItem)) {
                arm = -arm;
            }
            double side = arm * 0.35D;
            float bodyYaw = Mth.lerp(partialTick, luna.yBodyRotO, luna.yBodyRot) * ((float) Math.PI / 180.0F);
            double sin = Mth.sin(bodyYaw);
            double cos = Mth.cos(bodyYaw);
            Vec3 at = luna.getPosition(partialTick);
            double handX = at.x - cos * side - sin * 0.8D;
            double handY = at.y + luna.getEyeHeight() - 0.45D;
            double handZ = at.z - sin * side + cos * 0.8D;
            // the hook end of the line, tied off-centre by her facing (original b / c constants)
            double facing = luna.getYRot();
            Vec3 h = hook.getPosition(partialTick);
            double endX = h.x - Math.sin(Math.toRadians(facing + 90.0D)) * 0.1896224320030116D - Math.sin(Math.toRadians(facing)) * 0.08742380916962415D;
            double endY = h.y + 0.25D - 0.5D;
            double endZ = h.z + Math.cos(Math.toRadians(facing + 90.0D)) * 0.1896224320030116D + Math.cos(Math.toRadians(facing)) * 0.08742380916962415D;
            float dx = (float) (handX - endX);
            float dy = (float) ((float) (handY - endY) + (luna.isShiftKeyDown() ? -0.1875D : 0.0D));
            float dz = (float) (handZ - endZ);
            VertexConsumer lines = buffers.getBuffer(RenderType.lineStrip());
            PoseStack.Pose linePose = poseStack.last();
            for (int i = 0; i <= 16; i++) {
                stringVertex(dx, dy, dz, lines, linePose, fraction(i, 16), fraction(i + 1, 16));
            }
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
