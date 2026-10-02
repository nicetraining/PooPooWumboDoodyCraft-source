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
    private static boolean forcedFirstPerson;
    private static net.minecraft.client.CameraType cameraBeforeScene;
    private static LocalPlayer cameraPlayer;
    private static Vec3 savedPlayerPosition;
    private static Vec3 savedPreviousPosition;
    private static boolean lunaFrontView;
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
        if (player == null || level == null) {
            return;
        }

        CompanionEntity companion = findEmbeddedScenePartner(level, player.getUUID());
        boolean wantsFirstPerson = companion != null
                && (companion.currentScene().usesBoyCam(companion.currentScenePhase())
                || companion.currentScene() == com.trolmastercard.sexmod.entity.CompanionScene.BIA_ANAL
                // requested extra: tamed Galath's cowgirl and anal also cut to the player's own view
                || companion.currentScene() == com.trolmastercard.sexmod.entity.CompanionScene.GALATH_COWGIRL
                || companion.currentScene() == com.trolmastercard.sexmod.entity.CompanionScene.GALATH_ANAL);
        net.minecraft.client.CameraType sceneView = companion == null ? null : switch (companion.currentScene()) {
            case LUNA_SITTING -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT; // original eb.a(player, 25)
            case MANGLELIE_THREESOME, GALATH_THREESOME -> net.minecraft.client.CameraType.THIRD_PERSON_BACK; // original f_ menu: thirdPersonView = 1
            default -> null;
        };
        if (sceneView != null) {
            if (!lunaFrontView) {
                lunaFrontView = true;
                if (KeyframeEvents.viewBefore == null) {
                    KeyframeEvents.viewBefore = minecraft.options.getCameraType();
                }
                minecraft.options.setCameraType(sceneView);
            }
        } else if (companion == null) {
            lunaFrontView = false;
        }
        if (wantsFirstPerson) {
            if (!forcedFirstPerson) {
                forcedFirstPerson = true;
                cameraBeforeScene = minecraft.options.getCameraType();
                minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); // the cut to the boy's view
            }
        } else if (forcedFirstPerson && (companion == null || !companion.currentScene().hasPartner())) {
            forcedFirstPerson = false;
            if (cameraBeforeScene != null) {
                minecraft.options.setCameraType(cameraBeforeScene);
            }
            cameraBeforeScene = null;
        }
        if (companion == null && KeyframeEvents.viewBefore != null && !hasAnyPartner(level, player.getUUID())) {
            minecraft.options.setCameraType(KeyframeEvents.viewBefore); // the scene ended: back to the player's own view
            KeyframeEvents.viewBefore = null;
        }
        if (companion == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        if (!companion.currentScene().usesBoyCam(companion.currentScenePhase())) {
            return; // original l.a: no other scene moves the first-person camera
        }
        {
            software.bernie.geckolib.cache.object.GeoBone bone = CompanionRenderer.boyCam(companion.getId());
            if (bone != null) {
                org.joml.Vector3d position = bone.getWorldPosition();
                if (position.lengthSquared() > 1.0E-6D) {
                    // GeckoLib's world position leaves out the entity's yaw (its matrices are taken after the
                    // rotation), so rotate the offset by hand: the camera sat off centre in every scene not at 180 degrees.
                    float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                    double ox = position.x - companion.getX();
                    double oz = position.z - companion.getZ();
                    double angle = Math.toRadians(180.0F - Mth.rotLerp(partial, companion.yBodyRotO, companion.yBodyRot));
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    Vec3 base = companion.getPosition(partial);
                    position = new org.joml.Vector3d(
                            base.x + ox * cos + oz * sin,
                            position.y - companion.getY() + base.y,
                            base.z - ox * sin + oz * cos);
                    cameraPlayer = player;
                    savedPlayerPosition = player.position();
                    savedPreviousPosition = new Vec3(player.xo, player.yo, player.zo);
                    sceneCameraEye = null;
                    sceneCameraTarget = null;
                    double eyeY = position.y - player.getEyeHeight();
                    player.setPos(position.x, eyeY, position.z);
                    player.xo = position.x;
                    player.yo = eyeY;
                    player.zo = position.z;
                    return;
                }
            }
        }

        if (true) {
            return; // no boyCam bone: the original leaves the camera alone
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

    /** Original d3: while the controls are locked every mouse click is swallowed (no attacking or using items mid-scene). */
    @SubscribeEvent
    public static void swallowClicks(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean locked = TransitionOverlay.isActive();
        UUID id = minecraft.player.getUUID();
        for (Entity loaded : minecraft.level.entitiesForRendering()) {
            if (loaded instanceof CompanionEntity companion && companion.isInScene()
                    && companion.scenePartnerId().filter(id::equals).isPresent()
                    && (companion.currentScene().locksControls() || companion.currentScene().hasInteractivePhases())) {
                locked = true;
            }
        }
        if (locked) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** The original's movement lock (d3.a(false)): during the transition wipe and while Bia's Talk plays. */
    @SubscribeEvent
    public static void lockControls(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (minecraft.player.getVehicle() instanceof CompanionEntity mount
                && mount.kind() == com.trolmastercard.sexmod.entity.CompanionKind.GALATH) {
            FlightHud.show();
            boolean jump = event.getInput().jumping;
            if (jump && !lastRideJump && FlightHud.canBoost()) {
                FlightHud.consume();
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.trolmastercard.sexmod.network.GalathFlightPayload(mount.getId(), 1, 0.0F, 0.0F, 0.0F));
            }
            lastRideJump = jump;
        } else {
            lastRideJump = false;
            if (FlightHud.isActive()) {
                FlightHud.hide();
            }
        }
        boolean locked = TransitionOverlay.isActive();
        CompanionEntity interactive = null;
        UUID id = minecraft.player.getUUID();
        for (Entity loaded : minecraft.level.entitiesForRendering()) {
            if (loaded instanceof CompanionEntity queen && !queen.isInScene()
                    && queen.kind() == com.trolmastercard.sexmod.entity.CompanionKind.GOBLIN
                    && queen.scenePartnerId().filter(id::equals).isPresent()) {
                locked = true; // original e3.E(): gz(false) locks the player's input while the breeding jump plays
            }
        }
        for (Entity loaded : minecraft.level.entitiesForRendering()) {
            if (loaded instanceof CompanionEntity companion
                    && companion.isInScene()
                    && companion.scenePartnerId().filter(id::equals).isPresent()) {
                if (companion.currentScene().locksControls()) {
                    locked = true;
                }
                if (companion.currentScene().hasInteractivePhases()) {
                    interactive = companion;
                    locked = true;
                }
            }
        }
        if (interactive == null && SceneHud.isShown()) {
            SceneHud.hide(); // the scene is over
        }
        if (interactive != null) {
            sceneKeys(interactive, event.getInput());
        } else {
            SceneHud.setKeys(false, false);
        }
        if (locked) {
            net.minecraft.client.player.Input input = event.getInput();
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
        }
    }

    private static int allieMenuDelay;

    /** Original ev.ac(): Allie's wish menu cannot be dismissed, it comes back until a wish is chosen. */
    @SubscribeEvent
    public static void reopenAllieMenu(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (minecraft.screen != null) {
            allieMenuDelay = 10;
            return;
        }
        if (allieMenuDelay > 0) {
            allieMenuDelay--;
            return;
        }
        UUID id = minecraft.player.getUUID();
        for (Entity loaded : minecraft.level.entitiesForRendering()) {
            if (loaded instanceof CompanionEntity companion
                    && companion.isInScene()
                    && companion.currentScene().isMenuWaitPhase(companion.currentScenePhase())
                    && companion.scenePartnerId().filter(id::equals).isPresent()) {
                ClientHooks.openSceneMenu(companion.getId(), java.util.List.of(
                        com.trolmastercard.sexmod.entity.CompanionScene.ALLIE_RICH.serializedName(),
                        com.trolmastercard.sexmod.entity.CompanionScene.ALLIE_DEEPTHROAT.serializedName(),
                        com.trolmastercard.sexmod.entity.CompanionScene.ALLIE_REVERSE_COWGIRL.serializedName()));
                allieMenuDelay = 10;
                return;
            }
        }
    }

    /** Original: the whole HUD is hidden while the lamp is being rubbed. */
    @SubscribeEvent
    public static void hideHudWhileRubbing(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.getPersistentData().getBoolean(com.trolmastercard.sexmod.item.AlliesLampItem.IN_USE_KEY)) {
            event.setCanceled(true);
        }
    }

    private static boolean lastRideJump;
    private static boolean lastShift;
    private static boolean lastJump;

    /** The original's in-scene keys (d3): hold sneak for the fast loop, jump once the meter is full to finish. */
    private static void sceneKeys(CompanionEntity companion, net.minecraft.client.player.Input input) {
        boolean shift = input.shiftKeyDown;
        boolean jump = input.jumping;
        if (shift != lastShift) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.trolmastercard.sexmod.network.SceneControlPayload(
                    companion.getId(), shift ? com.trolmastercard.sexmod.network.SceneControlPayload.FAST
                    : com.trolmastercard.sexmod.network.SceneControlPayload.SLOW));
        }
        if (jump && !lastJump && SceneHud.isFull()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.trolmastercard.sexmod.network.SceneControlPayload(
                    companion.getId(), com.trolmastercard.sexmod.network.SceneControlPayload.FINISH));
        }
        lastShift = shift;
        lastJump = jump;
        SceneHud.setKeys(shift, jump);
    }

    @SubscribeEvent
    public static void hideScenePartner(RenderPlayerEvent.Pre event) {
        if (event.getEntity().level() instanceof ClientLevel level
                && (findEmbeddedScenePartner(level, event.getEntity().getUUID()) != null
                || isCarriedByGoblin(level, event.getEntity().getUUID()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hideLocalScenePartnerHands(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.player != null
                && (findEmbeddedScenePartner(minecraft.level, minecraft.player.getUUID()) != null
                || isCarriedByGoblin(minecraft.level, minecraft.player.getUUID()))) {
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

    private static boolean hasAnyPartner(ClientLevel level, UUID playerId) {
        for (Entity loaded : level.entitiesForRendering()) {
            if (loaded instanceof CompanionEntity companion
                    && companion.scenePartnerId().filter(playerId::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Original am: while a goblin picks someone up or throws them, the carried player's body and hand are not drawn. */
    private static boolean isCarriedByGoblin(ClientLevel level, UUID playerId) {
        for (Entity loaded : level.entitiesForRendering()) {
            if (loaded instanceof CompanionEntity companion
                    && companion.kind() == com.trolmastercard.sexmod.entity.CompanionKind.GOBLIN
                    && (companion.goblinState() == com.trolmastercard.sexmod.entity.GoblinBehavior.PICK_UP
                    || companion.goblinState() == com.trolmastercard.sexmod.entity.GoblinBehavior.THROWING)
                    && companion.goblinCarrier().filter(playerId::equals).isPresent()) {
                return true;
            }
        }
        return false;
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
