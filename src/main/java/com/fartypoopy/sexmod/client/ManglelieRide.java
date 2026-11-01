package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.CompanionScene;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.List;

/**
 * Original ce (Manglelie model) and f8.m: while she sits on her mother's head (mangPos bone) she copies her mother's head pitch,
 * lets her arms hang or follow it, reaches for her arrow target with one arm, and looks at the nearest player.
 */
final class ManglelieRide {
    private static final float LEG = (float) Math.toRadians(140.0D);
    private static final float ELBOW = (float) Math.toRadians(35.0D);
    private static final float QUARTER = (float) Math.toRadians(45.0D);
    private static final float NEG_QUARTER = (float) Math.toRadians(-45.0D);

    private ManglelieRide() {
    }

    /** Original ce.c (dh.b): the daughter sits on her mother and is not in the threesome. */
    static boolean riding(CompanionEntity daughter) {
        return daughter.kind() == CompanionKind.MANGLELIE && daughter.mangRiding()
                && daughter.currentScene() != CompanionScene.MANGLELIE_THREESOME;
    }

    /** Original f8.a(false): her mother, if loaded here. */
    static CompanionEntity mother(CompanionEntity daughter) {
        if (daughter.familyId() == null) {
            return null;
        }
        List<CompanionEntity> found = daughter.level().getEntitiesOfClass(CompanionEntity.class,
                daughter.getBoundingBox().inflate(6.0D),
                c -> c.kind() == CompanionKind.GALATH && daughter.familyId().equals(c.getUUID()));
        return found.isEmpty() ? null : found.get(0);
    }

