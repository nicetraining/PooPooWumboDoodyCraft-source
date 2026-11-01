package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fartypoopy.sexmod.item.GalathCoinItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Original av + as: the coin is the geo model with the 4 s summon animation; its pentagram is drawn unlit-bright and
 * changes colour with the state of the coin: pink and pulsing when a summon is possible, grey while Galath is out, and
 * blended between the two during a summon (activation) or a dismissal (deactivation).
 */
public class GalathCoinRenderer extends GeoItemRenderer<GalathCoinItem> {
    private static final float[] PINK = {0.84705883F, 0.11764706F, 0.35686275F}; // original av.e
    private static final float[] GREY = {0.44705883F, 0.44705883F, 0.44705883F}; // original av.f

    public GalathCoinRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(GalathCoinItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "geo/galath/galath_coin.geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(GalathCoinItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "textures/items/galath_coin/galath_coin.png");
            }

            @Override
            public ResourceLocation getAnimationResource(GalathCoinItem animatable) {
                return ResourceLocation.fromNamespaceAndPath("sexmod", "animations/galath/galath_coin.animation.json");
            }
        });
    }

    @Override
    public void renderRecursively(PoseStack poseStack, GalathCoinItem animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, int colour) {
        if (bone.getName().equals("pentagram")) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            float[] rgb = PINK;
            float light = pulse(player, partialTick);
            if (player != null && this.currentItemStack != null
                    && (player.getMainHandItem() == this.currentItemStack || player.getOffhandItem() == this.currentItemStack)) {
                long now = player.level().getGameTime();
                long activated = player.getPersistentData().getLong(GalathCoinItem.activationKey());
                long deactivated = player.getPersistentData().getLong(GalathCoinItem.deactivationKey());
                float time = now + partialTick;
                if (activated != 0L) {
                    float t = (time - activated) * 50.0F; // original ms
                    light = t < 1000.0F ? 240.0F : t <= 3000.0F ? Mth.lerp((t - 1000.0F) / 2000.0F, 240.0F, 120.0F) : 120.0F;
                    rgb = t < 1000.0F ? PINK : t <= 3000.0F ? mix(PINK, GREY, (t - 1000.0F) / 2000.0F) : GREY;
                } else if (deactivated != 0L) {
                    float t = (time - deactivated) * 50.0F;
                    light = t < 1000.0F ? 120.0F : t <= 3000.0F ? Mth.lerp((t - 1000.0F) / 2000.0F, 120.0F, 240.0F) : 240.0F;
                    rgb = t < 1000.0F ? GREY : t <= 3000.0F ? mix(GREY, PINK, (t - 1000.0F) / 2000.0F) : PINK;
                } else if (GalathCoinItem.clientHasGalath(player.level(), player)) {
                    light = 120.0F;
                    rgb = GREY;
                }
            }
            colour = 0xFF000000 | ((int) (rgb[0] * 255.0F) << 16) | ((int) (rgb[1] * 255.0F) << 8) | (int) (rgb[2] * 255.0F);
            int level = Mth.clamp((int) (light / 16.0F), 0, 15);
            packedLight = LightTexture.pack(level, level);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    /** Original av.b(float): brightness 60 sin(t * 0.05) + 180. */
    private static float pulse(Player player, float partialTick) {
        return (float) (60.0D * Math.sin(((player == null ? 0 : player.tickCount) + partialTick) * 0.05F) + 180.0D);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }
}
