package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.PyrocinicalEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Original {@code e}: the Pyrocinical is a full-bright 2x2 sprite that always faces the player's view. */
public class PyrocinicalRenderer extends EntityRenderer<PyrocinicalEntity> {
    private static final ResourceLocation STANDING = tex("standing");
    private static final ResourceLocation PRAISING = tex("praising");
    private static final ResourceLocation WALKING_1 = tex("walking1");
    private static final ResourceLocation WALKING_2 = tex("walking2");
    private static final int FAT_FRAMES = 30;

    private ResourceLocation lastTexture;
    private long lastPraise;

    public PyrocinicalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "textures/entity/pyrocinical/" + path + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(PyrocinicalEntity entity) {
        return STANDING;
    }

    @Override
    public void render(PyrocinicalEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 here = new Vec3(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()));
        Vec3 you = new Vec3(Mth.lerp(partial, mc.player.xo, mc.player.getX()), Mth.lerp(partial, mc.player.yo, mc.player.getY()),
                Mth.lerp(partial, mc.player.zo, mc.player.getZ()));
        Vec3 offset = here.subtract(you);
        double near = Math.abs(offset.x) + Math.abs(offset.y) + Math.abs(offset.z);

        int hit = entity.hitTick();
        int fatAge = hit == -1 ? 0 : (int) Mth.clamp((float) (entity.tickCount - hit), 1.0F, (float) FAT_FRAMES);
        ResourceLocation texture;
        if (hit != -1) {
            texture = ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "textures/entity/pyrocinical/fat/" + fatAge + ".png");
        } else if (near < 3.0D) {
            texture = PRAISING;
        } else if (Math.abs(entity.xo - entity.getX()) + Math.abs(entity.yo - entity.getY())
                + Math.abs(entity.zo - entity.getZ()) == 0.0D) {
            texture = STANDING;
        } else {
            texture = Math.sin(mc.player.tickCount * 0.75F) > 0.0D ? WALKING_1 : WALKING_2;
        }
        double bob = (texture == WALKING_1 || texture == WALKING_2) ? Math.sin(mc.player.tickCount * 0.75F) * 0.1D : 0.0D;

        float alpha = 1.0F;
        if (hit != -1) {
            int age = entity.tickCount - hit;
            alpha = age > 120 ? 0.0F : 1.0F - (Mth.clamp((float) age, 90.0F, 120.0F) - 90.0F + partial) / 30.0F;
        }
        float grow = hit == -1 ? 0.0F : (fatAge == FAT_FRAMES ? 1.0F : (fatAge + partial) / 30.0F);
        float scale = 1.4F + grow;

        long now = System.currentTimeMillis();
        if (this.lastTexture != PRAISING && texture == PRAISING && now > this.lastPraise + 60000L) {
            com.trolmastercard.sexmod.registry.ModSounds.find("misc.pyro.pyro0").ifPresent(sound ->
                    mc.player.playSound(sound.get(), 1.0F, 1.0F));
            this.lastPraise = now;
        }
        this.lastTexture = texture;

        pose.pushPose();
        pose.translate(0.0D, bob, 0.0D);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - mc.gameRenderer.getMainCamera().getYRot()));
        pose.scale(scale, scale, scale);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        int full = 15728880;
        vertex(consumer, last, matrix, -1.0F, 0.0F, 0.0F, 1.0F, alpha, full);
        vertex(consumer, last, matrix, 1.0F, 0.0F, 1.0F, 1.0F, alpha, full);
        vertex(consumer, last, matrix, 1.0F, 2.0F, 1.0F, 0.0F, alpha, full);
        vertex(consumer, last, matrix, -1.0F, 2.0F, 0.0F, 0.0F, alpha, full);
        pose.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Matrix4f matrix,
                               float x, float y, float u, float v, float alpha, int light) {
        consumer.addVertex(matrix, x, y, 0.0F)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
