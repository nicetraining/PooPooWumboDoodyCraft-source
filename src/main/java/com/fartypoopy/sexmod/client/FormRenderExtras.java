package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Original dm and its per-form subclasses (d0 Bia, d2 Bee, d5 Slime, db Jenny, di Luna, dl Ellie, dv Allie, dx Galath,
 * de Kobold, dg Goblin): what a player form shows of her player - the held items on her weapon / offhand bones (each form
 * with its own grip), bow and shield arm poses while the item is in use, the sneaking lean, an elytra on her back, her
 * hidden head in her own first-person view, the Slime's figure following her body and Allie's sway.
 */
final class FormRenderExtras {
    /** Ops: {0, angle, x, y, z} rotate, {1, x, y, z} translate, {2, x, y, z} scale (the original GlStateManager calls). */
    private record Table(float[][] elytra, float[][][] bow, float[][][] block, float[][][] hold) {
    }

    private static final Map<CompanionKind, Table> TABLES = new EnumMap<>(CompanionKind.class);
    private static final ResourceLocation ELYTRA = ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");
    private static ElytraModel<Player> elytraModel;

    private static float[] r(float angle, float x, float y, float z) {
        return new float[] {0, angle, x, y, z};
    }

    private static float[] t(float x, float y, float z) {
        return new float[] {1, x, y, z};
    }

    private static float[] s(float x, float y, float z) {
        return new float[] {2, x, y, z};
    }

    private static float[][] ops(float[]... ops) {
        return ops;
    }

