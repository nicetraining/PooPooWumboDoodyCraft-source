package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class CompanionRenderer extends GeoEntityRenderer<CompanionEntity> {
    private static final java.util.Map<Integer, GeoBone> BOY_CAMS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The animated {@code boyCam} bone as of the last frame this entity was drawn, or null. */
    public static GeoBone boyCam(int entityId) {
        return BOY_CAMS.get(entityId);
    }

    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new CompanionModel());
        this.shadowRadius = 0.2F;
    }

    @Override
    public void preRender(
            PoseStack poseStack,
            CompanionEntity entity,
            BakedGeoModel model,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int renderColor
    ) {
        super.preRender(
                poseStack,
                entity,
                model,
                bufferSource,
                buffer,
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                renderColor
        );

        float baseScale = entity.kind() == CompanionKind.KOBOLD ? 0.75F : 1.0F;
        float scale = baseScale * entity.appearanceScale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void renderRecursively(
            PoseStack poseStack,
            CompanionEntity entity,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int renderColor
    ) {
        if (bone.getName().equals("boyCam")) {
            bone.setTrackingMatrices(true);
            BOY_CAMS.put(entity.getId(), bone);
        }
        if (bone.getName().equalsIgnoreCase("steve")
                && entity.currentScene() != null
                && entity.currentScene().hasPartner()) {
            PlayerSkin skin = partnerSkin(entity);
            renderType = RenderType.entityTranslucent(skin.texture());
            buffer = bufferSource.getBuffer(renderType);
        }

        super.renderRecursively(
                poseStack,
                entity,
                bone,
                renderType,
                bufferSource,
                buffer,
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                renderColor
        );
    }

    static PlayerSkin partnerSkin(CompanionEntity entity) {
        var partnerId = entity.scenePartnerId().orElse(entity.getUUID());
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo playerInfo = connection == null ? null : connection.getPlayerInfo(partnerId);
        return playerInfo == null ? DefaultPlayerSkin.get(partnerId) : playerInfo.getSkin();
    }
}
