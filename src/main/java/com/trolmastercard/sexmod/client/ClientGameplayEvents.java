package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.UUID;

@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientGameplayEvents {
    private static LocalPlayer cameraPlayer;
    private static Vec3 savedPlayerPosition;
    private static Vec3 savedPreviousPosition;
    private static Vec3 sceneCameraEye;
    private static Vec3 sceneCameraTarget;

    private ClientGameplayEvents() {
    }

    @SubscribeEvent
    public static void prepareFirstPersonSceneCamera(RenderFrameEvent.Pre event) {
        restorePlayerAfterSceneCamera();

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null
                || level == null
                || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        CompanionEntity companion = findEmbeddedScenePartner(level, player.getUUID());
        if (companion == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 target = interpolatedSceneTarget(companion, partialTick);
        float yaw = Mth.rotLerp(partialTick, companion.yRotO, companion.getYRot());
        Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        double distance = 2.65D * Math.max(0.85D, companion.appearanceScale());
        Vec3 desiredEye = target.add(forward.scale(distance)).add(right.scale(0.35D));
        Vec3 cameraEye = clipCamera(level, player, target, desiredEye);

        cameraPlayer = player;
        savedPlayerPosition = player.position();
        savedPreviousPosition = new Vec3(player.xo, player.yo, player.zo);
        sceneCameraEye = cameraEye;
        sceneCameraTarget = target;

        Vec3 temporaryFeet = cameraEye.subtract(0.0D, player.getEyeHeight(), 0.0D);
        player.setPos(temporaryFeet.x, temporaryFeet.y, temporaryFeet.z);
        player.xo = temporaryFeet.x;
        player.yo = temporaryFeet.y;
        player.zo = temporaryFeet.z;
    }

    @SubscribeEvent
    public static void orientFirstPersonSceneCamera(ViewportEvent.ComputeCameraAngles event) {
        if (cameraPlayer == null || sceneCameraEye == null || sceneCameraTarget == null) {
            return;
        }

        Vec3 direction = sceneCameraTarget.subtract(sceneCameraEye);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }

        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float)(Mth.atan2(direction.z, direction.x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float)(-(Mth.atan2(direction.y, horizontalDistance) * 180.0D / Math.PI));
        event.setYaw(Mth.wrapDegrees(yaw));
        event.setPitch(Mth.clamp(pitch, -89.0F, 89.0F));
        event.setRoll(0.0F);
    }

    @SubscribeEvent
    public static void restoreFirstPersonSceneCamera(RenderFrameEvent.Post event) {
        restorePlayerAfterSceneCamera();
    }

    @SubscribeEvent
    public static void hideScenePartner(RenderPlayerEvent.Pre event) {
        if (event.getEntity().level() instanceof ClientLevel level
                && findEmbeddedScenePartner(level, event.getEntity().getUUID()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hideLocalScenePartnerHands(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.player != null
                && findEmbeddedScenePartner(minecraft.level, minecraft.player.getUUID()) != null) {
            event.setCanceled(true);
        }
    }

    private static Vec3 interpolatedSceneTarget(CompanionEntity companion, float partialTick) {
        double x = Mth.lerp(partialTick, companion.xo, companion.getX());
        double y = Mth.lerp(partialTick, companion.yo, companion.getY())
                + Math.min(1.2D, companion.getBbHeight() * 0.65D);
        double z = Mth.lerp(partialTick, companion.zo, companion.getZ());
        return new Vec3(x, y, z);
    }

    private static Vec3 clipCamera(
            ClientLevel level,
            LocalPlayer player,
            Vec3 target,
            Vec3 desiredEye
    ) {
        BlockHitResult hit = level.clip(new ClipContext(
                target,
                desiredEye,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() == HitResult.Type.MISS) {
            return desiredEye;
        }

        Vec3 towardScene = target.subtract(hit.getLocation());
        return towardScene.lengthSqr() < 1.0E-6D
                ? hit.getLocation()
                : hit.getLocation().add(towardScene.normalize().scale(0.2D));
    }

    private static void restorePlayerAfterSceneCamera() {
        if (cameraPlayer != null && savedPlayerPosition != null && savedPreviousPosition != null) {
            cameraPlayer.setPos(
                    savedPlayerPosition.x,
                    savedPlayerPosition.y,
                    savedPlayerPosition.z
            );
            cameraPlayer.xo = savedPreviousPosition.x;
            cameraPlayer.yo = savedPreviousPosition.y;
            cameraPlayer.zo = savedPreviousPosition.z;
        }

        cameraPlayer = null;
        savedPlayerPosition = null;
        savedPreviousPosition = null;
        sceneCameraEye = null;
        sceneCameraTarget = null;
    }

    private static CompanionEntity findEmbeddedScenePartner(ClientLevel level, UUID playerId) {
        for (Entity loaded : level.entitiesForRendering()) {
            if (loaded instanceof CompanionEntity companion
                    && companion.isInScene()
                    && companion.currentScene().hasPartner()
                    && companion.scenePartnerId().filter(playerId::equals).isPresent()) {
                return companion;
            }
        }
        return null;
    }
}