    static {
        TABLES.put(CompanionKind.BIA, new Table(ops(t(0.0F, -1.0F, -0.05F), s(0.65F, 0.65F, 0.65F)),
                new float[][][] { ops(r(20.0F, 1.0F, 0.0F, 0.0F)), ops(r(20.0F, 1.0F, 0.0F, 0.0F), t(0.15F, 0.0F, 0.0F)) },
                new float[][][] { ops(t(0.0F, -0.1F, 0.05F), r(40.0F, 1.0F, 0.0F, 0.0F)), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(-0.025F, -0.1F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(35.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.0F, 0.228F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)) }));
        TABLES.put(CompanionKind.BEE, new Table(ops(t(0.0F, -0.6F, 0.0F), s(0.4F, 0.4F, 0.4F)),
                new float[][][] { ops(r(20.0F, 1.0F, 0.0F, 0.0F)), ops(r(20.0F, 1.0F, 0.0F, 0.0F), t(0.1F, 0.0F, 0.0F)) },
                new float[][][] { ops(), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(0.0F, -0.14F, -0.17F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(0.0F, -0.14F, -0.17F), r(90.0F, 0.0F, 0.0F, 1.0F), t(0.067F, 0.0F, 0.0F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(290.0F, 1.0F, 0.0F, 0.0F)), ops(r(290.0F, 1.0F, 0.0F, 0.0F)), ops(r(290.0F, 1.0F, 0.0F, 0.0F)) }));
        TABLES.put(CompanionKind.SLIME, new Table(ops(t(0.0F, -1.25F, 0.0F), s(0.8F, 0.8F, 0.8F)),
                new float[][][] { ops(r(20.0F, 1.0F, 0.0F, 0.0F), t(-0.02F, 0.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(20.0F, 1.0F, 0.0F, 0.0F), t(0.15F, 0.0F, 0.0F)) },
                new float[][][] { ops(t(0.0F, 0.4F, -0.1F), r(-30.0F, 1.0F, 0.0F, 0.0F)), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F), r(120.0F, 0.0F, 1.0F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(-0.025F, -0.025F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(35.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.0F, 0.228F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(135.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, -0.05F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), r(30.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, -0.05F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)) }));
        TABLES.put(CompanionKind.JENNY, new Table(ops(t(0.0F, -1.25F, 0.0F), s(0.8F, 0.8F, 0.8F)),
                new float[][][] { ops(r(20.0F, 1.0F, 0.0F, 0.0F)), ops(r(20.0F, 1.0F, 0.0F, 0.0F), t(0.15F, 0.0F, 0.0F)) },
                new float[][][] { ops(t(0.0F, -0.1F, 0.05F), r(40.0F, 1.0F, 0.0F, 0.0F)), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(-0.025F, -0.1F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(35.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.0F, 0.228F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)) }));
        TABLES.put(CompanionKind.LUNA, new Table(ops(t(0.0F, -1.0F, 0.0F), s(0.65F, 0.65F, 0.65F)),
                new float[][][] { ops(r(150.0F, 1.0F, 0.0F, 0.0F)), ops(r(60.0F, 1.0F, 0.0F, 0.0F), t(0.12F, 0.0F, 0.0F)) },
                new float[][][] { ops(t(0.0F, 0.3F, -0.15F), r(-45.0F, 1.0F, 0.0F, 0.0F)), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F), r(120.0F, 0.0F, 1.0F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(-0.025F, -0.05F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(35.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.0F, 0.228F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(150.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.08F, -0.05F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), r(60.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.08F, -0.05F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)) }));
        TABLES.put(CompanionKind.ELLIE, new Table(ops(t(0.0F, -1.5F, 0.0F)),
                new float[][][] { ops(r(180.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F), t(0.2F, -0.2F, 0.0F)) },
                new float[][][] { ops(t(0.0F, 0.282F, 0.141F), t(0.0F, 0.0F, -0.05F)), ops(t(0.0F, 0.282F, 0.141F), t(0.165F, -0.45F, 0.0F), r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), r(180.0F, 0.0F, 1.0F, 0.0F), r(-27.0F, 0.0F, 1.0F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.4F, 0.0F, 0.228F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(180.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.1F, -0.07F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.239F, -0.1F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)) }));
        TABLES.put(CompanionKind.ALLIE, new Table(ops(t(0.0F, -1.1F, 0.0F), s(0.7F, 0.7F, 0.7F)),
                new float[][][] { ops(r(20.0F, 1.0F, 0.0F, 0.0F), t(-0.05F, 0.0F, 0.0F)), ops(r(20.0F, 1.0F, 0.0F, 0.0F), t(0.15F, 0.0F, 0.0F)) },
                new float[][][] { ops(t(-0.05F, -0.125F, 0.125F), r(50.0F, 1.0F, 0.0F, 0.0F)), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(-0.025F, -0.1F, -0.1F), r(10.0F, 1.0F, 0.0F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(35.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.0F, 0.228F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, 0.0F)) }));
        TABLES.put(CompanionKind.GALATH, new Table(ops(),
                new float[][][] { ops(r(20.0F, 1.0F, 0.0F, 0.0F)), ops(r(20.0F, 1.0F, 0.0F, 0.0F), t(0.15F, 0.0F, 0.0F)) },
                new float[][][] { ops(t(0.0F, 0.0F, 0.1F), r(30.0F, 1.0F, 0.0F, 0.0F)), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F), t(0.0F, 0.0F, 0.1F), r(30.0F, 1.0F, 0.0F, 0.0F), r(-29.0F, 1.0F, 0.0F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(0.0F, -0.05F, -0.05F), r(15.0F, 1.0F, 0.0F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(35.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.0F, 0.228F), t(0.0F, -0.05F, -0.05F), r(15.0F, 1.0F, 0.0F, 0.0F), t(0.3F, 0.2F, 0.0F), r(-30.0F, 1.0F, 0.0F, 0.0F), r(15.0F, 0.0F, 0.0F, 1.0F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F)) }));
        TABLES.put(CompanionKind.KOBOLD, new Table(ops(t(0.0F, -0.8F, 0.05F), s(0.5F, 0.5F, 0.5F)),
                new float[][][] { ops(r(20.0F, 1.0F, 0.0F, 0.0F)), ops(r(20.0F, 1.0F, 0.0F, 0.0F)) },
                new float[][][] { ops(t(0.0F, -0.1F, -0.083F)), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F), r(150.0F, 0.0F, 1.0F, 0.0F), t(0.0F, -0.35F, 0.0F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(0.0F, -0.3F, -0.13F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(35.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.0F, 0.228F), t(0.06F, 0.0F, -0.13F), r(60.0F, 0.0F, 1.0F, 0.0F), r(38.0F, 1.0F, 0.0F, 0.0F), r(90.0F, 0.0F, 0.0F, 1.0F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(180.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(170.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(180.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), r(80.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), t(0.1F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), r(80.0F, 1.0F, 0.0F, 0.0F)) }));
        TABLES.put(CompanionKind.GOBLIN, new Table(ops(t(0.0F, -0.77F, -0.05F), s(0.5F, 0.5F, 0.5F)),
                new float[][][] { ops(), ops() },
                new float[][][] { ops(t(-0.02F, -0.05F, -0.05F)), ops(r(-90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 0.0F, 1.0F), t(0.0F, 0.165F, 0.0F), r(150.0F, 0.0F, 1.0F, 0.0F), t(0.0F, -0.33F, -0.1F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), t(0.0F, -0.265F, -0.04F)), ops(r(180.0F, 0.0F, 1.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F), r(-90.0F, 0.0F, 1.0F, 0.0F), r(35.0F, 0.0F, 0.0F, 1.0F), r(-20.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.0F, 0.228F), t(0.0F, 0.2F, -0.25F), r(85.0F, 0.0F, 1.0F, 0.0F), r(38.0F, 1.0F, 0.0F, 0.0F), r(90.0F, 0.0F, 0.0F, 1.0F)) },
                new float[][][] { ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(180.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, -0.03F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(170.0F, 1.0F, 0.0F, 0.0F)), ops(r(90.0F, 1.0F, 0.0F, 0.0F), r(180.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, -0.03F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), r(70.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, -0.03F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), t(0.1F, 0.0F, 0.0F), r(90.0F, 1.0F, 0.0F, 0.0F)), ops(r(200.0F, 1.0F, 0.0F, 0.0F), r(70.0F, 1.0F, 0.0F, 0.0F), t(0.0F, 0.05F, -0.03F)) }));
    }

    private FormRenderExtras() {
    }

    private static void apply(PoseStack poseStack, float[][] ops) {
        for (float[] op : ops) {
            switch ((int) op[0]) {
                case 0 -> {
                    if (op[2] != 0.0F) {
                        poseStack.mulPose(Axis.XP.rotationDegrees(op[1] * Math.signum(op[2])));
                    } else if (op[3] != 0.0F) {
                        poseStack.mulPose(Axis.YP.rotationDegrees(op[1] * Math.signum(op[3])));
                    } else if (op[4] != 0.0F) {
                        poseStack.mulPose(Axis.ZP.rotationDegrees(op[1] * Math.signum(op[4])));
                    }
                }
                case 1 -> poseStack.translate(op[1], op[2], op[3]);
                default -> poseStack.scale(op[1], op[2], op[3]);
            }
        }
    }

    static Player owner(CompanionEntity form) {
        return form.formOwner().map(form.level()::getPlayerByUUID).orElse(null);
    }

    /** Per render pass of one form: the stacks (main hand first, swapped when only the offhand bow is drawn). */
    static final class Pass {
        ItemStack main = ItemStack.EMPTY;
        ItemStack offhand = ItemStack.EMPTY;
        boolean using;
        boolean sneaking;
        Player owner;
        float partialTick;
    }

    static Pass begin(CompanionEntity form, float partialTick) {
        Pass pass = new Pass();
        pass.owner = owner(form);
        pass.partialTick = partialTick;
        if (pass.owner != null) {
            pass.main = pass.owner.getMainHandItem();
            pass.offhand = pass.owner.getOffhandItem();
            pass.using = pass.owner.isUsingItem();
            pass.sneaking = pass.owner.isShiftKeyDown();
            if (pass.using && pass.offhand.getItem() instanceof BowItem) {
                // original dm.renderRecursively: an offhand bow in use is drawn in the main hand
                ItemStack swap = pass.main;
                pass.main = pass.offhand;
                pass.offhand = swap;
            }
        }
        return pass;
    }

    /** Original dm.renderRecursively before the bone: lean, bow / shield arms, Slime and Allie bones. Returns what to restore. */
    static float[] poseBone(CompanionEntity form, Pass pass, GeoBone bone) {
        float[] saved = {bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getPosY()};
        String name = bone.getName();
        if (pass.sneaking) {
            if (name.equals("upperBody")) {
                bone.setRotX(bone.getRotX() - 0.5F);
            } else if (name.equals("head")) {
                bone.setRotX(bone.getRotX() + 0.5F);
            }
        }
        if (pass.using && (pass.main.getItem() instanceof BowItem || pass.offhand.getItem() instanceof BowItem)) {
            float pitch = form.getXRot() / 50.0F;
            if (name.equals("armR")) {
                bone.setRotX(bone.getRotX() - pitch);
            } else if (name.equals("armL")) {
                bone.setRotY(bone.getRotY() - pitch);
            }
        }
        if (pass.using && pass.main.getItem() instanceof ShieldItem && name.equals("armR")) {
            bone.setRotZ(0.0F);
            bone.setRotX(0.5F);
        }
        if (pass.using && pass.offhand.getItem() instanceof ShieldItem && name.equals("armL")) {
            bone.setRotZ(0.0F);
            bone.setRotX(0.5F);
        }
        if (form.kind() == CompanionKind.SLIME) {
            slimeBones(form, bone);
        }
        if (form.kind() == CompanionKind.ALLIE) {
            allieSway(form, pass, bone);
        }
        return saved;
    }

    static void restore(GeoBone bone, float[] saved) {
        bone.setRotX(saved[0]);
        bone.setRotY(saved[1]);
        bone.setRotZ(saved[2]);
        bone.setPosY(saved[3]);
    }

    /** Original dm: in her own first-person view (not in the inventory) her head and neck are not drawn. */
    static boolean hidesHead(CompanionEntity form, GeoBone bone) {
        if (!bone.getName().equals("head") && !bone.getName().equals("neck")) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && form.formOwner().filter(minecraft.player.getUUID()::equals).isPresent()
                && minecraft.options.getCameraType().isFirstPerson()
                && !(minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
                && !(minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)
                && !PlayerFormClient.drawingPickerPlayer;
    }

    /** Original dm.a(BufferBuilder, GeoBone, boolean): the held item at the weapon / offhand bone, drawn third-person right hand. */
    static boolean heldItem(CompanionEntity form, Pass pass, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        boolean offhand = bone.getName().equals("offhand");
        if (!offhand && !bone.getName().equals("weapon")) {
            return false;
        }
        ItemStack stack = offhand ? pass.offhand : pass.main;
        Table table = TABLES.get(form.kind());
        if (stack.isEmpty() || table == null) {
            return false;
        }
        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, bone);
        UseAnim use = stack.getUseAnimation();
        int side = offhand ? 1 : 0;
        if (use == UseAnim.BOW) {
            apply(poseStack, table.bow()[side]);
        } else if (use == UseAnim.BLOCK) {
            apply(poseStack, table.block()[side * 2 + (pass.using ? 1 : 0)]);
        }
        apply(poseStack, table.hold()[side * 3 + (use == UseAnim.BOW ? 1 : use == UseAnim.BLOCK ? 2 : 0)]);
        poseStack.scale(0.75F, 0.75F, 0.75F);
        ItemRenderer items = Minecraft.getInstance().getItemRenderer();
        // the player draws the bow / raises the shield: drawn for her so the item shows the same state
        items.renderStatic(pass.owner != null ? pass.owner : form, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
                poseStack, buffers, form.level(), packedLight, OverlayTexture.NO_OVERLAY, form.getId());
        poseStack.popPose();
        return true;
    }

    /** Original dm.a(BufferBuilder, GeoBone, Color) with bu and the form's c(): her player's elytra, hung from her head bone. */
    static boolean elytra(CompanionEntity form, Pass pass, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (!bone.getName().equals("head") || !form.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            return false;
        }
        Table table = TABLES.get(form.kind());
        if (table == null) {
            return false;
        }
        if (elytraModel == null) {
            elytraModel = new ElytraModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.ELYTRA));
        }
        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, bone);
        apply(poseStack, table.elytra());
        poseStack.translate(0.0F, 0.0F, 0.125F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        Player animated = pass.owner;
        if (animated != null) {
            elytraModel.setupAnim(animated, animated.walkAnimation.position(pass.partialTick), animated.walkAnimation.speed(pass.partialTick),
                    animated.tickCount + pass.partialTick, 0.0F, 0.0F);
        }
        ItemStack elytra = form.getItemBySlot(EquipmentSlot.CHEST);
        var consumer = net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(ELYTRA),
                elytra.hasFoil());
        elytraModel.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        return true;
    }

    // ---------------------------------------------------------------- Slime (d5.a(String, GeoBone))

    private static final Map<Integer, float[][]> SLIME = new HashMap<>();

    /** Original d5: figure follows the slime bone, dress the upper body, hat the head, boobsSlime the boobs. */
    private static void slimeBones(CompanionEntity form, GeoBone bone) {
        float[][] saved = SLIME.computeIfAbsent(form.getId(), id -> new float[5][]);
        switch (bone.getName()) {
            case "slime" -> saved[0] = new float[] {bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getScaleX(), bone.getScaleY(),
                    bone.getScaleZ(), bone.getPosX(), bone.getPosY(), bone.getPosZ()};
            case "upperBody" -> saved[1] = new float[] {bone.getRotX(), bone.getRotY(), bone.getRotZ()};
            case "torso" -> saved[2] = new float[] {bone.getRotX(), bone.getRotY(), bone.getRotZ()};
            case "head" -> saved[3] = new float[] {bone.getRotX(), bone.getRotY(), bone.getRotZ()};
            case "boobs" -> saved[4] = new float[] {bone.getRotX(), bone.getRotY(), bone.getRotZ()};
            case "figure" -> {
                if (saved[0] != null) {
                    bone.setRotX(saved[0][0]);
                    bone.setRotY(saved[0][1]);
                    bone.setRotZ(saved[0][2]);
                    bone.setScaleX(saved[0][3]);
                    bone.setScaleY(saved[0][4]);
                    bone.setScaleZ(saved[0][5]);
                    bone.setPosX(saved[0][6]);
                    bone.setPosY(saved[0][7]);
                    bone.setPosZ(saved[0][8]);
                }
            }
            case "dress" -> copyRotation(bone, saved[1]);
            case "hat" -> copyRotation(bone, saved[3]);
            case "boobsSlime" -> copyRotation(bone, saved[4]);
            default -> {
            }
        }
    }

    private static void copyRotation(GeoBone bone, float[] rotation) {
        if (rotation != null) {
            bone.setRotX(rotation[0]);
            bone.setRotY(rotation[1]);
            bone.setRotZ(rotation[2]);
        }
    }

    // ---------------------------------------------------------------- Allie (dv)

    /** Original dv fields per form: player position now / before (C, z / A, D), sway now / before (G, I / F, B), lift (L / H). */
    private static final class Sway {
        double x;
        double z;
        double prevX;
        double prevZ;
        float forward;
        float side;
        float prevForward;
        float prevSide;
        double lift;
        double prevLift;
    }

    private static final Map<Integer, Sway> SWAY = new HashMap<>();

    /** Original dv$a (ClientTick): her player's position steps on, the last frame's sway becomes the "before" values. */
    static void tickSway() {
        Minecraft minecraft = Minecraft.getInstance();
        SWAY.entrySet().removeIf(entry -> minecraft.level == null || !(minecraft.level.getEntity(entry.getKey()) instanceof CompanionEntity));
        SLIME.keySet().removeIf(id -> minecraft.level == null || minecraft.level.getEntity(id) == null);
        for (var entry : SWAY.entrySet()) {
            Sway sway = entry.getValue();
            sway.prevForward = sway.forward;
            sway.prevSide = sway.side;
            sway.prevLift = sway.lift;
            if (minecraft.level.getEntity(entry.getKey()) instanceof CompanionEntity form) {
                Player owner = owner(form);
                if (owner != null) {
                    sway.prevX = sway.x;
                    sway.prevZ = sway.z;
                    sway.x = owner.getX();
                    sway.z = owner.getZ();
                }
            }
        }
    }

    /** Original dv.a(String, GeoBone): tail and arms swing with her speed, her body floats 5 up while she hardly moves. */
    private static void allieSway(CompanionEntity form, Pass pass, GeoBone bone) {
        if (form.isInScene() || form.formWait() != 0) {
            return; // original em.G
        }
        Sway sway = SWAY.computeIfAbsent(form.getId(), id -> new Sway());
        double dx = sway.x - sway.prevX;
        double dz = sway.z - sway.prevZ;
        double yaw = Math.toRadians(form.getYRot());
        float sideways = (float) (dx * Math.cos(yaw) + dz * Math.sin(yaw));
        float forwards = (float) (-dx * Math.sin(yaw) + dz * Math.cos(yaw));
        String name = bone.getName();
        if (name.equals("body")) {
            sway.lift = net.minecraft.util.Mth.clamp((Math.abs(dx) + Math.abs(dz)) * 5.0D, 0.0D, 1.0D);
            double t = net.minecraft.util.Mth.lerp(pass.partialTick, sway.prevLift, sway.lift);
            bone.setPosY((float) net.minecraft.util.Mth.lerp(t, 5.0D, 0.0D));
            return;
        }
        float g = net.minecraft.util.Mth.clamp(forwards * -8.0F, -1.68F, 1.68F);
        float i = net.minecraft.util.Mth.clamp(sideways * 8.0F, -1.68F, 1.68F);
        sway.forward = g;
        sway.side = i;
        g = net.minecraft.util.Mth.lerp(pass.partialTick, sway.prevForward, g);
        i = net.minecraft.util.Mth.lerp(pass.partialTick, sway.prevSide, i);
        if (name.equals("tail")) {
            bone.setRotX(g);
            bone.setRotZ(i);
            return;
        }
        var action = form.action();
        if (action == com.fartypoopy.sexmod.entity.CompanionAction.BOW) {
            return;
        }
        if (name.equals("armL")) {
            bone.setRotX(g * 0.15F);
            bone.setRotZ(-0.34906584F + i * 0.15F);
        }
        if (action == com.fartypoopy.sexmod.entity.CompanionAction.ATTACK) {
            return;
        }
        if (name.equals("armR")) {
            bone.setRotX(g * 0.15F);
            bone.setRotZ(0.34906584F + i * 0.15F);
        }
    }

    /** Original e5.aq / i(): Allie's name tag rides 0.3 higher while she floats. */
    static float allieLift(CompanionEntity form, float partialTick) {
        Sway sway = SWAY.get(form.getId());
        if (sway == null) {
            return 0.3F;
        }
        return (float) net.minecraft.util.Mth.lerp(net.minecraft.util.Mth.lerp(partialTick, sway.prevLift, sway.lift), 0.3D, 0.0D);
    }

    /** Original ei.i() per form: where her player's name tag goes. */
    static float nameTagHeight(CompanionEntity form, float partialTick) {
        return switch (form.kind()) {
            case JENNY -> 1.75F;
            case ELLIE -> 2.05F;
            case BIA -> 1.5F;
            case SLIME, LUNA -> 1.6F;
            case BEE -> 1.4F;
            case ALLIE -> 1.9F + allieLift(form, partialTick);
            case KOBOLD -> 1.4F - (0.25F - form.koboldSize());
            case GOBLIN -> 0.9F;
            case GALATH -> 2.3F;
            default -> 1.8F;
        };
    }
}