    /** Original f8.func_70071_h_ (client): every 7th tick of the local player the head target is refreshed. */
    static void tick(CompanionEntity daughter) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.tickCount % 7 != 0 || !riding(daughter)) {
            return;
        }
        CompanionEntity mother = mother(daughter);
        if (mother == null) {
            return;
        }
        Entity look = target(daughter);
        if (look == null) {
            daughter.mangLookYaw = 0.0F;
            daughter.mangLookPitch = 0.0F;
            return;
        }
        Vec3 mangPos = CompanionRenderer.boneOffset(mother, "mangPos");
        Vec3 head = CompanionRenderer.boneOffset(daughter, "head");
        if (mangPos == null || head == null) {
            return;
        }
        Vec3 delta = mother.position().add(mangPos).add(head).subtract(look.position().add(0.0D, look.getEyeHeight(), 0.0D));
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) + 90.0D);
        Float facing = motherFacing(mother, 0.0F);
        float n2 = yaw - mother.yHeadRot;
        if (facing != null) {
            n2 -= facing;
        }
        daughter.mangLookYaw = Math.abs(angleBetween(0.0F, n2)) < 80.0F ? -(float) Math.toRadians(n2) : 0.0F;
        daughter.mangLookPitch = daughter.mangLookYaw == 0.0F ? 0.0F
                : (float) Mth.clamp(-delta.y / 2.0D, -0.75D, 0.75D);
    }

    /** Original f8.o: the arrow target, else the nearest player within 6 blocks. */
    private static Entity target(CompanionEntity daughter) {
        Entity target = daughter.mangTarget();
        if (target != null) {
            return target;
        }
        Entity best = null;
        for (net.minecraft.world.entity.player.Player player : daughter.level().players()) {
            float distance = player.distanceTo(daughter);
            if (distance > 6.0F) {
                continue;
            }
            if (best != null && best.distanceTo(daughter) <= distance) {
                continue;
            }
            best = player;
        }
        return best;
    }

    /** Original cj.a: signed angle between two degree values. */
    private static float angleBetween(float a, float b) {
        a = Mth.positiveModulo(a, 360.0F);
        b = Mth.positiveModulo(b, 360.0F);
        float abs = Math.abs(a - b);
        float min = Math.min(abs, 360.0F - abs);
        return a > b ? -min : min;
    }

    /** Original f_.a(f_, pt): while she flies, summons skeletons or prepares, her body faces her target. */
    static Float motherFacing(CompanionEntity mother, float partial) {
        String animation = GalathMeshLayer.playing(mother);
        if (!animation.equals("animation.galath.idle_flying") && !animation.startsWith("animation.galath.summon_skeleton")
                && !animation.equals("animation.galath.rape_prepare")) {
            return null;
        }
        Entity target = mother.level().getEntity(mother.bossTargetId());
        if (target == null) {
            return null;
        }
        Vec3 delta = target.getPosition(partial).subtract(mother.getPosition(partial));
        return (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
    }

    /** Original ce.a(em, Integer, event): the pose she takes on her mother's head. */
    static void pose(CompanionModel model, CompanionEntity daughter, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || daughter.currentScene() == CompanionScene.MANGLELIE_THREESOME) {
            return;
        }
        CompanionEntity mother = mother(daughter);
        if (mother == null) {
            return;
        }
        if (riding(daughter)) {
            followPitch(model, daughter, mother);
            reach(model, daughter, mother, partial);
        }
        GeoBone body = model.bone("body");
        if (body != null && mother.galathBodyScale > 0.0F) {
            body.setRotY(mother.galathBodyYaw + body.getRotY());
            body.setScaleX(mother.galathBodyScale);
            body.setScaleY(mother.galathBodyScale);
            body.setScaleZ(mother.galathBodyScale);
        }
        String state = GalathMeshLayer.playing(mother);
        if (state.equals("animation.galath.corrupt_cum") || state.equals("animation.galath.corrupt_intro")
                || state.equals("animation.galath.corrupt_slow")) {
            GeoBone leg = model.bone("legR");
            GeoBone lowerR = model.bone("lowerArmR");
            GeoBone lowerL = model.bone("lowerArmL");
            if (leg != null && lowerR != null && lowerL != null) {
                leg.setRotY(leg.getRotY() + NEG_QUARTER);
                lowerR.setRotX(lowerR.getRotX() + NEG_QUARTER);
                lowerL.setRotX(lowerL.getRotX() + NEG_QUARTER);
            }
        }
    }

    /** Original ce.b: the torso follows her mother's head pitch, and her head follows the player with a 7/s ease. */
    private static void followPitch(CompanionModel model, CompanionEntity daughter, CompanionEntity mother) {
        float pitch = mother.galathHeadPitch;
        GeoBone tool = model.bone("rotationTool");
        GeoBone head = model.bone("head");
        GeoBone upper = model.bone("upperBody");
        GeoBone boobs = model.bone("boobs");
        if (tool == null || head == null || upper == null || boobs == null) {
            return;
        }
        tool.setRotX(pitch);
        if (pitch > 0.0F) {
            upper.setRotX(-1.1111112F * pitch);
            head.setRotX(0.1333F * pitch);
            boobs.setRotX(pitch * 22.5F / 45.0F);
        } else {
            upper.setRotX(-1.6666666F * pitch);
            head.setRotX(pitch * 0.666F);
        }
        float yawDelta = radianDifference(daughter.mangSmoothYaw, daughter.mangLookYaw);
        float pitchDelta = radianDifference(daughter.mangSmoothPitch, daughter.mangLookPitch);
        float fps = Minecraft.getInstance().getFps();
        if (fps == 0.0F) {
            fps = 1.0F;
        }
        float yaw = daughter.mangSmoothYaw + 7.0F * Mth.clamp(yawDelta, -7.0F, 7.0F) * (1.0F / fps);
        float tilt = daughter.mangSmoothPitch + 7.0F * Mth.clamp(pitchDelta, -7.0F, 7.0F) * (1.0F / fps);
        head.setRotY(head.getRotY() + yaw);
        head.setRotX(head.getRotX() + tilt);
        daughter.mangSmoothYaw = yaw;
        daughter.mangSmoothPitch = tilt;
    }

    /** Original be.a(double, double): shortest signed difference of two radian angles. */
    private static float radianDifference(double from, double to) {
        double a = (from + Math.PI * 2.0D) % (Math.PI * 2.0D);
        double b = (to + Math.PI * 2.0D) % (Math.PI * 2.0D);
        double d = b - a;
        while (d < -Math.PI) {
            d += Math.PI * 2.0D;
        }
        while (d >= Math.PI) {
            d -= Math.PI * 2.0D;
        }
        return (float) d;
    }

    private static final class Arms {
        float[] armR;
        float[] armL;
        float[] lowerR;
        float[] lowerL;
        float scaleL = 1.0F;
        float scaleR = 1.0F;
        float elbowL;
        float elbowR;

        static Arms lerp(Arms a, Arms b, float t) {
            Arms r = new Arms();
            r.armR = lerp(a.armR, b.armR, t);
            r.armL = lerp(a.armL, b.armL, t);
            r.lowerR = lerp(a.lowerR, b.lowerR, t);
            r.lowerL = lerp(a.lowerL, b.lowerL, t);
            r.scaleL = a.scaleL + (b.scaleL - a.scaleL) * t;
            r.scaleR = a.scaleR + (b.scaleR - a.scaleR) * t;
            r.elbowL = a.elbowL + (b.elbowL - a.elbowL) * t;
            r.elbowR = a.elbowR + (b.elbowR - a.elbowR) * t;
            return r;
        }

        private static float[] lerp(float[] a, float[] b, float t) {
            return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
        }
    }

    private static float[] rotation(GeoBone bone) {
        return new float[] {bone.getRotX(), bone.getRotY(), bone.getRotZ()};
    }

    /** Original ce.d: arms hang from the head pitch, or reach for the arrow target. */
    private static void reach(CompanionModel model, CompanionEntity daughter, CompanionEntity mother, float partial) {
        GeoBone armL = model.bone("armL");
        GeoBone armR = model.bone("armR");
        GeoBone lowerL = model.bone("lowerArmL");
        GeoBone lowerR = model.bone("lowerArmR");
        GeoBone elbowR = model.bone("elbowR");
        GeoBone elbowL = model.bone("elbowL");
        if (armL == null || armR == null || lowerL == null || lowerR == null || elbowR == null || elbowL == null) {
            return;
        }
        Entity target = daughter.mangTarget();
        boolean noTarget = target == null;
        if (!noTarget) {
            daughter.mangTargetPos = target.getPosition(partial).add(0.0D, target.getEyeHeight(), 0.0D);
        }
        float fps = Minecraft.getInstance().getFps();
        if (fps == 0.0F) {
            fps = 1.0F;
        }
        if (daughter.mangNoTargetPrev == noTarget) {
            daughter.mangBlend = 0.0F;
        } else {
            daughter.mangBlend += 1.5F / fps;
            if (daughter.mangBlend >= 1.0F) {
                daughter.mangBlend = 0.0F;
                daughter.mangNoTargetPrev = noTarget;
            }
        }
        Arms result;
        float blend = daughter.mangBlend;
        if (blend == 0.0F) {
            result = noTarget || daughter.mangTargetPos == null ? hanging(mother, armR, armL, lowerL, lowerR)
                    : aiming(daughter, mother, lowerR, lowerL, model, partial);
            if (result == null) {
                result = hanging(mother, armR, armL, lowerL, lowerR);
            }
        } else {
            Arms idle = hanging(mother, armR, armL, lowerL, lowerR);
            Arms aim = daughter.mangTargetPos == null ? null : aiming(daughter, mother, lowerR, lowerL, model, partial);
            if (aim == null) {
                aim = idle;
            }
            double ease = easeOutBack(blend);
            result = Arms.lerp(idle, aim, (float) (daughter.mangNoTargetPrev ? ease : 1.0D - ease));
        }
        armR.setRotX(result.armR[0]);
        armR.setRotY(result.armR[1]);
        armR.setRotZ(result.armR[2]);
        armL.setRotX(result.armL[0]);
        armL.setRotY(result.armL[1]);
        armL.setRotZ(result.armL[2]);
        lowerL.setRotX(result.lowerL[0]);
        lowerL.setRotY(result.lowerL[1]);
        lowerL.setRotZ(result.lowerL[2]);
        lowerR.setRotX(result.lowerR[0]);
        lowerR.setRotY(result.lowerR[1]);
        lowerR.setRotZ(result.lowerR[2]);
        armL.setScaleY(result.scaleL);
        armR.setScaleY(result.scaleR);
        elbowR.setRotY(result.elbowR);
        elbowL.setRotY(result.elbowL);
    }

    private static double easeOutBack(double n) {
        double s = 1.70158D;
        return 1.0D + (s + 1.0D) * Math.pow(n - 1.0D, 3.0D) + s * Math.pow(n - 1.0D, 2.0D);
    }

    private static double easeOutQuart(double n) {
        return 1.0D - Math.pow(1.0D - n, 4.0D);
    }

    private static double easeInOutSine(double n) {
        return -(Math.cos(Math.PI * n) - 1.0D) / 2.0D;
    }

    /** Original ce.a(f_, bones): no target, the arms hang from her mother's head pitch. */
    private static Arms hanging(CompanionEntity mother, GeoBone armR, GeoBone armL, GeoBone lowerL, GeoBone lowerR) {
        float pitch = mother.galathHeadPitch;
        Arms arms = new Arms();
        if (pitch > 0.0F) {
            arms.armR = new float[] {armR.getRotX() - pitch, armR.getRotY() - pitch * -25.0F / 45.0F, armR.getRotZ() + pitch * 12.5F / 45.0F};
            arms.armL = new float[] {armL.getRotX() - pitch, armL.getRotY() + pitch * 15.0F / 45.0F, armL.getRotZ()};
            arms.lowerL = rotation(lowerL);
            arms.lowerR = rotation(lowerR);
            return arms;
        }
        arms.lowerR = new float[] {lowerR.getRotX() + 2.0F * pitch, lowerR.getRotY(), lowerR.getRotZ()};
        arms.lowerL = new float[] {lowerL.getRotX() + 2.2222223F * pitch, lowerL.getRotY(), lowerL.getRotZ()};
        arms.armR = new float[] {armR.getRotX() - pitch, armR.getRotY(), armR.getRotZ() + pitch * 5.0F / 45.0F};
        arms.armL = new float[] {armL.getRotX() - pitch, armL.getRotY(), armL.getRotZ() - pitch * 5.0F / 45.0F};
        return arms;
    }

    /** Yaw and pitch of the direction from one point to another (original be.a). */
    private static float[] angles(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from).normalize();
        return new float[] {(float) Math.atan2(d.x, d.z), (float) Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))};
    }

    /** Original ce.a(f8, f_, ...): one arm points at the target and the other balances; the bow pull follows the shot timer. */
    private static Arms aiming(CompanionEntity daughter, CompanionEntity mother, GeoBone lowerR, GeoBone lowerL,
                               CompanionModel model, float partial) {
        Vec3 mangPos = CompanionRenderer.boneOffset(mother, "mangPos");
        Vec3 offsetR = CompanionRenderer.boneOffset(daughter, "armR");
        Vec3 offsetL = CompanionRenderer.boneOffset(daughter, "armL");
        GeoBone upper = model.bone("upperBody");
        if (mangPos == null || offsetR == null || offsetL == null || upper == null) {
            return null;
        }
        Vec3 target = daughter.mangTargetPos;
        Arms arms = new Arms();
        arms.lowerL = new float[] {ELBOW, 0.0F, lowerR.getRotZ()};
        arms.lowerR = new float[] {LEG, 0.0F, lowerL.getRotZ()};
        float pitch = mother.galathHeadPitch + upper.getRotX();
        Vec3 seat = mother.getPosition(partial).add(mangPos);
        float[] r = angles(seat.add(offsetR), target);
        float[] l = angles(seat.add(offsetL), target);
        Float facing = motherFacing(mother, partial);
        float yawDegrees = facing != null ? facing
                : Mth.rotLerp(partial, mother.yHeadRotO, mother.yHeadRot);
        float c = (float) Math.toRadians(yawDegrees);
        long start = daughter.mangShotStart();
        float b = start == -1L ? 0.0F : (daughter.level().getGameTime() - start + partial) / 28.0F;
        float n3 = (float) easeOutQuart(Math.min(1.0F, b));
        float n4 = n3 != 1.0F ? 0.0F : Math.max(0.0F, (b * 28.0F - 28.0F) / 32.0F - 0.5F) * 2.0F;
        float n5 = (float) easeInOutSine(n4);
        float c2 = (float) Math.toRadians(0.0F + (90.0F - 0.0F) * n3);
        Vec3 delta = target.subtract(daughter.getPosition(partial));
        double yaw = Math.toRadians(Mth.rotLerp(partial, mother.yHeadRotO, mother.yHeadRot));
        double side = -Math.cos(yaw) * delta.x - Math.sin(yaw) * delta.z;
        boolean rightArm = side > 0.35D;
        float ninety = (float) Math.toRadians(90.0D);
        float twenty = (float) Math.toRadians(-20.0D);
        if (rightArm) {
            arms.armR = new float[] {-pitch + r[1] + ninety, r[0], 0.0F};
            arms.armL = new float[] {-pitch + l[1] + ninety,
                    (float) (l[0] + twenty * Math.cos(r[0] + c * 1.0F) + (c2 / 2.0F + (0.0F - c2 / 2.0F) * n5)), 0.0F};
            arms.scaleL = 1.0F + Math.abs(Math.abs(r[0]) - Math.abs(c)) * 0.1909F;
            arms.elbowL = ninety;
            arms.lowerL[2] = c2 + (0.0F - c2) * n5;
            if (n4 > 0.5F) {
                arms.lowerL[0] = ELBOW + (float) (QUARTER + (0.0D - QUARTER) * easeInOutSine((n4 - 0.5F) * 2.0F));
            } else if (n4 != 0.0F) {
                arms.lowerL[0] = ELBOW + (float) (0.0D + (QUARTER - 0.0D) * easeInOutSine(n4 * 2.0F));
            }
        } else {
            arms.armL = new float[] {-pitch + l[1] + ninety, l[0], 0.0F};
            arms.armR = new float[] {-pitch + r[1] + ninety,
                    (float) (r[0] + Math.toRadians(20.0D) * Math.cos(l[0] + c) - (c2 / 2.0F + (0.0F - c2 / 2.0F) * n5)), 0.0F};
            arms.scaleR = 1.0F + Math.abs(Math.abs(l[0]) - Math.abs(c)) * 0.1909F;
            arms.elbowR = ninety;
            arms.lowerR[2] = -(c2 + (0.0F - c2) * n5);
            if (n4 > 0.5F) {
                arms.lowerR[0] = LEG + (float) (QUARTER + (0.0D - QUARTER) * easeInOutSine((n4 - 0.5F) * 2.0F));
            } else if (n4 != 0.0F) {
                arms.lowerR[0] = LEG + (float) (0.0D + (QUARTER - 0.0D) * easeInOutSine(n4 * 2.0F));
            }
        }
        arms.armR[1] += c;
        arms.armL[1] += c;
        return arms;
    }
}
