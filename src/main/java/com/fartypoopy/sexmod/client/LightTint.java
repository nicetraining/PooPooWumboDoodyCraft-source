package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Original gx / c3 / cj.a(EntityLivingBase, float): every quad of a girl (except a few excluded bones and armour) is tinted
 * up to 10 % towards pink where it faces the brightest neighbouring block, and towards dark teal where it faces away. The
 * light direction is worked out in the model's own space, like the original, so GUI previews shade the same way.
 */
final class LightTint {
    private static final Vec3 TOWARDS = new Vec3(0.95D, 0.65D, 0.85D);
    private static final Vec3 AWAY = new Vec3(0.0D, 0.2D, 0.3D);
    private static final float STRENGTH = 0.1F;
    private static final Set<String> DEFAULT = Set.of("boobs", "booty", "vagina", "fuckhole");
    private static final Set<String> MANGLELIE = Set.of("boobs", "booty", "vagina", "fuckhole", "boobs2", "booty2", "vagina2", "fuckhole2");
    private static final Set<String> GALATH = Set.of("boobs", "booty", "vagina", "fuckhole", "boobs2", "booty2", "vagina2", "fuckhole2",
            "static", "turnable", "slip", "futaBallLR", "futaBallLL", "coin", "pentagram");
    private static final Set<String> BIA = Set.of("boobs", "booty", "vagina", "fuckhole", "leaf7", "leaf8");
    private static final Set<String> SLIME = Set.of("boobs", "booty", "vagina", "fuckhole", "figure");
    private static final Set<String> GOBLIN_FORM = Set.of("boobs", "booty", "vagina", "fuckhole", "preggy", "LegL", "LegR", "cheekR", "cheekL");

    /** Original gx.d: the answer per renderer (here: per kind and form) and bone name, worked out once. */
    private static final Map<String, Boolean> SHADED = new HashMap<>();

    private LightTint() {
    }

    /** Original c3.a() per renderer: dt / d0 (Bia), d1 / d5 (Slime), dg (goblin form), da (Galath), dh (Manglelie). */
    private static Set<String> excluded(CompanionEntity entity) {
        return switch (entity.kind()) {
            case BIA -> BIA;
            case SLIME -> SLIME;
            case GOBLIN -> entity.isPlayerForm() ? GOBLIN_FORM : DEFAULT;
            case GALATH -> GALATH; // da and the form's dx share it
            case MANGLELIE -> MANGLELIE;
            default -> DEFAULT;
        };
    }

    /** Original c3.a(HashSet, GeoBone): neither the bone nor a parent (the root aside) is excluded or armour. */
    static boolean shaded(CompanionEntity entity, GeoBone bone) {
        String key = entity.kind().name() + (entity.isPlayerForm() ? "#form#" : "#") + bone.getName();
        Boolean known = SHADED.get(key);
        if (known != null) {
            return known;
        }
        Set<String> excluded = excluded(entity);
        boolean result = true;
        for (GeoBone at = bone; at.getParent() != null; at = at.getParent()) {
            if (excluded.contains(at.getName()) || at.getName().startsWith("armor")) {
                result = false;
                break;
            }
        }
        SHADED.put(key, result);
        return result;
    }

    /**
     * Original cj.a(EntityLivingBase, float): the one neighbour block (3x3x3 around her feet) that is strictly the brightest
     * gives the light direction, turned into her model space by her body yaw; none (a tie) means (0.2, 0.8, 0).
     */
    static Vector3f lightInModelSpace(CompanionEntity entity, float partialTick) {
        var level = entity.level();
        BlockPos feet = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        int best = 0;
        int[] brightness = new int[27];
        for (int dx = -1; dx < 2; dx++) {
            for (int dy = -1; dy < 2; dy++) {
                for (int dz = -1; dz < 2; dz++) {
                    int light = level.getMaxLocalRawBrightness(feet.offset(dx, dy, dz));
                    brightness[(dx + 1) * 9 + (dy + 1) * 3 + dz + 1] = light;
                    best = Math.max(best, light);
                }
            }
        }
        Vec3 direction = null;
        boolean tie = false;
        for (int i = 0; i < 27; i++) {
            if (brightness[i] != best) {
                continue;
            }
            if (direction != null) {
                tie = true;
                break;
            }
            direction = new Vec3(i / 9 - 1, i / 3 % 3 - 1, i % 3 - 1);
        }
        Vec3 model;
        if (tie || direction == null) {
            model = new Vec3(0.2D, 0.8D, 0.0D);
        } else {
            // ck.a(v, yaw): x' = -cos(yaw) x - sin(yaw) z, z' = -sin(yaw) x + cos(yaw) z, with z negated first and yaw = -body yaw
            float yaw = -Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            double rad = Math.toRadians(yaw);
            double x = direction.x;
            double z = -direction.z;
            model = new Vec3(-Math.cos(rad) * x - Math.sin(rad) * z, direction.y, -Math.sin(rad) * x + Math.cos(rad) * z);
        }
        double length = model.length();
        return length < 1.0E-4D ? new Vector3f() : new Vector3f((float) (model.x / length), (float) (model.y / length), (float) (model.z / length));
    }

    /** Original gx.a(Vec3d, Vector3f, Vec3d): colour lerped by easeOutQuart(|n . l|) * 0.1 towards pink (facing) or teal. */
    static int tint(int argb, Vector3f worldNormal, Matrix3f toModel, Vector3f light) {
        Vector3f normal = toModel.transform(new Vector3f(worldNormal));
        float length = normal.length();
        if (length < 1.0E-6F) {
            return argb;
        }
        double dot = (normal.x * light.x + normal.y * light.y + normal.z * light.z) / length;
        double weight = (1.0D - Math.pow(1.0D - Math.min(1.0D, Math.abs(dot)), 4.0D)) * STRENGTH;
        Vec3 target = dot > 0.0D ? TOWARDS : AWAY;
        double r = ((argb >> 16) & 0xFF) / 255.0D;
        double g = ((argb >> 8) & 0xFF) / 255.0D;
        double b = (argb & 0xFF) / 255.0D;
        r += (target.x - r) * weight;
        g += (target.y - g) * weight;
        b += (target.z - b) * weight;
        return (argb & 0xFF000000) | ((int) Math.round(Mth.clamp(r, 0.0D, 1.0D) * 255.0D) << 16)
                | ((int) Math.round(Mth.clamp(g, 0.0D, 1.0D) * 255.0D) << 8) | (int) Math.round(Mth.clamp(b, 0.0D, 1.0D) * 255.0D);
    }
}
