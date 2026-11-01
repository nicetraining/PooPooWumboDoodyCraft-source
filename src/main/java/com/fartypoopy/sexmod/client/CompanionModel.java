package com.fartypoopy.sexmod.client;

import net.minecraft.client.Minecraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Locale;
import java.util.Set;

public final class CompanionModel extends GeoModel<CompanionEntity> {
    private static final Set<String> ALEX_ARM_BONES = Set.of(
            "leftarmalex",
            "leftlowerarmalex",
            "rightarmalex",
            "rightlowerarmalex"
    );
    private static final Set<String> STEVE_ARM_BONES = Set.of(
            "leftarmsteve",
            "leftlowerarmsteve",
            "rightarmsteve",
            "rightlowerarmsteve"
    );
    private static final Set<String> COMMON_SCENE_BONES = Set.of(
            "steve",
            "cum",
            "weapon",
            "weaponstart",
            "weaponend",
            "energyballr",
            "energyballl"
    );

    private static final ResourceLocation COMBINED = ResourceLocation.fromNamespaceAndPath("sexmod", "geo/galath/galath_con_mang.geo.json");

    /** Original cb.a(em): the combined Galath + daughter model in the hug (HUG_MANG) and in the morning scene. */
    static boolean combinedModel(CompanionEntity entity) {
        var scene = entity.currentScene();
        return (scene != null && scene.modelOverride() != null)
                || (entity.kind() == CompanionKind.GALATH && entity.action() == com.fartypoopy.sexmod.entity.CompanionAction.GALATH_HUG_MANG);
    }

    @Override
    public ResourceLocation getModelResource(CompanionEntity entity) {
        var scene = entity.currentScene();
        if (scene != null && scene.modelOverride() != null) {
            return scene.modelOverride();
        }
        if (combinedModel(entity)) {
            return COMBINED;
        }
        return entity.kind().modelResource(entity.outfit());
    }

