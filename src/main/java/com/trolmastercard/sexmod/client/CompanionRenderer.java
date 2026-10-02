package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.client.Minecraft;
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

    /** The animated {@code boyCam} bone as of the last frame this entity was drawn, or null. */
    public static GeoBone boyCam(int entityId) {
        return BOY_CAMS.get(entityId);
    }

    private static final java.util.Map<Integer, java.util.Map<String, GeoBone>> POSE_BONES = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean poseBone(CompanionEntity entity, String name) {
        return (entity.kind() == CompanionKind.GALATH && name.equals("mangPos"))
                || (entity.kind() == CompanionKind.MANGLELIE && (name.equals("armR") || name.equals("armL") || name.equals("head")));
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
        this.addRenderLayer(new FishingItemLayer(this));
        this.addRenderLayer(new SkirtLayer(this));
        this.addRenderLayer(new ClothLayer(this));
        this.addRenderLayer(new GalathMeshLayer(this));
    }

    @Override
    public void render(CompanionEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        var scene = entity.currentScene();
        if (scene != null && scene.hidesEntity()) {
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
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                poseStack.popPose();
                return;
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** True while the goblin on the local player's shoulder is drawn in first person (original dy.u): her legs and chest sway. */
    static boolean goblinFirstPersonShoulder;
    /** Original dy.x / dy.N: the sway the carrier's view movement gives the goblin (forwards/pitch and sideways). */
    static float goblinSwayX;
    static float goblinSwayZ;
    private static float swayPrevYaw;
    private static float swayPrevPitch;

    private static boolean carryState(int state) {
        return state == com.trolmastercard.sexmod.entity.GoblinBehavior.CATCH
                || state == com.trolmastercard.sexmod.entity.GoblinBehavior.CATCH_BJ
                || state == com.trolmastercard.sexmod.entity.GoblinBehavior.CATCH_BJ_IDLE
                || state == com.trolmastercard.sexmod.entity.GoblinBehavior.PICK_UP
                || state == com.trolmastercard.sexmod.entity.GoblinBehavior.THROWING;
    }

    /** Original am (first person hold on the camera) and dy.a (where and how a carried goblin is drawn for everyone else). */
    private boolean renderCarriedGoblin(CompanionEntity goblin, float entityYaw, float partial, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int packedLight) {
        int state = goblin.goblinState();
        boolean shoulder = state == com.trolmastercard.sexmod.entity.GoblinBehavior.SHOULDER;
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
            if (state == com.trolmastercard.sexmod.entity.GoblinBehavior.PICK_UP) {
                previousYaw = carrier.yHeadRotO; // the carrier's head turns the goblin while she is lifted
                yaw = carrier.yHeadRot;
            } else if (shoulder) {
                target = at.add(0.0D, sneaking ? -0.32D : 0.0D, 0.0D);
                yaw = net.minecraft.util.Mth.rotLerp(partial, carrier.yBodyRotO, carrier.yBodyRot);
                previousYaw = yaw;
            } else {
                target = at;
                if (state != com.trolmastercard.sexmod.entity.GoblinBehavior.THROWING) {
                    yaw = carrier.getYRot();
                    previousYaw = yaw;
                }
            }
        }
        if (!Float.isNaN(yaw)) {
            goblin.yBodyRotO = Float.isNaN(previousYaw) ? yaw : previousYaw;
            goblin.yBodyRot = yaw;
        }
        this.shadowRadius = state == com.trolmastercard.sexmod.entity.GoblinBehavior.PICK_UP || shoulder ? 0.0F : 0.2F;
        poseStack.pushPose();
        poseStack.translate(target.x - self.x, target.y - self.y, target.z - self.z);
        if (tilt != 0.0F) {
            double yawRad = Math.toRadians(minecraft.player.getYRot());
            poseStack.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(tilt),
                    (float) Math.cos(yawRad), 0.0F, (float) Math.sin(yawRad)));
        }
        super.render(goblin, entityYaw, partial, poseStack, bufferSource, packedLight);
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

    @Override
    public boolean shouldRender(CompanionEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        if (entity.goblin() != null && entity.goblinCarrier().isPresent() && Minecraft.getInstance().player != null
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && entity.goblinCarrier().get().equals(Minecraft.getInstance().player.getUUID())) {
            return true; // original am drew her from RenderWorldLast, outside the frustum test
        }
        return super.shouldRender(entity, frustum, camX, camY, camZ);
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

        float baseScale = entity.kind() == CompanionKind.KOBOLD ? 0.75F + entity.koboldSize() : 1.0F;
        if (entity.goblin() != null) {
            // original dy.a(): a caught goblin is drawn at half size; a vanishing robber shrinks with its fade
            baseScale = (entity.goblinState() == com.trolmastercard.sexmod.entity.GoblinBehavior.CATCH ? 0.5F : 1.0F) * entity.goblinFade();
        }
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
        if (bone.getName().equals("Head2")) {
            // The original skips the partner's head while the local player is that partner in first
            // person; the camera sits inside it otherwise. Children (the camera bone) still update.
            Minecraft minecraft = Minecraft.getInstance();
            boolean localPartner = minecraft.player != null
                    && entity.currentScene() != null
                    && entity.currentScene().hasPartner()
                    && entity.scenePartnerId().filter(minecraft.player.getUUID()::equals).isPresent();
            bone.setHidden(localPartner && minecraft.options.getCameraType().isFirstPerson());
            bone.setChildrenHidden(false); // setHidden also hides children, which would freeze the camera bone
        }
        if (poseBone(entity, bone.getName())) {
            bone.setTrackingMatrices(true);
            POSE_BONES.computeIfAbsent(entity.getId(), id -> new java.util.concurrent.ConcurrentHashMap<>()).put(bone.getName(), bone);
        }
        if (GalathMeshLayer.tracks(bone.getName())) {
            bone.setTrackingMatrices(true);
        }
        if (bone.getName().startsWith("skirt_") || bone.getName().startsWith("clothBoob")) {
            bone.setTrackingMatrices(true); // SkirtLayer joins these bones with quads
        }
        if (bone.getName().equals("boyCam")) {
            bone.setTrackingMatrices(true);
            BOY_CAMS.put(entity.getId(), bone);
        }
        if (bone.getName().equalsIgnoreCase("steve")
                && entity.currentScene() != null
                && entity.currentScene().hasPartner()) {
            PlayerSkin skin = partnerSkin(entity);
            renderType = RenderType.entityTranslucent(skin.texture());
            buffer = bufferSource.getBuffer(renderType);
        }

        if (entity.currentScene() != null && entity.currentScene().hasPartner() && isPartnerProp(bone)) {
            // original d_: the skin gets two painted patches (0,0,4,3 skin tone; 4,0,3,3 pink) that this mesh samples
            renderType = RenderType.entityCutoutNoCull(propTexture());
            buffer = bufferSource.getBuffer(renderType);
        }
        if (bone.getName().equals("body2") && entity.currentScene() != null
                && entity.currentScene().modelOverride() != null) {
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

        if (entity.kind() == CompanionKind.GALATH && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            // original f_.v(): in first person she is half transparent in the corrupt scene (the intro only after setCamCorrupt)
            String animation = GalathMeshLayer.playing(entity);
            if (animation.equals("animation.galath.corrupt_slow") || animation.equals("animation.galath.corrupt_soft")
                    || animation.equals("animation.galath.corrupt_hard") || animation.equals("animation.galath.corrupt_cum")
                    || (animation.equals("animation.galath.corrupt_intro") && entity.galathCorruptCam)) {
                renderType = RenderType.entityTranslucent(this.getTextureLocation(entity));
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
            }
            if (strip != 0) {
                buffer = new UvShift(buffer, 72.0F * strip / 4096.0F);
            }
        }
        if (entity.goblin() != null) {
            renderColor = goblinLook(entity, bone, renderColor);
        }

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
    }

    private static final java.util.Map<String, int[]> GOBLIN_FIELDS = new java.util.HashMap<>();
    private static final java.util.Set<String> GOBLIN_SKIN = java.util.Set.of("meatTorso", "meatCheekR", "meatCheekL", "meatFootR",
            "meatFootL", "meatShinR", "meatShinL", "meatLegL", "meatLegR", "nippleR", "nippleL", "preggy", "shoeL", "shoeR",
            "frontAndInside", "Lside", "Rside", "cheekR", "cheekL", "fuckhole", "head", "nose", "neck", "armL", "lowerArmL",
            "armR", "lowerArmR", "torso", "LegL", "LegR", "shinL", "shinR");
    private static final java.util.Set<String> GOBLIN_LASHES = java.util.Set.of("lashR", "lashL", "closedR", "closedL", "browL", "browR");

    /** Original dy: ears, hair and crown pick their bones from the model string; every bone is tinted by name. */
    private static int goblinLook(CompanionEntity entity, GeoBone bone, int renderColor) {
        int[] fields = GOBLIN_FIELDS.computeIfAbsent(entity.goblinModel(), com.trolmastercard.sexmod.entity.GoblinModel::parse);
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
        int rgb = com.trolmastercard.sexmod.entity.GoblinModel.tint(name, fields, GOBLIN_SKIN, GOBLIN_LASHES);
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
        for (int index : com.trolmastercard.sexmod.entity.GoblinModel.subset(children.size(), accessories)) {
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
        return animation.startsWith("animation.goblin.paizuri") || animation.startsWith("animation.goblin.nelson")
                || animation.startsWith("animation.goblin.pick_up") || animation.equals("animation.goblin.shoulder_idle")
                || animation.startsWith("animation.ellie.carry") || animation.startsWith("animation.bia.prone_doggy")
                || animation.equals("animation.bia.wave") || animation.equals("animation.bia.wave_idle")
                || animation.equals("animation.jenny.wave") || animation.equals("animation.jenny.wave_idle")
                || animation.startsWith("animation.allie.summon")
                || animation.equals("animation.galath.controlled_flight") || animation.equals("animation.galath.boost")
                || animation.equals("animation.galath.give_coin") || animation.equals("animation.galath.pussy_licking");
    }

    /** Original dj: tribe members show their coloured tribe name over their head. */
    @Override
    public boolean shouldShowName(CompanionEntity entity) {
        if (hidesNameTag(GalathMeshLayer.playing(entity))) {
            return false; // original fp.hideNameTag
        }
        if (entity.kind() == CompanionKind.KOBOLD && !"null".equals(entity.tribeName())) {
            return Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity) < 64.0D * 64.0D;
        }
        return super.shouldShowName(entity);
    }

    @Override
    protected void renderNameTag(CompanionEntity entity, net.minecraft.network.chat.Component displayName, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (entity.kind() == CompanionKind.KOBOLD && !"null".equals(entity.tribeName())) {
            net.minecraft.network.chat.MutableComponent tag = entity.hasCustomName()
                    ? entity.getCustomName().copy().append(" ") : net.minecraft.network.chat.Component.literal("");
            displayName = tag.append(net.minecraft.network.chat.Component.literal("-" + entity.tribeName() + "-")
                    .withStyle(entity.bodyColor().chat()));
        }
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
    }

    private static net.minecraft.resources.ResourceLocation propTextureLocation;

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
                    com.trolmastercard.sexmod.FapCraft.MOD_ID, "dynamic/partner_prop");
            Minecraft.getInstance().getTextureManager().register(
                    propTextureLocation, new net.minecraft.client.renderer.texture.DynamicTexture(image));
        }
        return propTextureLocation;
    }

    static PlayerSkin partnerSkin(CompanionEntity entity) {
        var partnerId = entity.scenePartnerId().orElse(entity.getUUID());
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo playerInfo = connection == null ? null : connection.getPlayerInfo(partnerId);
        return playerInfo == null ? DefaultPlayerSkin.get(partnerId) : playerInfo.getSkin();
    }

    /**
     * Original d_ item rendering on the "weapon" bone, for now only Luna's fishing items: her rod while she starts /
     * idles at the water (cast or not) and the caught item while she eats or throws it away.
     */
    private static final class FishingItemLayer extends software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer<CompanionEntity> {
        FishingItemLayer(software.bernie.geckolib.renderer.GeoRenderer<CompanionEntity> renderer) {
            super(renderer);
        }

        @Override
        protected net.minecraft.world.item.ItemStack getStackForBone(GeoBone bone, CompanionEntity entity) {
            if ("itemRenderer".equals(bone.getName())) {
                var scene = entity.currentScene();
                return scene != null && scene.isPayment() ? entity.paymentItem() : net.minecraft.world.item.ItemStack.EMPTY;
            }
            if (!"weapon".equals(bone.getName())) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            if (entity.kind() != CompanionKind.LUNA) {
                if (entity.kind() != CompanionKind.JENNY && entity.kind() != CompanionKind.ELLIE
                        && entity.kind() != CompanionKind.BIA) {
                    return net.minecraft.world.item.ItemStack.EMPTY;
                }
                // original d_ item hook: the girl's real weapon (slot 0) or bow (slot 1), whatever the player equipped
                return switch (entity.action()) {
                    case ATTACK -> entity.getMainHandItem();
                    case BOW -> entity.getOffhandItem();
                    default -> net.minecraft.world.item.ItemStack.EMPTY;
                };
            }
            if (entity.action() == com.trolmastercard.sexmod.entity.CompanionAction.ATTACK) {
                return entity.getMainHandItem();
            }
            return switch (entity.fishState()) {
                case com.trolmastercard.sexmod.entity.LunaFishing.START, com.trolmastercard.sexmod.entity.LunaFishing.IDLE ->
                        new net.minecraft.world.item.ItemStack(com.trolmastercard.sexmod.registry.ModItems.LUNA_ROD.get());
                case com.trolmastercard.sexmod.entity.LunaFishing.EAT, com.trolmastercard.sexmod.entity.LunaFishing.THROW_AWAY ->
                        entity.fishCaught();
                default -> net.minecraft.world.item.ItemStack.EMPTY;
            };
        }

        @Override
        protected net.minecraft.world.item.ItemDisplayContext getTransformTypeForStack(
                GeoBone bone, net.minecraft.world.item.ItemStack stack, CompanionEntity entity) {
            return net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }
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
