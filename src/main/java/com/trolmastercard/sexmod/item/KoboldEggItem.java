package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.entity.KoboldEggEntity;
import com.trolmastercard.sexmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

public final class KoboldEggItem extends LegacyPurposeItem {
    public KoboldEggItem(Properties properties) {
        super(properties, "kobold_egg_item", 1);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos position = context.getClickedPos().relative(context.getClickedFace());
        KoboldEggEntity egg = ModEntities.KOBOLD_EGG.get().create(serverLevel);
        if (egg == null) {
            return InteractionResult.FAIL;
        }

        egg.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        egg.setYRot(player.getYRot());
        egg.setOwnerId(player.getUUID());
        egg.setTribeId(egg.getUUID());
        if (!serverLevel.addFreshEntity(egg)) {
            return InteractionResult.FAIL;
        }

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.sexmod.kobold_egg_placed"), true);
        return InteractionResult.sidedSuccess(false);
    }
}
