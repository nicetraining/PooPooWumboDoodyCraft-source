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
            "energyballl",
            "egg"
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

        if (entity.goblin() != null) {
            goblinPose(entity);
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
            if (normalized.startsWith("freckles")) {
                return true;
            }

            if (normalized.matches("horn(?:ur|ul)[0-7]")) {
                int hornVariant = normalized.charAt(normalized.length() - 1) - '0';
                return hornVariant != Math.floorMod(variant, 8);
            }

            if (normalized.matches("horn(?:dr|dl)[1-2]")) {
                return true;
            }
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
