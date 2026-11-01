package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.fartypoopy.sexmod.entity.CompanionAction;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.GoblinBehavior;
import com.fartypoopy.sexmod.entity.LunaFishing;
import com.fartypoopy.sexmod.registry.ModItems;
import com.fartypoopy.sexmod.tribe.KoboldBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

/**
 * The items the original NPC renderers draw at a bone, at the parent's matrix moved to the bone's pivot (p.a) and before
 * the bone's own transform: d_.a(BufferBuilder, GeoBone) on "weapon" (with dp.a(ItemStack) for Luna's rod and d6 / dj / dy
 * for Kobolds and goblins), d_.b on "itemRenderer" (payment) and dp's "offhand" case (Luna's catch).
 */
final class NpcItems {
    private NpcItems() {
    }

    /** True when something was drawn (the caller then takes the shared buffer afresh). */
    static boolean draw(CompanionEntity girl, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick) {
        if (girl.kind() == CompanionKind.MANGLELIE) {
            return manglelieBow(girl, bone, poseStack, buffers, light, partialTick);
        }
        return switch (bone.getName()) {
            case "weapon" -> weapon(girl, bone, poseStack, buffers, light);
            case "itemRenderer" -> payment(girl, bone, poseStack, buffers, light);
            case "offhand" -> lunaCatch(girl, bone, poseStack, buffers, light);
            default -> false;
        };
    }

    private static boolean weapon(CompanionEntity girl, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers, int light) {
        return switch (girl.kind()) {
            case JENNY, BIA -> e2Weapon(girl, bone, poseStack, buffers, light, 140, 50, 140, 0.0F, -0.03F, -0.2F);
            case ELLIE -> e2Weapon(girl, bone, poseStack, buffers, light, -85, -175, -85, -0.1F, 0.05F, 0.0F);
            case LUNA -> e2Weapon(girl, bone, poseStack, buffers, light, 230, 150, 320, 0.0F, -0.06F, 0.1F);
            case KOBOLD -> tribeWeapon(girl, bone, poseStack, buffers, light, koboldTool(girl), 1.0F);
            case GOBLIN -> {
                if (girl.goblin() == null) {
                    yield false;
                }
                int state = girl.goblinState();
                yield state == GoblinBehavior.RUN || state == GoblinBehavior.CATCH
                        ? tribeWeapon(girl, bone, poseStack, buffers, light, girl.goblinItem(), state == GoblinBehavior.CATCH ? 0.5F : 1.0F)
                        : false;
            }
            default -> false;
        };
    }

