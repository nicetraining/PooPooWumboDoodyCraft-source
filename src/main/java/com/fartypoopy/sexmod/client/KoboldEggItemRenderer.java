package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fartypoopy.sexmod.item.KoboldEggItem;
import com.fartypoopy.sexmod.tribe.KoboldColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Original hn + f6: the item is the egg's geo model; the shell is tinted (223,206,155) and the spots take the tribe
 * colour stored on the stack (the original read it from the item damage).
 */
public class KoboldEggItemRenderer extends GeoItemRenderer<KoboldEggItem> {
    public KoboldEggItemRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(KoboldEggItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "geo/kobold/koboldegg.geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(KoboldEggItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "textures/entity/kobold/koboldegg.png");
            }

            @Override
            public ResourceLocation getAnimationResource(KoboldEggItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "animations/kobold/egg.animation.json");
            }
        });
    }

    @Override
    public void renderRecursively(PoseStack poseStack, KoboldEggItem animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, int colour) {
        int alpha = colour & 0xFF000000;
        if (bone.getName().equals("shell")) {
            colour = alpha | 0xDFCE9B;
        } else if (bone.getName().equals("colorSpots")) {
            CustomData data = this.currentItemStack == null ? null : this.currentItemStack.get(DataComponents.CUSTOM_DATA);
            colour = alpha | KoboldColor.byName(data == null ? "" : data.copyTag().getString("tribeColor")).mainRgb();
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }
}
