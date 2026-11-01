package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Original Jenny payment: the paying player's right arm is held up towards her while the item is shown
 * (keyframes drawItems .. removeItems). Vanilla resets the arm pose every frame, so the real arm is skipped in
 * the player model and a raised copy is drawn by a render layer.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class PaymentArm {
    private static final float RAISE_TICKS = 8.0F;
    /** player -> {game time of the last raise, game time of the last lower} */
    private static final Map<UUID, long[]> STATE = new ConcurrentHashMap<>();

    private PaymentArm() {
    }

    public static void raise(UUID player) {
        STATE.put(player, new long[] {now(), -1L});
    }

    public static void lower(UUID player) {
        long[] state = STATE.get(player);
        if (state != null && state[1] < 0L) {
            state[1] = now();
        }
    }

    private static long now() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    /** 0 = arm down, 1 = fully raised. */
    static float amount(UUID player, float partial) {
        long[] state = STATE.get(player);
        if (state == null) {
            return 0.0F;
        }
        float time = now() + partial;
        if (state[1] < 0L && time - state[0] > 200.0F) {
            state[1] = state[0] + 200L; // the scene was cut short: let the arm down again
        }
        float up = Mth.clamp((time - state[0]) / RAISE_TICKS, 0.0F, 1.0F);
        if (state[1] < 0L) {
            return up;
        }
        float down = 1.0F - Mth.clamp((time - state[1]) / RAISE_TICKS, 0.0F, 1.0F);
        if (down <= 0.0F) {
            STATE.remove(player);
        }
        return Math.min(up, down);
    }

    @SubscribeEvent
    public static void hideRealArm(RenderPlayerEvent.Pre event) {
        boolean raised = amount(event.getEntity().getUUID(), event.getPartialTick()) > 0.0F;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.rightArm.skipDraw = raised;
        model.rightSleeve.skipDraw = raised;
    }

    @SubscribeEvent
    public static void restore(RenderPlayerEvent.Post event) {
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.rightArm.skipDraw = false;
        model.rightSleeve.skipDraw = false;
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new Layer(renderer));
            }
        }
    }

    private static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            float amount = amount(player.getUUID(), partialTick);
            if (amount <= 0.0F || player.isInvisible()) {
                return;
            }
            PlayerModel<AbstractClientPlayer> model = this.getParentModel();
            var arm = model.rightArm;
            float x = arm.xRot;
            float y = arm.yRot;
            float z = arm.zRot;
            // held out in front of her, a little above the elbow line
            arm.xRot = Mth.lerp(amount, x, -1.35F + model.head.xRot * 0.5F);
            arm.yRot = Mth.lerp(amount, y, -0.12F);
            arm.zRot = Mth.lerp(amount, z, 0.0F);
            model.rightSleeve.copyFrom(arm);
            var consumer = buffer.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
            arm.skipDraw = false;
            model.rightSleeve.skipDraw = false;
            arm.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
            model.rightSleeve.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
            arm.skipDraw = true;
            model.rightSleeve.skipDraw = true;
            arm.xRot = x;
            arm.yRot = y;
            arm.zRot = z;
        }
    }
}
