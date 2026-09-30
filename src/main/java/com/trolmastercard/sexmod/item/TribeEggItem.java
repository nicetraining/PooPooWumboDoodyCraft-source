package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import java.util.ArrayList;
import java.util.List;

public final class TribeEggItem extends LegacyPurposeItem {
    private static final int[][] OFFSETS = {
            {0, 0},
            {1, 0},
            {-1, 0},
            {0, 1}
    };

    public TribeEggItem(Properties properties) {
        super(properties, "tribe_egg", 1);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (context.getLevel() instanceof ServerLevel serverLevel) {
            if (!CompanionItemSupport.hasCapacity(serverLevel, player, CompanionKind.KOBOLD, OFFSETS.length)) {
                CompanionItemSupport.showCapacityLimit(player);
                return InteractionResult.FAIL;
            }

            BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
            List<CompanionEntity> spawned = new ArrayList<>(OFFSETS.length);

            for (int[] offset : OFFSETS) {
                CompanionEntity kobold = CompanionItemSupport.spawn(
                        serverLevel,
                        CompanionKind.KOBOLD,
                        origin.offset(offset[0], 0, offset[1]),
                        player,
                        false
                );
                if (kobold == null) {
                    spawned.forEach(CompanionEntity::discard);
                    return InteractionResult.FAIL;
                }
                kobold.setAppearanceVariant(serverLevel.getRandom().nextInt(
                        CompanionKind.KOBOLD.appearanceVariants()
                ));
                spawned.add(kobold);
            }

            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }

            player.displayClientMessage(
                    Component.translatable("message.sexmod.tribe_created", spawned.size()),
                    true
            );
        }

        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
