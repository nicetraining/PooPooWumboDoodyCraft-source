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

        float baseScale = entity.kind() == CompanionKind.KOBOLD ? 0.75F + entity.koboldSize() : 1.0F;
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
        if (bone.getName().equals("Head2")) {
            // The original skips the partner's head while the local player is that partner in first
            // person; the camera sits inside it otherwise. Children (the camera bone) still update.
            Minecraft minecraft = Minecraft.getInstance();
            boolean localPartner = minecraft.player != null
                    && entity.currentScene() != null
                    && entity.currentScene().hasPartner()
                    && entity.scenePartnerId().filter(minecraft.player.getUUID()::equals).isPresent();
            bone.setHidden(localPartner && minecraft.options.getCameraType().isFirstPerson());
            bone.setChildrenHidden(false); // setHidden also hides children, which would freeze the camera bone
        }
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

        if (entity.kind() == CompanionKind.KOBOLD) {
            renderColor = koboldTint(entity, bone.getName(), renderColor);
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

    private static final java.util.Set<String> BODY_BONES = java.util.Set.of("colorSpots", "neck", "head", "snout", "midSectionR",
            "midSectionL", "innerCheekLR", "innerCheekRR", "gayL", "gayR", "legR", "legL", "shinL", "toesL", "kneeL", "curvesL",
            "shinR", "toesR", "kneeR", "curvesR", "sideL", "sideR", "hip", "torsoL", "torsoR", "armR", "lowerArmR", "ellbowR",
            "armL", "lowerArmL", "ellbowL", "hornUL", "hornUR", "tail", "tail2", "tail3", "tail4", "tail5", "hornDL2", "hornDR2",
            "hornDR3M", "hornDL3M", "frecklesAL1", "frecklesAL2", "frecklesAR1", "frecklesAR2", "frecklesHL1", "frecklesHL2",
            "frecklesHR1", "frecklesHR2");
    private static final java.util.Set<String> SECONDARY_BONES = java.util.Set.of("boobR", "boobL", "frontNeck", "Rside", "Lside",
            "frontAndInside", "innerCheekLL", "innerCheekRL", "layer", "layer2", "down", "down2", "down3", "down4", "down5",
            "fuckhole", "hornDR3S", "hornDL3S", "assholeCoverUp", "assholeCoverUp2");

    /** Original de.a(String): body bones use the body colour, secondary bones its secondary, irises the eye colour. */
    private static int koboldTint(CompanionEntity entity, String bone, int renderColor) {
        int alpha = renderColor & 0xFF000000;
        if (BODY_BONES.contains(bone)) {
            return alpha | entity.bodyColor().mainRgb();
        }
        if (SECONDARY_BONES.contains(bone)) {
            return alpha | entity.bodyColor().secondaryRgb();
        }
        if (bone.equals("irisR") || bone.equals("irisL")) {
            return alpha | entity.eyeColor().mainRgb();
        }
        return alpha | 0xFFFFFF;
    }

    /** Original dj: tribe members show their coloured tribe name over their head. */
    @Override
    public boolean shouldShowName(CompanionEntity entity) {
        if (entity.kind() == CompanionKind.KOBOLD && !"null".equals(entity.tribeName())) {
            return Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity) < 64.0D * 64.0D;
        }
        return super.shouldShowName(entity);
    }

    @Override
    protected void renderNameTag(CompanionEntity entity, net.minecraft.network.chat.Component displayName, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (entity.kind() == CompanionKind.KOBOLD && !"null".equals(entity.tribeName())) {
            net.minecraft.network.chat.MutableComponent tag = entity.hasCustomName()
                    ? entity.getCustomName().copy().append(" ") : net.minecraft.network.chat.Component.literal("");
            displayName = tag.append(net.minecraft.network.chat.Component.literal("-" + entity.tribeName() + "-")
                    .withStyle(entity.bodyColor().chat()));
        }
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
    }

    static PlayerSkin partnerSkin(CompanionEntity entity) {
        var partnerId = entity.scenePartnerId().orElse(entity.getUUID());
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo playerInfo = connection == null ? null : connection.getPlayerInfo(partnerId);
        return playerInfo == null ? DefaultPlayerSkin.get(partnerId) : playerInfo.getSkin();
    }
}