    @Override
    public ResourceLocation getTextureResource(CompanionEntity entity) {
        return entity.kind().textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(CompanionEntity entity) {
        return entity.kind().animationResource();
    }

    /** Runs before the controllers process this frame (original: Molang values set in the predicate / cv before super). */
    @Override
    public void applyMolangQueries(AnimationState<CompanionEntity> animationState, double animTime) {
        super.applyMolangQueries(animationState, animTime);
        CompanionEntity entity = animationState.getAnimatable();
        if (entity.kind() == CompanionKind.KOBOLD) {
            // original ff / e7 predicate: "size" = 0.25 - body size bends 16 Kobold animations ("-102.75 + size * 30")
            double size = 0.25D - entity.koboldSize();
            software.bernie.geckolib.loading.math.MathParser.setVariable("size", () -> size);
        } else if (entity.kind() == CompanionKind.GALATH
                && GalathMeshLayer.playing(entity).equals("animation.galath.masterbate")) {
            // original cb.k() before super.setLivingAnimations: aim pitch / armpitch / armyaw / yaw at the viewer
            GalathMeshLayer.aimAtViewer(entity, this.getBakedModel(this.getModelResource(entity)),
                    animationState.getPartialTick());
        }
    }

    @Override
    public void setCustomAnimations(
            CompanionEntity entity,
            long instanceId,
            AnimationState<CompanionEntity> animationState
    ) {
        super.setCustomAnimations(entity, instanceId, animationState);
        // original cv.a(): after the controllers ran, the action controller gets the state's transitionTick back
        var main = entity.getAnimatableInstanceCache().getManagerForId(instanceId).getAnimationControllers().get("main");
        if (main != null) {
            main.transitionLength(entity.mainHoldTicks());
        }

        for (GeoBone bone : this.getAnimationProcessor().getRegisteredBones()) {
            bone.setHidden(false);
            bone.setChildrenHidden(false);
        }

        for (GeoBone bone : this.getAnimationProcessor().getRegisteredBones()) {
            if (shouldHide(entity, bone.getName())) {
                bone.setHidden(true);
                bone.setChildrenHidden(true);
            }
        }

        if (entity.kind() == CompanionKind.MANGLELIE) {
            this.skirtPhysics();
        }
        if (entity.kind() == CompanionKind.KOBOLD) {
            this.koboldScales(entity);
            this.koboldTransitionOffset(entity);
        }
        if (entity.goblin() != null) {
            goblinPose(entity);
        } else if (entity.kind() == CompanionKind.ELLIE && entity.currentSceneAnimation() != null
                && entity.currentSceneAnimation().animation().equals("animation.ellie.sitdownidle")) {
            this.ellieSitLook(entity);
        } else if (entity.kind() != CompanionKind.MANGLELIE) {
            this.trackLook(entity, animationState);
        }
        if (entity.kind() == CompanionKind.MANGLELIE) {
            ManglelieRide.pose(this, entity, animationState.getPartialTick()); // original ce: seat pose, arms, head look
        }
        if (entity.kind() == CompanionKind.LUNA) {
            this.lunaHair(entity);
        }
        if (entity.kind() == CompanionKind.SLIME && !entity.isPlayerForm()) {
            // original cr.a(new String[]{"head"}, "hat"): the hat (a child of the dress) takes the head's rotation and position
            GeoBone head = this.bone("head");
            GeoBone hat = this.bone("hat");
            if (head != null && hat != null) {
                hat.setRotX(head.getRotX());
                hat.setRotY(head.getRotY());
                hat.setRotZ(head.getRotZ());
                hat.setPosX(head.getPosX());
                hat.setPosY(head.getPosY());
                hat.setPosZ(head.getPosZ());
            }
        }
        if (entity.kind() == CompanionKind.GALATH) {
            this.galathPose(entity, animationState.getPartialTick());
            GeoBone head = this.bone("head");
            GeoBone body = this.bone("body");
            if (head != null) {
                entity.galathHeadPitch = head.getRotX(); // original cb: f_.aE, read by her daughter's pose
            }
            if (body != null) {
                entity.galathBodyYaw = body.getRotY();
                entity.galathBodyScale = body.getScaleY();
            }
        }
    }

    /**
     * Original da.a (per bone): the hair counter-rotates the head pitch, the head and iris bones sway in the slow bed scene
     * (and fade in during its transition), the arms lean toward the target while she charges.
     */
    private void galathPose(CompanionEntity entity, float partialTick) {
        GeoBone head = this.bone("head");
        if (head != null) {
            float pitch = (float) Math.toDegrees(head.getRotX());
            GeoBone back = this.bone("hairBack");
            if (back != null) {
                back.setRotX((float) Math.toRadians(-pitch));
                if (pitch >= 0.0F) {
                    back.setPosY(back.getPosY() + Math.min(1.0F, pitch / 45.0F) * 1.5F);
                }
            }
            for (String side : new String[] {"hairDownSideL", "hairDownSideR"}) {
                GeoBone bone = this.bone(side);
                if (bone != null) {
                    bone.setRotX((float) Math.toRadians(pitch < 0.0F ? -pitch / 2.0F : -pitch));
                    if (pitch >= 0.0F) {
                        bone.setPosY(bone.getPosY() + Math.min(1.0F, pitch / 45.0F));
                    }
                }
            }
        }
        String animation = GalathMeshLayer.playing(entity);
        this.lickBob(entity, head, animation, partialTick);
        if (entity.action() == com.fartypoopy.sexmod.entity.CompanionAction.GALATH_HUG_MANG) {
            // original cb.d(): while the controller still blends into the hug, the daughter half sits at her rest offset
            var manager = entity.getAnimatableInstanceCache().getManagerForId(entity.getId());
            var controller = manager == null ? null : manager.getAnimationControllers().get("main");
            GeoBone body2 = this.bone("body2");
            if (controller != null && body2 != null
                    && controller.getAnimationState() == software.bernie.geckolib.animation.AnimationController.State.TRANSITIONING) {
                body2.setPosX(0.0F);
                body2.setPosY(-0.53F);
                body2.setPosZ(-40.05F);
            }
        }
        boolean slow = animation.equals("animation.shared.bed_slow") || animation.equals("animation.shared.bed_back");
        boolean soft = animation.equals("animation.shared.bed_soft"); // FAST with the transition flag set
        if (slow || soft) {
            Minecraft minecraft = Minecraft.getInstance();
            float t = (minecraft.player == null ? 0 : minecraft.player.tickCount) + partialTick;
            float seconds = GalathMeshLayer.elapsed(entity, animation, partialTick) / 20.0F;
            float sway = (float) Math.sin(t * -0.1F) * 0.1F;
            float sway2 = (float) Math.sin(t * 0.1F) * 0.1F;
            float ramp = Math.min(0.5F, seconds) / 0.5F;
            if (head != null) {
                float k = slow ? 1.0F : ramp;
                head.setRotY(head.getRotY() + sway * k);
                head.setRotZ(head.getRotZ() + sway2 * k);
            }
            GeoBone head3 = this.bone("head3");
            if (head3 != null) {
                float k = slow ? 1.0F : 1.0F - ramp;
                head3.setRotY(head3.getRotY() + ((float) (Math.sin(t * 0.1F) * 0.1D) + 0.2F) * k);
                head3.setRotZ(head3.getRotZ() + sway2 * k);
            }
            if (slow) {
                for (String iris : new String[] {"irisL", "irisR"}) {
                    GeoBone bone = this.bone(iris);
                    if (bone != null) {
                        bone.setPosX((float) (bone.getPosX() + Math.sin(t * 0.1F) * -0.1D));
                    }
                }
                for (String face : new String[] {"irsisFaceR2", "irsisFaceR3"}) {
                    GeoBone bone = this.bone(face);
                    if (bone != null) {
                        bone.setPosX((float) (bone.getPosX() + Math.sin(t * 0.1F) * -0.15D));
                    }
                }
            }
        }
        if (animation.equals("animation.galath.rape_charge") && entity.level().getEntity(entity.bossTargetId()) != null) {
            var target = entity.level().getEntity(entity.bossTargetId());
            float yaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            double rad = Math.toRadians(yaw);
            double dx = target.getX() - entity.getX();
            double dz = target.getZ() - entity.getZ();
            // original ck.a(v, yaw) reflection: x' = -cos(yaw) x - sin(yaw) z
            double local = -Math.cos(rad) * dx - Math.sin(rad) * dz;
            float lean = (float) Math.toRadians(45.0D * -net.minecraft.util.Mth.clamp(local, -1.0D, 1.0D));
            for (String arm : new String[] {"armL", "armR"}) {
                GeoBone bone = this.bone(arm);
                if (bone != null) {
                    bone.setRotZ(bone.getRotZ() + lean);
                }
            }
        }
    }

    /**
     * Original dp.a (Luna renderer): the hair counter-rotates the head pitch. The back hair and the side hair are also pushed
     * out along Y/Z by the pitch (back 0.75, sides 1.3 at 45 degrees), unless she is moved to a target position (scene).
     */
    private void lunaHair(CompanionEntity entity) {
        GeoBone head = this.bone("head");
        if (head == null) {
            return;
        }
        float pitch = head.getRotX();
        float t = pitch / (float) Math.toRadians(45.0D);
        if (entity.isPlayerForm()) {
            // original di.a(String, GeoBone): nothing while she is busy; the back hair only while she looks down, no side hair
            if (entity.isInScene() || entity.formWait() != 0) {
                return;
            }
            GeoBone back = this.bone("backHair");
            if (back != null && pitch > 0.0F) {
                back.setPosZ(0.75F * t);
                back.setPosY(0.75F * t);
                back.setRotX(-pitch);
            }
            for (String front : new String[] {"frontHairL", "frontHairR"}) {
                GeoBone bone = this.bone(front);
                if (bone != null) {
                    bone.setRotX(-pitch);
                }
            }
            return;
        }
        boolean placed = entity.isInScene();
        GeoBone back = this.bone("backHair");
        if (back != null && !placed) {
            back.setPosZ(0.75F * t);
            back.setPosY(0.75F * t);
            back.setRotX(-pitch);
        }
        for (String side : new String[] {"sideHairR", "sideHairL"}) {
            GeoBone bone = this.bone(side);
            if (bone != null) {
                float out = 1.3F * t;
                bone.setPosZ(-out);
                bone.setPosY(out);
                if (!placed) {
                    bone.setRotX(-pitch);
                }
            }
        }
        for (String front : new String[] {"frontHairL", "frontHairR"}) {
            GeoBone bone = this.bone(front);
            if (bone != null) {
                bone.setRotX(-pitch);
            }
        }
    }

    /**
     * Original cv.a(T, AnimationProcessor, AnimationEvent): outside scenes (states NULL, ATTACK and BOW) the neck turns half
     * of the head yaw, the head follows yaw and pitch of the look direction.
     */
    private void trackLook(CompanionEntity entity, AnimationState<CompanionEntity> state) {
        com.fartypoopy.sexmod.entity.CompanionAction action = entity.action();
        if (entity.isInScene() || entity.currentSceneAnimation() != null
                || action != com.fartypoopy.sexmod.entity.CompanionAction.NONE
                && action != com.fartypoopy.sexmod.entity.CompanionAction.ATTACK
                && action != com.fartypoopy.sexmod.entity.CompanionAction.BOW) {
            return;
        }
        if (entity.kind() == CompanionKind.GALATH && entity.getFirstPassenger() != null) {
            return;
        }
        if (entity.wildBoss() && entity.bossState() != com.fartypoopy.sexmod.entity.GalathBoss.S_NONE
                && entity.bossState() != com.fartypoopy.sexmod.entity.GalathBoss.S_FLY) {
            return; // original cv: only NULL / ATTACK / BOW turn the head; lying knocked out it twisted backwards
        }
        boolean kobold = entity.kind() == CompanionKind.KOBOLD && !entity.isPlayerForm();
        if (kobold) {
            // original c9.a(em, processor, event): only in her NULL state, and not under a solid block (ff.a()) unless
            // she just moved up or down more than 0.1 on the ground; no neck turn
            if (entity.koboldState() != 0) {
                return;
            }
            boolean climbing = entity.onGround() && Math.abs(Math.abs(entity.yo) - Math.abs(entity.getY())) > 0.1D;
            if (!climbing && entity.level().getBlockState(entity.blockPosition().above()).blocksMotion()) {
                return;
            }
        }
        software.bernie.geckolib.cache.object.GeoBone head = this.bone("head");
        if (head == null) {
            return;
        }
        software.bernie.geckolib.model.data.EntityModelData data =
                state.getData(software.bernie.geckolib.constant.DataTickets.ENTITY_MODEL_DATA);
        if (data == null) {
            return;
        }
        GeoBone neck = kobold ? null : this.bone("neck");
        if (neck != null) {
            neck.setRotY(data.netHeadYaw() * 0.5F * 0.017453292F);
        }
        head.setRotY(data.netHeadYaw() * 0.017453292F);
        // original c0 (Bee model): her head is tilted 1.0 rad further down
        head.setRotX((entity.kind() == CompanionKind.BEE ? 1.0F : 0.0F) + data.headPitch() * 0.017453292F);
        GeoBone body = this.bone("body") != null ? this.bone("body") : this.bone("dd");
        if (body != null) {
            body.setRotY(0.0F);
        }
    }

    /**
     * Original c9.b: while the main controller blends into a Kobold scene animation the body is moved by an offset that
     * depends on her size (d = 0.25 - size) so that she lines up with the player.
     */
    private void koboldTransitionOffset(CompanionEntity kobold) {
        var scene = kobold.currentScene();
        if (scene == null) {
            return;
        }
        var controller = kobold.getAnimatableInstanceCache().getManagerForId(kobold.getId()).getAnimationControllers().get("main");
        if (controller == null || controller.getAnimationState() != software.bernie.geckolib.animation.AnimationController.State.TRANSITIONING) {
            return;
        }
        GeoBone body = this.bone("body");
        if (body == null) {
            return;
        }
        float d = 0.25F - kobold.koboldSize();
        switch (scene) {
            case KOBOLD_BLOWJOB -> {
                if (kobold.currentScenePhase() != 0) {
                    body.setPosZ(11.43F + d * -7.0F);
                }
            }
            case KOBOLD_ANAL -> {
                body.setPosX(1.78F + d * -1.5F);
                body.setPosY(13.07F + d * -11.0F);
                body.setPosZ(2.05F + d * -8.0F);
            }
            case KOBOLD_MATING_PRESS -> {
                body.setPosX(0.0F);
                body.setPosY(2.85F);
                body.setPosZ(-7.0F + d * 4.7F);
            }
            default -> {
            }
        }
    }

    /** Original c9: breast size 0.75-1.35 (boobL, boobR, armorBoobs) and eye size 1.0-1.2 (eyeL, eyeR, pushed sideways). */
    private void koboldScales(CompanionEntity kobold) {
        float breast = 0.75F + (1.35F - 0.75F) * (kobold.koboldGene(2) / 100.0F);
        for (String name : new String[] {"boobL", "boobR", "armorBoobs"}) {
            GeoBone bone = this.bone(name);
            if (bone != null) {
                bone.setScaleX(breast);
                bone.setScaleY(breast);
                bone.setScaleZ(breast);
            }
        }
        float eye = 1.0F + (1.2F - 1.0F) * (kobold.koboldGene(3) / 100.0F);
        for (String name : new String[] {"eyeL", "eyeR"}) {
            GeoBone bone = this.bone(name);
            if (bone != null) {
                bone.setScaleX(eye);
                bone.setScaleY(eye);
                bone.setScaleZ(eye);
            }
        }
        GeoBone left = this.bone("eyeL");
        GeoBone right = this.bone("eyeR");
        if (left != null && right != null) {
            left.setPosX(left.getPosX() + (eye - 1.0F));
            right.setPosX(right.getPosX() - (eye - 1.0F));
        }
    }

    /** Original cx.a(): while Ellie sits on the bed waiting, her head follows the nearest player within 15 blocks. */
    private void ellieSitLook(CompanionEntity ellie) {
        GeoBone head = this.bone("head");
        net.minecraft.world.entity.player.Player player = ellie.level().getNearestPlayer(ellie, 15.0D);
        if (head == null || player == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 delta = ellie.position().subtract(player.position());
        float wrapped = net.minecraft.util.Mth.wrapDegrees(ellie.getYRot());
        int facing = Math.round(wrapped / 90.0F) * 90;
        if (facing == -180) {
            facing = 180;
        }
        float yaw;
        if (facing == 180) {
            yaw = (float) Math.atan2(delta.x, delta.z) * 1.2F;
            yaw = yaw > 0.0F ? Math.max(1.5F, Math.min(3.14F, yaw)) : Math.max(-3.14F, Math.min(-1.5F, yaw));
            yaw = yaw == 1.5F || yaw == 3.14F || yaw == -3.14F || yaw == -1.5F ? 0.0F : yaw + 3.0F;
        } else {
            float offset;
            float min;
            float max;
            switch (facing) {
                case -90 -> {
                    offset = 2.0F;
                    min = -71.56F;
                    max = -68.0F;
                }
                case 90 -> {
                    offset = -2.0F;
                    min = 68.0F;
                    max = 70.5F;
                }
                default -> {
                    offset = 0.0F;
                    min = -1.2F;
                    max = 1.2F;
                }
            }
            yaw = ((float) (Math.atan2(delta.x, delta.z) + offset) + facing) * 0.8F;
            yaw = Math.max(min, Math.min(max, yaw));
            if (yaw == min || yaw == max) {
                yaw = 0.0F;
            }
        }
        float pitch = yaw == 0.0F ? 0.0F
                : net.minecraft.util.Mth.clamp((float) ((player.getY() - ellie.getY()) * 0.5D), -0.75F, 0.75F);
        head.setRotY(yaw);
        head.setRotX(pitch);
    }

    /**
     * Original dh.a(em, String, GeoBone, boolean): the skirt rings follow the cheeks (rings 17-35: lifted by the cheek
     * rotation) and the legs (rings 1-11, middle bone: tilted by the leg rotation) when those swing forward.
     */
    private void skirtPhysics() {
        for (int ring = 1; ring <= 35; ring++) {
            if (ring >= 17) {
                GeoBone cheek = this.bone(ring < 26 ? "cheekL" : "cheekR");
                if (cheek == null) {
                    continue;
                }
                float degrees = (float) Math.toDegrees(cheek.getRotX());
                if (degrees < 0.0F) {
                    continue;
                }
                for (int part = 0; part < 3; part++) {
                    GeoBone skirt = this.bone("skirt_" + ring + "_" + part);
                    if (skirt != null) {
                        skirt.setPosY(skirt.getPosY() + degrees * 0.01F);
                    }
                }
            } else if (ring <= 11) {
                GeoBone leg = this.bone(ring < 6 ? "legR" : "legL");
                GeoBone skirt = this.bone("skirt_" + ring + "_1");
                if (leg == null || skirt == null) {
                    continue;
                }
                float degrees = (float) Math.toDegrees(leg.getRotX());
                if (degrees < 0.0F) {
                    continue;
                }
                skirt.setRotX((float) Math.toRadians(degrees));
                skirt.setPosY((float) Math.toRadians(degrees * 0.03F));
            }
        }
    }

    GeoBone bone(String name) {
        for (GeoBone bone : this.getAnimationProcessor().getRegisteredBones()) {
            if (bone.getName().equals(name)) {
                return bone;
            }
        }
        return null;
    }

    /** Original ci.a(): per-state bone rules of the goblin (look at the nearest player, carried legs, throw tumble). */
    private static final java.util.Map<Integer, Float> LICK_SIGN = new java.util.HashMap<>();

    /**
     * Original cb.i(): in the licking state her head bobs (pitch sin(0.3 t) * 10, roll sin(0.15 t) * 7, yaw sin(-0.15 t) * 7
     * degrees), fading in over the forward transition; once in the loop every sign change of the pitch wave plays a lip sound.
     */
    private void lickBob(CompanionEntity entity, GeoBone head, String animation, float partialTick) {
        boolean forward = animation.equals("animation.galath.pussy_licking_forward");
        boolean loop = animation.equals("animation.galath.pussy_licking");
        if (head == null || (!forward && !loop) || Minecraft.getInstance().isPaused()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float t = (minecraft.player == null ? 0 : minecraft.player.tickCount) + partialTick;
        float weight = forward ? Math.min(1.0F, GalathMeshLayer.elapsed(entity, animation, partialTick) / 15.0F) : 1.0F;
        head.setRotX(head.getRotX() + (float) Math.sin(t * 0.3F) * (float) Math.toRadians(10.0D) * weight);
        head.setRotY(head.getRotY() + (float) Math.sin(t * -0.15D) * (float) Math.toRadians(7.0D) * weight);
        head.setRotZ(head.getRotZ() + (float) Math.sin(t * 0.15F) * (float) Math.toRadians(7.0D) * weight);
        if (loop) {
            float wave = (float) (Math.sin(t * 0.3F) * 10.0D);
            Float previous = LICK_SIGN.put(entity.getId(), wave);
            if (previous != null && ((wave > 0.0F && previous < 0.0F) || (wave < 0.0F && previous > 0.0F))) {
                com.fartypoopy.sexmod.registry.ModSounds.find("girls.allie.lipsound.lipsound" + entity.getRandom().nextInt(14))
                        .ifPresent(sound -> entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                                sound.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F, false));
            }
        }
    }

    private void goblinPose(CompanionEntity goblin) {
        int state = goblin.goblinState();
        GeoBone body = this.bone("body");
        GeoBone head = this.bone("head");
        GeoBone steve = this.bone("steve");
        Minecraft minecraft = Minecraft.getInstance();
        boolean localCarrier = minecraft.player != null
                && goblin.goblinCarrier().filter(minecraft.player.getUUID()::equals).isPresent();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        if (body == null) {
            return;
        }

        if (state == com.fartypoopy.sexmod.entity.GoblinBehavior.AWAIT_PICK_UP
                || state == com.fartypoopy.sexmod.entity.GoblinBehavior.VANISH) {
            net.minecraft.world.entity.player.Player player = goblin.level().getNearestPlayer(goblin, 15.0D);
            if (player != null && head != null) {
                net.minecraft.world.phys.Vec3 delta = player.position().subtract(goblin.position());
                float yaw = (float) (-(Math.atan2(delta.z, delta.x) * 57.29577951308232D)) + 90.0F;
                float pitch = net.minecraft.util.Mth.clamp((float) (player.getEyeHeight() + player.getY()
                        - (goblin.getEyeHeight() + goblin.getY())), -0.75F, 0.75F);
                body.setRotY((float) Math.toRadians(yaw));
                head.setRotX(pitch);
            }
        }
        if (state == com.fartypoopy.sexmod.entity.GoblinBehavior.SIT && head != null) {
            this.goblinSitLook(goblin, head);
        }

        if (state == com.fartypoopy.sexmod.entity.GoblinBehavior.THROWING) {
            boolean hide = false;
            if (localCarrier) {
                hide = goblin.goblinPhase() < 15;
            }
            body.setHidden(hide);
            if (steve != null) {
                steve.setHidden(true);
            }
        }
        body.setPivotY(-0.15F); // original dg/dy bone rule; the first-person shoulder pass below moves it
        if (CompanionRenderer.goblinFirstPersonShoulder) {
            // original dy.a(em, bone) and dy.a(u, bone, ...): in her first-person shoulder pass she tilts with the view and sways
            body.setPivotY(8.0F);
            body.setRotX(body.getRotX() + (float) Math.toRadians(-Minecraft.getInstance().gameRenderer.getMainCamera().getXRot()));
            this.sway("LegR", 25.0F, 25.0F);
            this.sway("boobR", 30.0F, 30.0F);
            this.sway("boobR1", 10.0F, 15.0F);
            this.sway("boobR2", 5.0F, 3.0F);
        }
        if ((state == com.fartypoopy.sexmod.entity.GoblinBehavior.THROWING
                || state == com.fartypoopy.sexmod.entity.GoblinBehavior.PICK_UP) && goblin.goblinCarrier().isPresent()) {
            net.minecraft.world.entity.player.Player carrier = goblin.level().getPlayerByUUID(goblin.goblinCarrier().get());
            GeoBone left = this.bone("LeftLeg");
            GeoBone right = this.bone("RightLeg");
            if (carrier != null && left != null && right != null) {
                float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
                float speed = net.minecraft.util.Mth.lerp(partial, carrier.walkAnimation.speed(-1.0F), carrier.walkAnimation.speed());
                float swing = (float) Math.toRadians(60.0D * Math.sin(carrier.walkAnimation.position()) * speed);
                left.setRotX(swing);
                right.setRotX(-swing);
            }
        }
        if (state == com.fartypoopy.sexmod.entity.GoblinBehavior.THROWN
                || (state == com.fartypoopy.sexmod.entity.GoblinBehavior.THROWING && !body.isHidden())) {
            net.minecraft.world.phys.Vec3 move = new net.minecraft.world.phys.Vec3(goblin.getX() - goblin.xOld,
                    goblin.getY() - goblin.yOld, goblin.getZ() - goblin.zOld);
            net.minecraft.world.phys.Vec3 abs = new net.minecraft.world.phys.Vec3(Math.abs(move.x), Math.abs(move.y), Math.abs(move.z));
            double sum = abs.x + abs.y + abs.z;
            double y = sum == 0.0D ? 0.0D : (move.y > 0.0D ? 1 : -1) * abs.y / sum;
            double t = y / 2.0D + 0.5D;
            float rotX = (float) Math.toRadians(-180.0D + 180.0D * t);
            float posY = t < 0.5D ? 0.0F : (float) (16.0D * -t);
            float posZ = (float) (4.0D - Math.sin(1.5707963267948966D + t * 2.0D * Math.PI) * 4.0D);
            body.setRotX(rotX);
            body.setPosY(posY);
            body.setPosZ(posZ);
        }
    }

    private void sway(String name, float limitX, float limitZ) {
        GeoBone bone = this.bone(name);
        if (bone != null && !Minecraft.getInstance().isPaused()) {
            bone.setRotX(bone.getRotX() + (float) Math.toRadians(net.minecraft.util.Mth.clamp(CompanionRenderer.goblinSwayX, -limitX, limitX)));
            bone.setRotZ(bone.getRotZ() + (float) Math.toRadians(net.minecraft.util.Mth.clamp(CompanionRenderer.goblinSwayZ, -limitZ, limitZ)));
        }
    }

    /** Original ci.a(em, IBone) (bytecode): a sitting goblin turns her head to a player standing in front of her. */
    private void goblinSitLook(CompanionEntity goblin, GeoBone head) {
        net.minecraft.world.entity.player.Player player = goblin.level().getNearestPlayer(goblin, 15.0D);
        if (player == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 at = player.position();
        net.minecraft.world.phys.Vec3 me = goblin.position();
        net.minecraft.world.phys.Vec3 delta = at.subtract(me);
        int facing = (int) goblin.getYRot();
        boolean inFront = switch (facing) {
            case 0 -> at.z > me.z;
            case 180 -> at.z < me.z;
            case 90 -> at.x < me.x;
            case -90 -> at.x > me.x;
            default -> false;
        };
        if (!inFront) {
            head.setRotY(0.0F);
            return;
        }
        float offset = switch (facing) {
            case 0 -> -90.0F;
            case 90 -> 180.0F;
            case 180 -> 90.0F;
            default -> 0.0F;
        };
        float yaw = (float) (-(Math.atan2(delta.z, delta.x) * 57.29577951308232D + offset));
        float pitch = net.minecraft.util.Mth.clamp((float) (player.getEyeHeight() + at.y - (goblin.getEyeHeight() + me.y)), -0.75F, 0.75F);
        head.setRotY((float) Math.toRadians(yaw));
        head.setRotX(pitch);
    }

    private static boolean isWornArmourKind(CompanionKind kind) {
        return kind == CompanionKind.JENNY || kind == CompanionKind.ELLIE || kind == CompanionKind.BIA
                || kind == CompanionKind.LUNA;
    }

    private static final java.util.Set<String> ARMOUR_HEAD = java.util.Set.of("armorhelmet");
    private static final java.util.Set<String> ARMOUR_CHEST = java.util.Set.of("armorshoulderr", "armorshoulderl",
            "armorchest", "armorboobs");
    private static final java.util.Set<String> ARMOUR_LEGS = java.util.Set.of("armorbootyr", "armorbootyl",
            "armorpantslowl", "armorpantslowr", "armorpantsupr", "armorpantsupl", "armorhip");
    private static final java.util.Set<String> ARMOUR_FEET = java.util.Set.of("armorshoesl", "armorshoesr");
    private static final java.util.Set<String> LEGS_SKIN = java.util.Set.of("fleshl", "fleshr", "vagina", "curvesl",
            "curvesr", "kneel", "kneer");

    /** The armour piece a bone belongs to, or null (original cv.c/f/h/b lists of the girls that wear armour). */
    public static net.minecraft.world.entity.EquipmentSlot armourSlot(String lowerBone) {
        if (ARMOUR_HEAD.contains(lowerBone)) {
            return net.minecraft.world.entity.EquipmentSlot.HEAD;
        }
        if (ARMOUR_CHEST.contains(lowerBone)) {
            return net.minecraft.world.entity.EquipmentSlot.CHEST;
        }
        if (ARMOUR_LEGS.contains(lowerBone)) {
            return net.minecraft.world.entity.EquipmentSlot.LEGS;
        }
        if (ARMOUR_FEET.contains(lowerBone)) {
            return net.minecraft.world.entity.EquipmentSlot.FEET;
        }
        return null;
    }

    private static boolean wearing(CompanionEntity entity, net.minecraft.world.entity.EquipmentSlot slot) {
        net.minecraft.world.item.ItemStack stack = entity.getItemBySlot(slot);
        return slot == net.minecraft.world.entity.EquipmentSlot.CHEST
                ? stack.getItem() instanceof net.minecraft.world.item.ArmorItem
                : !stack.isEmpty();
    }

    /**
     * Original cv.a(processor, helmet, chest, legs, boots): an armour bone shows when its slot is filled, and the
     * clothing bones it replaces are hidden then. Null means "not a worn-armour bone".
     */
    private static Boolean wornArmourDecision(CompanionEntity entity, String bone) {
        net.minecraft.world.entity.EquipmentSlot slot = armourSlot(bone);
        if (slot != null) {
            return !wearing(entity, slot);
        }
        CompanionKind kind = entity.kind();
        if (entity.outfit() == com.fartypoopy.sexmod.entity.OutfitMode.ALTERNATE) {
            return null; // the nude model (index 0) never swaps clothing for armour
        }
        if (wearing(entity, net.minecraft.world.entity.EquipmentSlot.HEAD)
                && (kind == CompanionKind.ELLIE && bone.equals("headband")
                || kind == CompanionKind.BIA && (bone.equals("leaf7") || bone.equals("leaf8")))) {
            return true;
        }
        if (wearing(entity, net.minecraft.world.entity.EquipmentSlot.CHEST)) {
            boolean hide = switch (kind) {
                case JENNY, ELLIE -> bone.equals("boobsflesh") || bone.equals("upperbodyl") || bone.equals("upperbodyr");
                case BIA -> bone.equals("bra") || bone.equals("upperbodyl") || bone.equals("upperbodyr");
                case LUNA -> bone.equals("boobsflesh") || bone.equals("cloth");
                default -> false;
            };
            if (hide) {
                return true;
            }
        }
        if (wearing(entity, net.minecraft.world.entity.EquipmentSlot.LEGS)) {
            boolean hide = LEGS_SKIN.contains(bone)
                    || (kind == CompanionKind.ELLIE && (bone.equals("hotpants") || bone.equals("slip")))
                    || (kind == CompanionKind.BIA && bone.equals("slip"))
                    || (kind == CompanionKind.LUNA && bone.equals("cloth"));
            if (hide) {
                return true;
            }
        }
        return null;
    }

    private static boolean shouldHide(CompanionEntity entity, String boneName) {
        String normalized = boneName.toLowerCase(Locale.ROOT);

        if (isWornArmourKind(entity.kind())) {
            Boolean wornDecision = wornArmourDecision(entity, normalized);
            if (wornDecision != null) {
                return wornDecision;
            }
        } else if (normalized.startsWith("armor")) {
            return entity.outfit()
                    != com.fartypoopy.sexmod.entity.OutfitMode.ARMORED;
        }

        if (COMMON_SCENE_BONES.contains(normalized)) {
            return !shouldShowSceneBone(entity, normalized);
        }

        if (ALEX_ARM_BONES.contains(normalized)) {
            return CompanionRenderer.partnerSkin(entity).model()
                    != net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        }

        if (STEVE_ARM_BONES.contains(normalized)) {
            return CompanionRenderer.partnerSkin(entity).model()
                    == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        }

        CompanionKind kind = entity.kind();
        int variant = entity.appearanceVariant();

        if (kind == CompanionKind.KOBOLD) {
            return koboldBoneHidden(entity, normalized);
        }

        if (kind == CompanionKind.SLIME && (normalized.equals("bedslime") || normalized.equals("bedslimelayer"))) {
            // original cr: the bed slime is part of the doggy states only (going onto it and waiting included)
            return entity.currentScene() != com.fartypoopy.sexmod.entity.CompanionScene.SLIME_DOGGY
                    && !entity.slimeOnBed();
        }

        if (kind == CompanionKind.ALLIE && (normalized.equals("vagina") || normalized.equals("frontandinside")
                || normalized.equals("fuckhole") || normalized.equals("rside") || normalized.equals("lside")
                || normalized.equals("bone5"))) {
            // only the reverse cowgirl scene undresses her; the body detail stays out of every other state
            return entity.currentScene() != com.fartypoopy.sexmod.entity.CompanionScene.ALLIE_REVERSE_COWGIRL;
        }

        if (kind == CompanionKind.BEE) {
            // original c0: the chest bone shows while the current animation name contains "chest" (idle_has_chest)
            return normalized.equals("chest") && !(entity.hasBeeChest() && entity.currentSceneAnimation() == null
                    && entity.action() != com.fartypoopy.sexmod.entity.CompanionAction.THROW_PEARL);
        }

        if (kind == CompanionKind.GOBLIN) {
            int selectedVariant = Math.floorMod(variant, 8);
            return switch (normalized) {
                case "variant7", "variant2" -> selectedVariant != 0;
                case "variant22", "variant6" -> selectedVariant != 1;
                case "variant56", "variant11" -> selectedVariant != 2;
                case "variant", "variant27" -> selectedVariant != 3;
                case "variant24", "variant29" -> selectedVariant != 4;
                case "as", "as2" -> selectedVariant != 5;
                case "bone12", "bone43" -> selectedVariant != 6;
                case "ghjjghj5", "ghjjghj8" -> selectedVariant != 7;
                case "variant25", "variant28" -> selectedVariant < 5;
                default -> normalized.equals("preggy") && !entity.isPreggo();
            };
        }

        if (kind == CompanionKind.GALATH) {
            if (normalized.equals("futacock")
                    || normalized.equals("futaballll")
                    || normalized.equals("futaballlr")) {
                return !FutaSetting.shown(); // original cb: hidden unless /futa true (default)
            }

            String animation = GalathMeshLayer.playing(entity);
            // original cb.e(): while the bra state (f_.bb) is set the bra boxes go and the nipples show; the slip also
            // goes in the licking / sitting states
            boolean braOff = GalathMeshLayer.braHidden(entity, animation, GalathMeshLayer.elapsed(entity, animation,
                    Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
            if (normalized.equals("braboobl") || normalized.equals("braboobr")) {
                return braOff;
            }
            if (normalized.equals("slip")) {
                return braOff || animation.startsWith("animation.galath.pussy_licking")
                        || animation.startsWith("animation.galath.masterbating_sitting");
            }
            if (normalized.equals("wings")) {
                // original cb.g(): b7.a() is false in the corrupt states
                return animation.equals("animation.galath.corrupt_slow") || animation.equals("animation.galath.corrupt_soft")
                        || animation.equals("animation.galath.corrupt_hard") || animation.equals("animation.galath.corrupt_cum");
            }
            if (normalized.equals("nipplel") || normalized.equals("nippler")) {
                return !braOff;
            }
            // original da: the coin and its pentagram are drawn only while the coin scene plays
            return normalized.equals("coin") && !"animation.galath.give_coin".equals(animation);
        }

        if (kind == CompanionKind.MANGLELIE) {
            boolean noSkirtVariant = Math.floorMod(variant, 2) == 1;
            if (normalized.equals("cheeklbelowskirt")
                    || normalized.equals("cheekrbelowskirt")
                    || normalized.equals("sidelnoskirt")
                    || normalized.equals("sidernoskirt")) {
                return !noSkirtVariant;
            }

            if (normalized.equals("skirt")
                    || normalized.equals("sidelskirt")
                    || normalized.equals("siderskirt")) {
                return noSkirtVariant;
            }

            return normalized.equals("nipplel")
                    || normalized.equals("nippler");
        }

        return false;
    }

    /** Original c9.a(): crown, egg, tongue, packs, horns and freckles follow the Kobold's state and model string. */
    private static boolean koboldBoneHidden(CompanionEntity entity, String bone) {
        if (bone.equals("crown")) {
            return !entity.isTribeLeader();
        }
        if (bone.equals("egg")) {
            if (entity.currentScene() == null) {
                entity.koboldEggShown = false;
            }
            return !entity.koboldEggShown;
        }
        if (bone.equals("tounge")) {
            return entity.currentScene() != com.fartypoopy.sexmod.entity.CompanionScene.KOBOLD_BLOWJOB;
        }
        if (bone.equals("backpack") || bone.equals("tailpack")) {
            int pack = entity.koboldGene(6);
            boolean payment = entity.currentScene() == com.fartypoopy.sexmod.entity.CompanionScene.KOBOLD_PAYMENT;
            boolean backpack = pack == 0 || pack == 1 || payment;
            boolean tailpack = pack == 1 || pack == 2;
            return !(bone.equals("backpack") ? backpack : tailpack);
        }
        if (bone.matches("horn(?:ur|ul)[0-7]")) {
            return bone.charAt(bone.length() - 1) - '0' != entity.koboldGene(0);
        }
        if (bone.matches("horn(?:dr|dl)[0-9]")) {
            return bone.charAt(bone.length() - 1) - '0' != entity.koboldGene(1);
        }
        if (bone.matches("frecklesa[lr][12]")) {
            return bone.charAt(bone.length() - 1) - '0' != entity.koboldGene(4);
        }
        if (bone.matches("frecklesh[lr][12]")) {
            return bone.charAt(bone.length() - 1) - '0' != entity.koboldGene(5);
        }
        return false;
    }

    private static boolean shouldShowSceneBone(CompanionEntity entity, String boneName) {
        var scene = entity.currentScene();
        if (scene == null) {
            // original fp.CONTROLLED_FLIGHT / BOOST: hasPlayer - her rider is drawn by her model
            return boneName.equals("steve") && ClientGameplayEvents.flyingRider(entity) != null;
        }

        if (boneName.equals("steve")) {
            return scene.showsPartnerModel();
        }

        var phase = entity.currentSceneAnimation();
        return boneName.equals("cum")
                && phase != null
                && phase.animation().toLowerCase(Locale.ROOT).contains("cum");
    }
}
