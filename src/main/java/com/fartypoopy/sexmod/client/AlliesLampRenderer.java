package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fartypoopy.sexmod.item.AlliesLampItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Original f0 + a9: the lamp is the geo model; its two arm bones are drawn with the player's own skin and only while the
 * lamp is being rubbed in first person (the original pasted the skin over the lamp texture, this draws the arms with it).
 */
public class AlliesLampRenderer extends GeoItemRenderer<AlliesLampItem> {
    public AlliesLampRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(AlliesLampItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "geo/allie/lamp.geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(AlliesLampItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "textures/entity/allie/lamp.png");
            }

            @Override
            public ResourceLocation getAnimationResource(AlliesLampItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "animations/allie/lamp.animation.json");
            }
        });
    }

    @Override
    public void renderRecursively(PoseStack poseStack, AlliesLampItem animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        if (name.equals("leftArm") || name.equals("rightArm")) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean show = minecraft.player != null
                    && minecraft.player.getPersistentData().getBoolean(AlliesLampItem.IN_USE_KEY)
                    && minecraft.options.getCameraType().isFirstPerson();
            if (!show) {
                return;
            }
            renderType = RenderType.entityCutoutNoCull(minecraft.player.getSkin().texture());
            buffer = bufferSource.getBuffer(renderType);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }
}
