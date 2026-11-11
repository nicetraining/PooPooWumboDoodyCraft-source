package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.ClientConfig;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.animation.keyframe.KeyframeStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.math.MathValue;

/**
 * Jiggle physics for every girl, built like the body-physics mods of other games (CBP / CBPC, PhysBones).
 * <p>
 * <b>Soft parts</b> (chest, butt) are not swung as one rigid bone on a hinge. Their mesh is bent like skinned flesh: each
 * vertex moves by an amount that grows from nothing where the part meets the body to the full amount at its tip, so the
 * part stretches, sways and wobbles. Each breast has its own springs (also on models whose chest is a single bone: the two
 * halves are told apart by side), and so has each butt cheek. Every side has two springs in a row: the main mass, and a
 * faster, lighter tip driven by the main mass for follow-through. The springs are linear plus quadratic (they firm up the
 * further they are pulled, instead of hitting a hard stop) and damped.
 * <p>
 * <b>Bone chains</b> (tails, ponytails) keep a spring per link that turns the link around its joint; the links add up, so a
 * tail curls and whips.
 * <p>
 * Every spring is driven by the acceleration of the part's mass centre in the world: what the animation does to it
 * (walking bob, a scene's motion, turning in place) plus the girl's own movement. It is sampled after each girl is drawn
 * ({@link #capture}); GeckoLib shares bone objects between all girls of a model, so everything a girl needs is copied into
 * her own state there. The result is used for her next frame ({@link #apply} for the chains, {@link #enter} and
 * {@link #displace} while her mesh is written).
 */
final class JigglePhysics {
    /** A spring: frequency (Hz) and damping ratio of the main mass and of the tip, how far the motion carries (gain) and the
     * lag (rest-frame blocks) at which its springs are twice as stiff as at rest. */
    private record Soft(float frequency, float damping, float tipFrequency, float tipDamping, float gain, float tipGain,
                        float firmAt, boolean twoSided) {
    }

    // tuned on a walking Jenny: enough damping that a stride's bounce settles instead of flailing, a light tip
    private static final Soft CHEST = new Soft(2.2F, 0.18F, 4.0F, 0.25F, 1.9F, 0.5F, 0.05F, true);
    private static final Soft BUTT = new Soft(2.7F, 0.20F, 4.6F, 0.28F, 1.5F, 0.4F, 0.04F, false);
    private static final Map<String, Soft> SOFT = Map.of("boobs", CHEST, "cheekL", BUTT, "cheekR", BUTT);

    /** A chain link: spring frequency (Hz), damping ratio, how much it swings, and its largest swing (degrees). */
    private record Link(float frequency, float damping, float gain, float maxDegrees) {
    }

    private static final Link TAIL = new Link(2.0F, 0.28F, 1.4F, 12.0F); // per link: the links of a tail add up
    private static final Link PONYTAIL = new Link(2.2F, 0.22F, 2.0F, 28.0F);
    private static final Map<String, Link> LINKS = new HashMap<>();

    static {
        for (String tail : new String[] {"tail", "tail0", "tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail7"}) {
            LINKS.put(tail, TAIL);
        }
        LINKS.put("ponyTail", PONYTAIL);
        LINKS.put("ponyTailL", PONYTAIL);
        LINKS.put("ponyTailR", PONYTAIL);
    }

