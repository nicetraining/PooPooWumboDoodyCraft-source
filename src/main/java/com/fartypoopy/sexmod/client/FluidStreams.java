package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.CompanionScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Original ga (registry, RenderWorldLast, ClientTick), ep (one stream) and an (one drop): scene keyframes start streams of
 * drops at a bone; each drop falls with gravity and sticks where it meets a block. A stream is drawn as plain white
 * polygons through its drops (a new polygon wherever two neighbours are further apart than the stream's gap).
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class FluidStreams {
    private static final List<Stream> STREAMS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Manglelie girls seen in their threesome: when it is over, her and her mother's streams go (original f8.a(fp, ...)). */
    private static final Set<Integer> IN_THREESOME = new HashSet<>();

    private FluidStreams() {
    }

    /** Original an: position f (and the previous one d) and velocity e. */
    private static final class Drop {
        private final Level level;
        private Vec3 previous;
        private Vec3 position;
        private Vec3 velocity;

        Drop(Level level, Vec3 position, Vec3 velocity) {
            this.level = level;
            this.previous = position;
            this.position = position;
            this.velocity = velocity;
        }

        /** Original an.a(): 9.81 * 0.05 gravity, 0.95 drag, 0.05 s steps; it stops 0.03 in front of the face it hits. */
        void tick() {
            if (Vec3.ZERO.equals(this.velocity)) {
                this.previous = this.position;
                return;
            }
            this.velocity = new Vec3(this.velocity.x * 0.95F, (this.velocity.y - 0.4905000329017639D) * 0.95F, this.velocity.z * 0.95F);
            this.previous = this.position;
            this.position = this.position.add(this.velocity.x * 0.05F, this.velocity.y * 0.05F, this.velocity.z * 0.05F);
            BlockPos air = BlockPos.containing(this.previous);
            BlockPos solid = null;
            for (BlockPos at : line(BlockPos.containing(this.previous), BlockPos.containing(this.position))) {
                if (this.level.getBlockState(at).isAir()) {
                    air = at;
                    continue;
                }
                solid = at;
                break;
            }
            if (solid == null) {
                return;
            }
            Vec3 d = this.previous;
            Vec3 f = this.position;
            if (solid.getX() != air.getX()) {
                double plane = Math.max(solid.getX(), air.getX());
                double y = (d.y - f.y) / (d.x - f.x) * plane + (f.y - (d.y - f.y) / (d.x - f.x) * f.x);
                double z = (d.z - f.z) / (d.x - f.x) * plane + (f.z - (d.z - f.z) / (d.x - f.x) * f.x);
                this.position = new Vec3(plane + 0.03F * (solid.getX() > air.getX() ? -1 : 1), y, z);
            } else if (solid.getY() != air.getY()) {
                double plane = Math.max(solid.getY(), air.getY());
                double x = (d.x - f.x) / (d.y - f.y) * plane + (f.x - (d.x - f.x) / (d.y - f.y) * f.y);
                double z = (d.z - f.z) / (d.y - f.y) * plane + (f.z - (d.z - f.z) / (d.y - f.y) * f.y);
                this.position = new Vec3(x, plane + 0.03F * (solid.getY() > air.getY() ? -1 : 1), z);
            } else if (solid.getZ() != air.getZ()) {
                double plane = Math.max(solid.getZ(), air.getZ());
                double y = (d.y - f.y) / (d.z - f.z) * plane + (f.y - (d.y - f.y) / (d.z - f.z) * f.z);
                double x = (d.x - f.x) / (d.z - f.z) * plane + (f.x - (d.x - f.x) / (d.z - f.z) * f.z);
                this.position = new Vec3(x, y, plane + 0.03F * (solid.getZ() > air.getZ() ? -1 : 1));
            } else {
                return;
            }
            this.velocity = Vec3.ZERO;
        }

        /** Original an.a(BlockPos, BlockPos): the blocks on a 3D Bresenham line, both ends included. */
        private static List<BlockPos> line(BlockPos from, BlockPos to) {
            List<BlockPos> out = new ArrayList<>();
            out.add(from);
            int x = from.getX();
            int y = from.getY();
            int z = from.getZ();
            int dx = Math.abs(to.getX() - x);
            int dy = Math.abs(to.getY() - y);
            int dz = Math.abs(to.getZ() - z);
            int sx = x < to.getX() ? 1 : -1;
            int sy = y < to.getY() ? 1 : -1;
            int sz = z < to.getZ() ? 1 : -1;
            int steps = Math.max(dx, Math.max(dy, dz));
            int ex = steps / 2;
            int ey = steps / 2;
            int ez = steps / 2;
            for (int i = 0; i < steps; i++) {
                out.add(new BlockPos(x, y, z));
                ey -= dy;
                ez -= dz;
                if ((ex -= dx) < 0) {
                    x += sx;
                    ex += steps;
                } else if (ey < 0) {
                    y += sy;
                    ey += steps;
                } else if (ez < 0) {
                    z += sz;
                    ez += steps;
                }
            }
            out.add(to);
            return out;
        }
    }

    /** Original ep: up to max drops, six more per drawn frame; velocity jitter and polygon gap. */
    private record Stream(int max, CompanionEntity girl, UUID girlId, Function<CompanionEntity, Vec3> velocity,
                          Function<CompanionEntity, Vec3> position, float jitter, float gap, List<Drop> drops) {
    }

    private static void add(int max, CompanionEntity girl, Function<CompanionEntity, Vec3> velocity,
                            Function<CompanionEntity, Vec3> position, float jitter, float gap) {
        STREAMS.add(new Stream(max, girl, girl.getUUID(), velocity, position, jitter, gap, new ArrayList<>()));
    }

    /** Original ga.a(em): every stream of this girl goes. */
    static void clear(CompanionEntity girl) {
        STREAMS.removeIf(stream -> stream.girlId().equals(girl.getUUID()));
    }

    /** Original em.d(bone) / em.b(bone) + em.o(): a bone's world position (her anchor when the bone is not drawn). */
    private static Vec3 bone(CompanionEntity girl, String name) {
        Vec3 offset = CompanionRenderer.boneOffset(girl, name);
        return girl.position().add(offset == null ? Vec3.ZERO : offset);
    }

    /** Original lambda: (tip - helper).normalize(), the direction the futa bone points. */
    private static Vec3 direction(CompanionEntity girl, String tip, String helper) {
        return bone(girl, tip).subtract(bone(girl, helper)).normalize();
    }

    /** Original ck.a(new Vec3d(0, 0, 0.6), I()): 0.6 forwards along her scene yaw. */
    private static Vec3 forwards(CompanionEntity girl) {
        double yaw = Math.toRadians(girl.getYRot());
        return new Vec3(-Math.sin(yaw) * 0.6D, 0.0D, Math.cos(yaw) * 0.6D);
    }

    /** Original f_ / er / f8 keyframe handlers: which events start or clear streams. */
    static void keyframe(CompanionEntity girl, String eventId) {
        Function<CompanionEntity, Vec3> futaVelocity = g -> direction(g, "futaCockTip", "futaCockTipDirHelp");
        Function<CompanionEntity, Vec3> futaPosition = g -> bone(g, "futaCockTip");
        Function<CompanionEntity, Vec3> creampiePosition = g -> bone(g, "creampiePos");
        if (girl.kind() == CompanionKind.GALATH && girl.isPlayerForm()) {
            switch (eventId) {
                case "creampie" -> { // original er: the futa stream regardless of the futa setting, then the creampie
                    add(130, girl, futaVelocity, futaPosition, 0.3F, 0.3F);
                    add(100, girl, FluidStreams::forwards, creampiePosition, 0.6F, 0.5F);
                }
                case "clearcum" -> clear(girl);
                default -> {
                }
            }
            return;
        }
        if (girl.kind() == CompanionKind.GALATH) {
            switch (eventId) {
                case "masterbateCumming" -> {
                    if (FutaSetting.shown()) {
                        add(90, girl, futaVelocity, futaPosition, 0.3F, 0.3F);
                    }
                }
                case "creampie" -> {
                    add(100, girl, FluidStreams::forwards, creampiePosition, 0.6F, 0.5F);
                    if (FutaSetting.shown()) {
                        add(130, girl, futaVelocity, futaPosition, 0.3F, 0.3F);
                    }
                }
                case "creampieGalath" -> {
                    if (FutaSetting.shown()) {
                        add(130, girl, futaVelocity, futaPosition, 0.3F, 0.3F);
                    }
                }
                case "clearcum" -> clear(girl);
                default -> {
                }
            }
            return;
        }
        if (girl.kind() == CompanionKind.MANGLELIE && (eventId.equals("doubleSemen0") || eventId.equals("doubleSemen"))) {
            // original f8: her own model has no semenEmitter / semenDir bones, so this starts at her anchor
            add(10, girl, g -> direction(g, "semenEmitter", "semenDir"), g -> bone(g, "semenEmitter"), 0.3F, 0.3F);
        }
    }

    /** Original ga.a(ClientTickEvent) (start phase): every drop moves. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        for (Stream stream : STREAMS) {
            for (Drop drop : stream.drops()) {
                drop.tick();
            }
        }
        // original f8.a(fp, ...): when her THREESOME_CUM is over, her and her mother's streams are cleared
        for (CompanionEntity girl : minecraft.level.getEntitiesOfClass(CompanionEntity.class,
                minecraft.player == null ? net.minecraft.world.phys.AABB.ofSize(Vec3.ZERO, 0, 0, 0)
                        : minecraft.player.getBoundingBox().inflate(128.0D), g -> g.kind() == CompanionKind.MANGLELIE)) {
            boolean threesome = girl.currentScene() == CompanionScene.MANGLELIE_THREESOME;
            if (threesome) {
                IN_THREESOME.add(girl.getId());
            } else if (IN_THREESOME.remove(girl.getId())) {
                clear(girl);
                if (girl.familyId() != null) {
                    for (CompanionEntity mother : minecraft.level.getEntitiesOfClass(CompanionEntity.class, girl.getBoundingBox().inflate(16.0D),
                            g -> g.getUUID().equals(girl.familyId()))) {
                        clear(mother);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STREAMS.clear();
        IN_THREESOME.clear();
    }

    /** Original ga.a(RenderWorldLastEvent) / ep.a(...): lighting off, no culling, plain white polygons. */
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || STREAMS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        for (Stream stream : STREAMS) {
            CompanionEntity girl = stream.girl();
            if (stream.drops().size() < stream.max() && !girl.isRemoved() && girl.level() == minecraft.level) {
                for (int i = 0; i < 6; i++) {
                    Vec3 velocity = stream.velocity().apply(girl);
                    stream.drops().add(new Drop(minecraft.level, stream.position().apply(girl), new Vec3(
                            velocity.x + (RANDOM.nextFloat() * 2.0F - 1.0F) * stream.jitter(),
                            velocity.y + (RANDOM.nextFloat() * 2.0F - 1.0F) * stream.jitter(),
                            velocity.z + (RANDOM.nextFloat() * 2.0F - 1.0F) * stream.jitter())));
                }
            }
            // original ep.b(): an insertion sort that compares each drop with itself first, so it never moves one
            List<Vec3> polygon = new ArrayList<>();
            Vec3 last = null;
            for (Drop drop : stream.drops()) {
                Vec3 at = drop.previous.lerp(drop.position, partial);
                if (last != null && last.distanceTo(at) > stream.gap()) {
                    draw(polygon, pose, camera);
                    polygon.clear();
                }
                polygon.add(at);
                last = at;
            }
            draw(polygon, pose, camera);
        }
        RenderSystem.enableCull();
    }

    /** One GL_POLYGON of the original as a triangle fan; fewer than three drops fill nothing. */
    private static void draw(List<Vec3> polygon, Matrix4f pose, Vec3 camera) {
        if (polygon.size() < 3) {
            return;
        }
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        for (Vec3 at : polygon) {
            buffer.addVertex(pose, (float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z))
                    .setColor(255, 255, 255, 255);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
