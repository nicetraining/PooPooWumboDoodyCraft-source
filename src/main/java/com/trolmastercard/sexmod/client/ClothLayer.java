package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Original dh.a(em, BufferBuilder, Tessellator, float) + af.a: Manglelie's chest cloth is three thin four sided
 * prisms drawn between pairs of bones (clothBoob{L,R}conStart to End, clothBoobMidconStart to End).
 */
public final class ClothLayer extends GeoRenderLayer<CompanionEntity> {
    /** Original f7 (half width x, half height y, half depth z) at the start and the end of a strip. */
    private record Section(float x, float y, float z) {
    }

    private static final Section SIDE_START = new Section(0.05F, 0.04F, 0.0F);
    private static final Section SIDE_END = new Section(0.0F, 0.065F, 0.0F);
    private static final Section MID = new Section(0.0F, 0.03F, 0.03F);
    private static final int[] COLOUR = {115, 108, 188};

    public ClothLayer(GeoRenderer<CompanionEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, CompanionEntity entity, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (entity.kind() != CompanionKind.MANGLELIE) {
            return;
        }
        VertexConsumer consumer = null;
        Matrix4f pose = poseStack.last().pose();
        String[][] strips = {
                {"clothBoobLconStart", "clothBoobLconEnd"},
                {"clothBoobRconStart", "clothBoobRconEnd"},
                {"clothBoobMidconStart", "clothBoobMidconEnd"}};
        for (int index = 0; index < strips.length; index++) {
            GeoBone start = model.getBone(strips[index][0]).orElse(null);
            GeoBone end = model.getBone(strips[index][1]).orElse(null);
            if (start == null || end == null || start.isHidden() || end.isHidden()) {
                continue;
            }
            if (consumer == null) {
                consumer = bufferSource.getBuffer(RenderType.debugQuads());
            }
            Section a = index == 2 ? MID : SIDE_START;
            Section b = index == 2 ? MID : SIDE_END;
            prism(consumer, pose, position(start), position(end), a, b);
        }
    }

    static Vector3f position(GeoBone bone) {
        return bone.getModelSpaceMatrix().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
    }

    /** Original af.b / af.a: eight corners (four at each end) joined by six quads. */
    static void prism(VertexConsumer consumer, Matrix4f pose, Vector3f start, Vector3f end, Section a, Section b) {
        Vector3f[] c = new Vector3f[8];
        boolean flat = a.x() == 0.0F && b.x() == 0.0F; // both ends have no width: the section lies in the Y-Z plane
        if (flat) {
            c[0] = new Vector3f(0.0F, a.y(), a.z());
            c[1] = new Vector3f(0.0F, -a.y(), a.z());
            c[2] = new Vector3f(0.0F, -a.y(), -a.z());
            c[3] = new Vector3f(0.0F, a.y(), -a.z());
            c[4] = new Vector3f(0.0F, b.y(), b.z());
            c[5] = new Vector3f(0.0F, -b.y(), b.z());
            c[6] = new Vector3f(0.0F, -b.y(), -b.z());
            c[7] = new Vector3f(0.0F, b.y(), -b.z());
        } else {
            c[0] = new Vector3f(a.x(), a.y(), 0.0F);
            c[1] = new Vector3f(-a.x(), a.y(), 0.0F);
            c[2] = new Vector3f(-a.x(), -a.y(), 0.0F);
            c[3] = new Vector3f(a.x(), -a.y(), 0.0F);
            c[4] = new Vector3f(b.x(), b.y(), 0.0F);
            c[5] = new Vector3f(-b.x(), b.y(), 0.0F);
            c[6] = new Vector3f(-b.x(), -b.y(), 0.0F);
            c[7] = new Vector3f(b.x(), -b.y(), 0.0F);
        }
        for (int i = 0; i < 4; i++) {
            c[i].add(start);
        }
        for (int i = 4; i < 8; i++) {
            c[i].add(end);
        }
        int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {1, 2, 6, 5}, {3, 7, 4, 0}, {1, 0, 4, 5}, {2, 3, 7, 6}};
        for (int[] face : faces) {
            for (int corner : face) {
                consumer.addVertex(pose, c[corner].x, c[corner].y, c[corner].z)
                        .setColor(COLOUR[0], COLOUR[1], COLOUR[2], 255);
            }
        }
    }
}