    /** Integration step (seconds); a frame is split into steps no longer than this. */
    private static final double STEP = 1.0D / 240.0D;
    /**
     * Drive accelerations are eased towards this (blocks / s^2) instead of growing past it: a scene's hardest thrusts would
     * otherwise pin the parts at their stretch limit, where they stop moving like flesh and start twitching against it.
     */
    private static final double MAX_DRIVE = 40.0D;
    /**
     * Automatic gain: over about {@link #LEVEL_TIME} seconds the drive is measured, and when its typical size is above
     * this (blocks / s^2) the drive is turned down to it. Scenes range from gentle rocking to the whole body thrown about
     * by a block or more; springs tuned for the one swing from end to end on the other. Slow enough not to flatten a
     * single bounce, so the jiggle keeps its shape and only its size is evened out.
     */
    private static final double LEVEL = 12.0D;
    private static final double LEVEL_TIME = 1.0D;
    /**
     * The drive is a second difference of positions sampled once a frame, which turns the smallest unevenness between
     * frames into a kick that flips sign every frame; the springs then flip with it (a twitch). Above this frequency (Hz)
     * the drive is filtered out: flesh does not follow motion that fast anyway.
     */
    private static final double DRIVE_CUTOFF = 4.0D;
    /**
     * What is drawn follows the springs through this (Hz): fast enough to keep a bounce's shape, slow enough that no part
     * reverses within a frame however hard the animation turns or throws the body under it.
     */
    private static final double SHOWN_CUTOFF = 7.0D;
    /** A sampled point that moved more than this (blocks) since the last frame jumped; its springs start again at rest. */
    private static final double JUMP = 2.0D;
    /**
     * Within the model, a point moving faster than this (blocks / s) was snapped, not moved: the girls' animations start
     * without blending (a scene's next phase, a loop starting over, an idle swapped in), so a part can jump to a new pose
     * in one frame. Such a frame drives nothing; the springs carry on from where they were.
     */
    private static final double SNAP_SPEED = 8.0D;
    /** Shortest lever (blocks) used to turn a chain link's lag into an angle. */
    private static final float MIN_LEVER = 1.5F / 16.0F;
    /** Lags beyond this (world blocks) are clipped. */
    private static final double MAX_LAG = 0.4D;
    /** Width (rest blocks) over which the two sides of a one-bone chest blend into each other at the middle. */
    private static final float SIDE_BLEND = 0.9F / 16.0F;
    /** Largest displacement of a soft part's tip, as a share of its length: keeps a hard landing from tearing it apart. */
    private static final float MAX_STRETCH = 0.28F;
    /**
     * Along its own length a soft part mostly keeps its shape: of a lag in that direction only this share is used, and it
     * may stretch out by at most {@link #MAX_OUT} and be pushed in by at most {@link #MAX_IN} of its length (flesh does not
     * fold back into the body; letting it do so read as the part collapsing inwards and popping back out).
     */
    private static final float ALONG = 0.3F;
    private static final float MAX_OUT = 0.08F;
    private static final float MAX_IN = 0.03F;
    /** How much each side's spring is detuned from the other's, so the two do not move in lockstep. */
    private static final float DETUNE = 1.07F;

    /** A damped spring trailing a moving point: its lag behind the point (world blocks) and the lag's velocity. */
    private static final class Spring {
        final Vector3d lag = new Vector3d();
        final Vector3d speed = new Vector3d();
        final Vector3d accel = new Vector3d();

        void rest() {
            this.lag.zero();
            this.speed.zero();
            this.accel.zero();
        }

        /** lag'' = -k lag - k / firmAt |lag| lag - c lag' - drive, semi-implicit; leaves lag'' in accel. */
        void step(double frequency, double damping, double firmAt, Vector3d drive, double h) {
            double omega = 2.0D * Math.PI * frequency;
            double stiffness = omega * omega;
            double firm = firmAt > 0.0D ? stiffness / firmAt * this.lag.length() : 0.0D;
            double friction = 2.0D * damping * omega;
            this.accel.set(this.lag).mul(-(stiffness + firm)).fma(-friction, this.speed).sub(drive);
            this.speed.fma(h, this.accel);
            this.lag.fma(h, this.speed);
        }
    }

    /** Where a point is sampled and how fast it moved, to turn its motion into a drive. */
    private static final class Anchor {
        final Vector3d at = new Vector3d();
        final Vector3d local = new Vector3d();
        final Vector3d velocity = new Vector3d();
        /** Running mean of the squared drive, for the automatic gain. */
        double level;
        /** The filtered drive. */
        final Vector3d smooth = new Vector3d();
        boolean primed;

