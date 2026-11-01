package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.item.DragonStaffItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Original fa (GeoItemRenderer of the dragon staff, geo {@code kobold/staff}) with e8: 1.5 blocks above the staff bone
 * floats an end crystal (glass, glass, cube layers) that pulses between 0.175 and 0.225 of its size and tumbles with the
 * holder's movement; around it one little wool block per tribe member in the member's colour. Orbit style: the blocks
 * circle the crystal on tilted axes; compass style (toggled in the staff menu): every block sits in the direction of its
 * member, seen from the holder's eyes.
 */
public class DragonStaffRenderer extends GeoItemRenderer<DragonStaffItem> {
    private static final ResourceLocation CRYSTAL = ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final float SIN_45 = (float) Math.sin(Math.PI / 4.0D);
    /** Original fa.n: per stack, the holder's movement summed up every time the staff is drawn (it never decays). */
    private static final Map<ItemStack, Vector3f> TUMBLE = new WeakHashMap<>();

    private ModelPart glass;
    private ModelPart cube;
    /** Original fa.b: the holder-independent clock (local player ticks + partial), frozen while the game is paused. */
    private double time;

    public DragonStaffRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(DragonStaffItem animatable) {
                return ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "geo/kobold/staff.geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(DragonStaffItem animatable) {
                return ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "textures/entity/kobold/staff.png");
            }

            @Override
            public ResourceLocation getAnimationResource(DragonStaffItem animatable) {
                return ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "animations/kobold/staff.animation.json");
            }
        });
    }

    private void bake() {
        if (this.glass == null) {
            ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.END_CRYSTAL);
            this.glass = root.getChild("glass");
            this.cube = root.getChild("cube");
        }
    }

    /** The player that carries this stack (original fa.a: the first one whose inventory holds this very stack). */
    private static Player holder(ItemStack stack) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        for (Player player : level.players()) {
            for (ItemStack other : player.getInventory().items) {
                if (other == stack) {
                    return player;
                }
            }
            for (ItemStack other : player.getInventory().offhand) {
                if (other == stack) {
                    return player;
                }
            }
        }
        return null;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, DragonStaffItem animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, int colour) {
        if (bone.getName().equals("staff") && !isReRender) {
            this.renderTop(poseStack, bone, bufferSource, packedLight, packedOverlay);
            buffer = bufferSource.getBuffer(renderType); // the crystal and wool batches may have ended the staff's
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    private void renderTop(PoseStack poseStack, GeoBone bone, MultiBufferSource buffer, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer items = minecraft.getItemRenderer();
        ItemStack stack = this.currentItemStack;
        this.bake();

        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        if (!minecraft.isPaused() && minecraft.player != null) {
            this.time = minecraft.player.tickCount + partial;
        }
        double time = this.time;
        Player holder = holder(stack);
        // original fa.a: the holder's last-tick movement turned into its own frame (x sideways, y forward)
        float sideways = 0.0F;
        float forward = 0.0F;
        float up = 0.0F;
        if (holder != null) {
            double dx = holder.getX() - holder.xOld;
            double dz = holder.getZ() - holder.zOld;
            double yaw = Math.toRadians(holder.getYRot());
            sideways = (float) (dx * Math.cos(yaw) + dz * Math.sin(yaw));
            forward = (float) (-dx * Math.sin(yaw) + dz * Math.cos(yaw));
            up = (float) (holder.getY() - holder.yOld);
        }

        poseStack.pushPose();
        poseStack.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F, bone.getPivotZ() / 16.0F);
        poseStack.translate(0.0D, 1.5D + 0.001D * Math.sin(0.005D * time) + 0.001D, 0.0D);
        double size = 0.175D + 0.025D * Math.sin(0.005D * time) + 0.025D;
        poseStack.scale((float) size, (float) size, (float) size);
        Vector3f tumble = stack == null ? new Vector3f() : TUMBLE.computeIfAbsent(stack, s -> new Vector3f());
        tumble.add(sideways, up, forward);
        poseStack.mulPose(Axis.XP.rotationDegrees(tumble.z * 10.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(tumble.x * 10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-tumble.y * 10.0F));
        poseStack.mulPose(Axis.of(new Vector3f(1.0F, 1.0F, 1.0F).normalize()).rotationDegrees((float) (time * 0.1D)));
        this.renderCrystal(poseStack, buffer, light);
        poseStack.popPose();

        if (holder != null) {
            this.renderTribe(holder, poseStack, buffer, light, overlay, time, partial, items);
        }
    }

    /** Original e8: outer glass at scale 2, inner glass and the cube at 0.875 each, all tilted 60 degrees about (1,0,1). */
    private void renderCrystal(PoseStack poseStack, MultiBufferSource buffer, int light) {
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(CRYSTAL));
        Quaternionf tilt = new Quaternionf().setAngleAxis((float) (Math.PI / 3.0D), SIN_45, 0.0F, SIN_45);
        poseStack.pushPose();
        poseStack.scale(2.0F, 2.0F, 2.0F);
        poseStack.mulPose(tilt);
        this.glass.render(poseStack, vertices, light, OverlayTexture.NO_OVERLAY);
        poseStack.scale(0.875F, 0.875F, 0.875F);
        poseStack.mulPose(tilt);
        this.glass.render(poseStack, vertices, light, OverlayTexture.NO_OVERLAY);
        poseStack.scale(0.875F, 0.875F, 0.875F);
        poseStack.mulPose(tilt);
        this.cube.render(poseStack, vertices, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private void renderTribe(Player holder, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, double time,
                             float partial, ItemRenderer items) {
        List<int[]> members = TribeClient.members();
        if (members.isEmpty()) {
            return;
        }
        double bob = 1.5D + 0.001D * Math.sin(0.005D * time) + 0.001D;
        Vec3 eye = holder.getEyePosition(partial);
        float yaw = Mth.rotLerp(partial, holder.yHeadRotO, holder.yHeadRot);
        float pitch = Mth.lerp(partial, holder.xRotO, holder.getXRot());
        float step = 1.0F / members.size();
        float along = 0.0F;
        for (int i = 0; i < members.size(); i++) {
            int[] member = members.get(i);
            ItemStack wool = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(
                    DyeColor.byId(Mth.clamp(member[3], 0, 15)).getName() + "_wool")));
            poseStack.pushPose();
            poseStack.translate(0.0D, bob, 0.0D);
            poseStack.scale(0.04F, 0.04F, 0.04F);
            if (TribeClient.compassList()) {
                // compass: original ck.a(eye - member, -pitch, yaw), squeezed onto a sphere and scaled 1.3
                Vec3 view = rotate(eye.subtract(member[0], member[1], member[2]), -pitch, yaw);
                double sum = Math.abs(view.x) + Math.abs(view.y) + Math.abs(view.z);
                if (sum > 1.0E-6D) {
                    double dx = sphere(-view.x / sum) * 1.3D;
                    double dy = sphere(-view.y / sum) * 1.3D;
                    double dz = sphere(view.z / sum) * 1.3D;
                    poseStack.translate(dx * 6.0D, dy * 6.0D, dz * 6.0D);
                }
            } else {
                along += step;
                float speed = (float) Mth.lerp((double) i / members.size(), 0.8D, 1.2D);
                poseStack.mulPose(Axis.of(new Vector3f(0.0F, 1.0F - along, along).normalize())
                        .rotationDegrees((float) (time * 8.0D * speed)));
                poseStack.translate(6.0D, 0.0D, 0.0D);
            }
            items.renderStatic(wool, ItemDisplayContext.NONE, light, overlay, poseStack, buffer, Minecraft.getInstance().level, 0);
            poseStack.popPose();
        }
    }

    /** Original ck.a(Vec3d, float, float): a turn about X by the first angle, then the (mirrored) yaw turn. */
    private static Vec3 rotate(Vec3 v, float pitchArg, float yawArg) {
        double a = Math.toRadians(pitchArg);
        double y1 = v.y * Math.cos(a) - v.z * Math.sin(a);
        double z1 = v.y * Math.sin(a) + v.z * Math.cos(a);
        double b = Math.toRadians(yawArg);
        double x2 = -Math.cos(b) * v.x - Math.sin(b) * z1;
        double z2 = -Math.sin(b) * v.x + Math.cos(b) * z1;
        return new Vec3(x2, y1, z2);
    }

    /** Original fa.a(double): d * sqrt(1 - d^2 / 2). */
    private static double sphere(double d) {
        return d * Math.sqrt(1.0D - d * d / 2.0D);
    }
}
