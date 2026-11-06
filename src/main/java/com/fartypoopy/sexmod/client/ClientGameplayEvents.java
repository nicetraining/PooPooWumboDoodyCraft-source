package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.UUID;

@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientGameplayEvents {
    /** Original br.a: typing exactly "id" prints the UUID of the nearest girl within 10 blocks instead of chatting. */
    @SubscribeEvent
    public static void onChat(net.neoforged.neoforge.client.event.ClientChatEvent event) {
        if (!"id".equals(event.getOriginalMessage())) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        CompanionEntity nearest = null;
        for (CompanionEntity companion : player.level().getEntitiesOfClass(CompanionEntity.class,
                player.getBoundingBox().inflate(10.0D))) {
            if (nearest == null || player.distanceTo(companion) < player.distanceTo(nearest)) {
                nearest = companion;
            }
        }
        if (nearest == null) {
            return;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(nearest.getUUID().toString()), false);
        event.setCanceled(true);
    }

    private static boolean forcedFirstPerson;
    private static net.minecraft.client.CameraType cameraBeforeScene;
    private static LocalPlayer cameraPlayer;
    private static Vec3 savedPlayerPosition;
    private static Vec3 savedPreviousPosition;
    private static boolean lunaFrontView;

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
                || companion.currentScene() == com.fartypoopy.sexmod.entity.CompanionScene.BIA_ANAL
                // requested extra: tamed Galath's cowgirl and anal also cut to the player's own view
                || companion.currentScene() == com.fartypoopy.sexmod.entity.CompanionScene.GALATH_COWGIRL
                || companion.currentScene() == com.fartypoopy.sexmod.entity.CompanionScene.GALATH_ANAL);
        net.minecraft.client.CameraType sceneView = companion == null ? null : switch (companion.currentScene()) {
            case LUNA_SITTING -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT; // original eb.a(player, 25)
            case MANGLELIE_THREESOME, GALATH_THREESOME -> net.minecraft.client.CameraType.THIRD_PERSON_BACK; // original f_ menu: thirdPersonView = 1
            case GOBLIN_NELSON -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT; // original e3: NELSON_INTRO sets thirdPersonView = 2
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
        boolean flying = false;
        if (companion == null && minecraft.options.getCameraType().isFirstPerson()
                && player.getVehicle() instanceof CompanionEntity mount && mount.kind() == com.fartypoopy.sexmod.entity.CompanionKind.GALATH) {
            String playing = GalathMeshLayer.playing(mount);
            if (playing.equals("animation.galath.controlled_flight") || playing.equals("animation.galath.boost")) {
                companion = mount; // original fp.useBoyCam for CONTROLLED_FLIGHT and BOOST: the view sits on her boyCam bone
                flying = true;
            }
        }
        if (companion == null && minecraft.options.getCameraType().isFirstPerson()) {
            for (Entity loaded : level.entitiesForRendering()) {
                if (loaded instanceof CompanionEntity galath
                        && galath.kind() == com.fartypoopy.sexmod.entity.CompanionKind.GALATH
                        && galath.action() == com.fartypoopy.sexmod.entity.CompanionAction.GALATH_SUMMON
                        && galath.masterId().filter(player.getUUID()::equals).isPresent()) {
                    companion = galath; // original fp.GALATH_SUMMON useBoyCam: the 15-tick materialise is seen from her boyCam bone
                    flying = true;
                    break;
                }
            }
        }
        if (companion == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        if (companion.currentScene() == com.fartypoopy.sexmod.entity.CompanionScene.JENNY_BLOWJOB
                && companion.currentScenePhase() == 3) {
            // not in the original (free look): the finish view on his head stays within 90 degrees of facing her
            float facing = companion.getYRot() + 180.0F;
            float offset = Mth.wrapDegrees(player.getYRot() - facing);
            if (Math.abs(offset) > 90.0F) {
                float clamped = facing + Math.signum(offset) * 90.0F;
                player.setYRot(clamped);
                player.yRotO = clamped;
            }
        }
        if (!flying && !companion.currentScene().usesBoyCam(companion.currentScenePhase())) {
            // original l.a: no other scene moves the first-person camera. But 1.12's own first-person camera sat
            // 0.05 ahead of the eye (EntityRenderer.orientCamera translate(0, 0, 0.05)); 1.21 dropped it, which puts
            // the view 0.05 behind where the scenes were made for: looking down showed the inside of the neck
            // (measured side by side: the player's body drawn 1.28x larger in 1.12). Restored while in a scene.
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 nudge = player.getViewVector(partial).scale(0.06D) // measured: 0.058 (ground) to 0.065 (body)
                    .add(sceneCameraOffset(companion, player.getViewYRot(partial)));
            cameraPlayer = player;
            savedPlayerPosition = player.position();
            savedPreviousPosition = new Vec3(player.xo, player.yo, player.zo);
            player.setPos(savedPlayerPosition.x + nudge.x, savedPlayerPosition.y + nudge.y, savedPlayerPosition.z + nudge.z);
            player.xo += nudge.x;
            player.yo += nudge.y;
            player.zo += nudge.z;
            return;
        }
        {
            software.bernie.geckolib.cache.object.GeoBone bone = CompanionRenderer.boyCam(companion.getId());
            if (bone != null) {
                org.joml.Vector3d position = bone.getWorldPosition();
                if (position.lengthSquared() > 1.0E-6D) {
                    // GeckoLib's local position already includes her yaw (rotating it again put the camera off centre in
                    // every scene not facing 180 degrees); only move it onto her interpolated position. Not world minus
                    // her position: the world one was taken at her position of the frame she was last drawn in, so after
                    // each tick the view was a tick of her movement off - in flight it flicked, a boost threw it blocks away
                    float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                    Vec3 base = companion.getPosition(partial);
                    org.joml.Vector3d local = bone.getLocalPosition();
                    position = new org.joml.Vector3d(local.x + base.x, local.y + base.y, local.z + base.z);
                    cameraPlayer = player;
                    savedPlayerPosition = player.position();
                    savedPreviousPosition = new Vec3(player.xo, player.yo, player.zo);
                    Vec3 offset = sceneCameraOffset(companion, player.getViewYRot(partial));
                    position = new org.joml.Vector3d(position.x + offset.x, position.y + offset.y, position.z + offset.z);
                    double eyeY = position.y - player.getEyeHeight();
                    player.setPos(position.x, eyeY, position.z);
                    player.xo = position.x;
                    player.yo = eyeY;
                    player.zo = position.z;
                    return;
                }
            }
        }

        // no boyCam bone: the original leaves the camera alone
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
            if (loaded instanceof CompanionEntity companion && companion.approachPartnerId().filter(id::equals).isPresent()) {
                locked = true; // original: d3 locked from the menu choice while she walks up
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
                && mount.kind() == com.fartypoopy.sexmod.entity.CompanionKind.GALATH) {
            FlightHud.show();
            boolean jump = event.getInput().jumping;
            if (jump && !lastRideJump && FlightHud.canBoost()) {
                FlightHud.consume();
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.fartypoopy.sexmod.network.GalathFlightPayload(mount.getId(), 1, 0.0F, 0.0F, 0.0F));
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
                    && queen.kind() == com.fartypoopy.sexmod.entity.CompanionKind.GOBLIN
                    && queen.scenePartnerId().filter(id::equals).isPresent()) {
                locked = true; // original e3.E(): gz(false) locks the player's input while the breeding jump plays
            }
            if (loaded instanceof CompanionEntity approaching && approaching.approachPartnerId().filter(id::equals).isPresent()) {
                locked = true; // original: d3 locked from the menu choice while she walks up
            }
        }
        boolean held = false;
        for (Entity loaded : minecraft.level.entitiesForRendering()) {
            if (loaded instanceof CompanionEntity companion
                    && companion.isInScene()
                    && companion.scenePartnerId().filter(id::equals).isPresent()) {
                held = true;
                if (companion.currentScene().locksControls()) {
                    locked = true;
                }
                if (companion.currentScene().hasInteractivePhases()) {
                    interactive = companion;
                    locked = true;
                }
            }
        }
        heldInScene = held && locked;
        if (interactive == null && SceneHud.isShown()) {
            SceneHud.hide(); // the scene is over
        }
        if (interactive != null && interactive.kind() == com.fartypoopy.sexmod.entity.CompanionKind.ELLIE
                && !interactive.isPlayerForm()
                && interactive.currentScene() == com.fartypoopy.sexmod.entity.CompanionScene.ELLIE_CARRY
                && interactive.currentScene().canonicalPhase(interactive.currentScenePhase()) == 1) {
            // original el.h() every tick: the NPC shows the HUD whenever she is in CARRY_SLOW (her keyframe handler
            // has no sexUI; that one is the player form's)
            SceneHud.show(true);
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

    /**
     * Original: a scene partner gets noClip and no gravity (e0.d / em locks) and ey cancels the client's push out of
     * blocks. Without either hook in 1.21, the local partner's own physics are undone within each tick; the server's
     * placement (teleports between ticks) still applies.
     */
    private static boolean heldInScene;
    private static Vec3 heldFrom;

    @SubscribeEvent
    public static void holdPartnerBefore(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
        if (!event.getEntity().level().isClientSide()) {
            return; // the integrated server fires these too, on its own thread
        }
        Minecraft minecraft = Minecraft.getInstance();
        heldFrom = heldInScene && event.getEntity() == minecraft.player && !event.getEntity().isPassenger()
                ? event.getEntity().position() : null;
    }

    @SubscribeEvent
    public static void holdPartnerAfter(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 from = heldFrom;
        if (from != null && heldInScene && event.getEntity() == minecraft.player) {
            event.getEntity().setPos(from.x, from.y, from.z);
            event.getEntity().setDeltaMovement(Vec3.ZERO);
            event.getEntity().fallDistance = 0.0F;
        }
        heldFrom = null;
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
                        com.fartypoopy.sexmod.entity.CompanionScene.ALLIE_RICH.serializedName(),
                        com.fartypoopy.sexmod.entity.CompanionScene.ALLIE_DEEPTHROAT.serializedName(),
                        com.fartypoopy.sexmod.entity.CompanionScene.ALLIE_REVERSE_COWGIRL.serializedName()));
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
                && minecraft.player.getPersistentData().getBoolean(com.fartypoopy.sexmod.item.AlliesLampItem.IN_USE_KEY)) {
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
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fartypoopy.sexmod.network.SceneControlPayload(
                    companion.getId(), shift ? com.fartypoopy.sexmod.network.SceneControlPayload.FAST
                    : com.fartypoopy.sexmod.network.SceneControlPayload.SLOW));
        }
        if (jump && !lastJump && SceneHud.isFull()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fartypoopy.sexmod.network.SceneControlPayload(
                    companion.getId(), com.fartypoopy.sexmod.network.SceneControlPayload.FINISH));
        }
        lastShift = shift;
        lastJump = jump;
        SceneHud.setKeys(shift, jump);
    }

    @SubscribeEvent
    public static void hideScenePartner(RenderPlayerEvent.Pre event) {
        if (event.getEntity().level() instanceof ClientLevel level
                && (findEmbeddedScenePartner(level, event.getEntity().getUUID()) != null
                || isCarriedByGoblin(level, event.getEntity().getUUID())
                || event.getEntity().getVehicle() instanceof CompanionEntity mount && flyingRider(mount) == event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** The player riding a Galath in CONTROLLED_FLIGHT / BOOST (original fp.hasPlayer for those states), or null. */
    static net.minecraft.world.entity.player.Player flyingRider(CompanionEntity galath) {
        if (galath.kind() != com.fartypoopy.sexmod.entity.CompanionKind.GALATH
                || !(galath.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player rider)) {
            return null;
        }
        String playing = GalathMeshLayer.playing(galath);
        return playing.equals("animation.galath.controlled_flight") || playing.equals("animation.galath.boost") ? rider : null;
    }

    @SubscribeEvent
    public static void hideLocalScenePartnerHands(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.player != null
                && (findEmbeddedScenePartner(minecraft.level, minecraft.player.getUUID()) != null
                || isCarriedByGoblin(minecraft.level, minecraft.player.getUUID())
                || minecraft.player.getVehicle() instanceof CompanionEntity mount && flyingRider(mount) == minecraft.player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Not in the original: small first-person corrections the user asked for after playing, as (forward, right, up) in
     * blocks along the player's yaw. Jenny's blowjob view sits a quarter of the player's width further forward; Luna's
     * touch boobs view was too far forward and left of centre, her sitting start too far forward; the goblin breeding
     * intro looked through the girl on top (1/6 block lower); the goblin anal view sits a little further forward; Allie's
     * deepthroat view (boyCam) sits a little further back.
     */
    private static Vec3 sceneCameraOffset(CompanionEntity companion, float yaw) {
        if (companion.currentScene() == null) {
            return Vec3.ZERO; // riding Galath (controlled flight / boost) or her summon: the boyCam view without a scene
        }
        int phase = companion.currentScenePhase();
        double forward = 0.0D;
        double right = 0.0D;
        double up = 0.0D;
        switch (companion.currentScene()) {
            case JENNY_BLOWJOB -> forward = phase == 0 || phase == 3 ? 0.15D : 0.0D; // the views on his head
            case LUNA_TOUCH -> {
                forward = -0.15D;
                right = 0.08D;
            }
            case LUNA_SITTING -> forward = phase == 0 ? -0.12D : 0.0D;
            case GOBLIN_BREEDING -> up = phase == 0 ? -1.0D / 6.0D : 0.0D;
            case GOBLIN_NELSON -> forward = 0.1D;
            case ALLIE_DEEPTHROAT -> forward = -0.1D; // boyCam: sat a little in front of his head
            default -> {
                return Vec3.ZERO;
            }
        }
        double radians = Math.toRadians(yaw);
        double fx = -Math.sin(radians);
        double fz = Math.cos(radians);
        // the player's right in Minecraft's yaw convention (+z south at yaw 0): (-fz, fx)
        return new Vec3(fx * forward - fz * right, up, fz * forward + fx * right);
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
                    && companion.kind() == com.fartypoopy.sexmod.entity.CompanionKind.GOBLIN
                    && (companion.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.PICK_UP
                    || companion.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.THROWING)
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
                    && companion.currentScene().showsPartnerModel()
                    && companion.scenePartnerId().filter(playerId::equals).isPresent()) {
                return companion;
            }
        }
        return null;
    }
}