        /**
         * The point's acceleration since the last sample, or null when it is (re)starting. {@code inModel} is the same point
         * relative to the girl, to tell a snapped pose from motion.
         */
        Vector3d sample(Vector3d now, Vector3d inModel, double dt, boolean restart, Vector3d out) {
            if (restart || !this.primed || now.distance(this.at) > JUMP) {
                this.at.set(now);
                this.local.set(inModel);
                this.velocity.zero();
                this.level = 0.0D;
                this.smooth.zero();
                this.primed = true;
                return null;
            }
            boolean snapped = inModel.distance(this.local) > SNAP_SPEED * dt;
            this.local.set(inModel);
            if (snapped) {
                // a new pose, not motion: measure again from it, without a kick (the girl's own motion carries on)
                this.at.set(now);
                this.smooth.mul(Math.exp(-2.0D * Math.PI * DRIVE_CUTOFF * dt));
                return out.set(this.smooth);
            }
            Vector3d velocity = new Vector3d(now).sub(this.at).div(dt);
            out.set(velocity).sub(this.velocity).div(dt);
            double size = out.length();
            this.level += (size * size - this.level) * Math.min(1.0D, dt / LEVEL_TIME);
            double typical = Math.sqrt(this.level);
            if (typical > LEVEL) {
                out.mul(LEVEL / typical);
                size *= LEVEL / typical;
            }
            if (size > 1.0E-9D) {
                out.mul(MAX_DRIVE * Math.tanh(size / MAX_DRIVE) / size);
            }
            this.smooth.lerp(out, 1.0D - Math.exp(-2.0D * Math.PI * DRIVE_CUTOFF * dt));
            out.set(this.smooth);
            this.at.set(now);
            this.velocity.set(velocity);
            return out;
        }
    }

    /** A soft part of one girl: per side a main and a tip spring, and their displacement in the part's rest frame. */
    private static final class SoftState {
        final Anchor anchor = new Anchor();
        final Spring[] main = {new Spring(), new Spring()};
        final Spring[] tip = {new Spring(), new Spring()};
        /** World direction to the part's rest frame as of the last capture. */
        final Matrix3f toRest = new Matrix3f();
        /** Displacement of the main mass and of the tip, per side (0 = +x, 1 = -x), rest-frame blocks. */
        final Vector3f[] mainShift = {new Vector3f(), new Vector3f()};
        final Vector3f[] tipShift = {new Vector3f(), new Vector3f()};
        /** How much of the physics is shown: eased to 0 while the animation moves the part itself (see {@link #authored}). */
        float own = 1.0F;
        boolean placed;

        void rest() {
            for (int side = 0; side < 2; side++) {
                this.main[side].rest();
                this.tip[side].rest();
                this.mainShift[side].zero();
                this.tipShift[side].zero();
            }
        }
    }

    /** A chain link of one girl. */
    private static final class LinkState {
        final Anchor anchor = new Anchor();
        final Spring spring = new Spring();
        final Matrix3f toBone = new Matrix3f();
        boolean placed;
    }

    private static final class State {
        final Map<String, SoftState> soft = new HashMap<>();
        final Map<String, LinkState> links = new HashMap<>();
        double time = Double.NaN;
        long seen;
        /** How much of the jiggle is shown: 1 in a scene, the outside-scene share otherwise, eased between the two. */
        float context = Float.NaN;
    }

    /** How fast the shown share follows a scene starting or ending (per second): about a third of a second. */
    private static final float CONTEXT_RATE = 6.0F;

    /**
     * Like the games it is modelled on, the jiggle is tuned to the moment: scenes are hand-animated, mostly unclothed, and
     * where the motion is expected; walking around dressed, a supported chest barely moves, and everyday bounce tuned to
     * be visible looks wrong (the classic over-tuning). Outside scenes only the configured share is shown, none by default.
     */
    private static float strength(State state) {
        float base = ClientConfig.jiggleStrength();
        return state == null || Float.isNaN(state.context) ? 0.0F : base * state.context;
    }

    /** Per animation, the soft parts it moves itself (keyframes that change, or Molang). */
    private static final Map<Animation, Set<String>> AUTHORED = new WeakHashMap<>();

    /** The soft parts one of the girl's playing animations moves itself. */
    private static Set<String> authored(CompanionEntity entity) {
        Set<String> parts = new HashSet<>();
        try {
            for (AnimationController<?> controller : entity.getAnimatableInstanceCache().getManagerForId(entity.getId())
                    .getAnimationControllers().values()) {
                AnimationProcessor.QueuedAnimation playing = controller.getCurrentAnimation();
                if (playing != null && playing.animation() != null) {
                    parts.addAll(AUTHORED.computeIfAbsent(playing.animation(), JigglePhysics::moves));
                }
            }
        } catch (RuntimeException ignored) {
            // no manager yet: nothing is authored
        }
        return parts;
    }

