package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionAction;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.network.PlayerFormNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client side of player-as-girl (original e_, cn, fu GUI parts): a player with a form is drawn as that girl, sees her
 * arm in first person, looks out of her eyes (girlCam height) and gets a strip / dress-up button in the inventory.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class PlayerFormClient {
    /** Original ei.al (client): owner -> form, rebuilt every client tick. */
    private static final Map<UUID, CompanionEntity> FORMS = new HashMap<>();
    /** True while a form is drawn in place of its player; the form's own entity render is skipped otherwise. */
    public static boolean drawingForm;
    /** Original partial tick 1.2345679 flag: the picker draws the real player, not the form. */
    public static boolean drawingPickerPlayer;
    /** Her own form seen from inside (girlCam): back faces are culled like the 1.12 renderer did, so her head is see-through. */
    public static boolean drawingOwnFirstPerson;
    private static ModelPart arm;
    private static ModelPart ellieArm;
    private static float appliedEye = -1.0F;
    private static boolean wasStripping;

    private PlayerFormClient() {
    }

    public static CompanionEntity formOf(UUID owner) {
        CompanionEntity form = FORMS.get(owner);
        return form == null || form.isRemoved() ? null : form;
    }

    public static CompanionEntity localForm() {
        Player player = Minecraft.getInstance().player;
        return player == null ? null : formOf(player.getUUID());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        FormRenderExtras.tickSway(); // original dv$a
        Minecraft minecraft = Minecraft.getInstance();
        FORMS.clear();
        if (minecraft.level == null) {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof CompanionEntity girl && girl.isPlayerForm()) {
                girl.formOwner().ifPresent(owner -> FORMS.put(owner, girl));
            }
        }
        Player player = minecraft.player;
        if (player != null) {
            CompanionEntity own = formOf(player.getUUID());
            boolean stripping = own != null && own.action() == CompanionAction.STRIP;
            if (wasStripping && !stripping) {
                minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); // original ei.n()
            }
            wasStripping = stripping;
            float wanted = eyeHeight(own);
            if (wanted != appliedEye) {
                appliedEye = wanted;
                player.refreshDimensions();
            }
        }
    }

    /** Original R(): the form's girlCam pivot height (a Kobold's scaled by her size); -1 without a form. */
    static float eyeHeight(CompanionEntity form) {
        if (form == null || form.isInScene()) {
            return -1.0F;
        }
        EntityRenderer<? super CompanionEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(form);
        if (!(renderer instanceof GeoRenderer<?> geo)) {
            return -1.0F;
        }
        @SuppressWarnings("unchecked")
        var model = ((GeoRenderer<CompanionEntity>) geo).getGeoModel();
        var baked = model.getBakedModel(model.getModelResource(form, (GeoRenderer<CompanionEntity>) geo));
        GeoBone cam = baked == null ? null : baked.getBone("girlCam").orElse(null);
        if (cam == null) {
            return -1.0F;
        }
        float eye = cam.getPivotY() / 16.0F;
        return form.kind() == CompanionKind.KOBOLD ? eye * (0.75F + form.koboldSize()) : eye;
    }

    private static boolean wasBusy;
    private static int leaveCooldown;
    private static boolean askedFollowUp;

    /** True while the local player's form waits or plays a scene (original d3.c false: controls locked). */
    public static boolean ownFormBusy() {
        CompanionEntity form = localForm();
        return form != null && (form.isInScene() || form.formWait() != 0);
    }

    /** Original d3 (InputUpdateEvent): no walking while the form strips, waits or plays; jump gets her out. */
    @SubscribeEvent
    public static void onInput(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        boolean busy = ownFormBusy();
        if (busy && !wasBusy && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("Jump to get out of the animation"), true);
        }
        wasBusy = busy;
        if (leaveCooldown > 0) {
            leaveCooldown--;
        }
        if (busy && event.getInput().jumping && leaveCooldown == 0) {
            leaveCooldown = 20;
            PacketDistributor.sendToServer(new PlayerFormNetwork.Leave()); // original ei.i(): ResetGirl
        }
        CompanionEntity own = localForm();
        if (wasStripping || busy || own != null && com.fartypoopy.sexmod.entity.PlayerForms.goblinCarried(own)) {
            var input = event.getInput();
            input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
        }
    }

    /** The local player looks out of the form's eyes. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (event.getEntity() == Minecraft.getInstance().player && appliedEye > 0.0F) {
            event.setNewSize(event.getNewSize().withEyeHeight(appliedEye));
        }
    }

    /** Original e_.a(RenderPlayerEvent.Pre): the player is drawn as the form, with the player's pose. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        CompanionEntity form = formOf(player.getUUID());
        if (form == null || drawingPickerPlayer) {
            return;
        }
        event.setCanceled(true);
        if (com.fartypoopy.sexmod.entity.PlayerForms.goblinCarried(form)) {
            return; // original eq.c(Vec3d): she is drawn on her carrier by the entity renderer, not above him
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && player.isInvisibleTo(minecraft.player)) {
            return;
        }
        copyPose(form, player);
        if (form.action() == CompanionAction.BOW) {
            // original e_.a(ei, player): drawing the bow, her body turns with her head
            form.setYRot(form.yHeadRot);
            form.yBodyRot = form.yHeadRot;
            form.yBodyRotO = form.yHeadRotO;
        }
        EntityRenderer<? super CompanionEntity> renderer = minecraft.getEntityRenderDispatcher().getRenderer(form);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        if (player.isPassenger() && form.kind() != CompanionKind.GOBLIN && !form.isInScene() && form.formWait() == 0) {
            // original e_.a(ei, player): while riding she is drawn 0.2 higher, 0.4 in a boat (y + f12, both positive)
            poseStack.translate(0.0D, player.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat ? 0.4D : 0.2D, 0.0D);
        }
        drawingForm = true;
        try {
            renderer.render(form, player.getYRot(), event.getPartialTick(), poseStack, event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            drawingForm = false;
            poseStack.popPose();
        }
    }

    /** Original e_: every rotation and position of the player is copied onto the form before drawing. */
    private static void copyPose(CompanionEntity form, Player player) {
        form.setYRot(player.getYRot());
        form.yRotO = player.yRotO;
        form.setXRot(player.getXRot());
        form.xRotO = player.xRotO;
        form.yHeadRot = player.yHeadRot;
        form.yHeadRotO = player.yHeadRotO;
        form.yBodyRot = player.yBodyRot;
        form.yBodyRotO = player.yBodyRotO;
        form.setPos(player.getX(), player.getY(), player.getZ());
        form.xo = player.xo;
        form.yo = player.yo;
        form.zo = player.zo;
        form.xOld = player.xOld;
        form.yOld = player.yOld;
        form.zOld = player.zOld;
        form.setOnGround(player.onGround());
    }

    private static ModelPart arm(CompanionKind kind) {
        if (arm == null) {
            // original a0 and its copies: one 2x6x2 box at (-5, 2.5, 0); Ellie's (cf) is the same box turned 90 degrees
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("arm", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -6.0F, 0.0F, 2, 6, 2),
                    PartPose.offset(-5.0F, 2.5F, 0.0F));
            arm = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("arm");
            MeshDefinition ellie = new MeshDefinition();
            PartDefinition root = ellie.getRoot().addOrReplaceChild("arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 1.5708F, 0.0F));
            root.addOrReplaceChild("box", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -3.0F, -1.0F, 2, 6, 2),
                    PartPose.offsetAndRotation(-1.0F, -3.0F, 1.0F, 0.0F, 1.5708F, 0.0F));
            ellieArm = LayerDefinition.create(ellie, 16, 16).bakeRoot().getChild("arm");
        }
        return kind == CompanionKind.ELLIE ? ellieArm : arm;
    }

    /** Original c(int): the arm texture of each form (Jenny and Ellie have a nude variant). */
    private static ResourceLocation armTexture(CompanionEntity form) {
        String folder = switch (form.kind()) {
            case LUNA -> "cat";
            case GOBLIN -> "kobold";
            default -> form.kind().registryPath();
        };
        boolean nude = form.isNude() && (form.kind() == CompanionKind.JENNY || form.kind() == CompanionKind.ELLIE);
        return ResourceLocation.fromNamespaceAndPath("sexmod", "textures/entity/" + folder + "/hand" + (nude ? "_nude" : "") + ".png");
    }

    /** Original b(int): Kobolds and Goblins tint the arm with their body colour. */
    private static int armColour(CompanionEntity form) {
        if (form.kind() == CompanionKind.KOBOLD) {
            return 0xFF000000 | form.bodyColor().mainRgb();
        }
        if (form.kind() == CompanionKind.GOBLIN) {
            String[] fields = form.goblinModel().split("-");
            try {
                int[] rgb = com.fartypoopy.sexmod.entity.GoblinModel.BODY[Integer.parseInt(fields[7].trim())];
                return 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            } catch (RuntimeException ignored) {
                // fall back to white
            }
        }
        return 0xFFFFFFFF;
    }

    /** Original cn: the empty hand / map hand of a player with a form is the form's arm. */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        CompanionEntity form = formOf(event.getPlayer().getUUID());
        if (form == null) {
            return;
        }
        event.setCanceled(true);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        if (event.getArm() == HumanoidArm.RIGHT) {
            poseStack.translate(0.48F, 0.15F, 0.0F);
        }
        poseStack.translate(0.44F, 1.3F, 1.0F);
        poseStack.scale(2.8F, 2.8F, 2.8F); // original render scale 0.175 instead of 1/16
        arm(form.kind()).render(poseStack, event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(armTexture(form))),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY, armColour(form));
        poseStack.popPose();
    }

    /**
     * Original fu.b / fu.a (EntityInteract on a player): clicking a player-girl opens her action menu (the answer goes to
     * her as a request); a player-girl clicking someone opens her own menu (the request goes to him).
     */
    @SubscribeEvent
    public static void onInteractPlayer(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.getLevel().isClientSide || event.getEntity() != minecraft.player
                || !(event.getTarget() instanceof Player target)) {
            return;
        }
        CompanionEntity mine = localForm();
        CompanionEntity theirs = formOf(target.getUUID());
        if (theirs != null && !minecraft.player.isShiftKeyDown()) {
            event.setCanceled(true);
            if (theirs.goblinCarrier().isPresent() && theirs.kind() == CompanionKind.GOBLIN) {
                return; // original eq.l(): no menu while somebody carries her
            }
            if (mine != null) {
                minecraft.player.displayClientMessage(Component.literal("no lesbo yet owo"), true);
                return;
            }
            UUID male = minecraft.player.getUUID();
            minecraft.setScreen(new CompanionActionScreen(theirs, com.fartypoopy.sexmod.entity.PlayerForms.actions(theirs.kind()),
                    action -> PacketDistributor.sendToServer(new PlayerFormNetwork.Request(action, male, target.getUUID(), true))));
            return;
        }
        if (mine != null && theirs == null) {
            event.setCanceled(true);
            UUID girl = minecraft.player.getUUID();
            minecraft.setScreen(new CompanionActionScreen(mine, com.fartypoopy.sexmod.entity.PlayerForms.actions(mine.kind()),
                    action -> PacketDistributor.sendToServer(new PlayerFormNetwork.Request(action, target.getUUID(), girl, false))));
        }
    }

    /** The local player's carried goblin form seen from inside her girlCam: back faces culled like renderOwnForm. */
    static boolean ownCarriedFirstPerson(CompanionEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity.isPlayerForm() && entity == localForm() && minecraft.options.getCameraType().isFirstPerson()
                && com.fartypoopy.sexmod.entity.PlayerForms.goblinCarried(entity);
    }

    /** Original eq$a.a(RenderHandEvent): a carried goblin girl-player has no hand. */
    @SubscribeEvent
    public static void hideCarriedHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        CompanionEntity form = localForm();
        if (form != null && com.fartypoopy.sexmod.entity.PlayerForms.goblinCarried(form)) {
            event.setCanceled(true);
        }
    }

    /**
     * Original eq$a.a(PlayerTickEvent / RenderTickEvent) and eq.a(fp, player): the carried goblin girl-player stays 2 blocks
     * above her carrier, and looks at most 90 degrees to either side of him (backwards and level while lifted, 37.5 down
     * on the shoulder).
     */
    @SubscribeEvent
    public static void holdCarriedGoblin(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player me = minecraft.player;
        CompanionEntity form = localForm();
        if (me == null || form == null || !com.fartypoopy.sexmod.entity.PlayerForms.goblinCarried(form)) {
            return;
        }
        Player carrier = form.goblinCarrier().map(me.level()::getPlayerByUUID).orElse(null);
        if (carrier == null) {
            return;
        }
        me.setPos(carrier.getX(), carrier.getY() + 2.0D, carrier.getZ());
        me.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        me.noPhysics = true;
        me.setNoGravity(true);
        boolean lifting = form.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.PICK_UP;
        float centre = carrier.getYRot() + (lifting ? 180.0F : 0.0F);
        float yaw = me.getYRot();
        if (yaw < centre - 90.0F) {
            me.setYRot(centre - 90.0F);
        } else if (yaw > centre + 90.0F) {
            me.setYRot(centre + 90.0F);
        }
        float maxPitch = lifting ? 0.0F : 37.5F;
        if (me.getXRot() > maxPitch) {
            me.setXRot(maxPitch);
        }
    }

    /** Original e_.a(CameraSetup) with e5.F(): during her deepthroat scene the Allie player's view is upside down. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void allieUpsideDown(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        CompanionEntity form = localForm();
        if (form == null || form.kind() != CompanionKind.ALLIE
                || form.currentScene() != com.fartypoopy.sexmod.entity.CompanionScene.ALLIE_DEEPTHROAT) {
            return;
        }
        event.setRoll(180.0F);
        event.setPitch(-event.getPitch());
        event.setYaw(-event.getYaw());
    }

    /**
     * Original ee.c(player): once Ellie sits, her girl-player chooses cowgirl or missionary; original fu -> eg.H(): Bia's
     * girl-player chooses anal or doggy as soon as she stands at the bed.
     */
    @SubscribeEvent
    public static void askEllieFollowUp(ClientTickEvent.Post event) {
        CompanionEntity form = localForm();
        Minecraft minecraft = Minecraft.getInstance();
        boolean ellie = form != null && form.kind() == CompanionKind.ELLIE && form.formWait() == 2;
        boolean bia = form != null && form.kind() == CompanionKind.BIA && form.formWait() == 1 && form.formWaitAnims().isEmpty();
        if (!ellie && !bia) {
            askedFollowUp = false;
            return;
        }
        if (!askedFollowUp && minecraft.screen == null) {
            askedFollowUp = true;
            minecraft.setScreen(new CompanionActionScreen(form, ellie ? java.util.List.of("action.names.cowgirl", "action.names.missionary")
                    : java.util.List.of("anal", "doggy"),
                    action -> PacketDistributor.sendToServer(new PlayerFormNetwork.FollowUp(action))));
        }
    }

    private static net.minecraft.world.phys.Vec3 savedPosition;
    private static net.minecraft.world.phys.Vec3 savedOld;

    /** Original e_.a(RenderTickEvent): in first person the girl-player looks out of her form's girlCam bone. */
    @SubscribeEvent
    public static void girlCamPre(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        CompanionEntity form = localForm();
        boolean carried = form != null && com.fartypoopy.sexmod.entity.PlayerForms.goblinCarried(form);
        if (player == null || form == null || !ownFormBusy() && !carried || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (!carried) {
            clampView(form, player); // original eq.a(fp, player) replaces the clamp while she is carried
        }
        GeoBone bone = CompanionRenderer.girlCam(form.getId());
        if (bone == null) {
            return;
        }
        org.joml.Vector3d position = bone.getWorldPosition();
        if (position.lengthSquared() < 1.0E-6D) {
            return;
        }
        // the form is drawn by renderOwnForm while she is busy in first person; that pass already includes her yaw
        double x = position.x;
        double y = position.y - player.getEyeHeight();
        double z = position.z;
        savedPosition = player.position();
        savedOld = new net.minecraft.world.phys.Vec3(player.xo, player.yo, player.zo);
        player.setPos(x, y, z);
        player.xo = x;
        player.yo = y;
        player.zo = z;
    }

    @SubscribeEvent
    public static void girlCamPost(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        if (savedPosition != null && player != null) {
            player.setPos(savedPosition.x, savedPosition.y, savedPosition.z);
            player.xo = savedOld.x;
            player.yo = savedOld.y;
            player.zo = savedOld.z;
        }
        savedPosition = null;
        savedOld = null;
    }

    /**
     * Original fp.maxGirlPitch / minGirlPitch / flipGirlYaw per state (forms only; all other states 30 / -90 / no flip),
     * longest animation prefix first. The nine-argument fp constructor stores its two floats as min, max, so the Allie
     * reverse cowgirl start / fast states and Galath's rape cum have max below min, which e_'s clamp order turns into a
     * fixed pitch (= min).
     */
    private static final java.util.List<java.util.Map.Entry<String, float[]>> GIRL_VIEW = java.util.List.of(
            java.util.Map.entry("animation.allie.deepthroat_normal_prepare", new float[] {40.0F, -40.0F, 0}),
            java.util.Map.entry("animation.goblin.paizuri_fast_countinues", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.allie.reverse_cowgirl_start", new float[] {-90.0F, 30.0F, 1}),
            java.util.Map.entry("animation.allie.reverse_cowgirl_fastc", new float[] {-90.0F, 30.0F, 1}),
            java.util.Map.entry("animation.allie.reverse_cowgirl_fasts", new float[] {-90.0F, 30.0F, 1}),
            java.util.Map.entry("animation.allie.reverse_cowgirl_slow", new float[] {30.0F, -90.0F, 1}),
            java.util.Map.entry("animation.allie.reverse_cowgirl_cum", new float[] {30.0F, -90.0F, 1}),
            java.util.Map.entry("animation.kobold.mating_press_start", new float[] {-50.0F, -90.0F, 0}),
            java.util.Map.entry("animation.allie.deepthroat_prepare", new float[] {40.0F, -40.0F, 0}),
            java.util.Map.entry("animation.kobold.mating_press_soft", new float[] {-50.0F, -90.0F, 0}),
            java.util.Map.entry("animation.kobold.mating_press_hard", new float[] {-50.0F, -90.0F, 0}),
            java.util.Map.entry("animation.kobold.mating_press_cum", new float[] {-30.0F, -90.0F, 0}),
            java.util.Map.entry("animation.ellie.missionary_start", new float[] {30.0F, -90.0F, 1}),
            java.util.Map.entry("animation.allie.deepthroat_start", new float[] {40.0F, -40.0F, 0}),
            java.util.Map.entry("animation.ellie.missionary_slow", new float[] {30.0F, -90.0F, 1}),
            java.util.Map.entry("animation.ellie.missionary_fast", new float[] {30.0F, -90.0F, 1}),
            java.util.Map.entry("animation.allie.deepthroat_fast", new float[] {40.0F, -40.0F, 0}),
            java.util.Map.entry("animation.allie.deepthroat_slow", new float[] {40.0F, -40.0F, 0}),
            java.util.Map.entry("animation.galath.rape_cum_idle", new float[] {30.0F, -90.0F, 0}),
            java.util.Map.entry("animation.galath.corrupt_intro", new float[] {30.0F, -90.0F, 0}),
            java.util.Map.entry("animation.ellie.missionary_cum", new float[] {30.0F, -90.0F, 1}),
            java.util.Map.entry("animation.allie.deepthroat_cum", new float[] {40.0F, -40.0F, 0}),
            java.util.Map.entry("animation.goblin.paizuri_start", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.jenny.paizuri_start", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.goblin.nelson_intro", new float[] {30.0F, -20.0F, 0}),
            java.util.Map.entry("animation.goblin.paizuri_idle", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.goblin.paizuri_slow", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.goblin.paizuri_fast", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.galath.corrupt_slow", new float[] {-30.0F, -90.0F, 0}),
            java.util.Map.entry("animation.jenny.paizuri_slow", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.jenny.paizuri_fast", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.ellie.cowgirlstart", new float[] {60.0F, -60.0F, 0}),
            java.util.Map.entry("animation.ellie.cowgirlslow2", new float[] {60.0F, -60.0F, 0}),
            java.util.Map.entry("animation.goblin.nelson_fast", new float[] {30.0F, -20.0F, 0}),
            java.util.Map.entry("animation.goblin.nelson_slow", new float[] {30.0F, -20.0F, 0}),
            java.util.Map.entry("animation.goblin.paizuri_cum", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.galath.corrupt_cum", new float[] {-30.0F, -90.0F, 0}),
            java.util.Map.entry("animation.galath.rape_intro", new float[] {30.0F, -90.0F, 0}),
            java.util.Map.entry("animation.jenny.paizuri_cum", new float[] {-56.0F, -90.0F, 0}),
            java.util.Map.entry("animation.ellie.sitdownidle", new float[] {60.0F, -60.0F, 1}),
            java.util.Map.entry("animation.ellie.cowgirlfast", new float[] {60.0F, -60.0F, 0}),
            java.util.Map.entry("animation.goblin.nelson_cum", new float[] {30.0F, -20.0F, 0}),
            java.util.Map.entry("animation.ellie.cowgirlcum", new float[] {60.0F, -60.0F, 0}),
            java.util.Map.entry("animation.kobold.analStart", new float[] {4.0F, -80.0F, 1}),
            java.util.Map.entry("animation.bia.sitdownidle", new float[] {60.0F, -60.0F, 1}),
            java.util.Map.entry("animation.kobold.analSoft", new float[] {4.0F, -80.0F, 1}),
            java.util.Map.entry("animation.kobold.analHard", new float[] {4.0F, -80.0F, 1}),
            java.util.Map.entry("animation.galath.corrupt_", new float[] {-30.0F, -90.0F, 0}),
            java.util.Map.entry("animation.galath.rape_cum", new float[] {-30.0F, 60.0F, 0}),
            java.util.Map.entry("animation.kobold.analCum", new float[] {4.0F, -80.0F, 1}),
            java.util.Map.entry("animation.ellie.sitdown", new float[] {60.0F, -90.0F, 1}),
            java.util.Map.entry("animation.bee.sex_start", new float[] {10.0F, -90.0F, 0}),
            java.util.Map.entry("animation.bee.sex_slow", new float[] {10.0F, -90.0F, 0}),
            java.util.Map.entry("animation.bee.sex_fast", new float[] {10.0F, -90.0F, 0}),
            java.util.Map.entry("animation.bia.sitdown", new float[] {60.0F, -90.0F, 1}),
            java.util.Map.entry("animation.bee.sex_cum", new float[] {10.0F, -90.0F, 0}),
            java.util.Map.entry("animation.galath.rape", new float[] {60.0F, -30.0F, 0}),
            java.util.Map.entry("animation.cat.wait", new float[] {30.0F, -90.0F, 1})
    );

    /** Original e_: pitch capped at max, then raised to min; yaw kept within 90 degrees of her (flipped) yaw. */
    private static void clampView(CompanionEntity form, Player player) {
        String playing = GalathMeshLayer.playing(form);
        float max = 30.0F;
        float min = -90.0F;
        boolean flip = false;
        for (java.util.Map.Entry<String, float[]> entry : GIRL_VIEW) {
            if (playing.startsWith(entry.getKey())) {
                max = entry.getValue()[0];
                min = entry.getValue()[1];
                flip = entry.getValue()[2] != 0.0F;
                break;
            }
        }
        if (player.getXRot() > max) {
            player.setXRot(max);
            player.xRotO = max;
        }
        if (player.getXRot() < min) {
            player.setXRot(min);
            player.xRotO = min;
        }
        float yaw = form.getYRot() + (flip ? 180.0F : 0.0F);
        float offset = net.minecraft.util.Mth.wrapDegrees(player.getYRot() - yaw);
        if (Math.abs(offset) > 90.0F) {
            player.setYRot(yaw + Math.signum(offset) * 90.0F);
            player.yRotO = player.getYRot();
        }
    }

    /** Original e_.a(RenderWorldLastEvent): while busy in first person she still sees her own form. */
    @SubscribeEvent
    public static void renderOwnForm(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        CompanionEntity form = localForm();
        if (form == null || !ownFormBusy() || !minecraft.options.getCameraType().isFirstPerson()
                || com.fartypoopy.sexmod.entity.PlayerForms.goblinCarried(form)) {
            return; // a carried goblin is drawn on her carrier by the entity renderer
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().getPosition();
        var at = form.getPosition(partial);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        drawingForm = true;
        drawingOwnFirstPerson = true;
        try {
            minecraft.getEntityRenderDispatcher().getRenderer(form).render(form, form.getYRot(), partial, poseStack, buffers,
                    minecraft.getEntityRenderDispatcher().getPackedLightCoords(form, partial));
        } finally {
            drawingForm = false;
            drawingOwnFirstPerson = false;
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    /** Original fu (InitGuiEvent): a strip / dress-up button under the inventory of a player with a form. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        CompanionEntity form = localForm();
        if (form == null || !com.fartypoopy.sexmod.entity.PlayerForms.hasStripButton(form.kind())) {
            return;
        }
        var screen = event.getScreen();
        String key = form.isNude() || form.formBare() ? "action.names.dressup" : "action.names.strip";
        event.addListener(Button.builder(Component.translatable(key), button -> {
            CompanionEntity current = localForm();
            if (current == null || current.isInScene() || current.action() != CompanionAction.NONE) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); // original: view from the front
            PacketDistributor.sendToServer(new PlayerFormNetwork.Strip());
            minecraft.setScreen(null);
        }).bounds((int) (screen.width * 0.5D - 35.0D), (int) (screen.height * 0.87D), 70, 20).build());
    }
}
