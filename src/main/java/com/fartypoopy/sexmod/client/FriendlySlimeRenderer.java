package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;

/**
 * Original bp + c_: the slime girl's baby is drawn with a 64x32 model that is the vanilla slime core plus four tail
 * boxes behind the body, inside the vanilla outer gel layer (a4).
 */
public class FriendlySlimeRenderer extends MobRenderer<Slime, SlimeModel<Slime>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("sexmod", "friendly_slime"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");

    public FriendlySlimeRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel<>(context.bakeLayer(LAYER)), 0.25F);
        this.addLayer(new SlimeOuterLayer<>(this, context.getModelSet()));
    }

    /** Original c_ constructor, box for box. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).mirror(true)
                .addBox(-3.0F, 17.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
        root.addOrReplaceChild("eye_right", CubeListBuilder.create().texOffs(32, 0).mirror(true)
                .addBox(1.3F, 18.0F, -3.5F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("eye_left", CubeListBuilder.create().texOffs(32, 4).mirror(true)
                .addBox(-3.3F, 18.0F, -3.5F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(32, 8).mirror(true)
                .addBox(-1.0F, 21.0F, -3.5F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(-0.5F, 0.0F, 0.1F));
        tail.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(10, 11)
                .addBox(-2.5F, 0.0F, 0.0F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.0F, 20.7406F, 4.0504F, 1.0908F, 0.0F, 0.0F));
        tail.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(10, 11)
                .addBox(-3.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, 19.9214F, 3.4768F, 0.6109F, 0.0F, 0.0F));
        tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(10, 11)
                .addBox(-4.0F, 0.0F, 0.075F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, 19.0074F, 3.0643F, 0.3491F, 0.0F, 0.0F));
        tail.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(10, 11)
                .addBox(-3.0F, -1.0F, -0.5F, 7.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 17.925F, 3.5F, 0.1309F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void render(Slime entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        this.shadowRadius = 0.25F * entity.getSize();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(Slime entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.999F, 0.999F, 0.999F);
        float size = entity.getSize();
        float squish = Mth.lerp(partialTick, entity.oSquish, entity.squish) / (size * 0.5F + 1.0F);
        float inverse = 1.0F / (squish + 1.0F);
        poseStack.scale(inverse * size, 1.0F / inverse * size, inverse * size);
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        return TEXTURE;
    }
}