    private static Set<String> moves(Animation animation) {
        Set<String> parts = new HashSet<>();
        for (BoneAnimation bone : animation.boneAnimations()) {
            if (SOFT.containsKey(bone.boneName()) && (changes(bone.rotationKeyFrames()) || changes(bone.positionKeyFrames())
                    || changes(bone.scaleKeyFrames()))) {
                parts.add(bone.boneName());
            }
        }
        return parts;
    }

    private static boolean changes(KeyframeStack<Keyframe<MathValue>> stack) {
        for (List<Keyframe<MathValue>> axis : List.of(stack.xKeyframes(), stack.yKeyframes(), stack.zKeyframes())) {
            Double first = null;
            for (Keyframe<MathValue> frame : axis) {
                for (MathValue value : List.of(frame.startValue(), frame.endValue())) {
                    if (value.isMutable()) {
                        return true;
                    }
                    double v = value.get();
                    if (first == null) {
                        first = v;
                    } else if (Math.abs(v - first) > 1.0E-4D) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The rest shape of a soft part (per model, since bones are): where it is attached, its length axis and length. */
    private record Shape(Vector3f pivot, Vector3f centre, Vector3f axis, float length, boolean twoSided) {
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<GeoBone, Shape> SHAPES = new WeakHashMap<>();
    private static final Map<GeoBone, Vector3f> LEVERS = new WeakHashMap<>();
    private static long captures;

    /** The soft part whose mesh is being written right now (render thread only), or null. */
    private static Active active;

    private static final class Active {
        final SoftState state;
        final Shape shape;
        final float strength;
        final GeoBone root;
        /** The root bone's posed matrix (rest model space to the render space) and its inverse, once it is drawn. */
        final Matrix4f pose = new Matrix4f();
        final Matrix4f unpose = new Matrix4f();
        boolean posed;

        Active(SoftState state, Shape shape, float strength, GeoBone root) {
            this.state = state;
            this.shape = shape;
            this.strength = strength;
            this.root = root;
        }
    }

    private JigglePhysics() {
    }

    // ---------------------------------------------------------------- pose (chains) and mesh (soft parts)

    /** Marks the sampled bones for matrix tracking; called while the pose is built, before they are drawn. */
    static void track(AnimationProcessor<?> processor) {
        for (String name : SOFT.keySet()) {
            mark(processor.getBone(name));
        }
        for (String name : LINKS.keySet()) {
            mark(processor.getBone(name));
        }
    }

    private static void mark(GeoBone bone) {
        if (bone != null) {
            bone.setTrackingMatrices(true);
        }
    }

    /** Turns each chain link by its spring's lag, on top of the pose the animation just set. */
    static void apply(CompanionEntity entity, AnimationProcessor<?> processor) {
        State state = STATES.get(entity.getId());
        float strength = strength(state);
        if (strength <= 0.0F || Minecraft.getInstance().isPaused()) {
            return;
        }
        Vector3f lag = new Vector3f();
        Vector3f turn = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Vector3f euler = new Vector3f();
        for (Map.Entry<String, LinkState> entry : state.links.entrySet()) {
            LinkState link = entry.getValue();
            GeoBone bone = processor.getBone(entry.getKey());
            if (bone == null || bone.isHidden() || !link.placed) {
                continue;
            }
            Link part = LINKS.get(entry.getKey());
            Vector3f lever = lever(bone);
            float reach = Math.max(lever.length(), MIN_LEVER);
            // the lag in the bone's rest frame; the rotation that moves its mass centre by it is lever x lag / |lever|^2
            Vector3d world = link.spring.lag;
            link.toBone.transform(lag.set((float) world.x, (float) world.y, (float) world.z));
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

    /**
     * Called as the renderer enters a bone: when it is a soft part of this girl, its vertices (and its children's) are
     * displaced until {@link #leave}. Returns what to restore.
     */
    static Object enter(CompanionEntity entity, GeoBone bone) {
        Active before = active;
        if (!SOFT.containsKey(bone.getName())) {
            return before;
        }
        State state = STATES.get(entity.getId());
        float strength = strength(state);
        SoftState soft = state == null ? null : state.soft.get(bone.getName());
        float shown = soft == null ? 0.0F : strength * soft.own;
        active = shown > 0.001F && soft.placed && !Minecraft.getInstance().isPaused()
                ? new Active(soft, shape(bone, SOFT.get(bone.getName()).twoSided()), shown, bone) : null;
        return before;
    }

    static void leave(Object before) {
        active = (Active) before;
    }

    static boolean displacing() {
        return active != null && active.posed;
    }

    /** Called as a bone's cubes are drawn: the soft part's own bone gives the frame every displacement is made in. */
    static void posed(GeoBone bone, Matrix4f pose) {
        Active part = active;
        if (part != null && part.root == bone) {
            part.pose.set(pose);
            part.pose.invert(part.unpose);
            part.posed = true;
        }
    }

    /**
     * Writes where a vertex of the active soft part is drawn: its fully transformed position ({@code poseState}: bones and
     * the cube's own rotation) moved by the springs. The amount is read and the move is made in the soft part's own bone
     * frame, the same for every cube of it: Galath's chest is hundreds of small, individually rotated cubes, and moving
     * each in its own frame tore them apart. Nothing moves at the attachment, the main mass's lag over the whole part, the
     * tip's growing towards the end, each side by its own springs.
     */
    static void displace(Vector3f vertex, Matrix4f poseState, Vector4f out) {
        poseState.transform(out.set(vertex, 1.0F));
        Active part = active;
        if (part == null || !part.posed || part.shape.length() <= 1.0E-4F) {
            return;
        }
        Shape shape = part.shape;
        Vector4f rest = part.unpose.transform(new Vector4f(out));
        float along = (rest.x - shape.pivot().x) * shape.axis().x + (rest.y - shape.pivot().y) * shape.axis().y
                + (rest.z - shape.pivot().z) * shape.axis().z;
        float t = Math.max(0.0F, Math.min(1.0F, along / shape.length()));
        if (t <= 0.0F) {
            return;
        }
        float body = t * t * (3.0F - 2.0F * t); // smooth from the attachment: no crease where the part meets the body
        float end = t * t * t;
        float side = 0.0F;
        if (shape.twoSided()) {
            float s = Math.max(0.0F, Math.min(1.0F, (rest.x - shape.centre().x + SIDE_BLEND) / (2.0F * SIDE_BLEND)));
            side = 1.0F - s * s * (3.0F - 2.0F * s); // 0 = the +x side, 1 = the -x side
        }
        SoftState state = part.state;
        float k = part.strength;
        Vector3f move = new Vector3f(
                k * (body * mix(state.mainShift[0].x, state.mainShift[1].x, side) + end * mix(state.tipShift[0].x, state.tipShift[1].x, side)),
                k * (body * mix(state.mainShift[0].y, state.mainShift[1].y, side) + end * mix(state.tipShift[0].y, state.tipShift[1].y, side)),
                k * (body * mix(state.mainShift[0].z, state.mainShift[1].z, side) + end * mix(state.tipShift[0].z, state.tipShift[1].z, side)));
        part.pose.transformDirection(move);
        out.x += move.x;
        out.y += move.y;
        out.z += move.z;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ---------------------------------------------------------------- sampling and stepping

    /** Samples the parts after the girl was drawn and steps their springs to now. */
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
        float target = entity.isInScene() ? 1.0F : ClientConfig.jiggleOutsideScenes();
        state.context = restart || Float.isNaN(state.context) ? target
                : state.context + (target - state.context) * Math.min(1.0F, (float) dt * CONTEXT_RATE);
        Set<String> authored = authored(entity);
        Vec3 at = entity.getPosition(partialTick);
        Vector3d point = new Vector3d();
        Vector3d inModel = new Vector3d();
        Vector3d drive = new Vector3d();
        Matrix3f frame = new Matrix3f();
        Vector4f centre = new Vector4f();

        for (Map.Entry<String, Soft> entry : SOFT.entrySet()) {
            GeoBone bone = processor.getBone(entry.getKey());
            if (bone == null || !bone.isTrackingMatrices()) {
                continue;
            }
            SoftState soft = state.soft.computeIfAbsent(entry.getKey(), name -> new SoftState());
            Matrix4f matrix = bone.getLocalSpaceMatrix();
            matrix.get3x3(frame);
            if (Math.abs(frame.determinant()) < 1.0E-9F) {
                continue; // not drawn yet
            }
            frame.invert(soft.toRest);
            soft.placed = true;
            Shape shape = shape(bone, entry.getValue().twoSided());
            // the mass centre, not the pivot: turning in place swings it too
            matrix.transform(centre.set(shape.centre(), 1.0F));
            inModel.set(centre.x, centre.y, centre.z);
            point.set(centre.x + at.x, centre.y + at.y, centre.z + at.z);
            Vector3d acceleration = soft.anchor.sample(point, inModel, dt, restart || bone.isHidden(), drive);
            if (acceleration == null) {
                soft.rest();
                continue;
            }
            // the animation's own jiggle is the artist's: the physics hands the part over to it instead of adding to it
            boolean keyed = authored.contains(entry.getKey());
            soft.own += ((keyed ? 0.0F : 1.0F) - soft.own) * Math.min(1.0F, (float) dt * CONTEXT_RATE);
            if (keyed) {
                acceleration.zero(); // let the springs settle, so they come back calm when the animation lets go
            }
            stepSoft(soft, entry.getValue(), shape, acceleration, dt);
        }

        for (Map.Entry<String, Link> entry : LINKS.entrySet()) {
            GeoBone bone = processor.getBone(entry.getKey());
            if (bone == null || !bone.isTrackingMatrices()) {
                continue;
            }
            LinkState link = state.links.computeIfAbsent(entry.getKey(), name -> new LinkState());
            bone.getLocalSpaceMatrix().get3x3(frame);
            if (Math.abs(frame.determinant()) < 1.0E-9F) {
                continue;
            }
            frame.invert(link.toBone);
            link.placed = true;
            Vector3d local = bone.getLocalPosition();
            point.set(local.x + at.x, local.y + at.y, local.z + at.z);
            Vector3d acceleration = link.anchor.sample(point, local, dt, restart || bone.isHidden(), drive);
            if (acceleration == null) {
                link.spring.rest();
                continue;
            }
            Link part = entry.getValue();
            int steps = (int) Math.ceil(dt / STEP);
            double h = dt / steps;
            for (int i = 0; i < steps; i++) {
                link.spring.step(part.frequency(), part.damping(), 0.0D, acceleration, h);
            }
            clip(link.spring);
        }
    }

    private static void stepSoft(SoftState soft, Soft part, Shape shape, Vector3d drive, double dt) {
        int steps = (int) Math.ceil(dt / STEP);
        double h = dt / steps;
        Vector3d tipDrive = new Vector3d();
        for (int side = 0; side < (part.twoSided() ? 2 : 1); side++) {
            double detune = side == 0 ? 1.0D : DETUNE;
            Spring main = soft.main[side];
            Spring tip = soft.tip[side];
            for (int i = 0; i < steps; i++) {
                main.step(part.frequency() * detune, part.damping(), part.firmAt(), drive, h);
                // the tip hangs on the main mass: it feels the body's motion and the main mass's own swing
                tipDrive.set(drive).add(main.accel);
                tip.step(part.tipFrequency() * detune, part.tipDamping(), part.firmAt(), tipDrive, h);
            }
            clip(main);
            clip(tip);
        }
        if (!part.twoSided()) {
            soft.main[1].lag.set(soft.main[0].lag);
            soft.tip[1].lag.set(soft.tip[0].lag);
        }
        float limit = shape.length() * MAX_STRETCH;
        float follow = (float) (1.0D - Math.exp(-2.0D * Math.PI * SHOWN_CUTOFF * dt));
        Vector3f before = new Vector3f();
        Vector3f tipBefore = new Vector3f();
        for (int side = 0; side < 2; side++) {
            before.set(soft.mainShift[side]);
            tipBefore.set(soft.tipShift[side]);
            toRest(soft, soft.main[side].lag, part.gain(), soft.mainShift[side]);
            toRest(soft, soft.tip[side].lag, part.tipGain(), soft.tipShift[side]);
            keepLength(soft.mainShift[side], shape);
            keepLength(soft.tipShift[side], shape);
            // eased into the limit, never stopped against it: a part that hits a wall twitches
            float total = soft.mainShift[side].length() + soft.tipShift[side].length();
            if (total > 1.0E-6F) {
                float eased = limit * (float) Math.tanh(total / limit) / total;
                soft.mainShift[side].mul(eased);
                soft.tipShift[side].mul(eased);
            }
            soft.mainShift[side].set(before.lerp(soft.mainShift[side], follow));
            soft.tipShift[side].set(tipBefore.lerp(soft.tipShift[side], follow));
        }
    }

    /** Takes most of a displacement's component along the part's length away, and caps what is left, more so inwards. */
    private static void keepLength(Vector3f shift, Shape shape) {
        float along = shift.dot(shape.axis());
        float kept = Math.max(-MAX_IN * shape.length(), Math.min(MAX_OUT * shape.length(), along * ALONG));
        shift.fma(kept - along, shape.axis());
    }

    private static void toRest(SoftState soft, Vector3d lag, float gain, Vector3f out) {
        soft.toRest.transform(out.set((float) lag.x, (float) lag.y, (float) lag.z)).mul(gain);
    }

    private static void clip(Spring spring) {
        if (spring.lag.length() > MAX_LAG) {
            spring.lag.normalize(MAX_LAG);
            spring.speed.mul(0.5D);
        }
    }

    // ---------------------------------------------------------------- rest shapes

    /** Attachment, mass centre, length axis and length of a soft part, from its cubes and its children's. */
    private static Shape shape(GeoBone bone, boolean twoSided) {
        return SHAPES.computeIfAbsent(bone, b -> {
            java.util.List<Vector3f> points = new java.util.ArrayList<>();
            addVertices(b, points);
            Vector3f pivot = new Vector3f(b.getPivotX() / 16.0F, b.getPivotY() / 16.0F, b.getPivotZ() / 16.0F);
            if (points.isEmpty()) {
                return new Shape(pivot, pivot, new Vector3f(0.0F, 0.0F, 1.0F), 0.0F, twoSided);
            }
            Vector3f centre = new Vector3f();
            points.forEach(centre::add);
            centre.div(points.size());
            Vector3f axis = new Vector3f(centre).sub(pivot);
            if (axis.lengthSquared() < 1.0E-8F) {
                axis.set(0.0F, 0.0F, 1.0F);
            }
            axis.normalize();
            // the attachment: the points nearest the body along the axis; the length: from there to the farthest point
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (Vector3f p : points) {
                float along = new Vector3f(p).sub(pivot).dot(axis);
                min = Math.min(min, along);
                max = Math.max(max, along);
            }
            Vector3f base = new Vector3f(axis).mul(min).add(pivot);
            return new Shape(base, centre, axis, max - min, twoSided);
        });
    }

    private static void addVertices(GeoBone bone, java.util.List<Vector3f> out) {
        for (GeoCube cube : bone.getCubes()) {
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) {
                    continue;
                }
                for (GeoVertex vertex : quad.vertices()) {
                    out.add(new Vector3f(vertex.position()));
                }
            }
        }
        for (GeoBone child : bone.getChildBones()) {
            addVertices(child, out);
        }
    }

    /** Centre of a chain link's cubes and its children's, relative to its pivot (blocks, rest frame). */
    private static Vector3f lever(GeoBone bone) {
        return LEVERS.computeIfAbsent(bone, b -> {
            java.util.List<Vector3f> points = new java.util.ArrayList<>();
            addVertices(b, points);
            if (points.isEmpty()) {
                return new Vector3f();
            }
            Vector3f sum = new Vector3f();
            points.forEach(sum::add);
            return sum.div(points.size()).sub(b.getPivotX() / 16.0F, b.getPivotY() / 16.0F, b.getPivotZ() / 16.0F);
        });
    }
}
