package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trolmastercard.sexmod.FapCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Original gm: a textured cross-hair on every marked block, seen through walls. Beds blue, chests green, tasks red. */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class TribeMarkerRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "textures/mark.png");
    private static final RenderType TYPE = RenderType.textSeeThrough(TEXTURE);

    private TribeMarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || TribeClient.marked().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // original gm: only drawn while the Dragon Staff is in either hand
        if (!minecraft.player.getMainHandItem().is(com.trolmastercard.sexmod.registry.ModItems.DRAGON_STAFF.get())
                && !minecraft.player.getOffhandItem().is(com.trolmastercard.sexmod.registry.ModItems.DRAGON_STAFF.get())) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(TYPE);
        for (BlockPos pos : TribeClient.marked()) {
            BlockState state = minecraft.level.getBlockState(pos);
            int r = 255;
            int g = 0;
            int b = 0;
            if (state.getBlock() instanceof BedBlock) {
                r = 0;
                b = 255;
            } else if (state.getBlock() instanceof ChestBlock) {
                r = 0;
                g = 255;
            }
            pose.pushPose();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            Matrix4f m = pose.last().pose();
            float e = 0.002F;
            // the six faces, slightly outside the block
            quad(buffer, m, -e, -e, -e, 1 + e, -e, -e, 1 + e, 1 + e, -e, -e, 1 + e, -e, r, g, b);
            quad(buffer, m, -e, -e, 1 + e, 1 + e, -e, 1 + e, 1 + e, 1 + e, 1 + e, -e, 1 + e, 1 + e, r, g, b);
            quad(buffer, m, -e, -e, -e, -e, -e, 1 + e, -e, 1 + e, 1 + e, -e, 1 + e, -e, r, g, b);
            quad(buffer, m, 1 + e, -e, -e, 1 + e, -e, 1 + e, 1 + e, 1 + e, 1 + e, 1 + e, 1 + e, -e, r, g, b);
            quad(buffer, m, -e, -e, -e, 1 + e, -e, -e, 1 + e, -e, 1 + e, -e, -e, 1 + e, r, g, b);
            quad(buffer, m, -e, 1 + e, -e, 1 + e, 1 + e, -e, 1 + e, 1 + e, 1 + e, -e, 1 + e, 1 + e, r, g, b);
            pose.popPose();
        }
        buffers.endBatch(TYPE);
    }

    private static void quad(VertexConsumer buffer, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int r, int g, int b) {
        int a = 255;
        int light = 0xF000F0;
        buffer.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setUv(0, 1).setLight(light);
        buffer.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setUv(1, 1).setLight(light);
        buffer.addVertex(m, x3, y3, z3).setColor(r, g, b, a).setUv(1, 0).setLight(light);
        buffer.addVertex(m, x4, y4, z4).setColor(r, g, b, a).setUv(0, 0).setLight(light);
    }
}
