package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.entity.KoboldEggEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class KoboldEggRenderer extends GeoEntityRenderer<KoboldEggEntity> {
    public KoboldEggRenderer(EntityRendererProvider.Context context) {
        super(context, new KoboldEggModel());
        this.shadowRadius = 0.2F;
    }

    /** Original cp.renderRecursively: the shell is tinted (223,206,155), the spots take the tribe colour. */
    @Override
    public void renderRecursively(PoseStack poseStack, KoboldEggEntity egg, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        int alpha = colour & 0xFF000000;
        if (bone.getName().equals("shell")) {
            colour = alpha | 0xDFCE9B;
        } else if (bone.getName().equals("colorSpots")) {
            colour = alpha | com.fartypoopy.sexmod.tribe.KoboldColor.byName(egg.eggColor()).mainRgb();
        }
        super.renderRecursively(poseStack, egg, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, colour);
    }
}
