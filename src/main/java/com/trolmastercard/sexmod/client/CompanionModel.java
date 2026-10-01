package com.trolmastercard.sexmod.client;

import net.minecraft.client.Minecraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
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

    @Override
    public ResourceLocation getModelResource(CompanionEntity entity) {
        var scene = entity.currentScene();
        if (scene != null && scene.modelOverride() != null) {
            return scene.modelOverride();
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

    /**
     * The original renderers changed the visibility of armour, player proxies,
     * props and cosmetic variants every frame. Rendering the old geometry
     * without those rules draws all variants on top of each other. This
     * establishes one clean, clothed baseline until the full equipment and
     * scene state machines are ported.
     */
    @Override
    public void setCustomAnimations(
            CompanionEntity entity,
            long instanceId,
            AnimationState<CompanionEntity> animationState
    ) {
        super.setCustomAnimations(entity, instanceId, animationState);

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

        if (entity.kind() == CompanionKind.KOBOLD) {
            this.koboldScales(entity);
        }
        if (entity.goblin() != null) {
            goblinPose(entity);
        } else if (entity.kind() == CompanionKind.ELLIE && entity.currentSceneAnimation() != null
                && entity.currentSceneAnimation().animation().equals("animation.ellie.sitdownidle")) {
            this.ellieSitLook(entity);
        } else if (entity.kind() != CompanionKind.MANGLELIE) {
            this.trackLook(entity, animationState);
        }
        if (entity.kind() == com.trolmastercard.sexmod.entity.CompanionKind.MANGLELIE && entity.mangRiding()) {
            // original f8.af / W: she looks at the nearest player or her target while sitting on her mother's head
            GeoBone head = this.bone("Head2");
            if (head != null) {
                head.setRotY(head.getRotY() + (float) Math.toRadians(entity.mangLookYaw));
                head.setRotX(head.getRotX() + entity.mangLookPitch);
            }
        }
    }

    /**
     * Original cv.a(T, AnimationProcessor, AnimationEvent): outside scenes (states NULL, ATTACK and BOW) the neck turns half
     * of the head yaw, the head follows yaw and pitch of the look direction.
     */
    private void trackLook(CompanionEntity entity, AnimationState<CompanionEntity> state) {
        com.trolmastercard.sexmod.entity.CompanionAction action = entity.action();
        if (entity.isInScene() || entity.currentSceneAnimation() != null
                || action != com.trolmastercard.sexmod.entity.CompanionAction.NONE
                && action != com.trolmastercard.sexmod.entity.CompanionAction.ATTACK
                && action != com.trolmastercard.sexmod.entity.CompanionAction.BOW) {
            return;
        }
        if (entity.kind() == CompanionKind.GALATH && entity.getFirstPassenger() != null) {
            return;
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
        GeoBone neck = this.bone("neck");
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

    private GeoBone bone(String name) {
        for (GeoBone bone : this.getAnimationProcessor().getRegisteredBones()) {
            if (bone.getName().equals(name)) {
                return bone;
            }
        }
        return null;
    }

    /** Original ci.a(): per-state bone rules of the goblin (look at the nearest player, carried legs, throw tumble). */
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

        if (state == com.trolmastercard.sexmod.entity.GoblinBehavior.AWAIT_PICK_UP
                || state == com.trolmastercard.sexmod.entity.GoblinBehavior.VANISH) {
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
        if (state == com.trolmastercard.sexmod.entity.GoblinBehavior.SIT && head != null) {
            this.goblinSitLook(goblin, head);
        }

        if (state == com.trolmastercard.sexmod.entity.GoblinBehavior.THROWING) {
            boolean hide = false;
            if (localCarrier) {
                hide = goblin.goblinPhase() < 15 || firstPerson;
            }
            body.setHidden(hide);
            if (steve != null) {
                steve.setHidden(true);
            }
        }
        if (state == com.trolmastercard.sexmod.entity.GoblinBehavior.PICK_UP && localCarrier && firstPerson) {
            body.setPosY(body.getPosY() - 32.0F);
            if (steve != null) {
                steve.setPosY(steve.getPosY() - 32.0F);
            }
        }
        if ((state == com.trolmastercard.sexmod.entity.GoblinBehavior.THROWING
                || state == com.trolmastercard.sexmod.entity.GoblinBehavior.PICK_UP) && goblin.goblinCarrier().isPresent()) {
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
        if (state == com.trolmastercard.sexmod.entity.GoblinBehavior.THROWN) {
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

    private static boolean shouldHide(CompanionEntity entity, String boneName) {
        String normalized = boneName.toLowerCase(Locale.ROOT);

        if (normalized.startsWith("armor")) {
            return entity.outfit()
                    != com.trolmastercard.sexmod.entity.OutfitMode.ARMORED;
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
            // original cr: the bed slime is part of the doggy states only
            return entity.currentScene() != com.trolmastercard.sexmod.entity.CompanionScene.SLIME_DOGGY;
        }

        if (kind == CompanionKind.ALLIE && (normalized.equals("vagina") || normalized.equals("frontandinside")
                || normalized.equals("fuckhole") || normalized.equals("rside") || normalized.equals("lside")
                || normalized.equals("bone5"))) {
            // only the reverse cowgirl scene undresses her; the body detail stays out of every other state
            return entity.currentScene() != com.trolmastercard.sexmod.entity.CompanionScene.ALLIE_REVERSE_COWGIRL;
        }

        if (kind == CompanionKind.BEE) {
            return normalized.equals("chest");
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
                return Math.floorMod(variant, 2) == 0;
            }

            return normalized.equals("coin")
                    || normalized.equals("nipplel")
                    || normalized.equals("nippler");
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
            return entity.currentScene() != com.trolmastercard.sexmod.entity.CompanionScene.KOBOLD_BLOWJOB;
        }
        if (bone.equals("backpack") || bone.equals("tailpack")) {
            int pack = entity.koboldGene(6);
            boolean payment = entity.currentScene() == com.trolmastercard.sexmod.entity.CompanionScene.KOBOLD_PAYMENT;
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
            return false;
        }

        if (boneName.equals("steve")) {
            return scene.hasPartner();
        }

        var phase = entity.currentSceneAnimation();
        return boneName.equals("cum")
                && phase != null
                && phase.animation().toLowerCase(Locale.ROOT).contains("cum");
    }
}
