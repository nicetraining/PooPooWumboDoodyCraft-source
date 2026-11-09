package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.ClientConfig;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * Jiggle physics for the soft parts of every girl: chest, butt, tails and ponytails swing and bounce with her motion.
 * <p>
 * Each part is a damped spring. Its drive is the acceleration of the part's pivot in the world: everything the animation
 * does to it (walking bob, a scene's motion, turning) plus the girl's own movement. The spring's lag behind that pivot is
 * turned into a rotation of the bone around its pivot, using where the bone's mass sits (the centre of its cubes), so the
 * part swings the right way whatever its orientation in the pose.
 * <p>
 * The pivot is sampled after each girl is drawn ({@link #capture}); GeckoLib shares the bone objects between all girls of a
 * model, so everything a girl needs is copied into her own state there. The springs' lag is applied to the next frame's
 * pose ({@link #apply}), after the animation set it.
 */
final class JigglePhysics {
    /** One kind of soft part: spring frequency (Hz), damping ratio, how much it swings, and its largest swing (degrees). */
    private record Part(float frequency, float damping, float gain, float maxDegrees) {
    }

    private static final Part CHEST = new Part(3.0F, 0.12F, 3.2F, 24.0F);
    private static final Part BREAST = new Part(4.2F, 0.16F, 1.6F, 15.0F); // each side on top of the chest: a little out of step
    private static final Part BUTT = new Part(3.8F, 0.15F, 2.4F, 15.0F);
    private static final Part TAIL = new Part(2.0F, 0.28F, 1.4F, 12.0F); // per link: the links of a tail add up
    private static final Part PONYTAIL = new Part(2.2F, 0.22F, 2.0F, 28.0F);

    private static final Map<String, Part> PARTS = new HashMap<>();

    static {
        PARTS.put("boobs", CHEST);
        PARTS.put("boobL", BREAST);
        PARTS.put("boobR", BREAST);
        PARTS.put("cheekL", BUTT);
        PARTS.put("cheekR", BUTT);
        for (String tail : new String[] {"tail", "tail0", "tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail7"}) {
            PARTS.put(tail, TAIL);
        }
        PARTS.put("ponyTail", PONYTAIL);
        PARTS.put("ponyTailL", PONYTAIL);
        PARTS.put("ponyTailR", PONYTAIL);
    }

    /** Integration step (seconds); a frame is split into steps no longer than this. */
    private static final double STEP = 1.0D / 240.0D;
    /** Drive accelerations above this (blocks / s^2) are clipped: teleports, model swaps and hitches are not motion. */
    private static final double MAX_DRIVE = 120.0D;
    /** A pivot that moved more than this (blocks) since the last frame jumped; its spring starts again at rest. */
    private static final double JUMP = 2.0D;
    /** Shortest lever (blocks) used to turn a lag into an angle, so a bone whose mass sits on its pivot does not spin. */
    private static final float MIN_LEVER = 1.5F / 16.0F;
    /** Lags beyond this (blocks) are clipped. */
    private static final double MAX_LAG = 0.5D;

    private static final class Spring {
        final Vector3d lag = new Vector3d();
        final Vector3d speed = new Vector3d();
        final Vector3d anchor = new Vector3d();
        final Vector3d anchorSpeed = new Vector3d();
        /** World direction to the bone's rest frame as of the last capture. */
        final Matrix3f toBone = new Matrix3f();
        boolean primed;
        boolean placed;

        void rest() {
            this.lag.zero();
            this.speed.zero();
            this.anchorSpeed.zero();
            this.primed = false;
        }
    }

    private static final class State {
        final Map<String, Spring> springs = new HashMap<>();
        double time = Double.NaN;
        long seen;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    /** The bone's mass centre relative to its pivot, in its rest frame (blocks); bones are per model, so this is too. */
    private static final Map<GeoBone, Vector3f> LEVERS = new WeakHashMap<>();
    private static long captures;

    private JigglePhysics() {
    }

    /** Marks the soft bones for matrix tracking; called while the pose is built, before they are drawn. */
    static void track(AnimationProcessor<?> processor) {
        for (String name : PARTS.keySet()) {
            GeoBone bone = processor.getBone(name);
            if (bone != null) {
                bone.setTrackingMatrices(true);
            }
        }
    }

    /** Adds each spring's lag to the pose the animation just set. */
    static void apply(CompanionEntity entity, AnimationProcessor<?> processor) {
        float strength = ClientConfig.jiggleStrength();
        State state = STATES.get(entity.getId());
        if (strength <= 0.0F || state == null || Minecraft.getInstance().isPaused()) {
            return;
        }
        Vector3f lag = new Vector3f();
        Vector3f turn = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Vector3f euler = new Vector3f();
        for (Map.Entry<String, Spring> entry : state.springs.entrySet()) {
            Spring spring = entry.getValue();
            GeoBone bone = processor.getBone(entry.getKey());
            if (bone == null || bone.isHidden() || !spring.placed) {
                continue;
            }
            Part part = PARTS.get(entry.getKey());
            Vector3f lever = lever(bone);
            float reach = Math.max(lever.length(), MIN_LEVER);
            // the lag in the bone's rest frame; the rotation that moves its mass centre by it is lever x lag / |lever|^2
            spring.toBone.transform(lag.set((float) spring.lag.x, (float) spring.lag.y, (float) spring.lag.z));
            lever.cross(lag, turn).div(reach * reach).mul(part.gain() * strength);
            float max = (float) Math.toRadians(part.maxDegrees());
            float angle = turn.length();
            if (angle < 1.0E-5F) {
                continue;
            }
            if (angle > max) {
                turn.mul(max / angle);
                angle = max;
            }
            // GeckoLib turns a bone by Z, then Y, then X; the swing is about an axis of the bone's own (rest) frame
            rotation.rotationZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX())
                    .rotateAxis(angle, turn.x / angle, turn.y / angle, turn.z / angle);
            rotation.getEulerAnglesZYX(euler);
            bone.setRotX(euler.x);
            bone.setRotY(euler.y);
            bone.setRotZ(euler.z);
        }
    }

    /** Samples the soft bones' pivots after the girl was drawn and steps their springs to now. */
    static void capture(CompanionEntity entity, AnimationProcessor<?> processor, float partialTick) {
        if (entity.level() == null) {
            return;
        }
        long now = System.nanoTime();
        if (++captures % 600 == 0) {
            STATES.values().removeIf(old -> now - old.seen > 10_000_000_000L);
        }
        State state = STATES.computeIfAbsent(entity.getId(), id -> new State());
        state.seen = now;
        double time = (entity.level().getGameTime() + partialTick) / 20.0D;
        double dt = time - state.time;
        state.time = time;
        boolean restart = Double.isNaN(dt) || dt > 0.25D || dt < 0.0D || Minecraft.getInstance().isPaused();
        if (!restart && dt < 1.0E-4D) {
            return; // drawn again this frame (an inventory preview): the springs already stepped
        }
        Vec3 at = entity.getPosition(partialTick);
        Vector3d anchor = new Vector3d();
        Vector3d drive = new Vector3d();
        Vector3d move = new Vector3d();
        for (Map.Entry<String, Part> entry : PARTS.entrySet()) {
            GeoBone bone = processor.getBone(entry.getKey());
            if (bone == null || !bone.isTrackingMatrices()) {
                continue;
            }
            Spring spring = state.springs.computeIfAbsent(entry.getKey(), name -> new Spring());
            Matrix3f frame = new Matrix3f();
            bone.getLocalSpaceMatrix().get3x3(frame);
            if (Math.abs(frame.determinant()) < 1.0E-9F) {
                continue; // not drawn yet
            }
            frame.invert(spring.toBone);
            spring.placed = true;
            Vector3d local = bone.getLocalPosition();
            anchor.set(local.x + at.x, local.y + at.y, local.z + at.z);
            if (restart || bone.isHidden() || !spring.primed || anchor.distance(spring.anchor) > JUMP) {
                spring.rest();
                spring.anchor.set(anchor);
                spring.primed = true;
                continue;
            }
            anchor.sub(spring.anchor, move).div(dt);  // pivot velocity
            move.sub(spring.anchorSpeed, drive).div(dt); // pivot acceleration
            if (drive.length() > MAX_DRIVE) {
                drive.normalize(MAX_DRIVE);
            }
            spring.anchor.set(anchor);
            spring.anchorSpeed.set(move);
            step(spring, entry.getValue(), drive, dt);
        }
    }

    /** lag'' = -w^2 lag - 2 z w lag' - pivot'' (the part trails its pivot), integrated semi-implicitly. */
    private static void step(Spring spring, Part part, Vector3d drive, double dt) {
        double omega = 2.0D * Math.PI * part.frequency();
        double stiffness = omega * omega;
        double friction = 2.0D * part.damping() * omega;
        int steps = (int) Math.ceil(dt / STEP);
        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            spring.speed.x += (-stiffness * spring.lag.x - friction * spring.speed.x - drive.x) * h;
            spring.speed.y += (-stiffness * spring.lag.y - friction * spring.speed.y - drive.y) * h;
            spring.speed.z += (-stiffness * spring.lag.z - friction * spring.speed.z - drive.z) * h;
            spring.lag.fma(h, spring.speed);
        }
        if (spring.lag.length() > MAX_LAG) {
            spring.lag.normalize(MAX_LAG);
            spring.speed.mul(0.5D);
        }
    }

    /** Centre of the bone's cubes and its children's, relative to its pivot (blocks, rest frame). */
    private static Vector3f lever(GeoBone bone) {
        return LEVERS.computeIfAbsent(bone, b -> {
            Vector3f sum = new Vector3f();
            int[] count = {0};
            addVertices(b, sum, count);
            if (count[0] == 0) {
                return new Vector3f();
            }
            return sum.div(count[0]).sub(b.getPivotX() / 16.0F, b.getPivotY() / 16.0F, b.getPivotZ() / 16.0F);
        });
    }

    private static void addVertices(GeoBone bone, Vector3f sum, int[] count) {
        for (GeoCube cube : bone.getCubes()) {
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) {
                    continue;
                }
                for (GeoVertex vertex : quad.vertices()) {
                    sum.add(vertex.position());
                    count[0]++;
                }
            }
        }
        for (GeoBone child : bone.getChildBones()) {
            addVertices(child, sum, count);
        }
    }
}
