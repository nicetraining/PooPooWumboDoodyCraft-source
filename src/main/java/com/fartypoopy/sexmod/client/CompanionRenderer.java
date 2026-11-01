package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fartypoopy.sexmod.entity.CompanionAction;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class CompanionRenderer extends GeoEntityRenderer<CompanionEntity> {
    private static final java.util.Map<Integer, GeoBone> BOY_CAMS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, GeoBone> GIRL_CAMS = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public RenderType getRenderType(CompanionEntity entity, net.minecraft.resources.ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        // original d_: every girl is drawn with blending on (SRC_ALPHA, ONE_MINUS_SRC_ALPHA), so half transparent
        // texels stay half transparent (the slime's hat and bed slime, wing membranes) instead of GeckoLib's cutout
        return RenderType.entityTranslucent(texture);
    }

    public static GeoBone girlCam(int entityId) {
        return GIRL_CAMS.get(entityId);
    }

    /** The animated {@code boyCam} bone as of the last frame this entity was drawn, or null. */
    public static GeoBone boyCam(int entityId) {
        return BOY_CAMS.get(entityId);
    }

    private static final java.util.Map<Integer, java.util.Map<String, GeoBone>> POSE_BONES = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean poseBone(CompanionEntity entity, String name) {
        return (entity.kind() == CompanionKind.GALATH && (name.equals("mangPos") || name.equals("futaCockTip")
                || name.equals("energyBallR") || name.equals("energyBallL")
                || name.equals("futaCockTipDirHelp") || name.equals("creampiePos")))
                || (entity.kind() == CompanionKind.MANGLELIE && (name.equals("armR") || name.equals("armL") || name.equals("head")
                || name.equals("semenEmitter") || name.equals("semenDir")))
                || (entity.kind() == CompanionKind.ALLIE && name.length() == 5 && name.startsWith("tail"));
    }

    /**
     * Original f_.b(name): where a bone of this companion was in the last frame, relative to the entity and turned into world
     * directions (the same correction as the first-person camera uses), or null before it has been drawn.
     */
    static net.minecraft.world.phys.Vec3 boneOffset(CompanionEntity entity, String name) {
        java.util.Map<String, GeoBone> bones = POSE_BONES.get(entity.getId());
        GeoBone bone = bones == null ? null : bones.get(name);
        if (bone == null) {
            return null;
        }
        org.joml.Vector3d position = bone.getWorldPosition();
        double ox = position.x - entity.getX();
        double oz = position.z - entity.getZ();
        double angle = Math.toRadians(180.0F - net.minecraft.util.Mth.rotLerp(1.0F, entity.yBodyRotO, entity.yBodyRot));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new net.minecraft.world.phys.Vec3(ox * cos + oz * sin, position.y - entity.getY(), -ox * sin + oz * cos);
    }

    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new CompanionModel());
        this.shadowRadius = 0.2F;
        this.addRenderLayer(new SkirtLayer(this));
        this.addRenderLayer(new ClothLayer(this));
        this.addRenderLayer(new GalathMeshLayer(this));
    }

    @Override
    public void render(CompanionEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (entity.isPlayerForm() && !PlayerFormClient.drawingForm
                && !com.fartypoopy.sexmod.entity.PlayerForms.goblinCarried(entity)) {
            return; // original e_: a form is drawn in place of its player, never on its own (a carried goblin: on her carrier)
        }
        this.shadowRadius = 0.2F; // a riding Manglelie / carried goblin below sets its own
        this.formPass = entity.isPlayerForm() ? FormRenderExtras.begin(entity, partialTick) : null;
        var scene = entity.currentScene();
        if (scene != null && scene.hidesEntity()) {
            return;
        }
        if (entity.kind() == CompanionKind.GALATH && entity.bossFlag(com.fartypoopy.sexmod.entity.GalathBoss.F_KNOCKED)) {
            // original da.d(f_): knocked out she faces the way she flies; nearly still she keeps the last such yaw
            double dx = entity.getX() - entity.xOld;
            double dz = entity.getZ() - entity.zOld;
            if (Math.abs(dx) + Math.abs(dz) >= 0.05F) {
                entity.galathKnockYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            }
            entity.yBodyRot = entity.galathKnockYaw;
            entity.yBodyRotO = entity.galathKnockYaw;
        }
        if (entity.goblin() != null && entity.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.AWAIT_PICK_UP) {
            // original e3 end of breeding: b(0.0F) with the position lock (a(true)) draws both guards at yaw 0; ci then
            // turns the body bone to the player by an angle that assumes exactly that yaw. Her own look AI had turned
            // the body as well, so one of the two ended up facing away from the player
            entity.yBodyRot = 0.0F;
            entity.yBodyRotO = 0.0F;
            entity.yHeadRot = 0.0F;
            entity.yHeadRotO = 0.0F;
            entity.setYRot(0.0F);
            entity.yRotO = 0.0F;
        }
        if (entity.kind() == CompanionKind.GALATH && entity.action() == CompanionAction.GALATH_MASTURBATE) {
            // original da.b(f_): body and head stay on the yaw she was given (back turned to her owner)
            float yaw = entity.getYRot();
            entity.yBodyRot = yaw;
            entity.yBodyRotO = yaw;
            entity.yHeadRot = yaw;
            entity.yHeadRotO = yaw;
            entity.yRotO = yaw;
        }
        if (entity.kind() == CompanionKind.MANGLELIE && motherHugging(entity)) {
            return; // original dh.d(f8): while her mother hugs her, she is part of the mother's combined model
        }
        if (entity.kind() == CompanionKind.MANGLELIE && !manglelieShown(entity)) {
            return;
        }
        if (entity.goblin() != null && entity.goblinCarrier().isPresent()
                && renderCarriedGoblin(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)) {
            return;
        }
        if (ManglelieRide.riding(entity)) {
            // original f8.a(Vec3d, float): she is drawn on her mother's animated mangPos bone, not at a fixed height
            CompanionEntity mother = ManglelieRide.mother(entity);
            net.minecraft.world.phys.Vec3 seat = mother == null ? null : boneOffset(mother, "mangPos");
            if (seat != null) {
                net.minecraft.world.phys.Vec3 at = mother.getPosition(partialTick).add(seat);
                net.minecraft.world.phys.Vec3 self = entity.getPosition(partialTick);
                poseStack.pushPose();
                poseStack.translate(at.x - self.x, at.y - self.y, at.z - self.z);
                // original dh.func_76979_b: no shadow while she rides her mother (the dispatcher reads it after render)
                this.shadowRadius = 0.0F;
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                this.clearBoneMarks();
                poseStack.popPose();
                return;
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        this.clearBoneMarks();
        this.renderChargingBalls(entity, partialTick, poseStack, bufferSource);
    }

    /**
     * GeckoLib 4 resets a bone the controllers did not touch only if nothing marked it as changed, and it clears those
     * marks before the model's setCustomAnimations and the render layers run. Their relative tweaks (Kobold eye spacing,
     * bed sway, form poses ...) would then mark the shared bones and pile up frame after frame and across every entity
     * of the model. GeckoLib 3 (the original) reset them each pass, so the marks are cleared once the entity is drawn.
     */
    private void clearBoneMarks() {
        for (software.bernie.geckolib.cache.object.GeoBone bone : this.getGeoModel().getAnimationProcessor().getRegisteredBones()) {
            bone.resetStateChanges();
        }
    }

    /**
     * Original f_$a RenderWorldLastEvent: while Galath summons (ticks 9 to 30) an energy ball grows in each hand that will
     * throw one, drawn at her energyBallR / energyBallL bones with size (tick - 9) / 21.
     */
    private void renderChargingBalls(CompanionEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        if (entity.kind() != CompanionKind.GALATH || entity.bossState() != com.fartypoopy.sexmod.entity.GalathBoss.S_SUMMON) {
            return;
        }
        int tick = entity.summonTick();
        if (tick < 9 || tick > 30) {
            return;
        }
        double size = (tick - 9) / 21.0D;
        String[] bones = {"energyBallR", "energyBallL"};
        int[] flags = {com.fartypoopy.sexmod.entity.GalathBoss.F_ORB_RIGHT, com.fartypoopy.sexmod.entity.GalathBoss.F_ORB_LEFT};
        for (int i = 0; i < 2; i++) {
            net.minecraft.world.phys.Vec3 offset = entity.bossFlag(flags[i]) ? boneOffset(entity, bones[i]) : null;
            if (offset == null) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            EnergyBallRenderer.draw(this.entityRenderDispatcher, entity.level().getGameTime(), partialTick, size, poseStack, buffers);
            poseStack.popPose();
        }
    }

    /** True while the goblin on the local player's shoulder is drawn in first person (original dy.u): her legs and chest sway. */
    static boolean goblinFirstPersonShoulder;
    /** Original dy.x / dy.N: the sway the carrier's view movement gives the goblin (forwards/pitch and sideways). */
    static float goblinSwayX;
    static float goblinSwayZ;
    private static float swayPrevYaw;
    private static float swayPrevPitch;

    private static boolean carryState(int state) {
        return state == com.fartypoopy.sexmod.entity.GoblinBehavior.CATCH
                || state == com.fartypoopy.sexmod.entity.GoblinBehavior.CATCH_BJ
                || state == com.fartypoopy.sexmod.entity.GoblinBehavior.CATCH_BJ_IDLE
                || state == com.fartypoopy.sexmod.entity.GoblinBehavior.PICK_UP
                || state == com.fartypoopy.sexmod.entity.GoblinBehavior.THROWING;
    }

    /** Original am (first person hold on the camera) and dy.a (where and how a carried goblin is drawn for everyone else). */
    private boolean renderCarriedGoblin(CompanionEntity goblin, float entityYaw, float partial, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int packedLight) {
        int state = goblin.goblinState();
        boolean shoulder = state == com.fartypoopy.sexmod.entity.GoblinBehavior.SHOULDER;
        if (!carryState(state) && !shoulder) {
            return false;
        }
        java.util.UUID carrierId = goblin.goblinCarrier().get();
        net.minecraft.world.entity.player.Player carrier = goblin.level().getPlayerByUUID(carrierId);
        Minecraft minecraft = Minecraft.getInstance();
        if (carrier == null || minecraft.player == null) {
            return false;
        }
        boolean first = minecraft.options.getCameraType().isFirstPerson();
        boolean localCarrier = minecraft.player.getUUID().equals(carrierId);
        net.minecraft.world.phys.Vec3 self = goblin.getPosition(partial);
        net.minecraft.world.phys.Vec3 target = self;
        float yaw = Float.NaN;
        float previousYaw = Float.NaN;
        float tilt = 0.0F;
        boolean sneaking = carrier.isShiftKeyDown();
        if (localCarrier && first) {
            net.minecraft.world.entity.player.Player me = minecraft.player;
            net.minecraft.world.phys.Vec3 feet = me.getPosition(partial);
            updateSway(me);
            if (shoulder) {
                // original dy.a: the goblin sits beside the view, bobbing with the camera
                float yawRad = me.getYRot();
                double side = net.minecraft.util.Mth.lerp(minecraft.options.fov().get() / 110.0F, -0.1F, 0.2F);
                double radians = Math.toRadians(yawRad);
                double dx = -Math.cos(radians) * side;
                double dz = -Math.sin(radians) * side;
                float walk = -(me.walkDist + (me.walkDist - me.walkDistO) * partial);
                float bob = net.minecraft.util.Mth.lerp(partial, me.oBob, me.bob);
                double swing = Math.sin(walk * Math.PI) * bob * 0.5D;
                target = feet.add(dx + Math.cos(radians) * swing, Math.abs(Math.cos(walk * Math.PI) * bob) - (sneaking ? 0.075D : 0.0D),
                        dz + Math.sin(radians) * swing);
                yaw = me.getYRot();
                previousYaw = me.yRotO;
                goblinFirstPersonShoulder = true;
            } else {
                net.minecraft.world.phys.Vec3 look = me.getLookAngle();
                target = feet.add(look.x, look.y + me.getEyeHeight(), look.z);
                yaw = me.getYRot() + 180.0F;
                previousYaw = yaw;
                tilt = me.getXRot();
            }
        } else {
            net.minecraft.world.phys.Vec3 at = carrier.getPosition(partial);
            if (state == com.fartypoopy.sexmod.entity.GoblinBehavior.PICK_UP) {
                previousYaw = carrier.yHeadRotO; // the carrier's head turns the goblin while she is lifted
                yaw = carrier.yHeadRot;
            } else if (shoulder) {
                target = at.add(0.0D, sneaking ? -0.32D : 0.0D, 0.0D);
                yaw = net.minecraft.util.Mth.rotLerp(partial, carrier.yBodyRotO, carrier.yBodyRot);
                previousYaw = yaw;
            } else {
                target = at;
                if (state != com.fartypoopy.sexmod.entity.GoblinBehavior.THROWING) {
                    yaw = carrier.getYRot();
                    previousYaw = yaw;
                }
            }
        }
        if (!Float.isNaN(yaw)) {
            goblin.yBodyRotO = Float.isNaN(previousYaw) ? yaw : previousYaw;
            goblin.yBodyRot = yaw;
        }
        this.shadowRadius = state == com.fartypoopy.sexmod.entity.GoblinBehavior.PICK_UP || shoulder ? 0.0F : 0.2F;
        poseStack.pushPose();
        poseStack.translate(target.x - self.x, target.y - self.y, target.z - self.z);
        if (tilt != 0.0F) {
            double yawRad = Math.toRadians(minecraft.player.getYRot());
            poseStack.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(tilt),
                    (float) Math.cos(yawRad), 0.0F, (float) Math.sin(yawRad)));
        }
        super.render(goblin, entityYaw, partial, poseStack, bufferSource, packedLight);
        this.clearBoneMarks();
        poseStack.popPose();
        goblinFirstPersonShoulder = false;
        return true;
    }

    /** Original am.a: the view movement is eased into the sway the first-person goblin shows. */
    private static void updateSway(net.minecraft.world.entity.player.Player me) {
        float yaw = me.getYRot();
        float pitch = -me.getXRot();
        float strafe = me instanceof net.minecraft.client.player.LocalPlayer local ? local.input.leftImpulse : 0.0F;
        float forward = me instanceof net.minecraft.client.player.LocalPlayer local ? local.input.forwardImpulse : 0.0F;
        float n = strafe * 10.0F + -(yaw - swayPrevYaw) * 3.0F;
        n = goblinSwayZ + (n - goblinSwayZ) * 0.1F;
        float x = (float) (forward * -10.0D + (float) me.getDeltaMovement().y * -20.0D) + -(pitch - swayPrevPitch) * 3.0F;
        x = goblinSwayX + (x - goblinSwayX) * 0.1F;
        swayPrevYaw = yaw;
        swayPrevPitch = pitch;
        goblinSwayZ = n;
        goblinSwayX = x;
    }

    /** Original d_.b(T, f, f, f): a form whose player glides on an elytra lies in the flight direction like the player. */
    @Override
    protected void applyRotations(CompanionEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick, float nativeScale) {
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        if (!entity.isPlayerForm()) {
            return;
        }
        Player owner = FormRenderExtras.owner(entity);
        if (owner == null || !owner.isFallFlying()) {
            return;
        }
        float ticks = owner.getFallFlyingTicks() + partialTick;
        float amount = net.minecraft.util.Mth.clamp(ticks * ticks / 100.0F, 0.0F, 1.0F);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(amount * (-90.0F - owner.getXRot())));
        net.minecraft.world.phys.Vec3 look = owner.getViewVector(partialTick);
        net.minecraft.world.phys.Vec3 motion = owner.getDeltaMovement();
        double speed = motion.x * motion.x + motion.z * motion.z;
        double facing = look.x * look.x + look.z * look.z;
        if (speed > 0.0D && facing > 0.0D) {
            double cos = (motion.x * look.x + motion.z * look.z) / (Math.sqrt(speed) * Math.sqrt(facing));
            double side = motion.x * look.z - motion.z * look.x;
            poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) (Math.signum(side) * Math.acos(Math.min(1.0D, cos)))));
        }
    }

    @Override
    public boolean shouldRender(CompanionEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        if (entity.goblin() != null && entity.goblinCarrier().isPresent() && Minecraft.getInstance().player != null
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && entity.goblinCarrier().get().equals(Minecraft.getInstance().player.getUUID())) {
            return true; // original am drew her from RenderWorldLast, outside the frustum test
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && (entity.scenePartnerId().filter(minecraft.player.getUUID()::equals).isPresent()
                || entity.getFirstPassenger() == minecraft.player)) {
            // the first-person camera sits on her boyCam bone as of her last drawn frame; culled once (that bone was
            // still where she stood before the scene, facing away from her new spot), she was never drawn again and
            // the view stayed on an empty field (tamed Galath cowgirl)
            return true;
        }
        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    /**
     * The layers (wing membranes, hair, skirt, cloth ...) place their quads from the bones' model space matrices, which
     * GeckoLib measures from the frame the model itself was drawn in: her yaw and the small lift included. The pose stack
     * the layers get lacks both (a Galath turned 90 degrees had her membranes drawn a quarter turn off her wings), so the
     * layers run in the model's own frame.
     */
    @Override
    public void applyRenderLayers(PoseStack poseStack, CompanionEntity entity, software.bernie.geckolib.cache.object.BakedGeoModel model,
                                  net.minecraft.client.renderer.RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        org.joml.Matrix4f frame = this.modelRenderTranslations;
        if (frame == null) {
            super.applyRenderLayers(poseStack, entity, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
            return;
        }
        poseStack.pushPose();
        poseStack.last().pose().set(frame);
        poseStack.last().normal().set(frame.normal(new org.joml.Matrix3f()));
        try {
            super.applyRenderLayers(poseStack, entity, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public void preRender(
            PoseStack poseStack,
            CompanionEntity entity,
            BakedGeoModel model,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int renderColor
    ) {
        super.preRender(
                poseStack,
                entity,
                model,
                bufferSource,
                buffer,
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                renderColor
        );
        if (!isReRender) {
            CustomModelRender.prepare(entity, this.modelHiddenBones, this.attachByBone);
            this.attachments.clear();
        }

        float baseScale = entity.kind() == CompanionKind.KOBOLD ? 0.75F + entity.koboldSize() : 1.0F;
        float scale = baseScale * entity.appearanceScale();
        if (entity.allieFadeStart >= 0L && entity.level() != null) {
            // original d8: while she fades out she shrinks to U and rises by 3 - 3U
            float u = Math.max(0.0F, 1.0F - (entity.level().getGameTime() + partialTick - entity.allieFadeStart) / 33.0F);
            poseStack.translate(0.0F, 3.0F - 3.0F * u, 0.0F);
            scale *= u;
        }
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void renderRecursively(
            PoseStack poseStack,
            CompanionEntity entity,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int renderColor
    ) {
        if (entity.kind() == CompanionKind.MANGLELIE
                && entity.currentScene() == com.fartypoopy.sexmod.entity.CompanionScene.MANGLELIE_THREESOME
                && bone.getParent() != null && bone.getParent().getParent() == null
                && !bone.getName().equals("steve") && !bone.getName().equals("body2")) {
            return; // original dh: in the threesome only the combined model's player and daughter halves are drawn
        }
        if (entity.isPlayerForm() && !isReRender && this.formPass != null) {
            FormRenderExtras.elytra(entity, this.formPass, bone, poseStack, bufferSource, packedLight);
            FormRenderExtras.heldItem(entity, this.formPass, bone, poseStack, bufferSource, packedLight);
            // an item or elytra drawn at this bone or a sibling ends the shared batch: every form bone takes it afresh
            buffer = bufferSource.getBuffer(renderType);
        }
        if (!entity.isPlayerForm() && !isReRender && NpcItems.draw(entity, bone, poseStack, bufferSource, packedLight, partialTick)) {
            buffer = bufferSource.getBuffer(renderType); // original d_: the item ends the batch, the bone's own quads start a new one
        }
        if (bone.getParent() == null) {
            // original gx: the quads are shaded in model space; this undoes her base pose (yaw, scale, GUI rotation)
            this.shadeToModel = new org.joml.Matrix3f(poseStack.last().normal()).invert();
            this.shadeEntity = entity;
            this.shadeLight = LightTint.lightInModelSpace(entity, partialTick);
        }
        if (!isReRender && !this.attachByBone.isEmpty()) {
            java.util.List<String> worn = this.attachByBone.get(bone.getName());
            if (worn != null) {
                CustomModelRender.capture(poseStack, bone, worn, this.attachments);
            }
        }
        if (bone.getName().equals("Head2")) {
            // original d_.c() / em.n(): the partner's head is skipped while the local player is that partner (em.ae(),
            // also before the scene, e.g. the payment) in first person. Children (the camera bone) still update.
            Minecraft minecraft = Minecraft.getInstance();
            boolean localPartner = minecraft.player != null
                    && (entity.scenePartnerId().filter(minecraft.player.getUUID()::equals).isPresent()
                    || entity.getFirstPassenger() == minecraft.player); // riding: ae() is the rider in 1.12 too
            bone.setHidden(localPartner && minecraft.options.getCameraType().isFirstPerson());
            bone.setChildrenHidden(false); // setHidden also hides children, which would freeze the camera bone
        }
        if (poseBone(entity, bone.getName())) {
            bone.setTrackingMatrices(true);
            POSE_BONES.computeIfAbsent(entity.getId(), id -> new java.util.concurrent.ConcurrentHashMap<>()).put(bone.getName(), bone);
        }
        if (GalathMeshLayer.tracks(bone.getName())
                || (entity.kind() == CompanionKind.GALATH && bone.getName().equals("head"))) {
            bone.setTrackingMatrices(true);
        }
        if (bone.getName().startsWith("skirt_") || bone.getName().startsWith("clothBoob")) {
            bone.setTrackingMatrices(true); // SkirtLayer joins these bones with quads
        }
        if (bone.getName().equals("girlCam")) {
            bone.setTrackingMatrices(true);
            GIRL_CAMS.put(entity.getId(), bone); // original em.b("girlCam"): the girl-player's eye in her scenes
        }
        if (bone.getName().equals("boyCam")) {
            bone.setTrackingMatrices(true);
            BOY_CAMS.put(entity.getId(), bone);
        }
        if (bone.getName().equalsIgnoreCase("steve")) {
            // original d_.a(GeoModel, ...): the steve bone always wears a player skin (the partner's, else the local
            // player's), also outside a scene such as the payment arm-up
            PlayerSkin skin = partnerSkin(entity);
            renderType = RenderType.entityTranslucent(skin.texture());
            buffer = bufferSource.getBuffer(renderType);
        }

        if (isPartnerProp(bone)) {
            // original d_: the skin gets two painted patches (0,0,4,3 skin tone; 4,0,3,3 pink) that this mesh samples
            renderType = RenderType.entityCutoutNoCull(propTexture());
            buffer = bufferSource.getBuffer(renderType);
        }
        if (bone.getName().equals("body2") && CompanionModel.combinedModel(entity)) {
            // original da: in the combined model the daughter's half (body2) is drawn with her own texture
            renderType = RenderType.entityCutoutNoCull(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "sexmod", "textures/entity/manglelie/manglelie.png"));
            buffer = bufferSource.getBuffer(renderType);
        }
        if (entity.allieFadeStart >= 0L && entity.level() != null) {
            float fade = 1.0F - (entity.level().getGameTime() + partialTick - entity.allieFadeStart) / 33.0F;
            if (fade <= 0.0F) {
                return;
            }
            renderType = RenderType.entityTranslucent(this.getTextureLocation(entity));
            buffer = bufferSource.getBuffer(renderType);
            renderColor = (Math.min(255, (int) (fade * 255.0F)) << 24) | (renderColor & 0xFFFFFF);
        }

        if (entity.kind() == CompanionKind.GALATH && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && underBone(bone, "steve", "body2") && !isPartnerProp(bone)) { // d_: cock / ball bones keep alpha 1
            // original da with f_.v(): in first person the player ("steve") and the daughter ("body2") are drawn half
            // transparent in the corrupt scene (the intro only after setCamCorrupt); Galath herself stays opaque
            String animation = GalathMeshLayer.playing(entity);
            if (animation.equals("animation.galath.corrupt_slow") || animation.equals("animation.galath.corrupt_soft")
                    || animation.equals("animation.galath.corrupt_hard") || animation.equals("animation.galath.corrupt_cum")
                    || (animation.equals("animation.galath.corrupt_intro") && entity.galathCorruptCam)) {
                renderType = RenderType.entityTranslucent(underBone(bone, "steve", "steve")
                        ? partnerSkin(entity).texture()
                        : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sexmod", "textures/entity/manglelie/manglelie.png"));
                buffer = bufferSource.getBuffer(renderType);
                renderColor = 0x80000000 | (renderColor & 0xFFFFFF);
            }
        }
        if (entity.kind() == CompanionKind.KOBOLD) {
            renderColor = koboldTint(entity, bone.getName(), renderColor);
        }
        if (entity.kind() == CompanionKind.GALATH && bone.getName().equals("pentagram")
                && "animation.galath.give_coin".equals(GalathMeshLayer.playing(entity))) {
            // original da: the pentagram turns from grey to pink and glows brighter between ticks 105 and 125 of the coin scene
            float glow = (net.minecraft.util.Mth.clamp(GalathMeshLayer.elapsed(entity, "animation.galath.give_coin", partialTick),
                    105.0F, 125.0F) - 105.0F) / 20.0F;
            int r = (int) (255.0F * net.minecraft.util.Mth.lerp(glow, 0.44705883F, 0.84705883F));
            int g = (int) (255.0F * net.minecraft.util.Mth.lerp(glow, 0.44705883F, 0.11764706F));
            int b = (int) (255.0F * net.minecraft.util.Mth.lerp(glow, 0.44705883F, 0.35686275F));
            renderColor = 0xFF000000 | (r << 16) | (g << 8) | b;
            int level = (int) (net.minecraft.util.Mth.lerp(glow, 120.0F, 240.0F) / 16.0F);
            packedLight = net.minecraft.client.renderer.LightTexture.pack(level, level);
        }
        net.minecraft.world.entity.EquipmentSlot armourSlot = CompanionModel.armourSlot(bone.getName().toLowerCase(java.util.Locale.ROOT));
        if (armourSlot != null && (entity.kind() == CompanionKind.JENNY || entity.kind() == CompanionKind.ELLIE
                || entity.kind() == CompanionKind.BIA || entity.kind() == CompanionKind.LUNA)
                && entity.getItemBySlot(armourSlot).getItem() instanceof net.minecraft.world.item.ArmorItem armour) {
            // original d_.a(String, r, g, b): the material picks a 72 pixel wide strip of the texture, leather is dyed
            var material = armour.getMaterial();
            int strip = 0;
            if (material.is(net.minecraft.world.item.ArmorMaterials.LEATHER)) {
                strip = 4;
                int dye = net.minecraft.world.item.component.DyedItemColor.getOrDefault(entity.getItemBySlot(armourSlot), -6265536);
                renderColor = (renderColor & 0xFF000000) | (dye & 0xFFFFFF);
            } else if (material.is(net.minecraft.world.item.ArmorMaterials.CHAIN) || material.is(net.minecraft.world.item.ArmorMaterials.IRON)) {
                strip = 2;
            } else if (material.is(net.minecraft.world.item.ArmorMaterials.GOLD)) {
                strip = 1;
            } else if (material.is(net.minecraft.world.item.ArmorMaterials.NETHERITE)) {
                strip = 5; // not in the original (pre-netherite): a recoloured diamond strip added to the textures
            }
            if (strip != 0) {
                buffer = new UvShift(buffer, 72.0F * strip / 4096.0F);
            }
        }
        if (entity.kind() == CompanionKind.KOBOLD && entity.koboldGene(7) == 1) {
            // original dj / de: the second mouth (gene 8) samples the texture 0.078125 to the left; on the NPC the shift
            // carries to the mouth's children except blowOpening (a form's bones each take the buffer afresh)
            if (bone.getName().equals("mouth")) {
                buffer = new UvShift(buffer, -0.078125F);
            } else if (bone.getName().equals("blowOpening") && buffer instanceof UvShift shift) {
                buffer = shift.delegate;
            }
        }
        if (entity.goblin() != null) {
            renderColor = goblinLook(entity, bone, renderColor);
        }
        if (entity.goblin() != null && entity.goblinFade() < 1.0F) {
            // original dy.a(GeoModel, e3, ...): a vanishing robber is drawn with alpha e3.ar (-0.05 per tick)
            renderType = RenderType.entityTranslucent(this.getTextureLocation(entity));
            buffer = bufferSource.getBuffer(renderType);
            renderColor = ((int) (Math.max(0.0F, entity.goblinFade()) * 255.0F) << 24) | (renderColor & 0xFFFFFF);
        }
        if (entity.kind() == CompanionKind.GALATH && entity.isPlayerForm()) {
            // original dx.a(String, r, g, b): her clothes show her player's armour - a UV shift per material, leather dyed
            net.minecraft.world.entity.EquipmentSlot slot = switch (bone.getName()) {
                case "braBoobL", "braBoobR", "armorNippleR", "armorNippleL" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                case "turnable", "static", "slip" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                case "shinL", "shinR", "sockL", "sockR", "kneeL", "kneeR" -> net.minecraft.world.entity.EquipmentSlot.FEET;
                default -> null;
            };
            if (slot != null && entity.getItemBySlot(slot).getItem() instanceof net.minecraft.world.item.ArmorItem armour) {
                var material = armour.getMaterial();
                float shift = -0.1875F;
                if (material.is(net.minecraft.world.item.ArmorMaterials.GOLD)) {
                    shift = -0.15625F;
                } else if (material.is(net.minecraft.world.item.ArmorMaterials.IRON) || material.is(net.minecraft.world.item.ArmorMaterials.CHAIN)) {
                    shift = -0.125F;
                } else if (material.is(net.minecraft.world.item.ArmorMaterials.LEATHER)) {
                    shift = -0.09375F;
                    int dye = net.minecraft.world.item.component.DyedItemColor.getOrDefault(entity.getItemBySlot(slot), -6265536);
                    int r = ((renderColor >> 16) & 0xFF) * ((dye >> 16) & 0xFF) / 255;
                    int g = ((renderColor >> 8) & 0xFF) * ((dye >> 8) & 0xFF) / 255;
                    int b = (renderColor & 0xFF) * (dye & 0xFF) / 255;
                    renderColor = (renderColor & 0xFF000000) | (r << 16) | (g << 8) | b;
                }
                buffer = new UvShift(buffer, shift);
            }
        }

        FormRenderExtras.Pass form = entity.isPlayerForm() && !isReRender ? this.formPass : null;
        float[] formSaved = null;
        boolean hideHead = false;
        if (form != null) {
            formSaved = FormRenderExtras.poseBone(entity, form, bone);
            hideHead = !this.hidingFormHead && FormRenderExtras.hidesHead(entity, bone);
            if (hideHead) {
                this.hidingFormHead = true; // her own head and neck (with everything on them) are not drawn, their bones still move
            }
        }
        // original dj.b() / de.b(): the partner ("steve") is drawn after the Kobold's size scale is undone
        boolean unscale = entity.kind() == CompanionKind.KOBOLD && bone.getParent() == null && bone.getName().equals("steve");
        if (unscale) {
            float size = 0.75F + entity.koboldSize();
            poseStack.pushPose();
            poseStack.scale(1.0F / size, 1.0F / size, 1.0F / size);
        }
        try {
            super.renderRecursively(
                    poseStack,
                    entity,
                    bone,
                    renderType,
                    bufferSource,
                    buffer,
                    isReRender,
                    partialTick,
                    packedLight,
                    packedOverlay,
                    renderColor
            );
        } finally {
            if (unscale) {
                poseStack.popPose();
            }
            if (formSaved != null) {
                FormRenderExtras.restore(bone, formSaved);
            }
            if (hideHead) {
                this.hidingFormHead = false;
            }
        }
    }

    private FormRenderExtras.Pass formPass;
    private boolean hidingFormHead;

    /** Original d_.p: bones a worn custom model hides (their cubes only; child bones still draw). */
    private final java.util.Set<String> modelHiddenBones = new java.util.HashSet<>();
    private final java.util.Map<String, java.util.List<String>> attachByBone = new java.util.HashMap<>();
    private final java.util.List<CustomModelRender.Attachment> attachments = new java.util.ArrayList<>();

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
                                  int colour) {
        if (this.modelHiddenBones.contains(bone.getName()) || this.hidingFormHead) {
            return;
        }
        this.shadeOn = this.shadeEntity != null && this.shadeToModel != null && LightTint.shaded(this.shadeEntity, bone);
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
        this.shadeOn = false;
    }

    private org.joml.Matrix3f shadeToModel;
    private CompanionEntity shadeEntity;
    private org.joml.Vector3f shadeLight = new org.joml.Vector3f(0.0F, 1.0F, 0.0F);
    private boolean shadeOn;

    private static final org.joml.Vector3f UNLIT_NORMAL = new org.joml.Vector3f(0.0F, 1.0F, 0.0F);

    /** Original d_ quad loop with gx.a(this, bone, colour, normal): the light tint per quad. */
    @Override
    public void createVerticesOfQuad(software.bernie.geckolib.cache.object.GeoQuad quad, org.joml.Matrix4f poseState,
                                     org.joml.Vector3f normal, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (this.shadeOn) {
            colour = LightTint.tint(colour, normal, this.shadeToModel, this.shadeLight);
        }
        // original d_: GL_LIGHTING is off while the girl is drawn, so only gx's tint shades her. 1.21's entity shaders
        // light by the vertex normal; an upward (world) normal is fully lit by both level lights
        super.createVerticesOfQuad(quad, poseState, UNLIT_NORMAL, buffer, packedLight, packedOverlay, colour);
    }

    /** Original b: the worn custom models, drawn after her with their own textures. */
    @Override
    public void postRender(PoseStack poseStack, CompanionEntity entity, BakedGeoModel model, MultiBufferSource bufferSource,
                           VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                           int colour) {
        super.postRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (!isReRender) {
            for (CustomModelRender.Attachment attachment : this.attachments) {
                CustomModelRender.draw(attachment, bufferSource, packedLight, this.shadeLight);
            }
            this.attachments.clear();
        }
    }

    private static final java.util.Map<String, int[]> GOBLIN_FIELDS = new java.util.HashMap<>();
    private static final java.util.Set<String> GOBLIN_SKIN = java.util.Set.of("meatTorso", "meatCheekR", "meatCheekL", "meatFootR",
            "meatFootL", "meatShinR", "meatShinL", "meatLegL", "meatLegR", "nippleR", "nippleL", "preggy", "shoeL", "shoeR",
            "frontAndInside", "Lside", "Rside", "cheekR", "cheekL", "fuckhole", "head", "nose", "neck", "armL", "lowerArmL",
            "armR", "lowerArmR", "torso", "LegL", "LegR", "shinL", "shinR");
    private static final java.util.Set<String> GOBLIN_LASHES = java.util.Set.of("lashR", "lashL", "closedR", "closedL", "browL", "browR");

    /** Original dy: ears, hair and crown pick their bones from the model string; every bone is tinted by name. */
    private static int goblinLook(CompanionEntity entity, GeoBone bone, int renderColor) {
        int[] fields = GOBLIN_FIELDS.computeIfAbsent(entity.goblinModel(), com.fartypoopy.sexmod.entity.GoblinModel::parse);
        String name = bone.getName();
        if (fields.length >= 10) {
            switch (name) {
                case "earL" -> goblinEar(bone, fields[0], fields[1], fields[3]);
                case "earR" -> goblinEar(bone, fields[0], fields[2], fields[4]);
                case "hair" -> goblinSelect(bone, fields[5]);
                default -> {
                }
            }
            if (name.contains("crown")) {
                bone.setHidden(fields[9] == 0);
            }
        }
        int rgb = com.fartypoopy.sexmod.entity.GoblinModel.tint(name, fields, GOBLIN_SKIN, GOBLIN_LASHES);
        return rgb < 0 ? renderColor : (renderColor & 0xFF000000) | rgb;
    }

    /** Original d6.a(bone, index): children sorted by pivot height, only the chosen one is shown. */
    private static GeoBone goblinSelect(GeoBone parent, int index) {
        java.util.List<GeoBone> sorted = new java.util.ArrayList<>(parent.getChildBones());
        sorted.sort(java.util.Comparator.comparingDouble(GeoBone::getPivotY));
        GeoBone chosen = null;
        for (int i = 0; i < sorted.size(); i++) {
            boolean show = i == index;
            sorted.get(i).setHidden(!show);
            if (show) {
                chosen = sorted.get(i);
            }
        }
        return chosen;
    }

    private static void goblinEar(GeoBone ear, int group, int sub, int accessories) {
        GeoBone groupBone = goblinSelect(ear, group);
        GeoBone subBone = groupBone == null ? null : goblinSelect(groupBone, sub);
        if (subBone == null) {
            return;
        }
        java.util.List<GeoBone> children = subBone.getChildBones();
        for (GeoBone child : children) {
            child.setHidden(true);
        }
        for (int index : com.fartypoopy.sexmod.entity.GoblinModel.subset(children.size(), accessories)) {
            if (index < children.size()) {
                children.get(index).setHidden(false);
            }
        }
    }

    private static final java.util.Set<String> BODY_BONES = java.util.Set.of("colorSpots", "neck", "head", "snout", "midSectionR",
            "midSectionL", "innerCheekLR", "innerCheekRR", "gayL", "gayR", "legR", "legL", "shinL", "toesL", "kneeL", "curvesL",
            "shinR", "toesR", "kneeR", "curvesR", "sideL", "sideR", "hip", "torsoL", "torsoR", "armR", "lowerArmR", "ellbowR",
            "armL", "lowerArmL", "ellbowL", "hornUL", "hornUR", "tail", "tail2", "tail3", "tail4", "tail5", "hornDL2", "hornDR2",
            "hornDR3M", "hornDL3M", "frecklesAL1", "frecklesAL2", "frecklesAR1", "frecklesAR2", "frecklesHL1", "frecklesHL2",
            "frecklesHR1", "frecklesHR2");
    private static final java.util.Set<String> SECONDARY_BONES = java.util.Set.of("boobR", "boobL", "frontNeck", "Rside", "Lside",
            "frontAndInside", "innerCheekLL", "innerCheekRL", "layer", "layer2", "down", "down2", "down3", "down4", "down5",
            "fuckhole", "hornDR3S", "hornDL3S", "assholeCoverUp", "assholeCoverUp2");

    /** Original de.a(String): body bones use the body colour, secondary bones its secondary, irises the eye colour. */
    private static int koboldTint(CompanionEntity entity, String bone, int renderColor) {
        int alpha = renderColor & 0xFF000000;
        if (BODY_BONES.contains(bone)) {
            return alpha | entity.bodyColor().mainRgb();
        }
        if (SECONDARY_BONES.contains(bone)) {
            return alpha | entity.bodyColor().secondaryRgb();
        }
        if (bone.equals("irisR") || bone.equals("irisL")) {
            return alpha | entity.eyeColor().mainRgb();
        }
        return alpha | 0xFFFFFF;
    }

    /** Original fp.hideNameTag, matched on the animation each flagged state plays. */
    private static boolean hidesNameTag(String animation) {
        // PAIZURI_* (goblin and Jenny's boobjob), NELSON_*, PICK_UP, SHOULDER_IDLE, CARRY_*, PRONE_DOGGY_*, WAVE(_IDLE),
        // SUMMON / SUMMON_WAIT (not summon_normal / _sand), CONTROLLED_FLIGHT, BOOST, GALATH_DE_SUMMON, GIVE_COIN and
        // Manglelie's THREESOME_* (double_holding)
        return animation.startsWith("animation.goblin.paizuri") || animation.startsWith("animation.jenny.paizuri")
                || animation.startsWith("animation.goblin.nelson")
                || animation.startsWith("animation.goblin.pick_up") || animation.equals("animation.goblin.shoulder_idle")
                || animation.startsWith("animation.ellie.carry") || animation.startsWith("animation.bia.prone_doggy")
                || animation.equals("animation.bia.wave") || animation.equals("animation.bia.wave_idle")
                || animation.equals("animation.jenny.wave") || animation.equals("animation.jenny.wave_idle")
                || animation.equals("animation.allie.summon") || animation.equals("animation.allie.summon_wait")
                || animation.equals("animation.galath.controlled_flight") || animation.equals("animation.galath.boost")
                || animation.equals("animation.galath.desummon") || animation.equals("animation.galath.give_coin")
                || animation.startsWith("animation.shared.double_holding");
    }

    /** Original dj: tribe members show their coloured tribe name over their head. */
    @Override
    public boolean shouldShowName(CompanionEntity entity) {
        if (!entity.isPlayerForm() && entity.kind() == CompanionKind.KOBOLD && !"null".equals(entity.tribeName())) {
            return npcLabel(entity); // original dj replaces the whole label method, hideNameTag included
        }
        if (hidesNameTag(GalathMeshLayer.playing(entity))) {
            return false; // original fp.hideNameTag
        }
        if (entity.isPlayerForm()) {
            // original dm.a(EntityPlayer, em): her player's name, except for the player herself
            Player owner = FormRenderExtras.owner(entity);
            return owner != null && Minecraft.getInstance().player != null && owner != Minecraft.getInstance().player
                    && !owner.isInvisibleTo(Minecraft.getInstance().player);
        }
        return npcLabel(entity);
    }

    /**
     * Original d_.a(double, double, double) with em.t(): an NPC's name always floats over her (within 300 blocks), unless
     * her action hides it, she is a GUI preview, or her class says no: the Slime never, Ellie not while carried, Manglelie
     * not on her mother's head, Allie only during an action, a goblin only when free and tamed or in sight.
     */
    private static boolean npcLabel(CompanionEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(entity.getId()) != entity || minecraft.player == null) {
            return false;
        }
        if (minecraft.getEntityRenderDispatcher().distanceToSqr(entity) > 300.0D * 300.0D) {
            return false;
        }
        CompanionAction action = entity.action();
        return switch (entity.kind()) {
            case SLIME -> false;
            case MANGLELIE -> action != CompanionAction.MANG_RIDE;
            case ALLIE -> action != CompanionAction.NONE || entity.isInScene();
            case GOBLIN -> entity.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.NULL && !entity.isInScene()
                    && entity.goblinCarrier().isEmpty() && (entity.goblinTamed() || minecraft.player.hasLineOfSight(entity));
            default -> true;
        };
    }

    /** Original i() per girl: the label's height above the usual spot (her height + half a block). */
    private static float labelOffset(CompanionEntity entity) {
        return switch (entity.kind()) {
            case JENNY, BIA, LUNA -> -0.2F;
            case ELLIE -> 0.4F;
            case GOBLIN -> 0.1F;
            case KOBOLD -> 0.2F - (0.25F - entity.koboldSize());
            case GALATH -> entity.familyId() == null ? 0.5F : 1.35F;
            case ALLIE -> 1.0F;
            case BEE -> -0.1F;
            default -> 0.0F;
        };
    }

    @Override
    protected void renderNameTag(CompanionEntity entity, net.minecraft.network.chat.Component displayName, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (entity.isPlayerForm()) {
            // original dm: the label at y + i() (+ the usual half block), a quarter lower while she sneaks
            Player owner = FormRenderExtras.owner(entity);
            poseStack.pushPose();
            poseStack.translate(0.0F, FormRenderExtras.nameTagHeight(entity, partialTick) - entity.getBbHeight()
                    - (owner != null && owner.isCrouching() ? 0.25F : 0.0F), 0.0F);
            super.renderNameTag(entity, owner != null ? owner.getDisplayName() : displayName, poseStack, bufferSource, packedLight, partialTick);
            poseStack.popPose();
            return;
        }
        if (entity.kind() == CompanionKind.KOBOLD && !"null".equals(entity.tribeName())) {
            // original dj: her name, then " -tribe-" in her colour
            displayName = entity.getName().copy().append(net.minecraft.network.chat.Component.literal(" -" + entity.tribeName() + "-")
                    .withStyle(entity.bodyColor().chat()));
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, labelOffset(entity), 0.0F);
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
        poseStack.popPose();
    }

    private static net.minecraft.resources.ResourceLocation propTextureLocation;

    private static boolean underBone(GeoBone bone, String first, String second) {
        for (GeoBone current = bone; current != null; current = current.getParent()) {
            if (current.getName().equals(first) || current.getName().equals(second)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPartnerProp(GeoBone bone) {
        for (GeoBone current = bone; current != null; current = current.getParent()) {
            String name = current.getName();
            if (name.equals("cock") || name.equals("ballL") || name.equals("ballR")) {
                return true;
            }
            if (name.equals("steve")) {
                return false;
            }
        }
        return false;
    }

    private static net.minecraft.resources.ResourceLocation propTexture() {
        if (propTextureLocation == null) {
            com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 7; x++) {
                    // NativeImage pixels are ABGR
                    image.setPixelRGBA(x, y, x < 4 ? 0xFFA5C7F5 : 0xFFA99DF5);
                }
            }
            propTextureLocation = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.fartypoopy.sexmod.FapCraft.MOD_ID, "dynamic/partner_prop");
            Minecraft.getInstance().getTextureManager().register(
                    propTextureLocation, new net.minecraft.client.renderer.texture.DynamicTexture(image));
        }
        return propTextureLocation;
    }

    /** Original em.ae() for the goblin: while she is lifted / thrown the steve bone wears the carrier's skin. */
    private static java.util.UUID carriedSkinId(CompanionEntity entity) {
        if (entity.kind() != CompanionKind.GOBLIN) {
            return null;
        }
        int state = entity.goblinState();
        if (state != com.fartypoopy.sexmod.entity.GoblinBehavior.PICK_UP
                && state != com.fartypoopy.sexmod.entity.GoblinBehavior.THROWING) {
            return null;
        }
        return entity.goblinCarrier().orElse(null);
    }

    /**
     * Original dh.a(f8, ...): she is not drawn while her mother is in a combined-model scene (f_.b(): hug, morning
     * blowjob), flies under a player's control (CONTROLLED_FLIGHT / BOOST), or her mother's body is shrunk below half
     * (f_.bm, the summon / dismiss scale).
     */
    private static boolean manglelieShown(CompanionEntity daughter) {
        CompanionEntity mother = ManglelieRide.mother(daughter);
        if (mother == null) {
            return true;
        }
        if (CompanionModel.combinedModel(mother)) {
            return false;
        }
        String animation = GalathMeshLayer.playing(mother);
        if (animation.equals("animation.galath.controlled_flight") || animation.equals("animation.galath.boost")) {
            return false;
        }
        return mother.galathBodyScale >= 0.5F;
    }

    static boolean motherHugging(CompanionEntity daughter) {
        java.util.UUID mother = daughter.familyId();
        if (mother == null || daughter.level() == null) {
            return false;
        }
        for (CompanionEntity candidate : daughter.level().getEntitiesOfClass(CompanionEntity.class,
                daughter.getBoundingBox().inflate(8.0D), c -> mother.equals(c.getUUID()))) {
            if (candidate.action() == com.fartypoopy.sexmod.entity.CompanionAction.GALATH_HUG_MANG) {
                return true;
            }
        }
        return false;
    }

    static PlayerSkin partnerSkin(CompanionEntity entity) {
        var carried = carriedSkinId(entity);
        Minecraft minecraft = Minecraft.getInstance();
        var partnerId = carried != null ? carried : entity.scenePartnerId()
                .orElse(minecraft.player != null ? minecraft.player.getUUID() : entity.getUUID()); // d_.d(): else the local player
        var connection = minecraft.getConnection();
        PlayerInfo playerInfo = connection == null ? null : connection.getPlayerInfo(partnerId);
        return playerInfo == null ? DefaultPlayerSkin.get(partnerId) : playerInfo.getSkin();
    }

    /** Adds a fixed offset to the U coordinate of every vertex (the armour material strips of the girls' textures). */
    private static final class UvShift implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float offset;

        private UvShift(VertexConsumer delegate, float offset) {
            this.delegate = delegate;
            this.offset = offset;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.delegate.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u + this.offset, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
