package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.GalathBoss;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Original da (Galath renderer) procedural geometry: wing membranes between wingRV0..13 / wingLV0..13 (galath.png tiles),
 * the two hair strands (12-corner prisms from the head), the bra strings (black lines) and the paralysis stars.
 */
public final class GalathMeshLayer extends GeoRenderLayer<CompanionEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("sexmod", "textures/entity/galath/galath.png");
    private static final ResourceLocation STAR = ResourceLocation.fromNamespaceAndPath("sexmod", "textures/star.png");
    private static final String[] BRA = {
            "braStringMidStartR", "braStringMidMid1R", "braStringMidMid2R", "braStringMidMid3R", "braStringMidEndR",
            "braStringBackR", "braStringRightEndR", "braStringRightStartR", null,
            "braStringRightR", "braStringRightL", null,
            "braStringMidStartL", "braStringMidMid1L", "braStringMidMid2L", "braStringMidMid3L", "braStringMidEndL",
            "braStringBackL", "braStringLeftEndL", "braStringLeftStartL"};

    public GalathMeshLayer(GeoRenderer<CompanionEntity> renderer) {
        super(renderer);
    }

    /** Bones this layer needs matrices for. */
    public static boolean tracks(String name) {
        return name.startsWith("wingRV") || name.startsWith("wingLV") || name.startsWith("hairStrand")
                || name.startsWith("braString") || name.equals("stars") || name.equals("tongue")
                || name.equals("mangTongue") || name.equals("weapon") || name.equals("weaponStart")
                || name.equals("weaponEnd") || name.equals("cockParticles") || name.equals("slipR") || name.equals("slipL") || name.equals("turnable");
    }

    @Override
    public void render(PoseStack poseStack, CompanionEntity entity, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (entity.kind() != CompanionKind.GALATH) {
            return;
        }
        String animation = playing(entity);
        if (FutaSetting.takePuff(entity)) {
            Vector3f local = pos(model, "cockParticles");
            if (local != null) {
                var at = world(entity, partialTick, local);
                for (int i = 0; i < 10; i++) { // original a_: ten dragon-breath puffs with a random +-0.025 drift
                    entity.level().addParticle(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z,
                            entity.getRandom().nextFloat() * 0.025F * (entity.getRandom().nextBoolean() ? 1 : -1),
                            entity.getRandom().nextFloat() * 0.025F * (entity.getRandom().nextBoolean() ? 1 : -1),
                            entity.getRandom().nextFloat() * 0.025F * (entity.getRandom().nextBoolean() ? 1 : -1));
                }
            }
        }
        Matrix4f pose = poseStack.last().pose();
        if (wingsOut(animation)) {
            wings(model, bufferSource, pose);
        }
        float elapsed = elapsed(entity, animation, partialTick);
        boolean coin = animation.equals("animation.galath.give_coin");
        if (!(coin && elapsed > 100.0F)) {
            hair(entity, model, bufferSource, pose, partialTick);
        }
        tongues(entity, model, bufferSource, poseStack, animation, elapsed);
        if (!animation.equals("animation.galath.attack")) {
            entity.galathSword = false; // original f_.L(): only while in ATTACK_SWORD
            entity.galathBlade = false;
        }
        if (entity.galathSword) {
            sword(entity, model, bufferSource, poseStack, packedLight);
        }
        if (entity.galathBlade && entity.galathBladeTick != entity.level().getGameTime()) {
            entity.galathBladeTick = entity.level().getGameTime();
            blade(entity, model, partialTick);
        }
        if (entity.galathNudeBurst) {
            entity.galathNudeBurst = false;
            nudeBurst(entity, model, partialTick);
        }
        if (!braHidden(entity, animation, elapsed)) {
            bra(model, bufferSource, pose);
        }
        if (entity.bossFlag(GalathBoss.F_KNOCKED) && !entity.bossFlag(GalathBoss.F_PARALYSED)) {
            stars(entity, model, bufferSource, poseStack, partialTick);
        }
    }

    /** Name of the animation the main controller is playing (the original asked its state / controller). */
    static String playing(CompanionEntity entity) {
        var manager = entity.getAnimatableInstanceCache().getManagerForId(entity.getId());
        var controller = manager == null ? null : manager.getAnimationControllers().get("main");
        var current = controller == null ? null : controller.getCurrentAnimation();
        return current == null ? "" : current.animation().name();
    }

    private static final java.util.Map<Integer, Object[]> STARTS = new java.util.HashMap<>();

    /** Ticks since the current animation began (original fp.c: controller tick minus tick offset). */
    static float elapsed(CompanionEntity entity, String animation, float partialTick) {
        float now = entity.level().getGameTime() + partialTick;
        Object[] entry = STARTS.get(entity.getId());
        if (entry == null || !entry[0].equals(animation) || now < (Float) entry[1]) {
            entry = new Object[] {animation, now};
            STARTS.put(entity.getId(), entry);
        }
        return now - (Float) entry[1];
    }

    // original ck.a(v, x, y, z): rotations in degrees, X then Y then Z
    private static Vector3f rotXYZ(Vector3f v, float xDeg, float yDeg, float zDeg) {
        float sx = (float) Math.sin(Math.toRadians(xDeg));
        float cx = (float) Math.cos(Math.toRadians(xDeg));
        float y = v.y * cx - v.z * sx;
        float z = v.y * sx + v.z * cx;
        return rotYZ(new Vector3f(v.x, y, z), yDeg, zDeg);
    }

    @FunctionalInterface
    private interface Wave {
        float at(int segment, float time);
    }

    /**
     * Original ef.a: a tongue ribbon of 12 segments. Every segment turns the growth direction by the three wave
     * functions; the cross-section tapers in X. Drawn in the bone's own frame in (152,45,62).
     */
    private static void ribbon(BakedGeoModel model, String boneName, MultiBufferSource bufferSource, PoseStack poseStack,
                               float offset, float length, Wave rx, Wave ry, Wave rz) {
        GeoBone bone = model.getBone(boneName).orElse(null);
        if (bone == null || bone.isHidden()) {
            return;
        }
        float time = net.minecraft.client.Minecraft.getInstance().player == null ? 0.0F
                : net.minecraft.client.Minecraft.getInstance().player.tickCount
                + net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        final float halfX = 0.03F;
        final float halfY = 0.005F;
        final int segments = 12;
        Vector3f[] base = {new Vector3f(-halfX, -halfY, 0), new Vector3f(-halfX, halfY, 0), new Vector3f(halfX, halfY, 0),
                new Vector3f(halfX, -halfY, 0)};
        Vector3f dir = new Vector3f(0, 0, -length);
        Vector3f position = length == 0.0F ? new Vector3f() : new Vector3f(0, 0, -offset);
        Vector3f[][] rings = new Vector3f[segments + 1][4];
        for (int i = 0; i <= segments; i++) {
            float taper = 1.0F - i / (float) segments;
            for (int j = 0; j < 4; j++) {
                rings[i][j] = new Vector3f(base[j].x * taper, base[j].y, base[j].z).add(position);
            }
            dir = rotXYZ(dir, rx.at(i, time), ry.at(i, time), rz.at(i, time));
            position = new Vector3f(position).add(dir);
        }
        poseStack.pushPose();
        poseStack.mulPose(bone.getModelSpaceMatrix());
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        link(consumer, pose, base, rings[0]);
        for (int k = 0; k < segments - 1; k++) {
            link(consumer, pose, rings[k], rings[k + 1]);
        }
        poseStack.popPose();
    }

    private static void link(VertexConsumer consumer, Matrix4f pose, Vector3f[] a, Vector3f[] b) {
        int[][] quads = {{1, 2}, {0, 1}, {2, 3}, {0, 3}};
        for (int[] q : quads) {
            for (Vector3f p : new Vector3f[] {a[q[0]], a[q[1]], b[q[1]], b[q[0]]}) {
                consumer.addVertex(pose, p.x, p.y, p.z).setColor(152, 45, 62, 255);
            }
        }
    }

    /** Original da.e / c / f: Galath's tongue while licking or in the slow bed scene, and the daughter's tongue. */
    private static void tongues(CompanionEntity entity, BakedGeoModel model, MultiBufferSource bufferSource,
                                PoseStack poseStack, String animation, float elapsed) {
        var phase = entity.currentSceneAnimation();
        float progress = 0.0F;
        if (phase != null && phase.durationTicks() > 0) {
            float ticks = phase.loop() ? elapsed % phase.durationTicks() : elapsed;
            progress = Mth.clamp(ticks / phase.durationTicks(), 0.0F, 1.0F);
        }
        double turn = Math.PI * 2.0D * progress;
        if (animation.equals("animation.galath.pussy_licking")) {
            ribbon(model, "tongue", bufferSource, poseStack, 0.1F, 0.035F,
                    (i, t) -> (float) (Math.sin(t * 0.3D - 0.2D * i) * 15.0D),
                    (i, t) -> (float) (Math.sin(t * -0.15D - 0.2D * i) * 3.0D),
                    (i, t) -> 0.0F);
            return;
        }
        boolean slow = animation.equals("animation.shared.bed_slow") || animation.equals("animation.shared.bed_back");
        boolean transition = animation.equals("animation.shared.bed_soft"); // original f_.aD
        if (slow) {
            ribbon(model, "tongue", bufferSource, poseStack, 0.0F, 0.02F,
                    (i, t) -> (float) (Math.cos(turn - 0.2D * i) * 15.0D),
                    (i, t) -> (float) (Math.cos(turn - 0.2D * i) * 5.0D),
                    (i, t) -> 0.0F);
        }
        if (slow || transition) {
            float n = transition ? 1.0F - Math.min(0.29F, elapsed / 20.0F) / 0.29F : 1.0F;
            float length = n * (float) (0.02D * (-0.4D * Math.cos(turn + 1.05D) + 0.6D));
            ribbon(model, "mangTongue", bufferSource, poseStack, 0.0F, length,
                    (i, t) -> n * (float) (Math.cos(turn + 0.35D - 0.2D * i) * -10.0D),
                    (i, t) -> 0.0F,
                    (i, t) -> n * (float) (Math.cos(turn + 1.25D - 0.1D * i) * -5.0D));
        }
    }

    /** Original f_.a(): no wings in CORRUPT_SLOW / FAST / CUM (and COWGIRLCUM). */
    private static boolean wingsOut(String animation) {
        return !(animation.equals("animation.galath.corrupt_slow") || animation.equals("animation.galath.corrupt_soft")
                || animation.equals("animation.galath.corrupt_hard") || animation.equals("animation.galath.corrupt_cum"));
    }

    /**
     * Original cb.k(): while she masturbates the molang variables pitch / armpitch / armyaw / yaw of that animation are
     * fed with the direction to the viewer (her partner, else the local player).
     */
    static void aimAtViewer(CompanionEntity entity, BakedGeoModel model, float partialTick) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.entity.player.Player player = entity.scenePartnerId()
                .map(id -> entity.level().getPlayerByUUID(id)).orElse(minecraft.player);
        Vector3f head = pos(model, "head");
        if (player == null || head == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 me = entity.getPosition(partialTick);
        net.minecraft.world.phys.Vec3 eyeUp = new net.minecraft.world.phys.Vec3(0.0D, player.getEyeHeight(), 0.0D);
        net.minecraft.world.phys.Vec3 headOffset = world(entity, partialTick, head).subtract(me);
        net.minecraft.world.phys.Vec3 v = me.add(eyeUp).subtract(player.getPosition(partialTick)).add(headOffset);
        float yaw = (float) Math.toDegrees(Math.atan2(v.z, v.x)) - entity.getYRot();
        float pitchAngle = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
        double distance = Math.abs(v.x) + Math.abs(v.y) + Math.abs(v.z);
        double pitch = distance * 7.0D - 20.0D + pitchAngle - 80.0D;
        double armPitch = distance * 5.0D - 20.0D + pitchAngle - 110.0D;
        double armYaw = yaw + 80.0F;
        double bodyYaw = yaw + 90.0F;
        software.bernie.geckolib.loading.math.MathParser.setVariable("pitch", () -> pitch);
        software.bernie.geckolib.loading.math.MathParser.setVariable("armpitch", () -> armPitch);
        software.bernie.geckolib.loading.math.MathParser.setVariable("armyaw", () -> armYaw);
        software.bernie.geckolib.loading.math.MathParser.setVariable("yaw", () -> bodyYaw);
    }

    private static net.minecraft.world.phys.Vec3 world(CompanionEntity entity, float partialTick, Vector3f local) {
        float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        Vector3f v = new Vector3f(local).rotateY((float) Math.toRadians(180.0F - yaw));
        return entity.getPosition(partialTick).add(v.x, v.y, v.z);
    }

    /** Original da weapon bone: the iron sword in her hand while she attacks with it (scaled 1.5 x 1 x 2). */
    private static void sword(CompanionEntity entity, BakedGeoModel model, MultiBufferSource bufferSource,
                              PoseStack poseStack, int packedLight) {
        GeoBone bone = model.getBone("weapon").orElse(null);
        if (bone == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(bone.getModelSpaceMatrix());
        poseStack.scale(1.5F, 1.0F, 2.0F);
        poseStack.translate(0.0F, 0.0F, 0.05F);
        poseStack.mulPose(Axis.XP.rotationDegrees(110.0F));
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(entity,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD),
                net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, bufferSource,
                entity.level(), packedLight, OverlayTexture.NO_OVERLAY, entity.getId());
        poseStack.popPose();
    }

    private static double spread(net.minecraft.util.RandomSource random) {
        return random.nextDouble() * 0.25D * (random.nextBoolean() ? 1 : -1);
    }

    /** Original f_.af(): dragon breath along the blade (10 points, 3 each) and around its tip. */
    private static void blade(CompanionEntity entity, BakedGeoModel model, float partialTick) {
        Vector3f a = pos(model, "weaponStart");
        Vector3f b = pos(model, "weaponEnd");
        if (a == null || b == null) {
            return;
        }
        var start = world(entity, partialTick, a);
        var end = world(entity, partialTick, b);
        var random = entity.getRandom();
        for (int step = 0; step < 10; step++) {
            var at = start.lerp(end, step * 0.1D);
            for (int i = 0; i < 3; i++) {
                entity.level().addParticle(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH,
                        at.x + spread(random), at.y + spread(random), at.z + spread(random), 0.0D, 0.0D, 0.0D);
            }
        }
        for (int i = 0; i < 3; i++) {
            entity.level().addParticle(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH,
                    end.x + spread(random) * (random.nextBoolean() ? 1 : -1), end.y + spread(random),
                    end.z + spread(random), 0.0D, 0.0D, 0.0D);
        }
    }

    /** Original setNude keyframe: one dragon-breath puff at each of the slipR, slipL and turnable bones. */
    private static void nudeBurst(CompanionEntity entity, BakedGeoModel model, float partialTick) {
        for (String name : new String[] {"slipR", "slipL", "turnable"}) {
            Vector3f local = pos(model, name);
            if (local != null) {
                var at = world(entity, partialTick, local);
                entity.level().addParticle(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** Original f_.bb (C()): set while paralysed or in the rape / corrupt / masturbate states. */
    static boolean braHidden(CompanionEntity entity, String animation, float elapsed) {
        if (entity.isPlayerForm() && entity.formBare()) {
            return true; // original er.c(): her player stripped her (model 0)
        }
        if (animation.equals("animation.galath.rape_prepare")) {
            return elapsed >= 1.3333F * 20.0F; // set by the "setNude" keyframe
        }
        return entity.bossFlag(GalathBoss.F_PARALYSED)
                || animation.startsWith("animation.galath.rape") || animation.startsWith("animation.galath.corrupt_slow")
                || animation.equals("animation.galath.corrupt_soft") || animation.equals("animation.galath.corrupt_hard")
                || animation.equals("animation.galath.corrupt_cum") || animation.contains("masturbat");
    }

    private static Vector3f pos(BakedGeoModel model, String name) {
        GeoBone bone = model.getBone(name).orElse(null);
        if (bone == null || bone.isHidden()) {
            return null;
        }
        return bone.getModelSpaceMatrix().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vector3f p, float u, float v) {
        consumer.addVertex(pose, p.x, p.y, p.z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    private static void wings(BakedGeoModel model, MultiBufferSource bufferSource, Matrix4f pose) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        for (String side : new String[] {"wingRV", "wingLV"}) {
            Vector3f[] p = new Vector3f[14];
            for (int i = 0; i < 14; i++) {
                p[i] = pos(model, side + i);
                if (p[i] == null) {
                    return;
                }
            }
            float cu = 0.25F;
            float cv = 0.125F;
            float xu = 0.375F;
            float xv = 0.125F;
            float t = 0.125F;
            // two triangles (sent as degenerate quads)
            vertex(consumer, pose, p[0], cu, cv);
            vertex(consumer, pose, p[1], cu + t, cv);
            vertex(consumer, pose, p[2], cu + t, cv + t);
            vertex(consumer, pose, p[2], cu + t, cv + t);
            vertex(consumer, pose, p[11], cu, cv);
            vertex(consumer, pose, p[12], cu + t, cv);
            vertex(consumer, pose, p[13], cu + t, cv + t);
            vertex(consumer, pose, p[13], cu + t, cv + t);
            vertex(consumer, pose, p[3], xu, xv + t);
            vertex(consumer, pose, p[4], xu, xv);
            vertex(consumer, pose, p[5], xu + t, xv);
            vertex(consumer, pose, p[6], xu + t, xv + t);
            vertex(consumer, pose, p[7], xu, xv + t);
            vertex(consumer, pose, p[8], xu, xv);
            vertex(consumer, pose, p[9], xu + t, xv);
            vertex(consumer, pose, p[10], xu + t, xv + t);
        }
    }

    // original ck.a(v, 0, rotY, rotZ)
    private static Vector3f rotYZ(Vector3f v, float yDeg, float zDeg) {
        float sy = (float) Math.sin(Math.toRadians(yDeg));
        float cy = (float) Math.cos(Math.toRadians(yDeg));
        float sz = (float) Math.sin(Math.toRadians(zDeg));
        float cz = (float) Math.cos(Math.toRadians(zDeg));
        float x = v.x * cy + v.z * sy;
        float z = -v.x * sy + v.z * cy;
        return new Vector3f(x * cz - v.y * sz, x * sz + v.y * cz, z);
    }

    // original ck.a(v, yaw): a reflection that is its own inverse
    private static Vector3f yaw(Vector3f v, float deg) {
        double a = Math.toRadians(deg);
        double s = Math.sin(a);
        double c = Math.cos(a);
        double s2 = Math.sin(a + Math.PI / 2.0D);
        double c2 = Math.cos(a + Math.PI / 2.0D);
        return new Vector3f((float) (-s2 * v.x - s * v.z), v.y, (float) (c2 * v.x + c * v.z));
    }

    private static final int[][] FACES = {
            {0, 1, 5, 4}, {1, 2, 6, 5}, {3, 2, 6, 7}, {0, 4, 7, 3}, {0, 1, 2, 3},
            {4, 5, 9, 8}, {9, 10, 6, 5}, {10, 11, 7, 6}, {4, 7, 11, 8}, {8, 9, 10, 11}};

    private static void hair(CompanionEntity entity, BakedGeoModel model, MultiBufferSource bufferSource, Matrix4f pose,
                             float partialTick) {
        GeoBone head = model.getBone("head").orElse(null);
        if (head == null) {
            return;
        }
        float yawDeg = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headY = (float) Math.toDegrees(head.getRotY());
        float headZ = (float) Math.toDegrees(head.getRotZ());
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        for (String side : new String[] {"R", "L"}) {
            Vector3f start = pos(model, "hairStrandStart" + side);
            Vector3f mid = pos(model, "hairStrandMid" + side);
            Vector3f end = pos(model, "hairStrandEnd" + side);
            if (start == null || mid == null || end == null) {
                continue;
            }
            float n2 = 0.0296875F;
            float n3 = 0.06484375F;
            float n4 = 0.026125F;
            float n5 = 0.0570625F;
            Vector3f[] c = {
                    new Vector3f(n2, 0, -n3), new Vector3f(-n2, 0, -n3), new Vector3f(-n2, 0, n3), new Vector3f(n2, 0, n3),
                    new Vector3f(n2, n3, 0), new Vector3f(-n2, n3, 0), new Vector3f(-n2, -n3, 0), new Vector3f(n2, -n3, 0),
                    new Vector3f(n4, 0, -n5), new Vector3f(-n4, 0, -n5), new Vector3f(-n4, 0, n5), new Vector3f(n4, 0, n5)};
            for (int i = 0; i < 4; i++) {
                // the original turns the start ring by the head in world axes: conjugate by the yaw reflection
                c[i] = yaw(rotYZ(yaw(c[i], yawDeg), headY, headZ), yawDeg).add(start);
            }
            for (int i = 4; i < 8; i++) {
                c[i].add(mid);
            }
            for (int i = 8; i < 12; i++) {
                c[i].add(end);
            }
            for (int[] face : FACES) {
                for (int index : face) {
                    consumer.addVertex(pose, c[index].x, c[index].y, c[index].z).setColor(84, 66, 88, 255);
                }
            }
        }
    }

    private static void bra(BakedGeoModel model, MultiBufferSource bufferSource, Matrix4f pose) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        for (int i = 0; i + 1 < BRA.length; i++) {
            if (BRA[i] == null || BRA[i + 1] == null) {
                continue;
            }
            Vector3f a = pos(model, BRA[i]);
            Vector3f b = pos(model, BRA[i + 1]);
            if (a == null || b == null) {
                continue;
            }
            Vector3f d = new Vector3f(b).sub(a);
            if (d.lengthSquared() > 0.0F) {
                d.normalize();
            }
            consumer.addVertex(pose, a.x, a.y, a.z).setColor(0, 0, 0, 255).setNormal(d.x, d.y, d.z);
            consumer.addVertex(pose, b.x, b.y, b.z).setColor(0, 0, 0, 255).setNormal(d.x, d.y, d.z);
        }
    }

    private static void stars(CompanionEntity entity, BakedGeoModel model, MultiBufferSource bufferSource,
                              PoseStack poseStack, float partialTick) {
        Vector3f origin = pos(model, "stars");
        if (origin == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(origin.x, origin.y, origin.z);
        float time = entity.level().getGameTime() + partialTick;
        poseStack.mulPose(Axis.XP.rotationDegrees((float) (Math.sin(time * 0.2D) * 5.0D)));
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 3.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) (Math.cos(time * 0.2D) * 5.0D)));
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer ring = bufferSource.getBuffer(RenderType.debugLineStrip(2.0D));
        for (float a = 0.0F; a < Math.PI * 2.0D; a += (float) Math.toRadians(9.0D)) {
            ring.addVertex(pose, (float) (Math.sin(a) * 0.3D), 0.0F, (float) (Math.cos(a) * 0.3D))
                    .setColor(212, 175, 55, 255);
        }
        VertexConsumer sprite = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(STAR));
        for (float a = 0.0F; a < Math.PI * 2.0D; a += (float) Math.toRadians(60.0D)) {
            float x = (float) (Math.sin(a) * 0.3D);
            float z = (float) (Math.cos(a) * 0.3D);
            vertex(sprite, pose, new Vector3f(x - 0.1F, 0.1F, z), 0.0F, 0.0F);
            vertex(sprite, pose, new Vector3f(x + 0.1F, 0.1F, z), 1.0F, 0.0F);
            vertex(sprite, pose, new Vector3f(x + 0.1F, -0.1F, z), 1.0F, 1.0F);
            vertex(sprite, pose, new Vector3f(x - 0.1F, -0.1F, z), 0.0F, 1.0F);
        }
        poseStack.popPose();
    }
}
