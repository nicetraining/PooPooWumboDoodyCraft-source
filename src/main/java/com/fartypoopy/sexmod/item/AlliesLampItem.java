package com.fartypoopy.sexmod.item;

import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The original lamp: right-click starts a 95 tick rub (HUD hidden, sparkles growing from tick 50), then Allie
 * appears two blocks in front of the player. The wish counter is the item's {@code sexmodUses}; three wishes.
 */
public final class AlliesLampItem extends LegacyPurposeItem implements software.bernie.geckolib.animatable.GeoItem {
    private static final int MAX_WISHES = 3;
    private static final int RUB_TICKS = 95;
    private static final int PARTICLES_FROM = 50;

    public static final String USES_KEY = "sexmodUses";
    /** Player persistent data, read by the client to hide the HUD while rubbing. */
    public static final String IN_USE_KEY = "sexmodAllieInUse";
    private static final String TICKS_KEY = "sexmodAllieInUseTicks";

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    public AlliesLampItem(Properties properties) {
        super(properties, "allies_lamp", 0);
        software.bernie.geckolib.animatable.SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /** Original ap predicate: while the player rubs the lamp it plays animation.lamp.rub (and holds its last frame), else nothing. */
    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "controller", 0, state -> {
            if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient() || !rubbing()) {
                state.getController().forceAnimationReset();
                return software.bernie.geckolib.animation.PlayState.STOP;
            }
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.lamp.rub"));
        }));
    }

    private static boolean rubbing() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.getPersistentData().getBoolean(IN_USE_KEY);
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public static int uses(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(USES_KEY);
    }

    private static void setUses(ItemStack stack, int uses) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(USES_KEY, uses));
    }

    private static boolean busy(Level level, Player player) {
        for (CompanionEntity companion : level.getEntitiesOfClass(
                CompanionEntity.class, player.getBoundingBox().inflate(48.0D))) {
            if (companion.scenePartnerId().filter(player.getUUID()::equals).isPresent()
                    || (companion.kind() == CompanionKind.ALLIE && companion.isMaster(player))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag data = player.getPersistentData();
        if (uses(stack) >= MAX_WISHES || data.getBoolean(IN_USE_KEY) || busy(level, player)) {
            return InteractionResultHolder.pass(stack);
        }
        data.putBoolean(IN_USE_KEY, true);
        data.putInt(TICKS_KEY, 0);
        return InteractionResultHolder.consume(stack); // 1.12 does not swing the arm for an item use (success would)
    }

    /** Original cj.a(world, particle, pos, count, radius, rise): a ring of particles, each rising at random(0..rise). */
    private static void ring(Level level, Vec3 at, int count, double radius, double rise) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            level.addParticle(ParticleTypes.ENCHANTED_HIT, at.x + Math.sin(angle) * radius, at.y, at.z + Math.cos(angle) * radius,
                    0.0D, level.getRandom().nextFloat() * rise, 0.0D);
        }
    }

    private static Vec3 front(Player player) {
        return player.position().add(Vec3.directionFromRotation(0.0F, player.getYHeadRot()).scale(2.0D));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!(entity instanceof Player player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(IN_USE_KEY)) {
            return;
        }
        boolean held = stack == player.getMainHandItem() || stack == player.getOffhandItem();
        if (!held) {
            return; // original ap: the rub only goes on (and the HUD stays hidden) while the lamp is in hand
        }

        int ticks = data.getInt(TICKS_KEY);
        data.putInt(TICKS_KEY, ticks + 1);
        if (ticks > PARTICLES_FROM && ticks < RUB_TICKS && level.isClientSide) {
            // original cj.a on the rubbing player's client: a widening ring of magic crits sinking from eye height
            double progress = (double) (ticks - PARTICLES_FROM) / (double) (RUB_TICKS - PARTICLES_FROM);
            double eased = -(Math.cos(Math.PI * progress) - 1.0D) / 2.0D;
            Vec3 at = front(player).add(0.0D, player.getEyeHeight() * (1.0D - eased), 0.0D);
            ring(level, at, (int) (eased * 150.0D), eased * 0.75D, eased);
        }
        if (ticks < RUB_TICKS) {
            return;
        }
        if (level.isClientSide) {
            ring(level, front(player), 150, 0.75D, 2.0D);
        }

        data.putBoolean(IN_USE_KEY, false);
        data.putInt(TICKS_KEY, 0);
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            int uses = uses(stack) + 1;
            setUses(stack, uses); // written before she appears, as in the original
            CompanionEntity.summonAllie(serverPlayer, uses);
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        // Original: nothing on a fresh lamp, then "2 wishes left", "1 wish left", "no wishes left".
        if (stack.get(DataComponents.CUSTOM_DATA) == null) {
            return;
        }
        int left = MAX_WISHES - uses(stack);
        String text = switch (left) {
            case 2 -> "2 wishes left";
            case 1 -> "1 wish left";
            case 0 -> "no wishes left";
            default -> null;
        };
        if (text != null) {
            tooltip.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
        }
    }
}
