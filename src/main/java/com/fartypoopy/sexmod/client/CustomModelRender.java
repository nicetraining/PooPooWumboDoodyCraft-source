package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fartypoopy.sexmod.custommodel.CustomModelConfig;
import com.fartypoopy.sexmod.custommodel.CustomModels;
import com.fartypoopy.sexmod.custommodel.Lighting;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.network.CustomModelNetwork;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Original b (custom model renderer) and d_.a(Boolean, boolean) (bones hidden by worn models): a worn custom model hangs
 * from its girl bone with that bone's pose (rotation point kept), shifted back by the custom model's own root pivot.
 */
public final class CustomModelRender {
    public static final ResourceLocation CROSS_MODEL = ResourceLocation.fromNamespaceAndPath("sexmod", "geo/cross.geo.json");
    public static final ResourceLocation CROSS_TEXTURE = ResourceLocation.fromNamespaceAndPath("sexmod", "textures/cross.png");
    /** Singleplayer clean-up already asked for, per entity, so the request is not repeated every frame. */
    private static final Map<Integer, String> STRIPPED = new HashMap<>();

    private CustomModelRender() {
    }

    /** One worn model captured during the girl's bone pass. */
    public record Attachment(Matrix4f pose, Matrix3f normal, String model) {
    }

    /** Original d_.a(Boolean, boolean) and b.render's early outs: which worn models draw, which girl bones hide. */
    public static void prepare(CompanionEntity girl, Set<String> hiddenBones, Map<String, List<String>> byBone) {
        hiddenBones.clear();
        byBone.clear();
        if (CustomModels.loadFailed) {
            return;
        }
        Set<String> worn = girl.wornModels();
        if (worn.isEmpty()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String name : worn) {
            CustomModelConfig config = CustomModels.get(name);
            if (config == null) {
                missing.add(name);
                continue;
            }
            if (!config.enableWhenNude && girl.isNude()) {
                continue;
            }
            hiddenBones.addAll(config.bonesToHide);
            if (CustomModelClient.model(name) != null && CustomModelClient.texture(name) != null) {
                byBone.computeIfAbsent(config.attachBone(), bone -> new ArrayList<>()).add(name);
            }
        }
        if (!missing.isEmpty() && CustomModelClient.serverHost() == null) {
            // original b.d(cy): in singleplayer a model that no longer exists is taken off the girl
            Set<String> kept = new java.util.LinkedHashSet<>(worn);
            missing.forEach(kept::remove);
            String code = CustomModels.toCode(kept);
            if (!code.equals(STRIPPED.get(girl.getId()))) {
                STRIPPED.put(girl.getId(), code);
                PacketDistributor.sendToServer(new CustomModelNetwork.SetModelCode(code, girl.getUUID(), List.of()));
            }
        }
    }

    /** Capture the pose a model on this bone hangs from (original em.a(String, false): parents, then the bone without moving back). */
    public static void capture(PoseStack poseStack, GeoBone bone, List<String> models, List<Attachment> into) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        RenderUtil.translateToPivotPoint(poseStack, bone);
        for (String model : models) {
            into.add(new Attachment(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), model));
        }
        poseStack.popPose();
    }

    /** Original b.doRender for a worn model. */
    public static void draw(Attachment attachment, MultiBufferSource buffers, int packedLight) {
        draw(attachment, buffers, packedLight, null);
    }

    /** As above; with which_lighting=SEXMOD the quads get the girl's light tint (original b with gx.a and cj.a). */
    public static void draw(Attachment attachment, MultiBufferSource buffers, int packedLight, org.joml.Vector3f girlLight) {
        CustomModelConfig config = CustomModels.get(attachment.model());
        BakedGeoModel model = CustomModelClient.model(attachment.model());
        ResourceLocation texture = CustomModelClient.texture(attachment.model());
        if (config == null || model == null || texture == null) {
            return;
        }
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(attachment.pose());
        poseStack.last().normal().set(attachment.normal());
        if (girlLight != null && config.lighting == Lighting.SEXMOD) {
            tintToModel = new Matrix3f(attachment.normal()).invert();
            tintLight = girlLight;
        }
        try {
            drawModel(poseStack, model, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light(config, packedLight));
        } finally {
            tintToModel = null;
            tintLight = null;
        }
    }

    /**
     * Original b.a(br.b, cy, float): DEFAULT keeps the normal lighting; SEXMOD and NONE switched GL lighting off and never
     * went darker than light level 10.
     */
    private static int light(CustomModelConfig config, int packedLight) {
        if (config.lighting == Lighting.DEFAULT) {
            return packedLight;
        }
        return LightTexture.pack(Math.max(10, LightTexture.block(packedLight)), Math.max(10, LightTexture.sky(packedLight)));
    }

    private static Matrix3f tintToModel;
    private static Vector3f tintLight;

    /** Every root bone is moved back by its own pivot, then drawn with its children. */
    public static void drawModel(PoseStack poseStack, BakedGeoModel model, VertexConsumer buffer, int packedLight) {
        for (GeoBone root : model.topLevelBones()) {
            poseStack.pushPose();
            poseStack.translate(-root.getPivotX() / 16.0F, -root.getPivotY() / 16.0F, -root.getPivotZ() / 16.0F);
            drawBone(poseStack, root, buffer, packedLight);
            poseStack.popPose();
        }
    }

    private static void drawBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        if (!bone.isHidden()) {
            for (GeoCube cube : bone.getCubes()) {
                poseStack.pushPose();
                drawCube(poseStack, cube, buffer, packedLight);
                poseStack.popPose();
            }
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                drawBone(poseStack, child, buffer, packedLight);
            }
        }
        poseStack.popPose();
    }

    private static void drawCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight) {
        RenderUtil.translateToPivotPoint(poseStack, cube);
        RenderUtil.rotateMatrixAroundCube(poseStack, cube);
        RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
        Matrix3f normalMatrix = poseStack.last().normal();
        Matrix4f poseMatrix = new Matrix4f(poseStack.last().pose());
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            Vector3f normal = normalMatrix.transform(new Vector3f(quad.normal()));
            RenderUtil.fixInvertedFlatCube(cube, normal);
            int colour = tintToModel == null ? 0xFFFFFFFF : LightTint.tint(0xFFFFFFFF, normal, tintToModel, tintLight);
            for (GeoVertex vertex : quad.vertices()) {
                Vector3f position = vertex.position();
                Vector4f at = poseMatrix.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0F));
                buffer.addVertex(at.x(), at.y(), at.z(), colour, vertex.texU(), vertex.texV(),
                        OverlayTexture.NO_OVERLAY, packedLight, normal.x(), normal.y(), normal.z());
            }
        }
    }

    /** The cross shown for an empty wardrobe slot (original o with cy.f). */
    public static BakedGeoModel crossModel() {
        return GeckoLibCache.getBakedModels().get(CROSS_MODEL);
    }
}
