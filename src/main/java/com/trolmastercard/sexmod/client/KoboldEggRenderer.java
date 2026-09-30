package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.entity.KoboldEggEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class KoboldEggRenderer extends GeoEntityRenderer<KoboldEggEntity> {
    public KoboldEggRenderer(EntityRendererProvider.Context context) {
        super(context, new KoboldEggModel());
        this.shadowRadius = 0.2F;
    }
}
