package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.KoboldEggEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class KoboldEggModel extends GeoModel<KoboldEggEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "geo/kobold/koboldegg.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "textures/entity/kobold/koboldegg.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            FapCraft.MOD_ID, "animations/kobold/egg.animation.json");

    @Override
    public ResourceLocation getModelResource(KoboldEggEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(KoboldEggEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(KoboldEggEntity entity) {
        return ANIMATION;
    }
}
