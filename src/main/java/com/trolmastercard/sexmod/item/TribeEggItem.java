package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.tribe.TribeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Original b9: right-click within 5 blocks of a block and a wild tribe of Kobolds appears there. */
public final class TribeEggItem extends LegacyPurposeItem {
    public TribeEggItem(Properties properties) {
        super(properties, "tribe_egg", 0);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(5.0D));
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel serverLevel) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            TribeManager.spawnTribe(serverLevel, hit.getLocation());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
