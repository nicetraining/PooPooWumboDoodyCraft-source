package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class NpcEditorWandItem extends LegacyPurposeItem {
    public NpcEditorWandItem(Properties properties) {
        super(properties, "npc_editor_wand", 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.sexmod.editor_use_on_companion"), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity target,
            InteractionHand hand
    ) {
        if (!(target instanceof CompanionEntity companion)) {
            return InteractionResult.PASS;
        }

        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!companion.openControlMenu(serverPlayer)) {
                player.displayClientMessage(
                        Component.translatable("message.sexmod.editor_denied", companion.getDisplayName()),
                        true
                );
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