    /**
     * Original d_.a with e2.P / O / K / V: weapon mode 1 shows slot 0, 2 the bow (slot 1). A bow is turned by K; the first
     * attack animation (S == 0) moves the weapon by V and turns it by O; otherwise P.
     */
    private static boolean e2Weapon(CompanionEntity girl, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers, int light,
                                    int rest, int strike, int bowAngle, float vx, float vy, float vz) {
        CompanionAction action = girl.action();
        if (action != CompanionAction.BOW) {
            girl.bowDraw = 0.0F;
        }
        int mode = girl.weaponMode();
        ItemStack stack = mode == 1 ? girl.getMainHandItem() : mode == 2 ? girl.getOffhandItem() : null;
        if (girl.kind() == CompanionKind.LUNA
                && (girl.fishState() == LunaFishing.START || girl.fishState() == LunaFishing.IDLE)) {
            // original dp.a(ItemStack): her own rod, with the enchantments of the rod she keeps (eb.az)
            stack = new ItemStack(ModItems.LUNA_ROD.get());
            ItemStack stored = girl.lunaStoredRod();
            if (!stored.isEmpty()) {
                stack.set(DataComponents.ENCHANTMENTS, stored.get(DataComponents.ENCHANTMENTS));
            }
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.BOW) && action == CompanionAction.BOW) {
            // the renderer pulls the bow 0.015 per frame and hands it to the item model as the item in use
            girl.bowDraw += 0.015F;
            girl.renderUseItem = stack;
            girl.renderUseRemaining = Math.round(-girl.bowDraw * 20.0F + stack.getUseDuration(girl));
        }
        poseStack.pushPose();
        try {
            RenderUtil.translateToPivotPoint(poseStack, bone);
            if (stack.getItem() instanceof BowItem) {
                poseStack.mulPose(Axis.XP.rotationDegrees(bowAngle));
            } else if (action == CompanionAction.ATTACK && girl.attackCycle == 0) {
                poseStack.translate(vx, vy, vz);
                poseStack.mulPose(Axis.XP.rotationDegrees(strike));
            } else {
                poseStack.mulPose(Axis.XP.rotationDegrees(rest));
            }
            render(girl, stack, poseStack, buffers, light);
        } finally {
            poseStack.popPose();
            girl.renderUseItem = null;
        }
        return true;
    }

    /** Original dj.a(ItemStack): mining with a pickaxe (an axe while felling), a sword while attacking or when the tribe is attacked. */
    private static ItemStack koboldTool(CompanionEntity girl) {
        if (girl.isInScene()) {
            return ItemStack.EMPTY;
        }
        return switch (girl.koboldState()) {
            case KoboldBehavior.ST_WORK -> new ItemStack(girl.koboldChopping() ? Items.IRON_AXE : Items.IRON_PICKAXE);
            case KoboldBehavior.ST_ATTACK -> new ItemStack(Items.IRON_SWORD);
            case 0 -> girl.koboldUnderAttack() ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
    }

    /**
     * Original dh.a(BufferBuilder, String, GeoBone): while Manglelie shoots, a bow in her "weapon" hand when the target is
     * more than 0.35 to the side in her mother's head frame, else on "offhand"; drawn over 28 ticks (easeOutQuart), then
     * held undrawn.
     */
    private static boolean manglelieBow(CompanionEntity girl, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers,
                                        int light, float partialTick) {
        boolean weaponBone = bone.getName().equals("weapon");
        if (!weaponBone && !bone.getName().equals("offhand")) {
            return false;
        }
        net.minecraft.world.entity.Entity target = girl.mangTarget();
        if (target == null) {
            return false;
        }
        boolean rightHand = false;
        CompanionEntity mother = ManglelieRide.mother(girl);
        if (mother != null) {
            net.minecraft.world.phys.Vec3 delta = target.getPosition(partialTick).subtract(girl.getPosition(partialTick));
            double yaw = Math.toRadians(net.minecraft.util.Mth.rotLerp(partialTick, mother.yHeadRotO, mother.yHeadRot));
            rightHand = -Math.cos(yaw) * delta.x - Math.sin(yaw) * delta.z > 0.35D;
        }
        if (rightHand != weaponBone) {
            return false;
        }
        ItemStack bow = new ItemStack(Items.BOW);
        long start = girl.mangShotStart();
        float progress = start == -1L ? 0.0F : (girl.level().getGameTime() - start + partialTick) / 28.0F;
        if (progress < 1.0F) {
            float eased = (float) (1.0D - Math.pow(1.0D - progress, 4.0D));
            girl.renderUseItem = bow;
            girl.renderUseRemaining = (int) (11.0F * (1.0F - eased) + 71980.0F);
        }
        poseStack.pushPose();
        try {
            RenderUtil.translateToPivotPoint(poseStack, bone);
            if (weaponBone) {
                poseStack.translate(-0.01F, 0.0F, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(120.0F));
            } else {
                poseStack.translate(0.15F, 0.0F, -0.05F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-140.0F));
            }
            poseStack.scale(0.7F, 0.7F, 0.7F);
            render(girl, bow, poseStack, buffers, light);
        } finally {
            poseStack.popPose();
            girl.renderUseItem = null;
        }
        return true;
    }

    /** Original d6.a: scaled, then turned -90 about X (dy: 180 for stackable non-block items). */
    private static boolean tribeWeapon(CompanionEntity girl, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers, int light,
                                       ItemStack stack, float scale) {
        if (stack.isEmpty()) {
            return false;
        }
        float angle = girl.kind() == CompanionKind.GOBLIN && !(stack.getItem() instanceof BlockItem) && stack.getMaxStackSize() != 1
                ? 180.0F : -90.0F;
        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, bone);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        render(girl, stack, poseStack, buffers, light);
        poseStack.popPose();
        return true;
    }

    /**
     * Original d_.b: during her payment one item per count, turned by the bone's rotation values taken as degrees (+2.5 about
     * Z), the 2nd and 3rd fanned out by -15 / +15 degrees, all scaled by em.n.
     */
    private static boolean payment(CompanionEntity girl, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (girl.currentScene() == null || !girl.currentScene().isPayment()) {
            return false;
        }
        ItemStack stack = girl.paymentItem();
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack single = stack.copyWithCount(1);
        for (int index = 0; index < stack.getCount(); index++) {
            poseStack.pushPose();
            RenderUtil.translateToPivotPoint(poseStack, bone);
            poseStack.mulPose(Axis.ZP.rotationDegrees(bone.getRotX() + 2.5F));
            poseStack.mulPose(Axis.YP.rotationDegrees(bone.getRotY()));
            poseStack.mulPose(Axis.XP.rotationDegrees(bone.getRotZ()));
            if (index == 1) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-15.0F));
                poseStack.translate(0.0F, 0.0F, -0.025F);
            } else if (index == 2) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(15.0F));
                poseStack.translate(0.0F, 0.0F, 0.025F);
            }
            poseStack.scale(girl.paymentScale, girl.paymentScale, girl.paymentScale);
            render(girl, single, poseStack, buffers, light);
            poseStack.popPose();
        }
        return true;
    }

    /** Original dp "offhand": Luna's catch once renderItem showed it, turned 90 about X and shrunk per bite (eb.aa). */
    private static boolean lunaCatch(CompanionEntity girl, GeoBone bone, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (girl.kind() != CompanionKind.LUNA || !girl.lunaItemShown) {
            return false;
        }
        ItemStack caught = girl.fishCaught();
        if (caught.isEmpty()) {
            return false;
        }
        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, bone);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(girl.lunaItemScale, girl.lunaItemScale, girl.lunaItemScale);
        render(girl, caught, poseStack, buffers, light);
        poseStack.popPose();
        return true;
    }

    private static void render(CompanionEntity girl, ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Minecraft.getInstance().getItemRenderer().renderStatic(girl, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
                poseStack, buffers, girl.level(), light, OverlayTexture.NO_OVERLAY, girl.getId());
    }
}
