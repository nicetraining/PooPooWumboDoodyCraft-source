package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.CompanionScene;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Original dh.a(em, BufferBuilder, Tessellator): Manglelie's skirt is not made of cubes. Forty rings of three bones
 * (skirt_N_0..2) are joined by quads, alternating two shades of purple, and drawn flat shaded.
 */
public final class SkirtLayer extends GeoRenderLayer<CompanionEntity> {
    private static final int RINGS = 40;
    private static final int[] EVEN = {79, 74, 188};
    private static final int[] ODD = {63, 59, 150};

    public SkirtLayer(GeoRenderer<CompanionEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, CompanionEntity entity, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (entity.kind() != CompanionKind.MANGLELIE) {
            return;
        }
        CompanionScene scene = entity.currentScene();
        if (scene == CompanionScene.MANGLELIE_THREESOME && entity.currentScenePhase() <= 2) {
            return; // original fp.THREESOME_SLOW / FAST / CUM: no skirt
        }
        Vector3f[][] points = new Vector3f[RINGS][3];
        for (int ring = 0; ring < RINGS; ring++) {
            for (int part = 0; part < 3; part++) {
                GeoBone bone = model.getBone("skirt_" + ring + "_" + part).orElse(null);
                if (bone == null || bone.isHidden()) {
                    return;
                }
                points[ring][part] = bone.getModelSpaceMatrix().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
            }
        }
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        Matrix4f pose = poseStack.last().pose();
        for (int i = 0; i < RINGS; i++) {
            int j = (i + 1) % RINGS;
            int[] colour = i % 2 == 0 ? EVEN : ODD;
            quad(consumer, pose, colour, points[i][0], points[i][1], points[j][1], points[j][0]);
            quad(consumer, pose, colour, points[i][1], points[j][1], points[j][2], points[i][2]);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, int[] colour, Vector3f a, Vector3f b, Vector3f c,
                             Vector3f d) {
        for (Vector3f point : new Vector3f[] {a, b, c, d}) {
            consumer.addVertex(pose, point.x, point.y, point.z).setColor(colour[0], colour[1], colour[2], 255);
        }
    }
}
